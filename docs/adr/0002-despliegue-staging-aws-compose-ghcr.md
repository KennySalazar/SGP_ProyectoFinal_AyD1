# ADR-0002: Despliegue en AWS con EC2, RDS, S3, Compose y GHCR

## Estado

Aceptada para staging el 10 de octubre de 2026. La infraestructura AWS todavía
no ha sido creada ni validada. La configuración y el despliegue de producción
permanecen fuera del alcance de esta historia.

## Contexto

El SGP debe contar con desarrollo, staging y producción separados, imágenes
contenedorizadas, Nginx como único punto de entrada, PostgreSQL/PostGIS,
almacenamiento de objetos, configuración externa y trazabilidad entre commit,
imagen y despliegue.

El enunciado propone cuatro servicios en Docker Compose: Nginx, backend,
PostgreSQL y MinIO. La sección 9.4 permite adaptar esos lineamientos a un
proveedor de nube pública. Además, el auxiliar aclaró que los archivos no pueden
residir en el mismo servidor de la aplicación. El equipo eligió AWS y dispone de
créditos limitados, por lo que debe evitar componentes innecesarios.

El backend todavía no implementa `AlmacenamientoArchivos` ni consume MinIO o S3.
La infraestructura debe reflejar esa limitación sin agregar credenciales o
variables que el código aún no utiliza.

## Problema

Se necesita construir y publicar imágenes fuera de AWS, actualizar staging
automáticamente desde `develop`, conservar datos fuera del ciclo de vida de EC2
y permitir un futuro despliegue manual de producción usando exactamente los
mismos artefactos.

La solución no debe requerir Kubernetes, exponer servicios de datos, guardar
claves AWS estáticas ni compilar Java o Angular en EC2.

## Alternativas consideradas

### Ejecución

- **EC2 con Docker Compose:** menor complejidad para dos contenedores de
  aplicación y operación conocida por el equipo.
- **ECS/Fargate:** integración administrada, pero añade configuración y costo.
- **EKS/Kubernetes:** complejidad desproporcionada para el proyecto.

### Construcción de imágenes

- **GitHub Actions:** descarga trabajo de CPU, memoria y disco fuera de EC2 y
  conserva trazabilidad.
- **Compilar en EC2:** descartado por consumo y por mezclar build con operación.
- **Solo ARM64:** suficiente para `t4g`, pero limita validaciones y portabilidad.
- **AMD64 y ARM64:** elegida; publica un índice OCI reutilizable en ambos tipos
  de host y compila Maven/Angular una sola vez sobre `BUILDPLATFORM`.

### Registro

- **GHCR:** integración directa con GitHub Actions y artefactos reutilizables.
- **Amazon ECR:** técnicamente válido, pero innecesario para las imágenes que ya
  se publican desde GitHub.

### Base de datos

- **PostgreSQL/PostGIS en cada EC2:** menor costo de servicio, pero duplica
  operación, respaldo, actualización y consumo de memoria.
- **Dos instancias RDS:** mejor aislamiento, pero duplica el componente más caro.
- **Una RDS PostgreSQL 16 con dos bases y credenciales separadas:** elegida por
  costo. Aísla los datos lógicamente, aunque comparte capacidad y punto de fallo.

### Archivos

- **MinIO/Silo en EC2:** descartado para AWS porque incumple la aclaración de no
  almacenar archivos en el servidor de aplicación.
- **Un bucket S3 con prefijos:** posible, pero aumenta el riesgo de permisos y
  reglas de ciclo de vida cruzados.
- **Un bucket S3 privado por ambiente:** elegido por aislamiento de IAM,
  versionado y ciclo de vida. MinIO permanece solo en desarrollo local.

### Administración

- **AWS Systems Manager:** elegido para operación y automatización sin publicar
  SSH ni administrar claves permanentes.
- **SSH público restringido:** simple, pero amplía la superficie de ataque.
- **VPN:** no se justifica para el tamaño del equipo.

## Decisión

1. Usar una EC2 Amazon Linux 2023 ARM64 independiente por ambiente:
   `t4g.micro` como punto de partida para staging y `t4g.small` propuesto para
   producción. Esta historia solo prepara staging.
2. Ejecutar en cada EC2 únicamente dos servicios Compose: la imagen
   Nginx/Angular y la imagen Spring Boot. No ejecutar PostgreSQL ni MinIO en AWS.
3. Mantener el `docker-compose.yml` raíz con PostgreSQL/PostGIS y MinIO para
   desarrollo local.
4. Usar una RDS PostgreSQL 16 privada con las bases `sgp_staging` y `sgp_prod`,
   cada una con usuario migrador y usuario de aplicación propios.
5. Crear `postgis`, `pg_trgm` y `uuid-ossp` una vez como administrador de RDS;
   Flyway conserva las migraciones existentes sin recibir `rds_superuser`.
6. Usar un bucket S3 privado por ambiente. La EC2 accederá mediante un rol IAM
   de mínimo privilegio y credenciales temporales; nunca mediante access keys en
   `.env`. La integración se habilitará cuando exista en el backend.
7. Exigir IMDSv2 y configurar hop limit `2` para que un SDK dentro de Docker
   pueda obtener credenciales del rol de instancia.
8. Construir backend y frontend en GitHub Actions para `linux/amd64` y
   `linux/arm64`, publicarlos por separado en GHCR y desplegar por digest.
9. Publicar solo desde `develop` después de las validaciones. La etiqueta
   `sha-<SHA completo>` aporta trazabilidad y `develop` es solo informativa.
10. Mantener publicación y despliegue en jobs separados; un fallo en AWS no
    obliga a reconstruir las imágenes.
11. Usar OIDC para que GitHub Actions asuma un rol limitado a SSM. La EC2 lee
    GHCR privado con un PAT classic de solo `read:packages`, instalado fuera del
    repositorio y de GitHub Actions.
12. Guardar la configuración de staging en
    `/etc/sgp/staging/staging.env`, modo `0600`, e impedir despliegues simultáneos
    con concurrencia de GitHub y `flock` en el servidor.
13. Mantener temporalmente Nginx en loopback hasta implementar y validar el
    acceso HTTPS público definido para la arquitectura final.

## Justificación

Docker Compose mantiene simple la operación de la EC2. GHCR y GitHub Actions
evitan usar la instancia pequeña para compilar. Los índices OCI permiten usar
instancias Graviton sin crear Dockerfiles por ambiente o arquitectura.

RDS mueve persistencia, actualización y respaldo fuera del host de aplicación.
Compartir una instancia reduce costo, mientras bases y roles separados impiden
que staging use los datos de producción. El Security Group de RDS debe aceptar
5432 solo desde los Security Groups de ambas EC2.

S3 satisface la obligación de almacenar archivos fuera de EC2. Dos buckets
permiten políticas y ciclos de vida independientes. Los roles de instancia
evitan distribuir claves estáticas y permiten restringir cada EC2 a su bucket.

Systems Manager evita un puerto SSH público. La configuración externa permite
promover los mismos digests entre ambientes sin reconstruir artefactos.

## Consecuencias

### Positivas

- La EC2 ejecuta solo frontend y backend y dispone de más memoria para la JVM.
- Recrear contenedores o la EC2 no elimina los datos de RDS o S3.
- Cada despliegue se relaciona con un commit y dos digests.
- Desarrollo local conserva su flujo actual sin depender de AWS.
- Producción puede seleccionar manualmente lo validado en staging.
- RDS, buckets, roles y credenciales se separan por ambiente.

### Negativas y riesgos

- RDS, S3, dos IPv4 públicas y dos EC2 generan costo incluso con poco tráfico;
  deben configurarse presupuestos y revisar créditos antes de crearlos.
- Staging y producción comparten capacidad y disponibilidad de una RDS.
- `t4g.micro` puede sufrir presión de memoria y debe medirse.
- El backend no puede validar todavía el flujo de archivos en S3.
- Un PAT classic con `read:packages` puede leer otros paquetes privados a los
  que tenga acceso la cuenta y requiere rotación manual.
- `sslmode=require` cifra la conexión RDS, pero la verificación de identidad con
  `verify-full` requiere distribuir y probar el bundle CA.
- El acceso a credenciales IAM desde contenedores requiere configurar IMDSv2 de
  forma consciente; un hop limit incorrecto impediría acceder a S3.

## Limitaciones

- No incluye creación de EC2, RDS, S3, IAM, DNS ni certificados.
- No incluye Compose ni despliegue de producción.
- No implementa respaldos, restauración ni retención centralizada de logs.
- S3 está decidido como servicio, pero su integración de aplicación permanece
  pendiente de la funcionalidad de archivos.
- Brotli y HTTPS público todavía no están implementados.
- La configuración RDS/S3 no ha sido probada contra recursos AWS reales.
- La publicación multi-arquitectura, OIDC, SSM y GHCR desde EC2 aún requieren
  validación real.

## Relación con el enunciado

La decisión conserva Docker Compose, Nginx, imágenes independientes,
configuración externa, healthchecks, reinicio automático, Flyway y separación de
ambientes. Se desvía de DT-DEP-01 al usar RDS y S3 administrados en vez de
contenedores PostgreSQL/MinIO en el servidor; la sección 9.4 permite adaptar el
despliegue a nube pública y la aclaración del auxiliar exige que los archivos no
residan en EC2.

S3 mantiene una API de almacenamiento de objetos compatible con la intención de
DT-ALM. MinIO continúa en desarrollo local. La interfaz intercambiable de
DT-ALM-03 sigue pendiente en el backend y no se declara implementada.

## Referencias

- [Extensiones de PostgreSQL en Amazon RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.Extensions.html).
- [Configuración de PostGIS en RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html).
- [Uso de TLS con RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).
- [Roles IAM para aplicaciones en EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).
- [IMDSv2 para cargas contenedorizadas](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html).
- [Autenticación en GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).
