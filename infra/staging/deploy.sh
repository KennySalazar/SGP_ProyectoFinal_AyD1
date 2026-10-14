#!/usr/bin/env bash
set -Eeuo pipefail

readonly STACK_DIR="${SGP_STAGING_DIR:-/opt/sgp/staging}"
readonly SECRETS_FILE="${SGP_STAGING_ENV:-/etc/sgp/staging/staging.env}"
readonly COMPOSE_FILE="${STACK_DIR}/compose.yml"
readonly IMAGES_FILE="${STACK_DIR}/images.env"
readonly REVISION_FILE="${STACK_DIR}/REVISION"
readonly LOCK_FILE="/var/lock/sgp-staging-deploy.lock"

if [[ "${EUID}" -ne 0 ]]; then
  echo "El despliegue debe ejecutarse como root." >&2
  exit 1
fi

if [[ "$#" -ne 4 ]]; then
  echo "Uso: $0 <compose-candidato> <imagen-backend@digest> <imagen-frontend@digest> <revision>" >&2
  exit 2
fi

readonly CANDIDATE_COMPOSE="$1"
readonly BACKEND_IMAGE="$2"
readonly FRONTEND_IMAGE="$3"
readonly REVISION="$4"

validate_image() {
  local image="$1"
  local repository="$2"
  [[ "${image}" =~ ^ghcr\.io/kennysalazar/${repository}@sha256:[[:xdigit:]]{64}$ ]]
}

if ! validate_image "${BACKEND_IMAGE}" "sgp-api"; then
  echo "La referencia del backend no es un digest permitido de GHCR." >&2
  exit 2
fi

if ! validate_image "${FRONTEND_IMAGE}" "sgp-client"; then
  echo "La referencia del frontend no es un digest permitido de GHCR." >&2
  exit 2
fi

if [[ ! "${REVISION}" =~ ^[[:xdigit:]]{40}$ ]]; then
  echo "La revision debe ser un SHA completo de Git." >&2
  exit 2
fi

for required_file in "${CANDIDATE_COMPOSE}" "${SECRETS_FILE}"; do
  if [[ ! -f "${required_file}" ]]; then
    echo "No existe el archivo requerido: ${required_file}" >&2
    exit 1
  fi
done

install -d -m 0755 "${STACK_DIR}"
touch "${LOCK_FILE}"
chmod 0600 "${LOCK_FILE}"
exec 9>"${LOCK_FILE}"
if ! flock -n 9; then
  echo "Ya existe otro despliegue de staging en ejecucion." >&2
  exit 75
fi

umask 077
next_images="$(mktemp "${STACK_DIR}/images.env.next.XXXXXX")"
printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' \
  "${BACKEND_IMAGE}" "${FRONTEND_IMAGE}" >"${next_images}"

compose() {
  docker compose \
    --env-file "${SECRETS_FILE}" \
    --env-file "${IMAGES_FILE}" \
    --file "${COMPOSE_FILE}" \
    "$@"
}

rollback_available=false
deployment_started=false

rollback() {
  local exit_code=$?
  trap - ERR
  rm -f "${next_images}"

  if [[ "${deployment_started}" == true && "${rollback_available}" == true ]]; then
    echo "El despliegue fallo; restaurando la configuracion anterior." >&2
    install -m 0644 "${COMPOSE_FILE}.previous" "${COMPOSE_FILE}"
    install -m 0600 "${IMAGES_FILE}.previous" "${IMAGES_FILE}"
    if ! compose up --detach --remove-orphans --wait --wait-timeout 180; then
      echo "La restauracion automatica tambien fallo; se requiere intervencion." >&2
    fi
  fi

  exit "${exit_code}"
}
trap rollback ERR

docker compose \
  --env-file "${SECRETS_FILE}" \
  --env-file "${next_images}" \
  --file "${CANDIDATE_COMPOSE}" \
  config --quiet

if [[ -f "${COMPOSE_FILE}" && -f "${IMAGES_FILE}" ]]; then
  cp --preserve=mode,timestamps "${COMPOSE_FILE}" "${COMPOSE_FILE}.previous"
  cp --preserve=mode,timestamps "${IMAGES_FILE}" "${IMAGES_FILE}.previous"
  rollback_available=true
fi

install -m 0644 "${CANDIDATE_COMPOSE}" "${COMPOSE_FILE}"
install -m 0600 "${next_images}" "${IMAGES_FILE}"
rm -f "${next_images}"
deployment_started=true

compose pull
compose up --detach --remove-orphans --wait --wait-timeout 180

compose exec --no-TTY nginx wget --quiet --output-document=/dev/null http://127.0.0.1:8080/healthz
compose exec --no-TTY nginx wget --quiet --output-document=/dev/null 'http://127.0.0.1:8080/api/v1/puentes?size=1'
compose exec --no-TTY backend curl --fail --silent --show-error \
  http://127.0.0.1:8090/actuator/health >/dev/null

printf '%s\n' "${REVISION}" >"${REVISION_FILE}"
chmod 0644 "${REVISION_FILE}"
compose ps
echo "Staging actualizado a la revision ${REVISION}."
