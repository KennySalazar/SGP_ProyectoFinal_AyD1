# Despliegue de producción en AWS

## Estado del documento

Esta guía implementa la propuesta de
[`GUIA_IMPLEMENTACION_PRODUCCION.md`](GUIA_IMPLEMENTACION_PRODUCCION.md) y
conserva su infraestructura: EC2 ARM64 propia, RDS compartida con base y usuarios
exclusivos, HTTPS sobre una IPv4 estable y ningún secreto en el repositorio. El
[ADR-0004](../adr/0004-despliegue-automatico-produccion-desde-main.md) reemplazó
la promoción manual de imágenes de staging por el despliegue desde `main`.

| Área | Estado |
|---|---|
| Archivos del repositorio | `infra/production/` y `infra/backup/` preparados. |
| Recursos AWS de producción | Creados en `us-east-1`: `sgp-prod-ec2-sg`, regla 5432 en `sgp-rds-sg`, rol `sgp-prod-ec2-role`, bucket `sgp-prod-respaldos-ayd1-2026-gt01`, EC2 `sgp-prod-ec2` (`t4g.small`, IPv4 privada `172.31.85.45`, subred pública predeterminada de `us-east-1b`) y Elastic IP `35.174.88.15`. Session Manager y TCP 5432 hacia RDS verificados. |
| Base `sgp_prod` | Preparada con `prepare-database.sql` usando `sslmode=verify-full`; extensiones `postgis` 3.4.6, `pg_trgm` 1.6 y `uuid-ossp` 1.1 confirmadas con `\dx`. |
| Despliegue real | Revisión `61dae8a43bbaa064159d03b15872f6b8eceb7d4f` desplegada manualmente el 17 de octubre de 2026 con `deploy.sh`; backend y Nginx saludables. Ver el manifiesto de liberación más abajo. |
| Despliegue automático | El usuario confirmó que el despliegue automático de producción está funcionando; el procedimiento se describe en la sección 7. No se registró un identificador de ejecución. |
| HTTPS público | Operativo en `https://35.174.88.15` con certificado IP de Let's Encrypt y temporizador `sgp-prod-certbot-renew` habilitado. |
| SMTP | Gmail con contraseña de aplicación configurado en `production.env`. |
| Respaldos | Temporizador diario activo; primer volcado subido a S3 y prueba de restauración exitosa el 17 de octubre de 2026 (ver [`RESPALDO_Y_RESTAURACION.md`](RESPALDO_Y_RESTAURACION.md)). |

### Manifiesto de liberación

```text
commit-origen-develop=61dae8a43bbaa064159d03b15872f6b8eceb7d4f
backend-image=ghcr.io/kennysalazar/sgp-api@sha256:4e563c184fc31b418753b59fb306391dcbe03b6248c912fe98dc385cf2c5c33d
frontend-image=ghcr.io/kennysalazar/sgp-client@sha256:ee865c8b4f1ed0c792e5e98b3bc45a1e9d010ef9b7d9c3741f5c533ed4e813ba
fecha=2026-10-17
responsable=OkKid-A
```

Los digests son los que staging tenía desplegados en `/opt/sgp/staging/images.env`
al momento de la promoción. La credencial de GHCR de producción es un PAT propio
de `OkKid-A` con solo `read:packages`.

Actualice esta tabla con evidencia real a medida que se complete cada paso. No
registre contraseñas, tokens ni el contenido de los archivos de entorno.

## 1. Archivos

| Archivo | Responsabilidad |
|---|---|
| `infra/production/compose.yml` | Stack `sgp-prod`: Nginx/Angular y Spring Boot con perfil `prod`, red `sgp_prod_proxy`, límites para `t4g.small`. |
| `infra/production/.env.example` | Plantilla de `/etc/sgp/production/production.env`, con SMTP de Gmail. |
| `infra/production/bootstrap-amazon-linux-2023.sh` | Igual al de staging, con rutas de producción y `awscli-2` para los respaldos. |
| `infra/production/deploy.sh` | Despliegue manual por digest con bloqueo, comprobaciones y rollback, derivado del de staging. |
| `infra/production/https/` | Nginx del host, certificado IP de Let's Encrypt y temporizador `sgp-prod-certbot-renew`. |
| `infra/production/push-files.sh` | Copia archivos del repositorio a la EC2 mediante Systems Manager, desde el equipo del operador. |
| `infra/production/iam/` | Política de confianza y permisos del rol OIDC que usa `deploy-production`. |
| `.github/workflows/ci.yml` | Job `deploy-production`, que despliega producción en cada `push` a `main`. |
| `infra/backup/` | Respaldo diario con `pg_dump` a S3 y prueba de restauración. |
| `sgp-api/src/main/resources/application-prod.properties` | Perfil `prod`; incluye `server.forward-headers-strategy=framework` y `server.shutdown=graceful`, necesarios detrás del Nginx del host. |

Las diferencias con staging son solo nombres, rutas y límites; la lógica de los
scripts es la que staging ya validó.

## 2. Recursos AWS

Todos en `us-east-1`, en la VPC `vpc-04c3c96ee06abd1bc`, con las etiquetas
`Project=SGP` y `Environment=production`.

1. **Security Group `sgp-prod-ec2-sg`.** Entrada TCP 80 y 443 desde
   `0.0.0.0/0`; salida predeterminada. No abrir 22, 8088, 8090 ni 5432.
2. **Regla en `sgp-rds-sg`.** Agregar entrada PostgreSQL TCP 5432 con origen
   `sgp-prod-ec2-sg`. No modificar la regla existente de staging.
3. **Bucket de respaldos `sgp-prod-respaldos-ayd1-2026-gt01`.** Propósito
   general, bloqueo de acceso público completo, ACL deshabilitadas, SSE-S3. Aplicar
   el ciclo de vida de `infra/backup/s3-lifecycle.json` (ver la guía de respaldos).
4. **Rol IAM `sgp-prod-ec2-role`.** Entidad de confianza EC2, política
   administrada `AmazonSSMManagedInstanceCore` y una política en línea
   `sgp-prod-respaldos` con el contenido de `infra/backup/iam-policy-backup.json`.
   No crear access keys para la aplicación.
5. **EC2 `sgp-prod-ec2`.**
   - Amazon Linux 2023, ARM64, `t4g.small`.
   - Disco raíz de 20 GiB gp3, cifrado con `aws/ebs`.
   - Subred pública predeterminada de **`us-east-1b`**, la misma zona que RDS,
     para evitar la latencia y el costo entre zonas observados en staging. Es la
     subred `/20` marcada como *Default subnet: Yes*, cuya tabla de rutas incluye
     `0.0.0.0/0 → igw-…`. **No usar `sgp-rds-private-1b` (`172.31.97.0/24`)**:
     no tiene salida a Internet, por lo que SSM, GHCR, Let's Encrypt, SMTP y S3
     fallan (el agente SSM registra `dial tcp …:443: i/o timeout`).
   - Security Group `sgp-prod-ec2-sg` y perfil de instancia `sgp-prod-ec2-role`.
   - Sin par de claves; la administración es por Session Manager.
   - En *Advanced details*: metadatos **IMDSv2 obligatorio** y *hop limit* `2`.
6. **Elastic IP.** Asignarla y asociarla a `sgp-prod-ec2`. La guía de traspaso
   exige una IPv4 estable en producción: el certificado, CORS y la URL de
   activación dependen de ella. Su costo es el mismo de cualquier IPv4 pública.

El bucket de archivos de la aplicación se creará cuando el backend implemente
`AlmacenamientoArchivos`, siguiendo [`GUIA_INTEGRACION_S3.md`](GUIA_INTEGRACION_S3.md).

## 3. Preparación de la instancia

Desde el equipo del operador, con AWS CLI configurado con su propio usuario IAM:

```bash
infra/production/push-files.sh <INSTANCE_ID> /opt/sgp/production \
  infra/production/bootstrap-amazon-linux-2023.sh \
  infra/rds/prepare-database.sql
```

En una sesión de Session Manager:

```bash
sudo bash /opt/sgp/production/bootstrap-amazon-linux-2023.sh
```

## 4. Base de datos `sgp_prod`

Ejecutar una sola vez desde la EC2 de producción, como administrador de RDS, con
el mismo procedimiento documentado en `STAGING_AWS.md` y nombres exclusivos.

> **Ejecute los comandos uno por uno; no pegue el bloque completo.** Cada `read`
> espera una contraseña y, si se pega el bloque, toma la línea siguiente como
> valor. El texto escrito no se muestra en pantalla; es lo esperado. Antes de
> ejecutar `psql`, compruebe la longitud de las tres claves con
> `echo "${#SGP_MIGRATOR_PASSWORD} ${#SGP_APP_PASSWORD} ${#PGPASSWORD}"`.

```bash
sudo -i
cd /opt/sgp/production
curl --fail --silent --show-error --location \
  --output /etc/sgp/rds-global-bundle.pem \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem

export SGP_DATABASE_NAME=sgp_prod
export SGP_MIGRATOR_USER=sgp_prod_migrator
read -rsp "Clave del migrador: " SGP_MIGRATOR_PASSWORD; printf '\n'
export SGP_APP_USER=sgp_prod_app
read -rsp "Clave de aplicacion: " SGP_APP_PASSWORD; printf '\n'
read -rsp "Clave del administrador RDS: " PGPASSWORD; printf '\n'
export SGP_MIGRATOR_PASSWORD SGP_APP_PASSWORD PGPASSWORD

psql \
  "host=sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com port=5432 dbname=postgres user=postgres sslmode=verify-full sslrootcert=/etc/sgp/rds-global-bundle.pem" \
  --file prepare-database.sql

unset SGP_DATABASE_NAME SGP_MIGRATOR_USER SGP_MIGRATOR_PASSWORD
unset SGP_APP_USER SGP_APP_PASSWORD PGPASSWORD
rm prepare-database.sql
```

Genere las claves con `openssl rand -hex 32`. Guárdelas solo en el archivo de
entorno del paso siguiente y en el gestor de contraseñas del equipo. Esta
ejecución con `verify-full` también permite registrar evidencia de TLS desde
producción.

## 5. Archivo de entorno y SMTP de Gmail

```bash
sudo install -m 0600 /dev/null /etc/sgp/production/production.env
sudo vi /etc/sgp/production/production.env
```

Use `infra/production/.env.example` como plantilla, con valores propios de
producción: credenciales de `sgp_prod`, un `SECRET_KEY_JWT` nuevo
(`openssl rand -hex 32`), la Elastic IP en `INVITACION_URL_ACTIVACION` y
`CORS_ALLOWED_ORIGINS`, y la cuenta de Gmail.

Para Gmail:

1. En la cuenta de Google, activar la **verificación en dos pasos**.
2. Crear una **contraseña de aplicación** en
   <https://myaccount.google.com/apppasswords>. Google la muestra con espacios;
   copiar sus 16 caracteres **sin espacios** en `MAIL_PASSWORD`.
3. Usar la dirección completa en `MAIL_USERNAME` y la misma en `MAIL_FROM`.
   Gmail reemplaza cualquier otro remitente por la cuenta autenticada.
4. Mantener `MAIL_HOST=smtp.gmail.com` y `MAIL_PORT=587`. El backend ya habilita
   STARTTLS, y AWS no bloquea el puerto 587 (solo restringe el 25).

Una cuenta personal de Gmail admite unos 500 destinatarios por día, suficiente
para OTP, invitaciones y recuperación de contraseña en este proyecto. Si la
contraseña de aplicación se filtra, revóquela en la misma página de Google.

## 6. Credencial de GHCR

Producción no reutiliza el token de staging. Cree un Personal Access Token
(classic) propio con solo `read:packages` y fecha de expiración posterior a la
entrega, y autentique a `root` con el procedimiento de la sección 8 de
`GUIA_IMPLEMENTACION_PRODUCCION.md`.

## 7. Despliegue

### Automático desde `main` (procedimiento normal)

Según [ADR-0004](../adr/0004-despliegue-automatico-produccion-desde-main.md),
cada `push` a `main` (normalmente el merge de un Pull Request) ejecuta el
workflow `CI`:

```text
push a main -> backend + frontend -> publicar API y cliente (sha-<SHA>, main)
            -> deploy-production (OIDC + Systems Manager + deploy.sh)
```

`deploy-production` copia `infra/production/compose.yml` como candidato y
`infra/production/deploy.sh` a `/opt/sgp/production`, y ejecuta el despliegue con
los digests publicados por el mismo run. El resultado de `deploy.sh` (salud,
comprobaciones y rollback) aparece en el log del job, y el run de GitHub Actions
es el registro de la liberación.

Configuración inicial, una sola vez:

1. **Rol OIDC.** En IAM → Roles → *Create role* → *Custom trust policy*, pegar
   `infra/production/iam/github-actions-trust-policy.json`. Nombre:
   `sgp-prod-github-actions-role`. Agregar una política en línea
   `sgp-prod-ssm-deploy` con `infra/production/iam/github-actions-ssm-policy.json`,
   reemplazando `REEMPLAZAR_ID_INSTANCIA_PRODUCCION` por el ID de `sgp-prod-ec2`.
   La confianza solo acepta el sujeto de `refs/heads/main`; la política solo
   permite `AWS-RunShellScript` sobre la instancia de producción.
2. **Variables de repositorio.** En GitHub → *Settings → Secrets and variables →
   Actions → Variables*: `PRODUCTION_ROLE_ARN` (ARN del rol),
   `PRODUCTION_INSTANCE_ID` (ID de `sgp-prod-ec2`) y `PRODUCTION_ENABLED=true`.
   `AWS_REGION` ya existe. No son secretos.
3. **Protección de `main`.** En *Settings → Branches*, exigir Pull Request y que
   los checks `Validate backend` y `Validate frontend` pasen antes de fusionar.
   Sin esta regla, un `push` directo a `main` desplegaría producción.

Para suspender los despliegues automáticos, cambie `PRODUCTION_ENABLED` a
`false`; los contenedores en ejecución no se detienen.

### Manual (contingencia)

Para restaurar una versión anterior por digest, o si GitHub Actions no está
disponible:

1. Obtener los digests de la versión: del log del job de publicación, de
   GHCR (etiqueta `sha-<SHA>`) o de `/opt/sgp/production/images.env.previous`.
2. Copiar el Compose y el script a `candidate/`. No copiarlos directamente en
   `/opt/sgp/production`: ahí está el Compose activo, que `deploy.sh` guarda como
   punto de rollback antes de reemplazarlo.

   ```bash
   infra/production/push-files.sh <INSTANCE_ID> /opt/sgp/production/candidate \
     infra/production/compose.yml infra/production/deploy.sh
   ```

3. En Session Manager:

   ```bash
   sudo /opt/sgp/production/candidate/deploy.sh \
     /opt/sgp/production/candidate/compose.yml \
     ghcr.io/kennysalazar/sgp-api@sha256:<digest> \
     ghcr.io/kennysalazar/sgp-client@sha256:<digest> \
     <SHA-de-40-caracteres>
   ```

   El script valida los digests, ejecuta Flyway al arrancar el backend, espera
   los healthchecks, prueba frontend, API y Actuator por la red interna, y
   restaura la versión anterior si algo falla.

## 8. HTTPS

```bash
infra/production/push-files.sh <INSTANCE_ID> /opt/sgp/production/https \
  infra/production/https/install-https.sh \
  infra/production/https/renew-certificate.sh \
  infra/production/https/nginx-http.conf.template \
  infra/production/https/nginx-https.conf.template \
  infra/production/https/sgp-prod-certbot-renew.service \
  infra/production/https/sgp-prod-certbot-renew.timer
```

En Session Manager:

```bash
sudo bash /opt/sgp/production/https/install-https.sh <ELASTIC_IP> <correo-acme>
```

El script exige que el stack ya responda en `127.0.0.1:8088`, comprueba la IP
con IMDSv2, emite el certificado de corta duración y activa el temporizador.

## 9. Verificación

- `https://<ELASTIC_IP>` abre el frontend con certificado válido y
  `http://<ELASTIC_IP>` redirige con 301.
- `https://<ELASTIC_IP>/api/v1/puentes?size=1` responde 200.
- `/actuator/health` y `/api/docs` no son accesibles desde Internet.
- El inicio de sesión del administrador inicial funciona y la cookie de refresco
  es `Secure`, `HttpOnly` y `SameSite=Strict`.
- Una invitación o un OTP llega al correo con remitente `MAIL_FROM` y su enlace
  usa la Elastic IP.
- `sudo systemctl list-timers sgp-prod-certbot-renew.timer` muestra la próxima
  ejecución.
- Después de `sudo reboot`, ambos contenedores vuelven a estar saludables
  (`docker compose -p sgp-prod ps`).
- El primer respaldo existe en S3 (ver la guía de respaldos).

## 10. Rollback

`deploy.sh` restaura automáticamente el Compose y los digests anteriores si el
despliegue falla. Para volver a una versión anterior, revierta el cambio en
`main` mediante un Pull Request (el workflow desplegará la reversión) o use el
procedimiento manual de la sección 7 con los digests de esa versión. Ni el
rollback ni un redespliegue modifican datos de RDS; una migración Flyway ya
aplicada no se revierte automáticamente.
