# Guía de traspaso para la implementación de producción

## 1. Contexto y responsabilidades

La historia de despliegue se dividió en dos responsabilidades:

- **Staging y CI/CD:** imágenes, GHCR, Compose de staging, despliegue automático
  desde `develop`, validaciones y documentación.
- **Producción:** infraestructura AWS de producción, Compose independiente,
  selección y despliegue manual de versiones aprobadas, HTTPS sobre IP pública,
  seguridad y validación final.

Este documento no implementa producción. Registra contratos y decisiones que el
responsable de producción debe conservar o coordinar antes de modificarlos.

## 2. Estado del traspaso

| Componente | Estado |
|---|---|
| Dockerfiles reutilizables | Preparados para AMD64/ARM64; nueva publicación pendiente. |
| Compose de staging | Adaptado para RDS y S3 externos; validación real en AWS pendiente. |
| Workflow CI, publicación y staging | Validaciones y publicación ejecutadas correctamente; despliegue AWS deshabilitado. |
| Imágenes en GHCR | Paquetes privados existentes en AMD64; manifiestos multi-arquitectura pendientes. |
| Convención de versiones | Definida y utilizada en la primera publicación. |
| Infraestructura AWS de staging | Pendiente. |
| Infraestructura y Compose de producción | Responsabilidad del segundo integrante. |

## 3. Decisiones que deben conservarse

1. AWS EC2 con Linux y Docker Compose es la plataforma acordada.
2. EC2 solo descarga y ejecuta imágenes; no compila Angular ni Java.
3. Backend y frontend son imágenes independientes y reutilizables entre ambientes.
4. La configuración y secretos se inyectan externamente.
5. `develop` publica y despliega staging; no despliega producción.
6. Producción selecciona manualmente una versión aprobada.
7. No se depende exclusivamente de la etiqueta `latest`.
8. Nginx es el punto de entrada y proxy de `/api/`.
9. Backend, PostgreSQL y almacenamiento S3 no deben publicarse directamente.
10. Bases RDS, buckets S3, redes, roles IAM y secretos deben ser independientes
    por ambiente.
11. No se usan datos reales de producción en staging.
12. No deben modificarse migraciones Flyway ya aplicadas.
13. Las imágenes deben publicarse para `linux/amd64` y `linux/arm64`; las EC2
    objetivo usan ARM64 (`t4g`).

La nueva arquitectura asigna una EC2 independiente a cada ambiente: `t4g.micro`
para staging y `t4g.small` para producción. Ambas usarán una RDS compartida, pero
con bases y credenciales separadas. Estos recursos no se crean desde esta parte
de la historia de usuario.

## 4. Componentes entregados por staging

- Dockerfiles multietapa, multi-arquitectura, con usuario sin privilegios y
  healthcheck. Las etapas de compilación usan la plataforma nativa del runner
  para no repetir Maven y Angular por cada arquitectura.
- Nginx con SPA, proxy inverso, gzip, encabezados de seguridad y caché controlada.
- Perfil `application-staging.properties`.
- Perfil `application-prod.properties` ya existente, que desactiva OpenAPI y
  exige cookie segura.
- Compose de staging con una red explícita y sin volúmenes de datos locales.
- Plantilla de variables sin secretos.
- Preparación de Amazon Linux y despliegue con rollback.
- Preparación parametrizada de RDS con extensiones y usuarios separados.

Estos elementos sirven de referencia; `infra/staging/compose.yml` no debe
utilizarse directamente como Compose de producción.

## 5. Imágenes y convención de versiones

Repositorios reservados:

```text
ghcr.io/kennysalazar/sgp-api
ghcr.io/kennysalazar/sgp-client
```

Convención definida:

- `sha-<SHA completo>`: etiqueta inmutable asociada al commit de `develop`.
- `develop`: etiqueta móvil para identificar la última integración exitosa.
- `<imagen>@sha256:<digest>`: referencia que debe usarse en un despliegue.

Cada etiqueta publicará un índice OCI con variantes `linux/amd64` y
`linux/arm64`. Una EC2 `t4g` seleccionará automáticamente la variante ARM64 sin
cambiar el nombre ni la etiqueta. El digest aprobado debe corresponder al índice
multi-arquitectura, no a una variante aislada.

Los paquetes ya existen en GHCR y permanecen privados. El responsable de staging
tiene acceso de lectura, pero no permisos administrativos sobre los paquetes de
la cuenta `KennySalazar`; por ello la EC2 de staging se autenticará con un
Personal Access Token (classic) limitado a `read:packages`.

Producción no debe reutilizar el token de staging. Su responsable deberá obtener
una credencial independiente de una cuenta con lectura sobre ambos paquetes,
definir su vigencia y mantenerla fuera del repositorio, Compose, archivos de
entorno, User Data y logs. Si posteriormente el propietario vuelve públicos los
paquetes o adopta una identidad compartida, el cambio debe coordinarse y
documentarse; no es una decisión tomada por staging.

Producción no debe reconstruir una versión. Debe usar exactamente los digests
aprobados en staging, de modo que el binario validado y el desplegado sean el
mismo.

## 6. Archivos compartidos y específicos

| Archivo | Uso en producción |
|---|---|
| `sgp-api/Dockerfile` | Compartido sin cambios específicos por ambiente. |
| `sgp-client/Dockerfile` | Compartido sin cambios específicos por ambiente. |
| `sgp-client/nginx.conf` | Compartido; el TLS puede terminar en una capa Nginx separada o en una adaptación coordinada. |
| `infra/rds/prepare-database.sql` | Reutilizable para preparar la base RDS de producción con nombres y secretos propios; se ejecuta una vez como administrador. |
| `infra/postgres/init/00-create-app-user.sh` | Exclusivo del PostgreSQL contenedorizado de desarrollo local. |
| `sgp-api/src/main/resources/application-prod.properties` | Perfil obligatorio de producción. |
| `infra/staging/compose.yml` | Referencia de diseño; no es la configuración de producción. |
| `infra/staging/deploy.sh` | Referencia para comprobaciones; producción será manual. |

El responsable deberá crear la configuración de producción en una ubicación
independiente. Su ruta y nombre de red aún no están definidos; debe elegirse
explícitamente sin reutilizar `sgp_staging_proxy`.

## 7. Variables requeridas en producción

Producción requiere las mismas categorías de configuración, pero con valores y
controles independientes:

| Variable o grupo | Requisito de producción |
|---|---|
| Imágenes backend/frontend | Digests aprobados, no `latest`. |
| `DATABASE_URL` | JDBC hacia el endpoint RDS privado y la base exclusiva de producción; debe exigir TLS. |
| `MIGRATION_DATABASE_USERNAME/PASSWORD` | Credencial exclusiva para Flyway. |
| `DATABASE_USERNAME/PASSWORD` | Usuario de aplicación distinto y con menor privilegio. |
| S3 | El backend aún no define variables de bucket. Cuando exista la integración, usar el bucket de producción y el rol IAM de su EC2, nunca claves estáticas. |
| `SECRET_KEY_JWT` | Secreto aleatorio de al menos 32 caracteres, distinto de staging. |
| Expiraciones JWT/OTP | Revisar y aprobar valores antes del despliegue. |
| `INITIAL_ADMIN_EMAIL/PASSWORD` | Identidad controlada; contraseña de 10–72 caracteres con letras y números. |
| `MAIL_*` | Cuenta SMTP de producción y remitente autorizado. |
| `INVITACION_URL_ACTIVACION` | URL pública HTTPS definitiva. |
| `CORS_ALLOWED_ORIGINS` | Únicamente el origen HTTPS público de producción. |
| `SPRING_PROFILES_ACTIVE` | `prod`. |
| `REFRESH_COOKIE_SECURE` | `true`; el perfil `prod` ya lo establece. |
| `OPENAPI_PUBLIC` | `false`; el perfil `prod` ya lo establece. |

Los secretos no deben incluirse en Compose, AMI, User Data, logs, GitHub Actions
ni archivos versionados. El mecanismo definitivo de almacenamiento de secretos
en producción sigue pendiente de decisión.

Antes del primer despliegue, el responsable de producción deberá ejecutar
`infra/rds/prepare-database.sql` como administrador de RDS, usando nombres y
contraseñas exclusivos de producción mediante las variables
`SGP_DATABASE_NAME`, `SGP_MIGRATOR_USER`, `SGP_MIGRATOR_PASSWORD`,
`SGP_APP_USER` y `SGP_APP_PASSWORD`. El procedimiento detallado está en la guía
de staging; no debe reutilizar sus valores. El archivo crea las extensiones con
el administrador y deja a Flyway con permisos DDL sin concederle
`rds_superuser`.

La EC2 de producción debe recibir un rol de instancia que solo permita las
operaciones necesarias sobre su bucket. Debe exigir IMDSv2 y usar hop limit `2`
para cargas dentro de Docker. No se deben crear `AWS_ACCESS_KEY_ID` ni
`AWS_SECRET_ACCESS_KEY` para la aplicación.

## 8. Descarga manual desde GHCR

Mientras los paquetes sean privados, cree un Personal Access Token (classic) con
solo `read:packages` y una fecha de expiración. En una sesión interactiva de la
instancia, autentique al mismo usuario que ejecutará el despliegue:

```bash
sudo -i
install -d -m 0700 /root/.docker
read -rsp "GHCR token: " GHCR_TOKEN; printf '\n'
printf '%s' "${GHCR_TOKEN}" | docker login ghcr.io \
  --username <USUARIO_GITHUB_DEL_TOKEN> \
  --password-stdin
unset GHCR_TOKEN
chmod 0600 /root/.docker/config.json
exit
```

No escriba el token directamente en el comando porque quedaría en el historial.
Docker lo conserva en `/root/.docker/config.json`; proteja ese archivo como una
credencial. Descargue luego los artefactos aprobados:

```bash
docker pull ghcr.io/kennysalazar/sgp-api@sha256:<digest-aprobado>
docker pull ghcr.io/kennysalazar/sgp-client@sha256:<digest-aprobado>
```

Verifique lo descargado:

```bash
docker image inspect ghcr.io/kennysalazar/sgp-api@sha256:<digest-aprobado>
docker image inspect ghcr.io/kennysalazar/sgp-client@sha256:<digest-aprobado>
```

No usar `docker build` en la EC2 ni sustituir el digest por `develop` durante el
despliegue manual. Antes de que expire el token, reemplácelo mediante otro
`docker login`, verifique una descarga y revoque el anterior. La expiración no
detiene los contenedores activos, pero bloqueará actualizaciones y rollback que
requieran descargar una imagen ausente del host.

## 9. Recomendaciones para Compose de producción

- Definir un nombre de proyecto diferente a `sgp-staging`.
- Definir una red con nombre explícito que no empiece por `sgp_staging_`.
- Publicar únicamente el punto de entrada HTTPS.
- Mantener el backend sin puertos publicados, RDS en subredes privadas y S3 sin
  acceso público.
- Usar las imágenes por digest.
- Inyectar todas las variables desde un archivo protegido o un gestor aprobado.
- Incluir healthchecks para frontend y backend. RDS y S3 no son servicios del
  Compose y no deben agregarse a `depends_on`.
- Mantener `restart: unless-stopped`, límites de recursos y rotación de logs.
- No montar el código fuente ni usar `build:`.
- Ejecutar Flyway con su usuario separado antes de aceptar tráfico.
- No compartir redes, bases, buckets, roles IAM ni archivos de entorno con
  staging.

Los nombres y rutas definitivos pertenecen a la implementación de producción y
no se fijan en esta guía antes de que exista su diseño.

## 10. Nginx, HTTPS y seguridad

Producción debe exponer HTTPS sobre una IP pública sin comprar dominio. El
responsable debe confirmar una autoridad certificadora que emita certificados
válidos con la IP como Subject Alternative Name, su método de validación y la
renovación automática.

Requisitos mínimos:

- Redirigir HTTP a HTTPS si se mantiene el puerto 80 para validación o redirección.
- Permitir públicamente solo los puertos estrictamente necesarios.
- Mantener `/api/` detrás de Nginx.
- Configurar `INVITACION_URL_ACTIVACION` y CORS con la URL HTTPS exacta.
- Conservar cookies `Secure`, `HttpOnly` y `SameSite=Strict`.
- No exponer Actuator, OpenAPI, PostgreSQL ni el almacenamiento.
- Exigir IMDSv2 con hop limit `2` para que el SDK ejecutado dentro de Docker
  pueda obtener las credenciales temporales del rol de instancia.
- Probar renovación del certificado y documentar su operación.

La solución de certificados no ha sido elegida y no debe considerarse cerrada.

## 11. Procedimiento manual propuesto

1. Obtener aprobación del commit y digests desplegados satisfactoriamente en
   staging.
2. Registrar los digests en la evidencia de liberación.
3. Preparar o actualizar el archivo externo de configuración de producción.
4. Descargar ambas imágenes por digest.
5. Ejecutar `docker compose config --quiet`.
6. Confirmar capacidad de disco, memoria y respaldo manual previo cuando ya
   exista un procedimiento aprobado.
7. Ejecutar `docker compose pull` y `docker compose up -d --wait`.
8. Comprobar PostgreSQL, Flyway, backend, frontend y proxy.
9. Ejecutar pruebas funcionales mínimas mediante HTTPS.
10. Registrar commit, digests, fecha, operador y resultado.
11. Ante un fallo, restaurar el Compose y los digests anteriores sin eliminar
    ni modificar datos en RDS o S3.

Este procedimiento es una propuesta de interfaz; debe adaptarse y probarse al
crear el Compose real de producción.

## 12. Validaciones requeridas al responsable de producción

- Compose válido y sin `build:`.
- Imágenes coincidentes con los digests aprobados.
- HTTPS válido accediendo por IP pública.
- Renovación del certificado comprobada.
- Solo Nginx accesible desde Internet.
- RDS PostgreSQL/PostGIS accesible solo desde las EC2 autorizadas y con base y
  credenciales exclusivas de producción.
- Todas las migraciones Flyway aplicadas una sola vez y sin errores.
- Backend saludable y OpenAPI no público.
- Frontend y `/api/v1` accesibles mediante Nginx.
- CORS restringido al origen de producción.
- Cookies seguras y flujo de renovación funcional.
- SMTP e invitaciones utilizando la URL de producción.
- Redes, base RDS, bucket S3, rol IAM, credenciales y datos separados de staging.
- Reinicio de instancia y recuperación automática de servicios.
- Logs rotados sin secretos.
- Procedimiento de rollback probado sin eliminar datos.

## 13. Errores y limitaciones conocidos

- El backend todavía no implementa la interfaz de almacenamiento de objetos ni
  consume S3. No deben inventarse variables ni declararse validado hasta que esa
  funcionalidad exista.
- La verificación completa del certificado RDS con `sslmode=verify-full` y el
  bundle CA debe incorporarse y probarse al preparar la infraestructura.
- Brotli no está configurado; Nginx usa gzip.
- No existe retención centralizada de logs por 90 días.
- No se han implementado respaldos ni restauraciones.
- No se ha medido aún el consumo real en EC2.
- La publicación multi-arquitectura está implementada en la rama de trabajo, pero
  debe comprobarse en GHCR después de integrarla en `develop`.
- Los paquetes GHCR son privados. Cada ambiente necesita una credencial de
  lectura cuya vigencia y dependencia de una cuenta personal deben operarse.
- El PAT classic con `read:packages` puede leer los paquetes a los que tenga
  acceso su cuenta; no es una credencial limitada exclusivamente a este proyecto.

## 14. Decisiones pendientes

- Región, VPC y capacidad definitiva de disco para las dos EC2 separadas.
- Solución de certificado HTTPS válido para IP pública.
- Mecanismo de secretos de producción.
- Cuenta responsable, vigencia y rotación de la credencial GHCR de producción.
- Nombres y rutas del Compose de producción.
- Retención y centralización de logs.
- Estrategia futura de respaldos y restauración.
- Nombres de las variables S3 que expondrá el backend cuando se implemente
  `AlmacenamientoArchivos`.

Estas decisiones no deben cerrarse unilateralmente si cambian contratos
compartidos con staging.

## 15. Criterios de aceptación de producción

La parte de producción se considera completa cuando:

1. Existe infraestructura AWS autorizada y documentada.
2. Existe un Compose independiente que consume imágenes aprobadas de GHCR.
3. El despliegue manual por digest es reproducible y tiene rollback.
4. La aplicación es accesible mediante HTTPS válido sobre IP pública.
5. Solo el proxy está expuesto y los servicios internos permanecen aislados.
6. La persistencia en RDS/S3, Flyway, salud, reinicio y separación de ambientes están
   demostrados.
7. Variables y secretos están documentados sin publicar valores reales.
8. Las limitaciones y tareas pendientes, incluidos respaldos, están registradas.
9. La documentación contiene evidencia real de las pruebas ejecutadas.

## Referencias compartidas

- [Extensiones de PostgreSQL en Amazon RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.Extensions.html).
- [Configuración de PostGIS en RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html).
- [Uso de TLS con RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).
- [Roles IAM para aplicaciones en EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).

