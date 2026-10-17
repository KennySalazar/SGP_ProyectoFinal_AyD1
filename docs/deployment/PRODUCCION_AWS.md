# Despliegue de producción en AWS

## Estado del documento

Esta guía implementa la propuesta de
[`GUIA_IMPLEMENTACION_PRODUCCION.md`](GUIA_IMPLEMENTACION_PRODUCCION.md) y
conserva sus decisiones: EC2 ARM64 propia, RDS compartida con base y usuarios
exclusivos, imágenes aprobadas en staging desplegadas por digest, HTTPS sobre una
IPv4 estable y ningún secreto en el repositorio.

| Área | Estado |
|---|---|
| Archivos del repositorio | `infra/production/` y `infra/backup/` preparados. |
| Recursos AWS de producción | Creados en `us-east-1`: `sgp-prod-ec2-sg`, regla 5432 en `sgp-rds-sg`, rol `sgp-prod-ec2-role`, bucket `sgp-prod-respaldos-ayd1-2026-gt01`, EC2 `sgp-prod-ec2` (`t4g.small`, IPv4 privada `172.31.85.45`, subred pública predeterminada de `us-east-1b`) y Elastic IP `35.174.88.15`. Session Manager y TCP 5432 hacia RDS verificados. |
| Base `sgp_prod` | Preparada con `prepare-database.sql` usando `sslmode=verify-full`; extensiones `postgis` 3.4.6, `pg_trgm` 1.6 y `uuid-ossp` 1.1 confirmadas con `\dx`. |
| Despliegue real | Revisión `61dae8a43bbaa064159d03b15872f6b8eceb7d4f` desplegada el 17 de octubre de 2026 con `deploy.sh`; backend y Nginx saludables. Ver el manifiesto de liberación más abajo. |
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
ejecución con `verify-full` también deja la evidencia de TLS pendiente desde
staging.

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
`GUIA_IMPLEMENTACION_PRODUCCION.md`. Registre fuera del repositorio quién es su
responsable y cuándo expira.

## 7. Despliegue

1. Elegir el commit de `develop` validado en staging y obtener sus digests. En
   la EC2 de staging, `/opt/sgp/staging/images.env` contiene las referencias y
   `/opt/sgp/staging/REVISION` el commit desplegado.
2. Registrar el manifiesto de liberación de la sección 5 de la guía de traspaso.
3. Copiar el Compose y el script a `candidate/`. No copiarlos directamente en
   `/opt/sgp/production`: ahí está el Compose activo, que `deploy.sh` guarda como
   punto de rollback antes de reemplazarlo.

   ```bash
   infra/production/push-files.sh <INSTANCE_ID> /opt/sgp/production/candidate \
     infra/production/compose.yml infra/production/deploy.sh
   ```

4. En Session Manager:

   ```bash
   sudo /opt/sgp/production/candidate/deploy.sh \
     /opt/sgp/production/candidate/compose.yml \
     ghcr.io/kennysalazar/sgp-api@sha256:<digest-aprobado> \
     ghcr.io/kennysalazar/sgp-client@sha256:<digest-aprobado> \
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
despliegue falla. Para volver manualmente a una versión anterior, ejecute
`deploy.sh` con los digests registrados en el manifiesto de esa versión. Ni el
rollback ni un redespliegue modifican datos de RDS; una migración Flyway ya
aplicada no se revierte automáticamente.
