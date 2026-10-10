# Despliegue de staging en AWS

## Estado del documento

Esta guía describe la infraestructura de staging implementada en el repositorio y
se actualiza de forma incremental durante la historia de usuario.

| Área | Estado |
|---|---|
| Imágenes reutilizables de backend y frontend | Build multi-arquitectura implementado; pendiente de publicación desde `develop`. |
| Compose aislado de staging | Adaptado para ejecutar solo Nginx/Angular y Spring Boot; RDS y S3 son externos a la EC2. |
| Preparación de Amazon Linux 2023 | Script compatible con ARM64 y x86_64; no ejecutado en AWS. |
| Workflow de validación, publicación y despliegue | Versión anterior ejecutada correctamente; cambio actual pendiente de CI. |
| Publicación en GHCR | Paquetes privados publicados inicialmente para AMD64; manifiestos AMD64/ARM64 pendientes de este cambio. |
| Despliegue automático desde `develop` | Implementado y deshabilitado hasta disponer de AWS. |
| Instancia EC2, IAM y Systems Manager | Pendiente de autorización y creación. |
| Despliegue real en AWS | No ejecutado. |

No se han creado recursos ni credenciales en AWS.

### Transición de arquitectura

La aclaración recibida el 10 de octubre de 2026 exige almacenar los archivos
fuera del servidor de aplicación. La arquitectura objetivo usa dos EC2 ARM64
separadas, una RDS PostgreSQL/PostGIS compartida por ambientes y buckets S3
independientes. El Compose de staging ya refleja esta separación: PostgreSQL y
MinIO permanecen únicamente en el Compose de desarrollo local.

## Arquitectura de staging

El punto de entrada es Nginx. El puerto se publica únicamente en la interfaz de
loopback de la instancia para acceder mediante un túnel de AWS Systems Manager.
El backend no publica puertos en el host. RDS debe permanecer en subredes
privadas y el bucket S3 debe bloquear todo acceso público.

```text
Equipo del proyecto
        |
        | túnel SSM (pendiente de AWS)
        v
127.0.0.1:8088 en EC2
        |
        v
Nginx + Angular ---- red sgp_staging_proxy ---- Spring Boot
                                                   |
                               +-------------------+-------------------+
                               |                                       |
                               v                                       v
                  RDS PostgreSQL/PostGIS privado          S3 privado mediante
                  base y usuarios de staging              rol IAM de la EC2 (*)
```

`(*)` El backend todavía no implementa la interfaz de almacenamiento de objetos.
Por ello el Compose no recibe variables S3 ni credenciales estáticas. Cuando se
implemente el módulo, el SDK deberá obtener credenciales temporales desde el rol
de instancia y limitarse al bucket de staging.

## Archivos implementados

| Archivo | Responsabilidad |
|---|---|
| `sgp-api/Dockerfile` | Construye la API con Java 21 una vez y genera runtimes AMD64/ARM64 sin privilegios. |
| `sgp-client/Dockerfile` | Construye Angular una vez y genera runtimes Nginx AMD64/ARM64 sin privilegios. |
| `sgp-client/nginx.conf` | Sirve la SPA, comprime con gzip y reenvía `/api/` al backend. |
| `sgp-api/src/main/resources/application-staging.properties` | Desactiva OpenAPI público y configura el perfil de staging. |
| `infra/staging/compose.yml` | Define el stack aislado que consume imágenes publicadas. |
| `infra/staging/.env.example` | Plantilla sin credenciales reales. |
| `infra/staging/bootstrap-amazon-linux-2023.sh` | Prepara Amazon Linux 2023 ARM64 o x86_64 con Docker, Compose, SSM y swap. |
| `infra/staging/deploy.sh` | Valida, actualiza y comprueba el stack con bloqueo y rollback. |
| `infra/rds/prepare-database.sql` | Prepara una base RDS con extensiones y roles separados, sin incluir nombres ni secretos reales. |
| `infra/postgres/init/00-create-app-user.sh` | Inicializa PostgreSQL únicamente en desarrollo local. |

El `docker-compose.yml` de la raíz continúa siendo el entorno de desarrollo y no
ha sido sustituido por la configuración de staging.

## Requisitos de infraestructura

La infraestructura objetivo asigna una EC2 independiente a staging:

- Amazon Linux 2023, arquitectura ARM64 (`aarch64`).
- Tipo `t4g.micro` como punto de partida, con el heap de Java limitado y sujeto a
  medición. Si la memoria no es suficiente, cualquier aumento requiere revisar
  primero los créditos y el presupuesto.
- Disco raíz gp3 de aproximadamente 20 GiB como valor inicial, sujeto a
  autorización.
- 2 GiB de swap creados por el script de preparación.
- Rol de instancia con `AmazonSSMManagedInstanceCore` o una política equivalente
  de permisos mínimos y, cuando exista la integración, acceso exclusivo al
  bucket S3 de staging.
- Salida HTTPS hacia AWS, GHCR/Docker Hub y el proveedor SMTP.
- Administración mediante Systems Manager, sin publicar SSH.
- RDS PostgreSQL 16 no pública, en la misma VPC, cuyo Security Group acepte el
  puerto 5432 únicamente desde el Security Group de la EC2 de staging.
- Una base `sgp_staging`, un usuario migrador y un usuario de aplicación que no
  se reutilicen en producción.
- Bucket S3 de staging privado e independiente del bucket de producción.
- IMDSv2 obligatorio y salto de respuesta igual a `2`, necesario para que un SDK
  dentro de Docker pueda obtener credenciales temporales del rol de instancia.
- Las reglas públicas 80/443 se definirán junto con HTTPS; no se usarán ALB, NAT
  Gateway, ECS ni EKS.

El disco, región, VPC y recursos administrados no deben aprovisionarse sin
autorización y verificación previa de costos.

## Preparación del servidor

El script está diseñado para ejecutarse una sola vez como `root` en Amazon Linux
2023. Detecta `aarch64` para la EC2 `t4g` objetivo y conserva compatibilidad con
`x86_64`:

```bash
sudo bash /tmp/bootstrap-amazon-linux-2023.sh
```

Realiza las siguientes operaciones:

1. Instala Docker, el cliente PostgreSQL 16 y `curl`.
2. Verifica y habilita `amazon-ssm-agent` y `chronyd`.
3. Instala Docker Compose `v2.40.3` y verifica su SHA-256.
4. Crea `/opt/sgp/staging` y `/etc/sgp/staging`.
5. Configura `/swapfile` de 2 GiB si no existe.
6. Agrega `ssm-user` al grupo `docker`, si el usuario existe.

La forma definitiva de transferir y ejecutar este script mediante SSM se
documentará cuando exista la instancia.

## Directorios del servidor

| Ruta | Contenido | Permisos esperados |
|---|---|---|
| `/opt/sgp/staging` | Compose activo, imágenes seleccionadas, revisión y script de despliegue. | Administrado por `root`. |
| `/etc/sgp/staging/staging.env` | Secretos y configuración de staging. | `0600`, propietario `root`. |

La EC2 no necesita clonar el repositorio ni compilar Java o Angular.

## Imágenes y versiones

Las rutas acordadas son:

```text
ghcr.io/kennysalazar/sgp-api
ghcr.io/kennysalazar/sgp-client
```

El workflow `.github/workflows/ci.yml` publicará solamente desde `develop`:

- `sha-<SHA completo>` como etiqueta inmutable y trazable.
- `develop` como referencia móvil informativa.

El despliegue no utilizará `latest`: recibirá referencias con digest, por
ejemplo `ghcr.io/kennysalazar/sgp-api@sha256:...`. La primera publicación desde
`develop` se completó correctamente. Los paquetes pertenecen a la cuenta
`KennySalazar` y permanecen privados: el integrante que opera staging puede
leerlos, pero no posee permisos administrativos para cambiar su visibilidad.

## Autenticación de la EC2 en GHCR

La EC2 utilizará un Personal Access Token (classic) de una cuenta con acceso a
los paquetes. Debe conceder únicamente `read:packages`, tener fecha de expiración
y no incluir `write:packages` ni `delete:packages`. El token es una credencial de
operación del servidor: no debe guardarse en el repositorio, `staging.env`, User
Data, GitHub Actions, logs ni documentación.

Después de preparar la instancia, abra una sesión interactiva de Systems Manager
y autentique al usuario `root`, que es el usuario con el que Run Command ejecuta
el despliegue:

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

El valor escrito con `read -s` no se muestra ni se incorpora al historial. Docker
guarda la credencial en `/root/.docker/config.json`; el archivo debe permanecer
propiedad de `root` y con modo `0600`. Esta configuración no se transfiere mediante
el workflow.

Compruebe el acceso usando un digest publicado, no la etiqueta móvil:

```bash
sudo docker pull \
  ghcr.io/kennysalazar/sgp-api@sha256:<digest-publicado>
```

Para rotar el token, cree otro con el mismo permiso, repita `docker login`, pruebe
un `docker pull` por digest y solo entonces revoque el anterior. La expiración no
detiene contenedores en ejecución, pero impide descargar imágenes en el siguiente
despliegue. Debe registrarse externamente una fecha de renovación sin registrar el
valor del token.

## GitHub Actions y GHCR

El workflow reutiliza las validaciones existentes y aplica el siguiente flujo:

```text
Pull Request -> backend + frontend

push a develop -> backend + frontend
                         |
                         +-> publicar API en GHCR -----+
                         +-> publicar cliente en GHCR -+-> desplegar staging
```

Los Pull Requests, incluidos los provenientes de forks, reciben únicamente
`contents: read`; no publican imágenes ni solicitan credenciales AWS. Los jobs de
publicación se ejecutan después de que backend y frontend finalicen correctamente
y reciben solamente `packages: write`. El job de despliegue recibe
`id-token: write` para OIDC y no usa claves AWS permanentes.

Las acciones de terceros están fijadas a commits inmutables. Buildx usa cachés
separadas para cada imagen, genera SBOM y procedencia, y publica un único
manifiesto con `linux/amd64` y `linux/arm64`. QEMU se registra antes de Buildx y
su imagen `binfmt` está fijada por digest. Las etapas Maven y Angular usan
`BUILDPLATFORM`, por lo que se ejecutan una vez en la arquitectura nativa del
runner; las etapas finales producen los runtimes de ambas arquitecturas.

Configure un GitHub Environment llamado `staging`, restringido a la rama
`develop`, con estas variables:

| Variable | Nivel | Estado inicial | Propósito |
|---|---|---|---|
| `STAGING_ENABLED` | Repositorio | Ausente o `false` | Habilita el job; debe ser de repositorio porque se evalúa antes de cargar el Environment. |
| `AWS_REGION` | Environment `staging` | Pendiente | Región de la instancia y del rol. |
| `AWS_ROLE_ARN` | Environment `staging` | Pendiente | Rol asumido mediante OIDC. |
| `STAGING_INSTANCE_ID` | Environment `staging` | Pendiente | Instancia administrada por SSM. |

La publicación en GHCR no requiere GitHub Secrets: utiliza el `GITHUB_TOKEN`
efímero. La descarga desde la EC2 es una operación diferente y utiliza el PAT de
solo lectura instalado manualmente en Docker. AWS usa OIDC y tampoco requiere
claves permanentes en GitHub. La relación de confianza del rol debe limitar el
sujeto a `repo:KennySalazar/SGP_ProyectoFinal_AyD1:environment:staging`. Su
política debe permitir únicamente enviar `AWS-RunShellScript` a la instancia de
staging, consultar el resultado y cancelar el comando en caso de timeout.

Cada job vuelve a consultar el SHA actual de `develop` antes de publicar o
desplegar. La publicación por imagen está serializada, el despliegue tiene una
concurrencia exclusiva y el servidor utiliza `flock`. Con estas tres protecciones
se evitan despliegues simultáneos y revisiones antiguas fuera de orden.

## Variables de entorno

La plantilla es `infra/staging/.env.example`. El archivo real debe existir solo
en `/etc/sgp/staging/staging.env` y nunca debe registrarse en Git.

| Variable | Servicio | Obligatoria | Propósito / ejemplo seguro |
|---|---|---:|---|
| `BACKEND_IMAGE` | Compose | Sí, inyectada | Digest de `ghcr.io/kennysalazar/sgp-api`; el despliegue la escribe en `images.env`. |
| `FRONTEND_IMAGE` | Compose | Sí, inyectada | Digest de `ghcr.io/kennysalazar/sgp-client`. |
| `STAGING_HTTP_PORT` | Nginx | No | Puerto loopback; `8088`. |
| `DATABASE_URL` | Backend/Flyway | Sí | JDBC hacia el endpoint privado y base de staging, terminada en `?sslmode=require`. |
| `DATABASE_USERNAME` | Backend | Sí | Usuario de mínimo privilegio; `sgp_staging_app`. |
| `DATABASE_PASSWORD` | Backend | Sí | Secreto hexadecimal aleatorio del usuario de aplicación. |
| `MIGRATION_DATABASE_USERNAME` | Flyway | Sí | Propietario de migraciones; `sgp_staging_migrator`. |
| `MIGRATION_DATABASE_PASSWORD` | Flyway | Sí | Secreto distinto al del usuario de aplicación. |
| `SECRET_KEY_JWT` | Backend | Sí | Clave JWT de al menos 32 caracteres. |
| `ACCESS_EXPIRATION_TIME_JWT` | Backend | No | Vigencia de acceso en ms; `900000`. |
| `REFRESH_EXPIRATION_TIME_JWT` | Backend | No | Vigencia de renovación en ms; `604800000`. |
| `INITIAL_ADMIN_EMAIL` | Backend | Sí | Administrador inicial exclusivo de staging. |
| `INITIAL_ADMIN_PASSWORD` | Backend | Sí | Entre 10 y 72 caracteres, con letras y números. |
| `MAIL_HOST` | Backend | Sí | Host SMTP; `smtp.example.com`. |
| `MAIL_PORT` | Backend | No | Puerto SMTP; `587`. |
| `MAIL_USERNAME` | Backend | Sí | Cuenta SMTP de staging. |
| `MAIL_PASSWORD` | Backend | Sí | Credencial o contraseña de aplicación SMTP. |
| `MAIL_FROM` | Backend | Sí | Remitente de correos de staging. |
| `MAIL_FROM_NAME` | Backend | No | Nombre visible; `SGP CUNOC - Staging`. |
| `OTP_LENGTH` | Backend | No | Longitud OTP; `6`. |
| `OTP_EXPIRATION_MINUTES` | Backend | No | Vigencia OTP; `10`. |
| `OTP_MAX_ATTEMPTS` | Backend | No | Intentos permitidos; `5`. |
| `OTP_RESEND_COOLDOWN_SECONDS` | Backend | No | Espera para reenvío; `60`. |
| `INVITACION_VIGENCIA_HORAS` | Backend | No | Vigencia de invitación; `72`. |
| `INVITACION_URL_ACTIVACION` | Backend | No | `http://localhost:8088/activar-cuenta` mientras se use el túnel. |
| `CORS_ALLOWED_ORIGINS` | Backend | No | `http://localhost:8088` para el acceso privado. |
| `JAVA_TOOL_OPTIONS` | Backend | No | Límites de JVM definidos en la plantilla. |

En staging, `SPRING_PROFILES_ACTIVE=staging`, `OPENAPI_PUBLIC=false` y
`REFRESH_COOKIE_SECURE=false` están fijados por Compose. La cookie no se marca
`Secure` porque el HTTP viaja dentro del túnel cifrado de SSM. Producción deberá
usar HTTPS y el perfil `prod`, que exige cookie segura.

Para generar secretos que no requieran escapar caracteres especiales en el
archivo de entorno puede utilizarse:

```bash
openssl rand -hex 32
```

No se definen `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY`. El acceso futuro a
S3 utilizará credenciales temporales del rol IAM de la EC2. Tampoco se inventan
variables de bucket mientras el backend no tenga una configuración que las
consuma.

## Preparación de la base RDS

Después de crear RDS y antes del primer despliegue, copie temporalmente
`infra/rds/prepare-database.sql` a un equipo con acceso de red a la instancia,
por ejemplo la EC2 de staging administrada mediante SSM. Ejecútelo como el
usuario administrador de RDS:

```bash
read -rp "Base: " SGP_DATABASE_NAME
read -rp "Usuario migrador: " SGP_MIGRATOR_USER
read -rsp "Clave del migrador: " SGP_MIGRATOR_PASSWORD; printf '\n'
read -rp "Usuario de aplicacion: " SGP_APP_USER
read -rsp "Clave de aplicacion: " SGP_APP_PASSWORD; printf '\n'
export SGP_DATABASE_NAME SGP_MIGRATOR_USER SGP_MIGRATOR_PASSWORD
export SGP_APP_USER SGP_APP_PASSWORD

psql \
  "host=<RDS_ENDPOINT> port=5432 dbname=postgres user=<RDS_ADMIN> sslmode=require" \
  --file prepare-database.sql

unset SGP_DATABASE_NAME SGP_MIGRATOR_USER SGP_MIGRATOR_PASSWORD
unset SGP_APP_USER SGP_APP_PASSWORD
```

El archivo crea la base y los dos roles si no existen, habilita `postgis`,
`pg_trgm` y `uuid-ossp` con el administrador y configura privilegios actuales y
predeterminados. No concede `rds_superuser` al usuario de Flyway. Debe ejecutarse
por separado para la base de producción usando otros nombres y secretos.

La primera migración también contiene `CREATE EXTENSION IF NOT EXISTS`; al estar
las extensiones ya instaladas, Flyway puede ejecutarla sin elevar privilegios.
RDS debe configurarse para exigir TLS. `sslmode=require` evita conexiones sin
cifrado; la validación completa del certificado con `verify-full` y el bundle CA
de RDS queda pendiente del incremento de infraestructura.

## Docker Compose

| Servicio | Exposición | Persistencia | Salud |
|---|---|---|---|
| `nginx` | `127.0.0.1:8088` | Imagen inmutable | `GET /healthz`. |
| `backend` | Solo red `sgp_staging_proxy` | Sin datos locales | Actuator interno; incluye conectividad de base. |

La única red de Compose es `sgp_staging_proxy`. Staging no crea volúmenes: la
persistencia reside en RDS y S3, fuera del ciclo de vida de los contenedores.
Eliminar o recrear los contenedores no elimina datos persistentes.

Los servicios utilizan `restart: unless-stopped`, límites de memoria/procesos y
el driver `json-file` con cinco archivos de 10 MiB. Esta rotación protege el
disco, pero no satisface por sí sola un requisito de retención de 90 días ni
convierte todos los logs de aplicación a JSON estructurado.

## Despliegue

El workflow implementado, cuando `STAGING_ENABLED=true`, invoca en el servidor:

```bash
sudo /opt/sgp/staging/deploy.sh \
  /opt/sgp/staging/compose.candidate.yml \
  ghcr.io/kennysalazar/sgp-api@sha256:<digest> \
  ghcr.io/kennysalazar/sgp-client@sha256:<digest> \
  <sha-git-de-40-caracteres>
```

El script:

1. Rechaza imágenes fuera de los repositorios permitidos o sin digest.
2. Usa `flock` para impedir despliegues simultáneos.
3. Valida Compose antes de reemplazar la configuración activa.
4. Conserva la configuración anterior para rollback.
5. Descarga las imágenes usando la autenticación GHCR de `root` y ejecuta
   `docker compose up --wait`.
6. Comprueba Nginx, la API y Actuator. Spring Boot solo queda saludable después
   de conectarse a RDS, ejecutar Flyway y validar el esquema con Hibernate.
7. Registra el SHA desplegado en `/opt/sgp/staging/REVISION`.

La publicación de imágenes y el despliegue serán jobs independientes para que
un fallo de AWS no obligue a reconstruirlas.

## Acceso privado propuesto

Con Session Manager Plugin instalado en el equipo del integrante:

```bash
aws ssm start-session \
  --target <STAGING_INSTANCE_ID> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["8088"],"localPortNumber":["8088"]}'
```

Mientras la sesión esté abierta, la aplicación se accederá en
`http://localhost:8088`. El identificador, región y permisos reales están
pendientes.

## Verificación y operación

En la instancia:

```bash
sudo docker compose \
  --env-file /etc/sgp/staging/staging.env \
  --env-file /opt/sgp/staging/images.env \
  -f /opt/sgp/staging/compose.yml ps

curl --fail http://127.0.0.1:8088/healthz
curl --fail 'http://127.0.0.1:8088/api/v1/puentes?size=1'

sudo cat /opt/sgp/staging/REVISION
```

Consultar logs sin revelar el archivo de secretos:

```bash
sudo docker compose \
  --env-file /etc/sgp/staging/staging.env \
  --env-file /opt/sgp/staging/images.env \
  -f /opt/sgp/staging/compose.yml logs --tail=200 backend
```

No utilizar opciones destructivas contra RDS o S3 durante una actualización. El
Compose de staging no administra esos recursos.

## Problemas frecuentes

| Síntoma | Revisión |
|---|---|
| Backend no saludable | Revisar el Security Group de RDS, endpoint, TLS, variables obligatorias, credenciales y logs de Flyway. |
| `unauthorized` al descargar GHCR | Confirmar `/root/.docker/config.json`, acceso de la cuenta a ambos paquetes, permiso `read:packages` y vigencia del PAT. Repetir `docker login` sin mostrar el token. |
| Puerto 8088 inaccesible | Confirmar que el túnel SSM siga abierto; el puerto no se publica a Internet. |
| Despliegue con código 75 | Ya existe otro despliegue usando el bloqueo `flock`. |
| Migración o validación de esquema falla | No continuar: revisar logs del backend y consultar `flyway_schema_history` con el usuario migrador. |
| Acceso S3 falla en el futuro | Confirmar rol de instancia, política del bucket, región e IMDSv2 con hop limit `2`; no agregar claves estáticas. |
| Memoria insuficiente | Revisar consumo y swap; evaluar un tamaño ARM64 superior con autorización. |

## Validación realizada

El 9 de octubre de 2026 se validó localmente:

- Construcción de ambas imágenes.
- Sintaxis de Compose y scripts.
- Inicio saludable de los cuatro servicios.
- Respuesta del frontend, API, Nginx y Actuator.
- Ejecución exitosa de las 16 migraciones Flyway.
- Persistencia de PostgreSQL al destruir y recrear contenedores sin borrar
  volúmenes.
- Aislamiento de puertos, redes y volúmenes.

Esas pruebas corresponden al Compose anterior con PostgreSQL y MinIO locales. En
este incremento se validaron estáticamente el nuevo Compose y los scripts. El
SQL de preparación se ejecutó dos veces sobre un contenedor local
PostgreSQL/PostGIS 16: se comprobaron las extensiones, los privilegios DML y de
secuencias del usuario de aplicación, y la prohibición de crear tablas con ese
usuario. Luego la imagen local del backend inició saludablemente con esos roles
y Flyway registró 16 migraciones exitosas y ninguna fallida. Aún no se ha
probado contra una RDS ni un bucket S3 reales.

El workflow también se ejecutó en GitHub después de integrar los cambios en
`develop`: las validaciones de backend y frontend finalizaron correctamente y se
publicaron ambas imágenes en GHCR. El job `Deploy staging` permaneció omitido,
como se esperaba, porque `STAGING_ENABLED` no está activo. Aún no se han validado
los nuevos manifiestos multi-arquitectura, el acceso desde EC2 a los paquetes
privados, OIDC, SSM ni una instancia real. La URL o el identificador de cada
ejecución debe agregarse cuando se recopile la evidencia final.

## Limitaciones y trabajo pendiente

- Registrar la URL o el identificador de la ejecución exitosa de GitHub Actions.
- Publicar y verificar los manifiestos AMD64/ARM64 después de integrar este
  cambio en `develop`.
- Crear y autorizar los recursos mínimos de AWS.
- Ejecutar y validar `infra/rds/prepare-database.sql` contra RDS PostgreSQL 16.
- Confirmar en la versión elegida de RDS la disponibilidad de PostGIS antes de
  crear datos.
- Crear, instalar y probar en EC2 el PAT classic de solo lectura para GHCR.
- Definir responsable y recordatorio de rotación del PAT; su disponibilidad
  depende de que la cuenta conserve acceso a los paquetes privados.
- Confirmar el tamaño definitivo de EC2 y disco mediante medición.
- El backend todavía no integra almacenamiento de objetos; S3 no puede validarse
  funcionalmente hasta que exista la interfaz requerida por DT-ALM-03.
- Brotli no está habilitado; Nginx utiliza gzip para evitar una imagen con módulos
  adicionales no justificados.
- La retención centralizada de logs por 90 días no está implementada.
- Los respaldos y restauraciones de RDS y S3 están fuera del
  alcance de esta historia y permanecen pendientes.
- Configurar HTTPS público también para staging según la nueva arquitectura.

## Referencias de AWS

- [Extensiones de PostgreSQL en Amazon RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.Extensions.html).
- [Configuración de PostGIS en RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html).
- [Uso de TLS con RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).
- [Roles IAM para aplicaciones en EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).
- [IMDSv2 en entornos con contenedores](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html).

