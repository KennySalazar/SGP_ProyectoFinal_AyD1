# ADR-0002: Despliegue de staging con AWS, Docker Compose y GHCR

## Estado

En revisión desde el 10 de octubre de 2026 por la adopción de RDS y S3. La
plataforma ARM64 y la publicación multi-arquitectura descritas en esta revisión
están aceptadas. Las secciones sobre PostgreSQL y MinIO en la EC2 se actualizarán
en el siguiente incremento y no deben usarse para aprovisionar AWS.

## Contexto

El SGP debe contar con ambientes separados de staging y producción, imágenes
contenedorizadas, proxy Nginx, PostgreSQL/PostGIS, almacenamiento de objetos,
persistencia, variables externas, healthchecks y automatización de integración.

El equipo necesita una solución económica, comprensible y operable dentro de los
créditos disponibles de AWS. El proyecto continúa en desarrollo, por lo que la
infraestructura no debe acoplar configuración de ambiente dentro de las imágenes
ni impedir que staging y producción seleccionen versiones diferentes.

Durante la implementación se comprobó además que las imágenes comunitarias
oficiales de MinIO dejaron de admitir descargas anónimas. El backend aún no
consume el servicio de almacenamiento, pero el componente debe permanecer en el
stack por el alcance arquitectónico del proyecto.

## Problema

Se necesita construir y publicar imágenes fuera de AWS, actualizar staging
automáticamente desde `develop`, mantener los servicios internos aislados y
permitir un futuro despliegue manual de producción sin duplicar Dockerfiles ni
reconstruir artefactos.

La solución no debe requerir Kubernetes, servicios administrados costosos,
puertos administrativos públicos ni credenciales AWS permanentes en GitHub.

## Alternativas consideradas

### Orquestación

- **EC2 con Docker Compose:** menor complejidad y costo operativo para el alcance.
- **ECS/Fargate:** mejor integración administrada, pero añade servicios,
  configuración y costos innecesarios para este proyecto.
- **EKS/Kubernetes:** descartado por complejidad y consumo desproporcionados.

### Construcción de imágenes

- **GitHub Actions:** libera CPU, memoria y disco de EC2 y deja trazabilidad.
- **Compilar en EC2:** descartado por consumo y por mezclar build con operación.
- **Solo AMD64:** no puede ejecutarse en las instancias `t4g` seleccionadas.
- **Solo ARM64:** reduce el trabajo de publicación, pero limita la portabilidad y
  las validaciones en equipos x86_64.
- **AMD64 y ARM64:** conserva portabilidad; las etapas de compilación se fijan a
  `BUILDPLATFORM` para no repetir Maven y Angular.

### Registro

- **GHCR:** se integra con GitHub Actions y permite reutilizar los mismos
  artefactos en ambos ambientes.
- **Amazon ECR:** válido técnicamente, pero añade configuración AWS sin una
  necesidad actual.

### Acceso de administración

- **AWS Systems Manager:** evita puertos SSH públicos y permite Run Command y
  túneles con IAM.
- **SSH público restringido:** simple, pero requiere puerto, claves y control de
  origen adicionales.
- **VPN:** no se justifica para el tamaño del equipo y el alcance actual.

### Almacenamiento compatible con MinIO

- **Continuar con la imagen oficial fijada:** imposible para instalaciones nuevas
  al devolver `unauthorized` en Quay y Docker Hub.
- **Construir MinIO desde fuente en cada proyecto:** aumenta el tiempo de CI y la
  responsabilidad de mantenimiento de una tercera imagen.
- **Silo:** fork comunitario mantenido que conserva API S3, variables `MINIO_*` y
  formato de datos; publica imágenes versionadas, SBOM y procedencia.

## Decisión

1. Ejecutar staging en una EC2 `t4g.micro` Amazon Linux 2023 ARM64 con Docker
   Compose.
2. Construir backend y frontend en GitHub Actions y publicar un índice OCI con
   variantes `linux/amd64` y `linux/arm64` en GHCR.
3. Usar imágenes separadas:
   `ghcr.io/kennysalazar/sgp-api` y
   `ghcr.io/kennysalazar/sgp-client`.
4. Publicar únicamente desde `develop` después de las validaciones existentes.
5. Etiquetar con `sha-<SHA completo>` y `develop`, pero desplegar por digest.
6. Mantener publicación y despliegue en jobs separados.
7. Usar OIDC para credenciales AWS temporales y permisos mínimos. La política y
   recursos aún deben crearse y aprobarse.
8. Acceder a staging mediante Systems Manager; Nginx escucha solo en loopback.
9. Mantener el `docker-compose.yml` actual para desarrollo y agregar
   `infra/staging/compose.yml` para imágenes publicadas.
10. Aislar staging mediante redes, volúmenes, variables y nombres explícitos.
11. Reutilizar los mismos Dockerfiles en staging y producción.
12. Usar Silo `RELEASE.2026-09-16T00-00-00Z` fijado por digest como componente
    compatible con MinIO, manteniendo el servicio Compose llamado `minio`.
13. Serializar despliegues con concurrencia de GitHub y `flock` en la instancia.
14. Mantener los secretos solo en `/etc/sgp/staging/staging.env` con permisos
    `0600`; la EC2 no clona el repositorio.
15. Mantener por ahora los paquetes GHCR privados. La EC2 de staging se
    autentica como `root` con un Personal Access Token (classic) limitado a
    `read:packages`, almacenado por Docker fuera del repositorio. Producción no
    debe reutilizar esa credencial.

Staging y producción usarán EC2 separadas. Esta historia no crea ni despliega la
instancia de producción. Producción no se desplegará automáticamente desde
`develop`.

## Justificación

Docker Compose cubre los cuatro servicios requeridos con una operación conocida
por el equipo. Construir fuera de EC2 permite comenzar con una instancia pequeña
y evita instalar Maven o Node en el servidor. GHCR conserva la relación entre
commit e imagen, mientras que el digest garantiza que una etiqueta móvil no
cambie el artefacto aprobado.

La publicación multi-arquitectura permite usar las EC2 ARM64 seleccionadas sin
perder compatibilidad con hosts x86_64. QEMU solo ejecuta las instrucciones ARM64
de las capas finales que lo requieran; Maven y Angular se construyen en la
arquitectura nativa del runner y sus artefactos son independientes de la CPU.

Systems Manager reduce superficie de ataque y elimina la administración de
claves SSH. Las redes internas y el bind a loopback impiden exposición directa de
servicios. La configuración externa permite promover las mismas imágenes entre
ambientes.

La cuenta que opera staging puede leer los paquetes, pero no administrar su
visibilidad dentro de la cuenta propietaria. Mantenerlos privados y autenticar la
EC2 permite continuar sin ampliar permisos ni introducir el token en GitHub
Actions. La publicación sigue usando `GITHUB_TOKEN`; el PAT se limita a la
descarga en el servidor.

Silo se adopta como respuesta limitada a la retirada de las imágenes oficiales;
evita mantener una compilación propia y conserva compatibilidad para la
integración futura. La elección deberá revisarse cuando el backend empiece a
almacenar objetos o si cambia su mantenimiento.

## Consecuencias

### Positivas

- EC2 consume artefactos listos y necesita menos recursos de compilación.
- Cada despliegue se relaciona con un commit y dos digests.
- Staging permanece privado y los servicios de datos no exponen puertos.
- Desarrollo local continúa funcionando con su Compose actual.
- Producción puede seleccionar manualmente una versión validada sin cambiar las
  imágenes.
- El despliegue puede revertir Compose y digests sin eliminar volúmenes.
- Las imágenes no quedan disponibles para descarga anónima.

### Negativas y riesgos

- Una `t4g.micro` puede tener presión de memoria; se agrega swap y límites, pero
  debe medirse antes de confirmar el tamaño.
- Las capas finales ARM64 que ejecutan comandos durante el build dependen de
  emulación QEMU y pueden tardar más que AMD64.
- Silo es un fork comunitario y requiere seguimiento de seguridad y continuidad.
- Un único host sigue siendo un punto de fallo.
- El acceso depende de Systems Manager y de conectividad de salida.
- La EC2 mantiene una credencial GHCR en `/root/.docker/config.json`; debe
  protegerse, expira y depende de que la cuenta conserve acceso de lectura.
- Un PAT classic con `read:packages` no se restringe a un solo paquete: puede
  leer otros paquetes privados accesibles para la misma cuenta.
- La rotación del PAT es una tarea operativa manual y un token vencido impide
  nuevos pulls, aunque no detiene contenedores en ejecución.

## Limitaciones

- No incluye producción, HTTPS público, respaldos ni restauración.
- La nueva arquitectura define dos EC2 separadas, pero esta historia solo
  implementa staging.
- MinIO/Silo todavía no está integrado con el backend.
- Se usa gzip; Brotli queda pendiente para evitar módulos o imágenes adicionales.
- La rotación local de Docker no satisface retención centralizada por 90 días.
- La publicación inicial AMD64 ya se ejecutó. Los nuevos manifiestos
  multi-arquitectura, OIDC, IAM, la autenticación GHCR desde EC2 y la instancia
  no se han validado al actualizar esta versión del ADR.

## Relación con el enunciado

La decisión atiende las directrices de los apartados de infraestructura y CI/CD:

- contenedores separados para frontend, backend, PostgreSQL/PostGIS y
  almacenamiento de objetos;
- Nginx como punto de entrada;
- configuración externa y separación de ambientes;
- persistencia, healthchecks, reinicio y ejecución sin privilegios cuando aplica;
- compilación, pruebas y trazabilidad en CI;
- registro de decisiones mediante ADR.

Los respaldos, retención extendida de logs y producción se registran como trabajo
pendiente en lugar de declararse implementados.

## Referencias

- [Problema de acceso a la imagen oficial de MinIO](https://access.redhat.com/solutions/7148629).
- [Repositorio del fork Silo y su contrato de compatibilidad](https://github.com/pgsty/silo).
- [Release de Silo seleccionada](https://github.com/pgsty/silo/releases/tag/RELEASE.2026-09-16T00-00-00Z).
- [Docker Compose v2.40.3](https://github.com/docker/compose/releases/tag/v2.40.3).
- [Autenticación en GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

