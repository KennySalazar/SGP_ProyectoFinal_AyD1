# Despliegue de staging en AWS

## Estado del documento

Esta guía describe la infraestructura de staging implementada en el repositorio y
se actualiza de forma incremental durante la historia de usuario.

| Área | Estado |
|---|---|
| Imágenes reutilizables de backend y frontend | Implementada y validada localmente. |
| Compose aislado de staging | Implementado y validado localmente. |
| Preparación de Amazon Linux 2023 | Script implementado; no ejecutado en AWS. |
| Workflow de validación, publicación y despliegue | Implementado; pendiente de ejecución en GitHub. |
| Publicación en GHCR | Implementada en el workflow; pendiente de primera ejecución. |
| Despliegue automático desde `develop` | Implementado y deshabilitado hasta disponer de AWS. |
| Instancia EC2, IAM y Systems Manager | Pendiente de autorización y creación. |
| Despliegue real en AWS | No ejecutado. |

No se han creado recursos ni credenciales en AWS.

## Arquitectura de staging

El punto de entrada es Nginx. El puerto se publica únicamente en la interfaz de
loopback de la instancia para acceder mediante un túnel de AWS Systems Manager.
El backend, PostgreSQL y el almacenamiento S3 no publican puertos en el host.

```text
Equipo del proyecto
        |
        | túnel SSM (pendiente de AWS)
        v
127.0.0.1:8088 en EC2
        |
        v
Nginx + Angular ---- red proxy ---- Spring Boot
                                      |
                                      | red data (internal)
                         +------------+------------+
                         |                         |
                  PostgreSQL/PostGIS          Silo/MinIO
```

El backend pertenece a ambas redes: necesita comunicarse con la base y disponer
de salida para SMTP. La red `data` es interna. El servicio de almacenamiento se
mantiene con el nombre `minio` por compatibilidad conceptual, pero utiliza Silo,
un fork compatible con MinIO, porque las imágenes comunitarias oficiales dejaron
de estar disponibles para descargas anónimas. La decisión se apoya en el
[incidente documentado por Red Hat](https://access.redhat.com/solutions/7148629)
y en la
[release verificable de Silo](https://github.com/pgsty/silo/releases/tag/RELEASE.2026-09-16T00-00-00Z).

## Archivos implementados

| Archivo | Responsabilidad |
|---|---|
| `sgp-api/Dockerfile` | Construye la API con Java 21 y la ejecuta sin privilegios. |
| `sgp-client/Dockerfile` | Construye Angular y lo sirve con Nginx sin privilegios. |
| `sgp-client/nginx.conf` | Sirve la SPA, comprime con gzip y reenvía `/api/` al backend. |
| `sgp-api/src/main/resources/application-staging.properties` | Desactiva OpenAPI público y configura el perfil de staging. |
| `infra/staging/compose.yml` | Define el stack aislado que consume imágenes publicadas. |
| `infra/staging/.env.example` | Plantilla sin credenciales reales. |
| `infra/staging/bootstrap-amazon-linux-2023.sh` | Prepara una EC2 x86_64 con Docker, Compose, SSM y swap. |
| `infra/staging/deploy.sh` | Valida, actualiza y comprueba el stack con bloqueo y rollback. |
| `infra/postgres/init/00-create-app-user.sh` | Crea de forma segura el usuario de aplicación. |

El `docker-compose.yml` de la raíz continúa siendo el entorno de desarrollo y no
ha sido sustituido por la configuración de staging.

## Requisitos de infraestructura

La propuesta de menor complejidad y costo es una única EC2 para la primera
implementación de staging:

- Amazon Linux 2023, arquitectura x86_64.
- Tipo `t3.micro` como punto de partida, sujeto a medición y disponibilidad de
  créditos. Si la memoria no es suficiente, evaluar `t3.small` antes de cambiar
  la arquitectura.
- Disco raíz gp3 de aproximadamente 20 GiB como valor inicial, sujeto a
  autorización.
- 2 GiB de swap creados por el script de preparación.
- Rol de instancia con `AmazonSSMManagedInstanceCore` o una política equivalente
  de permisos mínimos.
- Salida HTTPS hacia AWS, GHCR/Docker Hub y el proveedor SMTP.
- Security Group sin reglas de entrada para staging.
- Sin ALB, RDS, NAT Gateway, ECS, EKS ni otros servicios de costo adicional.

El tipo, disco, región, VPC y uso compartido con producción no están aprobados de
forma definitiva. No deben aprovisionarse sin autorización.

## Preparación del servidor

El script está diseñado para ejecutarse una sola vez como `root` en Amazon Linux
2023 x86_64:

```bash
sudo bash /tmp/bootstrap-amazon-linux-2023.sh
```

Realiza las siguientes operaciones:

1. Instala y habilita Docker.
2. Verifica y habilita `amazon-ssm-agent` y `chronyd`.
3. Instala Docker Compose `v2.40.3` y verifica su SHA-256.
4. Crea `/opt/sgp/staging`, `/opt/sgp/postgres/init` y
   `/etc/sgp/staging`.
5. Configura `/swapfile` de 2 GiB si no existe.
6. Agrega `ssm-user` al grupo `docker`, si el usuario existe.

La forma definitiva de transferir y ejecutar este script mediante SSM se
documentará cuando exista la instancia.

## Directorios del servidor

| Ruta | Contenido | Permisos esperados |
|---|---|---|
| `/opt/sgp/staging` | Compose activo, imágenes seleccionadas, revisión y script de despliegue. | Administrado por `root`. |
| `/opt/sgp/postgres/init` | Script de inicialización de PostgreSQL. | Lectura para Docker. |
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
ejemplo `ghcr.io/kennysalazar/sgp-api@sha256:...`. Los paquetes todavía no han
sido publicados y su visibilidad pública deberá configurarse después de la
primera publicación.

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
separadas para cada imagen, genera SBOM y procedencia, y construye únicamente
`linux/amd64`, que coincide con la arquitectura seleccionada para staging.

Configure un GitHub Environment llamado `staging`, restringido a la rama
`develop`, con estas variables:

| Variable | Nivel | Estado inicial | Propósito |
|---|---|---|---|
| `STAGING_ENABLED` | Repositorio | Ausente o `false` | Habilita el job; debe ser de repositorio porque se evalúa antes de cargar el Environment. |
| `AWS_REGION` | Environment `staging` | Pendiente | Región de la instancia y del rol. |
| `AWS_ROLE_ARN` | Environment `staging` | Pendiente | Rol asumido mediante OIDC. |
| `STAGING_INSTANCE_ID` | Environment `staging` | Pendiente | Instancia administrada por SSM. |

No se requieren GitHub Secrets para GHCR ni AWS: GHCR usa el `GITHUB_TOKEN`
efímero y AWS usa OIDC. La relación de confianza del rol debe limitar el sujeto a
`repo:KennySalazar/SGP_ProyectoFinal_AyD1:environment:staging`. Su política debe
permitir únicamente enviar `AWS-RunShellScript` a la instancia de staging,
consultar el resultado y cancelar el comando en caso de timeout.

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
| `POSTGRES_DB` | PostgreSQL/backend | Sí | Base aislada; `sgp_staging`. |
| `POSTGRES_MIGRATOR_USER` | PostgreSQL/Flyway | Sí | Propietario de migraciones; `sgp_staging_migrator`. |
| `POSTGRES_MIGRATOR_PASSWORD` | PostgreSQL/Flyway | Sí | Secreto hexadecimal aleatorio. |
| `POSTGRES_APP_USER` | PostgreSQL/backend | Sí | Usuario de mínimo privilegio; `sgp_staging_app`. |
| `POSTGRES_APP_PASSWORD` | PostgreSQL/backend | Sí | Secreto distinto al del migrador. |
| `MINIO_ROOT_USER` | Silo/MinIO | Sí | Administrador del almacenamiento; `sgp_staging_minio`. |
| `MINIO_ROOT_PASSWORD` | Silo/MinIO | Sí | Secreto aleatorio de al menos 8 caracteres. |
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

## Docker Compose

| Servicio | Exposición | Persistencia | Salud |
|---|---|---|---|
| `nginx` | `127.0.0.1:8088` | Imagen inmutable | `GET /healthz`. |
| `backend` | Solo red `proxy` | Sin datos locales | Actuator interno. |
| `postgres` | Solo red `data` | `sgp_staging_postgres_data` | `pg_isready`. |
| `minio` | Solo red `data` | `sgp_staging_minio_data` | `/minio/health/live`. |

Las redes son `sgp_staging_proxy` y `sgp_staging_data`. Estos nombres y los
volúmenes son exclusivos de staging para evitar colisiones con producción.

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
5. Descarga las imágenes y ejecuta `docker compose up --wait`.
6. Comprueba Nginx, la API pública y al menos las 16 migraciones existentes.
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

No utilizar `docker compose down -v`: eliminaría datos persistentes.

## Problemas frecuentes

| Síntoma | Revisión |
|---|---|
| Backend no saludable | Revisar PostgreSQL, variables obligatorias, política de contraseña inicial y logs de Flyway. |
| Error al descargar GHCR | Confirmar que el paquete sea público o configurar autenticación de solo lectura fuera del repositorio. |
| Puerto 8088 inaccesible | Confirmar que el túnel SSM siga abierto; el puerto no se publica a Internet. |
| Despliegue con código 75 | Ya existe otro despliegue usando el bloqueo `flock`. |
| Migraciones menores que 16 | No continuar: revisar logs del backend y `flyway_schema_history`. |
| Servicio `minio` no inicia | Confirmar acceso a Docker Hub y el digest de Silo fijado en Compose. |
| Memoria insuficiente | Revisar consumo y swap; evaluar `t3.small` con autorización. |

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

El workflow fue revisado y formateado localmente, pero todavía no se ha ejecutado
en GitHub. Tampoco se han validado GHCR, OIDC, SSM ni una EC2 real.

## Limitaciones y trabajo pendiente

- Ejecutar el workflow en GitHub y registrar su evidencia.
- Crear y autorizar los recursos mínimos de AWS.
- Configurar la visibilidad de los paquetes GHCR.
- Confirmar el tamaño definitivo de EC2 y disco mediante medición.
- El backend todavía no usa MinIO/Silo; el dominio `archivo` está preparado pero
  no integra almacenamiento de objetos.
- Brotli no está habilitado; Nginx utiliza gzip para evitar una imagen con módulos
  adicionales no justificados.
- La retención centralizada de logs por 90 días no está implementada.
- Los respaldos y restauraciones de PostgreSQL y MinIO/Silo están fuera del
  alcance de esta historia y permanecen pendientes.
- HTTPS público corresponde a producción, no a staging.

