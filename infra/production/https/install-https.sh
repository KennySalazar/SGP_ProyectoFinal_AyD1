#!/usr/bin/env bash
set -Eeuo pipefail

readonly CERTBOT_IMAGE="certbot/certbot:v5.8.0@sha256:f70ad0adbb7e117f0fe42a63c553f28ea451edabc0148757b6efcd9735acaa20"
readonly CERT_NAME="sgp-prod-ip"
readonly NGINX_CONFIG="/etc/nginx/conf.d/sgp-production.conf"
readonly STATE_FILE="/etc/sgp/production/https-public-ip"
readonly INSTALL_DIR="/opt/sgp/production/https"
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  echo "Uso: sudo $0 <ipv4-publica> <correo-acme>" >&2
}

is_ipv4() {
  local ip=$1 octet
  local IFS=.
  read -r -a octets <<<"${ip}"
  [[ ${#octets[@]} -eq 4 ]] || return 1
  for octet in "${octets[@]}"; do
    [[ ${octet} =~ ^[0-9]{1,3}$ ]] || return 1
    ((10#${octet} <= 255)) || return 1
  done
}

if [[ ${EUID} -ne 0 ]]; then
  echo "Este script debe ejecutarse como root." >&2
  exit 1
fi

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi

public_ip=$1
acme_email=$2

if ! is_ipv4 "${public_ip}"; then
  echo "IPv4 publica invalida: ${public_ip}" >&2
  exit 2
fi

if [[ ! ${acme_email} =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
  echo "Correo ACME invalido." >&2
  exit 2
fi

for source_file in \
  nginx-http.conf.template \
  nginx-https.conf.template \
  renew-certificate.sh \
  sgp-prod-certbot-renew.service \
  sgp-prod-certbot-renew.timer; do
  if [[ ! -f "${SCRIPT_DIR}/${source_file}" ]]; then
    echo "Falta ${SCRIPT_DIR}/${source_file}." >&2
    exit 1
  fi
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker no esta instalado; ejecute primero el bootstrap de produccion." >&2
  exit 1
fi

curl --fail --silent --show-error http://127.0.0.1:8088/healthz >/dev/null

imds_token=$(curl --fail --silent --show-error --request PUT \
  --header "X-aws-ec2-metadata-token-ttl-seconds: 60" \
  http://169.254.169.254/latest/api/token)
detected_public_ip=$(curl --fail --silent --show-error \
  --header "X-aws-ec2-metadata-token: ${imds_token}" \
  http://169.254.169.254/latest/meta-data/public-ipv4)

if [[ "${detected_public_ip}" != "${public_ip}" ]]; then
  echo "La IP indicada (${public_ip}) no coincide con IMDSv2 (${detected_public_ip})." >&2
  exit 1
fi

dnf install --assumeyes nginx
systemctl enable --now docker nginx

install -d -m 0755 "${INSTALL_DIR}" /var/www/letsencrypt/.well-known/acme-challenge
install -d -m 0700 /etc/letsencrypt /var/lib/letsencrypt
install -d -m 0755 /etc/sgp/production
for source_file in install-https.sh renew-certificate.sh; do
  if [[ "$(readlink -f "${SCRIPT_DIR}/${source_file}")" != "$(readlink -m "${INSTALL_DIR}/${source_file}")" ]]; then
    install -m 0755 "${SCRIPT_DIR}/${source_file}" "${INSTALL_DIR}/${source_file}"
  fi
done
for source_file in nginx-http.conf.template nginx-https.conf.template; do
  if [[ "$(readlink -f "${SCRIPT_DIR}/${source_file}")" != "$(readlink -m "${INSTALL_DIR}/${source_file}")" ]]; then
    install -m 0644 "${SCRIPT_DIR}/${source_file}" "${INSTALL_DIR}/${source_file}"
  fi
done
install -m 0644 "${SCRIPT_DIR}/sgp-prod-certbot-renew.service" /etc/systemd/system/sgp-prod-certbot-renew.service
install -m 0644 "${SCRIPT_DIR}/sgp-prod-certbot-renew.timer" /etc/systemd/system/sgp-prod-certbot-renew.timer

backup_config=""
if [[ -f "${NGINX_CONFIG}" ]]; then
  backup_config="${NGINX_CONFIG}.backup.$(date -u +%Y%m%dT%H%M%SZ)"
  cp --preserve=mode,ownership,timestamps "${NGINX_CONFIG}" "${backup_config}"
fi

rollback_nginx() {
  local exit_code=$?
  echo "La instalacion HTTPS fallo; restaurando la configuracion anterior de Nginx." >&2
  if [[ -n "${backup_config}" ]]; then
    cp --preserve=mode,ownership,timestamps "${backup_config}" "${NGINX_CONFIG}"
  else
    rm -f "${NGINX_CONFIG}"
  fi
  nginx -t >/dev/null 2>&1 && systemctl reload nginx || true
  exit "${exit_code}"
}
trap rollback_nginx ERR

sed "s/__PUBLIC_IP__/${public_ip}/g" \
  "${INSTALL_DIR}/nginx-http.conf.template" >"${NGINX_CONFIG}"
chmod 0644 "${NGINX_CONFIG}"
nginx -t
systemctl reload nginx

docker pull "${CERTBOT_IMAGE}"

certificate_reusable=false
if [[ -s "${STATE_FILE}" ]] \
  && [[ "$(<"${STATE_FILE}")" == "${public_ip}" ]] \
  && [[ -s "/etc/letsencrypt/live/${CERT_NAME}/fullchain.pem" ]] \
  && [[ -s "/etc/letsencrypt/live/${CERT_NAME}/privkey.pem" ]] \
  && openssl x509 -checkend 3600 -noout \
    -in "/etc/letsencrypt/live/${CERT_NAME}/fullchain.pem" \
  && openssl x509 -noout -ext subjectAltName \
    -in "/etc/letsencrypt/live/${CERT_NAME}/fullchain.pem" \
    | grep --fixed-strings --quiet "IP Address:${public_ip}"; then
  certificate_reusable=true
fi

if [[ "${certificate_reusable}" == "true" ]]; then
  echo "Se reutilizara el certificado vigente para ${public_ip}."
else
  certbot_args=(
    certonly
    --non-interactive
    --agree-tos
    --email "${acme_email}"
    --preferred-profile shortlived
    --webroot
    --webroot-path /var/www/letsencrypt
    --ip-address "${public_ip}"
    --cert-name "${CERT_NAME}"
  )

  if [[ -e "/etc/letsencrypt/renewal/${CERT_NAME}.conf" ]]; then
    certbot_args+=(--force-renewal)
  fi

  docker run --rm \
    --volume /etc/letsencrypt:/etc/letsencrypt \
    --volume /var/lib/letsencrypt:/var/lib/letsencrypt \
    --volume /var/www/letsencrypt:/var/www/letsencrypt \
    "${CERTBOT_IMAGE}" "${certbot_args[@]}"
fi

sed \
  -e "s/__PUBLIC_IP__/${public_ip}/g" \
  -e "s/__CERT_NAME__/${CERT_NAME}/g" \
  "${INSTALL_DIR}/nginx-https.conf.template" >"${NGINX_CONFIG}"
chmod 0644 "${NGINX_CONFIG}"
printf '%s\n' "${public_ip}" >"${STATE_FILE}"
chmod 0644 "${STATE_FILE}"

nginx -t
systemctl reload nginx
systemctl daemon-reload
systemctl enable --now sgp-prod-certbot-renew.timer

trap - ERR
echo "HTTPS preparado para https://${public_ip}."
echo "Valide el certificado y las rutas publicas antes de declararlo operativo."
