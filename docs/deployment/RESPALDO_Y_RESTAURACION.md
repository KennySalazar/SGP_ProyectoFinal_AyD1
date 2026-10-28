# Guía de respaldo y restauración

## Política

| Requisito | Implementación |
|---|---|
| DT-DEP-07: volcado diario con `pg_dump` | `sgp-postgres-backup.timer` ejecuta `backup-postgres.sh` todos los días a las 03:00 de Guatemala (09:00 UTC). |
| DT-DEP-07: 30 respaldos diarios | Cada volcado se sube a `diarios/<base>/`; el ciclo de vida del bucket los elimina a los 30 días. |
| DT-DEP-07: 12 respaldos mensuales | El día 1 de cada mes (UTC) se sube además una copia a `mensuales/<base>/`, que se elimina a los 365 días. |
| DT-DEP-08: prueba de restauración | `restore-test.sh` restaura un volcado en una base temporal y muestra comprobaciones. Primera prueba exitosa el 17 de octubre de 2026 (sección 5). |

Este esquema no depende de la retención de respaldos automáticos de RDS, que el
plan gratuito de la cuenta limita a 1 día (ver ADR-0002). Esa retención sigue
activa como complemento: permite restaurar RDS a un punto en el tiempo dentro de
las últimas 24 horas.

Los volcados contienen datos personales. El bucket es privado, cifrado con
SSE-S3 y exclusivo para respaldos. El rol de la EC2 solo puede escribir y leer
dentro de `diarios/` y `mensuales/`, y no puede borrar objetos.

## 1. Bucket y ciclo de vida

Crear `sgp-prod-respaldos-ayd1-2026-gt01` desde la consola (sección 2 de
[`PRODUCCION_AWS.md`](PRODUCCION_AWS.md)) y aplicar el ciclo de vida desde un
equipo con AWS CLI:

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket sgp-prod-respaldos-ayd1-2026-gt01 \
  --lifecycle-configuration file://infra/backup/s3-lifecycle.json

aws s3api get-bucket-lifecycle-configuration \
  --bucket sgp-prod-respaldos-ayd1-2026-gt01
```

## 2. Permisos

Agregar a `sgp-prod-ec2-role` una política en línea con el contenido de
`infra/backup/iam-policy-backup.json`. Si cambia el nombre del bucket, hay que
actualizarlo en ese archivo y en `backup.env`.

## 3. Instalación en la EC2 de producción

Desde el equipo del operador:

```bash
infra/production/push-files.sh <INSTANCE_ID> /opt/sgp/backup \
  infra/backup/backup-postgres.sh \
  infra/backup/restore-test.sh \
  infra/backup/sgp-postgres-backup.service \
  infra/backup/sgp-postgres-backup.timer \
  infra/backup/backup.env.example
```

En Session Manager:

```bash
sudo -i
install -d -m 0700 /etc/sgp/backup
install -m 0600 /opt/sgp/backup/backup.env.example /etc/sgp/backup/backup.env
vi /etc/sgp/backup/backup.env   # revisar SGP_ENV_FILE y BACKUP_BUCKET

install -m 0644 /opt/sgp/backup/sgp-postgres-backup.service /etc/systemd/system/
install -m 0644 /opt/sgp/backup/sgp-postgres-backup.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now sgp-postgres-backup.timer

# Primer respaldo manual para validar la instalación.
systemctl start sgp-postgres-backup.service
journalctl -u sgp-postgres-backup.service --no-pager -n 20
```

El script usa el usuario migrador de `/etc/sgp/production/production.env`,
porque es el propietario del esquema, y las credenciales temporales del rol IAM.
Usa `pg_dump` 16 (paquete `postgresql16`) y `awscli-2`, ambos instalados por el
bootstrap de producción.

## 4. Operación

```bash
# Próxima ejecución y resultado de la última.
systemctl list-timers sgp-postgres-backup.timer
journalctl -u sgp-postgres-backup.service --since "2 days ago" --no-pager

# Respaldos disponibles.
aws s3 ls s3://sgp-prod-respaldos-ayd1-2026-gt01/diarios/sgp_prod/
aws s3 ls s3://sgp-prod-respaldos-ayd1-2026-gt01/mensuales/sgp_prod/
```

Antes de guardarlo en S3, el script verifica que el volcado sea legible con
`pg_restore --list`. Si `pg_dump`, la verificación o la subida fallan, el
servicio termina con error y queda registrado en `journalctl`.

## 5. Prueba de restauración (DT-DEP-08)

En Session Manager:

```bash
sudo /opt/sgp/backup/restore-test.sh
```

Sin argumentos, el script usa el respaldo diario más reciente. También acepta
una clave concreta, por ejemplo `mensuales/sgp_prod/sgp_prod-2026-11.dump`. Pide
el usuario y la contraseña del administrador de RDS y crea la base temporal
`sgp_restore_test_<fecha>`. Restaura el volcado, muestra la cantidad de
migraciones Flyway, la versión de PostGIS y los conteos de usuarios, puentes e
inspecciones, y elimina la base temporal al terminar. Con `--conservar`, la base
se mantiene para revisarla y debe borrarse a mano.

Registrar cada prueba:

| Fecha (UTC) | Volcado | Migraciones | Usuarios / puentes / inspecciones | Resultado | Responsable |
|---|---|---|---|---|---|
| 2026-10-17T05:35:29Z | `diarios/sgp_prod/sgp_prod-20261017T053444Z.dump` | 18 (última: 18) | 3 / 0 / 0 | Exitoso; PostGIS 3.4.6; base temporal eliminada | OkKid-A |

## 6. Restauración de producción ante una pérdida de datos

1. **Pérdida en las últimas 24 horas:** preferir la restauración a un punto en el
   tiempo de RDS desde la consola. Crea una instancia nueva; después hay que
   actualizar el endpoint en `DATABASE_URL` o copiar los datos necesarios.
2. **Pérdida anterior, o RDS no disponible:** restaurar un volcado en una base
   nueva y apuntar el backend a ella.

   ```bash
   sudo -i
   # Crear la base nueva con los mismos usuarios, con el procedimiento de
   # PRODUCCION_AWS.md (sección 4) y SGP_DATABASE_NAME=sgp_prod_restaurada.
   aws s3 cp s3://sgp-prod-respaldos-ayd1-2026-gt01/<clave> /var/tmp/restaurar.dump
   read -rsp "Clave del migrador: " PGPASSWORD; printf '\n'; export PGPASSWORD
   pg_restore \
     --host sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com \
     --username sgp_prod_migrator --dbname sgp_prod_restaurada \
     --no-owner --no-acl /var/tmp/restaurar.dump
   unset PGPASSWORD; rm /var/tmp/restaurar.dump
   ```

   Al restaurar como migrador, las tablas quedan a su nombre y el usuario de
   aplicación recibe sus permisos por los privilegios predeterminados que crea
   `prepare-database.sql`. Los únicos errores esperables son los de
   `COMMENT ON EXTENSION`, porque las extensiones pertenecen al administrador.
   Cualquier otro error debe revisarse antes de continuar.

3. Cambiar la base en `DATABASE_URL` de `/etc/sgp/production/production.env` y
   volver a ejecutar `deploy.sh` con los digests actuales. Flyway debe encontrar
   todas las migraciones ya aplicadas.
4. Registrar el incidente, el volcado usado y la pérdida de datos resultante.
