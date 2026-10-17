#!/usr/bin/env bash
set -Eeuo pipefail

# Respaldo diario de PostgreSQL con pg_dump hacia S3 (DT-DEP-07).
# - Cada ejecucion sube un volcado a diarios/<base>/; el ciclo de vida del bucket
#   lo elimina a los 30 dias.
# - El dia 1 de cada mes (UTC) sube ademas una copia a mensuales/<base>/, que se
#   conserva 12 meses.
# La conexion usa el usuario migrador del archivo de entorno del ambiente, porque
# es el propietario del esquema. Las credenciales de S3 provienen del rol IAM de la EC2.

readonly CONFIG_FILE="${SGP_BACKUP_CONFIG:-/etc/sgp/backup/backup.env}"
readonly LOCK_FILE="/run/lock/sgp-postgres-backup.lock"

if [[ ${EUID} -ne 0 ]]; then
  echo "Este script debe ejecutarse como root." >&2
  exit 1
fi

if [[ ! -s "${CONFIG_FILE}" ]]; then
  echo "No existe ${CONFIG_FILE}." >&2
  exit 1
fi

# backup.env define SGP_ENV_FILE y BACKUP_BUCKET.
# shellcheck source=/dev/null
source "${CONFIG_FILE}"
: "${SGP_ENV_FILE:?SGP_ENV_FILE es obligatorio en ${CONFIG_FILE}}"
: "${BACKUP_BUCKET:?BACKUP_BUCKET es obligatorio en ${CONFIG_FILE}}"

if [[ ! "${BACKUP_BUCKET}" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]]; then
  echo "Nombre de bucket invalido: ${BACKUP_BUCKET}" >&2
  exit 1
fi

for command_name in pg_dump pg_restore aws; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Falta el comando ${command_name}." >&2
    exit 1
  fi
done

exec 9>"${LOCK_FILE}"
if ! flock -n 9; then
  echo "Ya existe otro respaldo en ejecucion."
  exit 0
fi

# El archivo de entorno de Compose no se puede cargar con source: sus valores
# pueden contener espacios sin comillas. Se leen solo las claves necesarias.
env_value() {
  sed -n "s/^$1=//p" "${SGP_ENV_FILE}" | tail -n 1
}

jdbc_url="$(env_value DATABASE_URL)"
db_user="$(env_value MIGRATION_DATABASE_USERNAME)"
db_password="$(env_value MIGRATION_DATABASE_PASSWORD)"

if [[ ! "${jdbc_url}" =~ ^jdbc:postgresql://([^:/?]+)(:([0-9]+))?/([a-z][a-z0-9_]*)(\?(.*))?$ ]]; then
  echo "DATABASE_URL no tiene el formato jdbc:postgresql://host:puerto/base esperado." >&2
  exit 1
fi
db_host="${BASH_REMATCH[1]}"
db_port="${BASH_REMATCH[3]:-5432}"
db_name="${BASH_REMATCH[4]}"
db_query="${BASH_REMATCH[6]:-}"

ssl_mode="require"
if [[ "${db_query}" =~ (^|&)sslmode=([a-z-]+) ]]; then
  ssl_mode="${BASH_REMATCH[2]}"
fi

if [[ -z "${db_user}" || -z "${db_password}" ]]; then
  echo "Faltan las credenciales del migrador en ${SGP_ENV_FILE}." >&2
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
work_dir="$(mktemp -d /var/tmp/sgp-backup.XXXXXX)"
trap 'rm -rf "${work_dir}"' EXIT
dump_file="${work_dir}/${db_name}-${timestamp}.dump"

echo "Iniciando pg_dump de ${db_name} en ${db_host}:${db_port}."
PGPASSWORD="${db_password}" PGSSLMODE="${ssl_mode}" pg_dump \
  --host "${db_host}" \
  --port "${db_port}" \
  --username "${db_user}" \
  --dbname "${db_name}" \
  --format custom \
  --compress 6 \
  --no-password \
  --file "${dump_file}"

# Un volcado ilegible no debe reemplazar silenciosamente a los anteriores.
pg_restore --list "${dump_file}" >/dev/null
dump_size="$(stat --format %s "${dump_file}")"

daily_key="diarios/${db_name}/${db_name}-${timestamp}.dump"
aws s3 cp "${dump_file}" "s3://${BACKUP_BUCKET}/${daily_key}" --only-show-errors
echo "Respaldo diario subido: s3://${BACKUP_BUCKET}/${daily_key} (${dump_size} bytes)."

if [[ "$(date -u +%d)" == "01" ]]; then
  monthly_key="mensuales/${db_name}/${db_name}-$(date -u +%Y-%m).dump"
  aws s3 cp "${dump_file}" "s3://${BACKUP_BUCKET}/${monthly_key}" --only-show-errors
  echo "Respaldo mensual subido: s3://${BACKUP_BUCKET}/${monthly_key}."
fi
