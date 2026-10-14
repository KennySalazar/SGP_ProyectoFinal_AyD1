# ADR-0002: Despliegue en AWS con EC2, RDS, S3, Compose y GHCR

## Estado

Aceptada para staging el 10 de octubre de 2026. La región y VPC están
confirmadas; se crearon los Security Groups, el bucket S3, la red privada, el DB
Subnet Group, RDS y la EC2 de staging. Session Manager y el alcance TCP desde la
EC2 hacia RDS están verificados. El backend se autenticó, ejecutó Flyway y quedó
saludable durante el primer despliegue automático. La publicación
multi-arquitectura, la descarga ARM64, la asunción OIDC y Systems Manager también
fueron validados para la revisión
`488dae1ea7278eefc5f39e557eaadde4c07d00f9`.
La configuración y el despliegue de producción permanecen fuera del alcance de
esta historia.

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
- **Un bucket S3 privado por ambiente:** elegido porque permite aislar IAM,
  versionado y ciclo de vida por ambiente. MinIO permanece solo en desarrollo
  local.

### Administración

- **AWS Systems Manager:** elegido para operación y automatización sin publicar
  SSH ni administrar claves permanentes.
- **SSH público restringido:** simple, pero amplía la superficie de ataque.
- **VPN:** no se justifica para el tamaño del equipo.

## Decisión

1. Usar una EC2 Amazon Linux 2023 ARM64 independiente por ambiente:
   `t4g.micro` como punto de partida para staging y `t4g.small` propuesto para
   producción. Esta historia solo prepara staging.
2. Usar `us-east-1` y la VPC predeterminada existente para evitar crear una red
   adicional. La VPC dispone de DNS y seis subredes predeterminadas distribuidas
   en seis zonas. RDS utilizará dos subredes adicionales y dedicadas sin rutas a
   Internet Gateway o NAT y no tendrá acceso público.
3. Ejecutar en cada EC2 únicamente dos servicios Compose: la imagen
   Nginx/Angular y la imagen Spring Boot. No ejecutar PostgreSQL ni MinIO en AWS.
4. Mantener el `docker-compose.yml` raíz con PostgreSQL/PostGIS y MinIO para
   desarrollo local.
5. Usar una RDS PostgreSQL 16 privada y Single-AZ con las bases `sgp_staging` y
   `sgp_prod`, cada una con usuario migrador y usuario de aplicación propios. Se
   acepta la ausencia de conmutación automática para reducir costos en el
   contexto académico y con los créditos disponibles.
6. Crear `postgis`, `pg_trgm` y `uuid-ossp` una vez como administrador de RDS.
   Para crear la base con el migrador como propietario y configurar sus
   privilegios predeterminados, el administrador recibe membresía temporal,
   ejecuta las operaciones de propietario mediante `SET ROLE` y pierde las
   opciones `SET` e `INHERIT` al finalizar. Puede conservar el `ADMIN OPTION`
   que PostgreSQL 16 concede automáticamente al creador del rol, sin obtener por
   ello acceso como migrador. Flyway conserva las migraciones existentes sin
   recibir `rds_superuser`.
7. Usar un bucket S3 privado por ambiente. La EC2 accederá mediante un rol IAM
   de mínimo privilegio y credenciales temporales; nunca mediante access keys en
   `.env`. La integración se habilitará cuando exista en el backend.
8. Exigir IMDSv2 y configurar hop limit `2` para que un SDK dentro de Docker
   pueda obtener credenciales del rol de instancia.
9. Construir backend y frontend en GitHub Actions para `linux/amd64` y
   `linux/arm64`, publicarlos por separado en GHCR y desplegar por digest.
10. Publicar solo desde `develop` después de las validaciones. La etiqueta
   `sha-<SHA completo>` aporta trazabilidad y `develop` es solo informativa.
11. Mantener publicación y despliegue en jobs separados; un fallo en AWS no
    obliga a reconstruir las imágenes.
12. Usar OIDC para que GitHub Actions asuma un rol limitado a SSM. El job no
    depende de un GitHub Environment: consume `AWS_REGION`, `AWS_ROLE_ARN`,
    `STAGING_INSTANCE_ID` y el interruptor `STAGING_ENABLED` como variables de
    repositorio. Solo un `push` a `develop` puede desplegar y la confianza de AWS
    se limita al sujeto inmutable
    `repo:KennySalazar@99755600/SGP_ProyectoFinal_AyD1@1386435373:ref:refs/heads/develop`.
    La EC2 lee
    GHCR privado con un PAT classic de solo `read:packages`, instalado fuera del
    repositorio y de GitHub Actions.
13. Guardar la configuración de staging en
    `/etc/sgp/staging/staging.env`, modo `0600`, e impedir despliegues simultáneos
    con concurrencia de GitHub y `flock` en el servidor.
14. Mantener temporalmente Nginx en loopback hasta implementar y validar el
    acceso HTTPS público definido para la arquitectura final.
15. No comprar un dominio ni crear una zona DNS pública. Staging se administrará
    inicialmente mediante túnel SSM; producción deberá ofrecer HTTPS válido
    sobre una IPv4 pública. El mecanismo de emisión y renovación del certificado
    para IP continúa pendiente de validación.
16. Conservar para staging la IPv4 pública dinámica observada y no asignar una
    Elastic IP, dado el plazo académico hasta el 28 de octubre de 2026. Se acepta
    que detener e iniciar EC2 puede cambiar la IP y obligar a actualizar URLs y
    emitir otro certificado.

### Estado de implementación en AWS

En `us-east-1` se confirmaron los siguientes recursos de staging:

- `sgp-staging-ec2-sg` (`sg-0e617972b3eedeb73`), con entrada pública solo para
  TCP 80 y 443; los puertos internos 8088, 8090 y 5432 no están expuestos.
- `sgp-rds-sg` (`sg-0ab8d04a2ef4d3db8`), con PostgreSQL TCP 5432 permitido solo
  desde el Security Group anterior.
- `sgp-staging-archivos-ayd1-2026-gt01`, bucket con bloqueo de acceso público,
  ACL deshabilitadas y SSE-S3. Está vacío; versionado y Object Lock están
  deshabilitados y no tiene política personalizada ni CORS.
- `sgp-staging-ec2-role`, confiado a EC2 y con
  `AmazonSSMManagedInstanceCore`. Todavía no concede acceso a S3.
- `sgp-rds-private-rt` (`rtb-0bd589b3134333454`), tabla no principal con solo
  la ruta local `172.31.0.0/16` y sin salida mediante Internet Gateway o NAT.
- `sgp-rds-private-1a` (`subnet-0e261924496a742c8`, `172.31.96.0/24`,
  `us-east-1a`) y `sgp-rds-private-1b` (`subnet-0e6f2f94648deb3f9`,
  `172.31.97.0/24`, `us-east-1b`), asociadas con la tabla privada.
- `sgp-rds-subnet-group`, compuesto por las dos subredes anteriores.
- `sgp-rds-postgres`, solicitado como PostgreSQL 16 Single-AZ,
  `db.t4g.micro`, 20 GiB gp3 cifrados con `aws/rds`, IPv4, sin acceso público,
  protegido contra eliminación y con `sgp-rds-sg`. Está `Disponible` en
  `us-east-1b` y publica el endpoint privado
  `sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com:5432`.
- `sgp-staging-ec2` (`i-0385c95df8cd7909d`), Amazon Linux 2023 ARM64,
  `t4g.micro`, 20 GiB gp3 cifrados con `aws/ebs`, en la subred pública
  `subnet-0745affbda7ec4bb1` (`172.31.64.0/20`) de `us-east-1f`. Se observó en
  ejecución, con las comprobaciones `3/3` superadas y la IPv4 pública dinámica
  `3.235.197.36`, IPv4 privada `172.31.64.35` y DNS público
  `ec2-3-235-197-36.compute-1.amazonaws.com`; no tiene Elastic IP.
- La EC2 usa `sgp-staging-ec2-sg` y `sgp-staging-ec2-role`. El acceso mediante
  Session Manager fue exitoso y una prueba desde la sesión abrió TCP 5432 hacia
  el endpoint de RDS.
- La preparación inicial de Amazon Linux 2023 finalizó exitosamente el 11 de
  octubre de 2026. El bootstrap instala Docker y PostgreSQL 16 sin solicitar el
  paquete `curl`, porque la AMI ya proporciona el comando mediante
  `curl-minimal` y ambos paquetes entran en conflicto.
- Se creó `sgp-staging-key`, ED25519, pero SSH no se ha probado ni se abrió el
  puerto 22. La clave privada permanece fuera del
  repositorio.

La prueba TCP inicial demostró conectividad de red. El despliegue posterior
demostró autenticación de aplicación, Flyway, validación del esquema y respuesta
HTTP 200 del frontend, `/healthz` y `/api/v1/puentes?size=1`. El rol OIDC
`arn:aws:iam::416086757987:role/sgp-staging-github-actions-role` es distinto del
rol de instancia y su asunción quedó validada después de corregir el sujeto de
confianza al identificador inmutable. `sgp_staging` está operativa; no se creó
`sgp_prod`. Sigue pendiente una comprobación administrativa independiente de
TLS `verify-full` y extensiones.

### Excepción operativa de respaldos

El diseño conserva el requisito de 30 días de respaldos automáticos. La cuenta
rechazó ese valor porque supera el máximo disponible en su plan gratuito y no se
autorizó migrar a un plan de pago. Para que AWS aceptara la creación, se redujo
temporalmente la retención a 1 día, con copia de etiquetas habilitada y sin
replicación entre regiones.

Esta excepción no modifica el requisito original: la retención de 30 días, los
12 respaldos mensuales mediante AWS Backup y la prueba de restauración siguen
pendientes.

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
- Las bases dentro de RDS, los buckets, roles y credenciales se separan por
  ambiente.

### Negativas y riesgos

- RDS, S3, dos IPv4 públicas y dos EC2 generan costo incluso con poco tráfico;
  deben configurarse presupuestos y revisar créditos antes de crearlos.
- La cuenta informó USD 120 en créditos, pero no se pudieron configurar alertas
  automáticas; el propietario debe controlar manualmente el consumo.
- Staging y producción comparten capacidad y disponibilidad de una RDS.
- Single-AZ reduce el costo, pero no ofrece conmutación automática ante la falla
  de la zona que aloje la instancia.
- El plan gratuito limita la retención automática aplicada a 1 día. Este valor
  no satisface los 30 días del diseño ni sustituye los respaldos mensuales o la
  prueba de restauración.
- `t4g.micro` puede sufrir presión de memoria y debe medirse.
- El backend no puede validar todavía el flujo de archivos en S3.
- El bucket de staging no tiene versionado ni Object Lock; la recuperación ante
  borrados y los respaldos permanecen fuera de esta historia.
- Un PAT classic con `read:packages` puede leer otros paquetes privados a los
  que tenga acceso la cuenta y requiere rotación manual.
- `sslmode=require` cifra la conexión RDS, pero la verificación de identidad con
  `verify-full` requiere distribuir y probar el bundle CA.
- El acceso a credenciales IAM desde contenedores requiere configurar IMDSv2 de
  forma consciente; un hop limit incorrecto impediría acceder a S3.

## Limitaciones

- La aplicación ya se desplegó y superó las comprobaciones técnicas básicas,
  pero no las pruebas funcionales completas ni los flujos dependientes de SMTP.
- RDS está operativa para el backend y Flyway; falta una prueba administrativa
  de `verify-full` y una consulta directa de extensiones.
- El host EC2, OIDC y Systems Manager están validados; todavía falta HTTPS
  público. La compra de dominio, una zona DNS pública y Elastic IP se descartaron
  para staging.
- No incluye Compose ni despliegue de producción.
- Solo existe una retención automática solicitada de 1 día en RDS. La política
  requerida de respaldos, restauración y la retención centralizada de logs no
  están implementadas.
- S3 está decidido como servicio, pero su integración de aplicación permanece
  pendiente de la funcionalidad de archivos.
- Brotli y HTTPS público sobre IP todavía no están implementados.
- El bucket S3 existe, pero la aplicación todavía no lo consume.
- La publicación multi-arquitectura, descarga ARM64, GHCR, OIDC, SSM y el primer
  despliegue ya cuentan con validación real.

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
- [Creación de bases de datos en PostgreSQL 16](https://www.postgresql.org/docs/16/sql-createdatabase.html).
- [Privilegios predeterminados en PostgreSQL 16](https://www.postgresql.org/docs/16/sql-alterdefaultprivileges.html).
- [Membresía de roles en PostgreSQL 16](https://www.postgresql.org/docs/16/role-membership.html).
- [Roles IAM para aplicaciones en EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).
- [IMDSv2 para cargas contenedorizadas](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html).
- [Autenticación en GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).
