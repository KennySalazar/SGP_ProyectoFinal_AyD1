#!/usr/bin/env bash
set -Eeuo pipefail

readonly COMPOSE_VERSION="v2.40.3"
readonly COMPOSE_DIR="/usr/local/lib/docker/cli-plugins"
readonly COMPOSE_PATH="${COMPOSE_DIR}/docker-compose"

if [[ "${EUID}" -ne 0 ]]; then
  echo "La preparacion debe ejecutarse como root." >&2
  exit 1
fi

case "$(uname -m)" in
  aarch64 | arm64)
    readonly COMPOSE_ARCH="aarch64"
    ;;
  x86_64 | amd64)
    readonly COMPOSE_ARCH="x86_64"
    ;;
  *)
    echo "Arquitectura no soportada: $(uname -m). Se requiere ARM64 o x86_64." >&2
    exit 1
    ;;
esac

dnf install --assumeyes docker curl
systemctl enable --now docker
systemctl enable --now chronyd

if systemctl list-unit-files amazon-ssm-agent.service >/dev/null 2>&1; then
  systemctl enable --now amazon-ssm-agent
else
  echo "No se encontro amazon-ssm-agent; instalalo antes de habilitar el despliegue." >&2
  exit 1
fi

install -d -m 0755 "${COMPOSE_DIR}"
compose_url="https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${COMPOSE_ARCH}"
checksum_url="${compose_url}.sha256"
temporary_compose="$(mktemp)"
temporary_checksum="$(mktemp)"
trap 'rm -f "${temporary_compose}" "${temporary_checksum}"' EXIT

curl --fail --location --silent --show-error "${compose_url}" --output "${temporary_compose}"
curl --fail --location --silent --show-error "${checksum_url}" --output "${temporary_checksum}"
expected_checksum="$(awk '{print $1}' "${temporary_checksum}")"
actual_checksum="$(sha256sum "${temporary_compose}" | awk '{print $1}')"
if [[ -z "${expected_checksum}" || "${actual_checksum}" != "${expected_checksum}" ]]; then
  echo "La suma SHA-256 de Docker Compose no coincide." >&2
  exit 1
fi
install -m 0755 "${temporary_compose}" "${COMPOSE_PATH}"

install -d -m 0755 /opt/sgp/staging /opt/sgp/postgres/init
install -d -m 0700 /etc/sgp/staging

if [[ ! -f /swapfile ]]; then
  fallocate --length 2G /swapfile
  chmod 0600 /swapfile
  mkswap /swapfile
fi
if ! swapon --show=NAME --noheadings | grep --quiet --fixed-strings /swapfile; then
  swapon /swapfile
fi
if ! grep --quiet --extended-regexp '^/swapfile[[:space:]]' /etc/fstab; then
  printf '/swapfile none swap sw 0 0\n' >>/etc/fstab
fi

if id ssm-user >/dev/null 2>&1; then
  usermod --append --groups docker ssm-user
fi

docker --version
docker compose version
echo "Servidor preparado. Crea /etc/sgp/staging/staging.env con permisos 0600 antes del primer despliegue."
