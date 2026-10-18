#!/usr/bin/env bash
set -Eeuo pipefail

# Copia archivos del repositorio a la EC2 de produccion mediante Systems Manager,
# con el mismo mecanismo que usa el workflow de staging. Se ejecuta desde el equipo
# del operador con AWS CLI v2 y credenciales de su propio usuario IAM.

usage() {
  echo "Uso: $0 <instance-id> <directorio-destino> <archivo>..." >&2
  echo "Ejemplo: $0 i-0123456789abcdef0 /opt/sgp/production infra/production/compose.yml" >&2
}

if [[ $# -lt 3 ]]; then
  usage
  exit 2
fi

readonly INSTANCE_ID="$1"
readonly TARGET_DIR="$2"
shift 2

if [[ ! "${INSTANCE_ID}" =~ ^i-[[:xdigit:]]{8,17}$ ]]; then
  echo "Identificador de instancia invalido: ${INSTANCE_ID}" >&2
  exit 2
fi

if [[ ! "${TARGET_DIR}" =~ ^/opt/sgp/(production|backup)(/[a-z0-9_-]+)*$ ]]; then
  echo "El destino debe estar dentro de /opt/sgp/production o /opt/sgp/backup." >&2
  exit 2
fi

for command_name in aws jq base64; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Falta el comando ${command_name}." >&2
    exit 1
  fi
done

commands=("set -eu" "install -d -m 0755 '${TARGET_DIR}'")
for source_file in "$@"; do
  if [[ ! -f "${source_file}" ]]; then
    echo "No existe el archivo: ${source_file}" >&2
    exit 1
  fi
  file_name="$(basename -- "${source_file}")"
  mode=0644
  if [[ -x "${source_file}" ]]; then
    mode=0755
  fi
  content_b64="$(base64 --wrap=0 "${source_file}")"
  commands+=("printf %s '${content_b64}' | base64 --decode > '${TARGET_DIR}/${file_name}'")
  commands+=("chmod ${mode} '${TARGET_DIR}/${file_name}'")
done
commands+=("ls -l '${TARGET_DIR}'")

parameters="$(printf '%s\n' "${commands[@]}" | jq --raw-input --slurp '{commands: (split("\n") | map(select(length > 0)))}')"

command_id="$(aws ssm send-command \
  --document-name AWS-RunShellScript \
  --instance-ids "${INSTANCE_ID}" \
  --comment "SGP produccion: copia de archivos" \
  --timeout-seconds 120 \
  --parameters "${parameters}" \
  --query 'Command.CommandId' \
  --output text)"

echo "Comando SSM enviado: ${command_id}"

for _ in $(seq 1 30); do
  sleep 2
  status="$(aws ssm get-command-invocation \
    --command-id "${command_id}" \
    --instance-id "${INSTANCE_ID}" \
    --query 'Status' \
    --output text 2>/dev/null || echo Pending)"
  case "${status}" in
    Success)
      aws ssm get-command-invocation \
        --command-id "${command_id}" \
        --instance-id "${INSTANCE_ID}" \
        --query 'StandardOutputContent' \
        --output text
      exit 0
      ;;
    Failed | Cancelled | TimedOut)
      aws ssm get-command-invocation \
        --command-id "${command_id}" \
        --instance-id "${INSTANCE_ID}" \
        --query 'StandardErrorContent' \
        --output text >&2
      exit 1
      ;;
  esac
done

echo "El comando no termino a tiempo; revise ${command_id} en Systems Manager." >&2
exit 1
