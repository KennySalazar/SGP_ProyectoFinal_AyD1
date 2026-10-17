#!/usr/bin/env bash
set -Eeuo pipefail

# Prueba de restauracion (DT-DEP-08). Descarga un volcado de S3, lo restaura en
# una base temporal de la misma instancia RDS, ejecuta comprobaciones basicas y
# elimina la base temporal. No modifica la base del ambiente.
# Requiere el usuario administrador de RDS, que se solicita de forma interactiva.

readonly CONFIG_FILE="${SGP_BACKUP_CONFIG:-/etc/sgp/backup/backup.env}"

usage() {
  echo "Uso: sudo $0 [clave-s3-del-volcado] [--conservar]" >&2
  echo "Sin clave, se usa el respaldo diario mas reciente." >&2
}

if [[ ${EUID} -ne 0 ]]; then
  echo "Este script debe ejecutarse como root." >&2
  exit 1
fi

dump_key=""
keep_database=false
for argument in "$@"; do
  case "${argument}" in
    --conservar) keep_database=true ;;
    -h | --help) usage; exit 0 ;;
    *) dump_key="${argument}" ;;
  esac
done

# shellcheck source=/dev/null
source "${CONFIG_FILE}"
: "${SGP_ENV_FILE:?SGP_ENV_FILE es obligatorio en ${CONFIG_FILE}}"
: "${BACKUP_BUCKET:?BACKUP_BUCKET es obligatorio en ${CONFIG_FILE}}"

jdbc_url="$(sed -n 's/^DATABASE_URL=//p' "${SGP_ENV_FILE}" | tail -n 1)"
if [[ ! "${jdbc_url}" =~ ^jdbc:postgresql://([^:/?]+)(:([0-9]+))?/([a-z][a-z0-9_]*) ]]; then
  echo "DATABASE_URL no tiene el formato esperado." >&2
  exit 1
fi
db_host="${BASH_REMATCH[1]}"
db_port="${BASH_REMATCH[3]:-5432}"
db_name="${BASH_REMATCH[4]}"

if [[ -z "${dump_key}" ]]; then
  dump_key="$(aws s3api list-objects-v2 \
    --bucket "${BACKUP_BUCKET}" \
    --prefix "diarios/${db_name}/" \
    --query 'sort_by(Contents, &LastModified)[-1].Key' \
    --output text)"
  if [[ -z "${dump_key}" || "${dump_key}" == "None" ]]; then
    echo "No hay respaldos en s3://${BACKUP_BUCKET}/diarios/${db_name}/." >&2
    exit 1
  fi
fi

read -rp "Usuario administrador de RDS: " admin_user
read -rsp "Contrasena del administrador: " admin_password
printf '\n'
export PGPASSWORD="${admin_password}"
export PGSSLMODE=require
unset admin_password

restore_db="sgp_restore_test_$(date -u +%Y%m%d%H%M%S)"
work_dir="$(mktemp -d /var/tmp/sgp-restore.XXXXXX)"

admin_psql() {
  psql --host "${db_host}" --port "${db_port}" --username "${admin_user}" \
    --no-password --set ON_ERROR_STOP=1 --quiet --tuples-only --no-align "$@"
}

cleanup() {
  rm -rf "${work_dir}"
  if [[ "${keep_database}" != true ]]; then
    admin_psql --dbname postgres --command "DROP DATABASE IF EXISTS ${restore_db}" || true
  fi
  unset PGPASSWORD
}
trap cleanup EXIT

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Descargando s3://${BACKUP_BUCKET}/${dump_key}."
aws s3 cp "s3://${BACKUP_BUCKET}/${dump_key}" "${work_dir}/respaldo.dump" --only-show-errors

admin_psql --dbname postgres --command "CREATE DATABASE ${restore_db}"
echo "Restaurando en la base temporal ${restore_db}."
pg_restore --host "${db_host}" --port "${db_port}" --username "${admin_user}" \
  --no-password --dbname "${restore_db}" --no-owner --no-acl --exit-on-error \
  "${work_dir}/respaldo.dump"

echo
echo "=== Resultado de la prueba de restauracion ==="
echo "Fecha (UTC):          ${started_at}"
echo "Volcado:              s3://${BACKUP_BUCKET}/${dump_key}"
echo "Base temporal:        ${restore_db}"
echo "Migraciones Flyway:   $(admin_psql --dbname "${restore_db}" --command 'SELECT count(*) FROM flyway_schema_history WHERE success')"
echo "Ultima migracion:     $(admin_psql --dbname "${restore_db}" --command 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1')"
echo "PostGIS:              $(admin_psql --dbname "${restore_db}" --command 'SELECT postgis_lib_version()')"
echo "Usuarios:             $(admin_psql --dbname "${restore_db}" --command 'SELECT count(*) FROM usuario')"
echo "Puentes:              $(admin_psql --dbname "${restore_db}" --command 'SELECT count(*) FROM puente')"
echo "Inspecciones:         $(admin_psql --dbname "${restore_db}" --command 'SELECT count(*) FROM inspeccion')"
if [[ "${keep_database}" == true ]]; then
  echo "La base temporal se conserva; eliminela manualmente al terminar la revision."
else
  echo "La base temporal se elimina al finalizar."
fi
