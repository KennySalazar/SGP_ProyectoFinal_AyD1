#!/usr/bin/env bash
set -Eeuo pipefail

readonly CERTBOT_IMAGE="certbot/certbot:v5.8.0@sha256:f70ad0adbb7e117f0fe42a63c553f28ea451edabc0148757b6efcd9735acaa20"
readonly CERT_NAME="sgp-staging-ip"
readonly STATE_FILE="/etc/sgp/staging/https-public-ip"
readonly LOCK_FILE="/run/lock/sgp-certbot-renew.lock"

if [[ ${EUID} -ne 0 ]]; then
  echo "Este script debe ejecutarse como root." >&2
  exit 1
fi

exec 9>"${LOCK_FILE}"
if ! flock -n 9; then
  echo "Ya existe otra verificacion de renovacion en curso."
  exit 0
fi

if [[ ! -s "${STATE_FILE}" ]]; then
  echo "No existe ${STATE_FILE}; vuelva a ejecutar install-https.sh." >&2
  exit 1
fi

expected_public_ip=$(<"${STATE_FILE}")
imds_token=$(curl --fail --silent --show-error --request PUT \
  --header "X-aws-ec2-metadata-token-ttl-seconds: 60" \
  http://169.254.169.254/latest/api/token)
current_public_ip=$(curl --fail --silent --show-error \
  --header "X-aws-ec2-metadata-token: ${imds_token}" \
  http://169.254.169.254/latest/meta-data/public-ipv4)

if [[ "${current_public_ip}" != "${expected_public_ip}" ]]; then
  echo "La IP publica cambio de ${expected_public_ip} a ${current_public_ip}." >&2
  echo "Actualice las URLs publicas y emita un certificado nuevo con install-https.sh." >&2
  exit 1
fi

if ! docker image inspect "${CERTBOT_IMAGE}" >/dev/null 2>&1; then
  docker pull "${CERTBOT_IMAGE}"
fi

docker run --rm --pull=never \
  --volume /etc/letsencrypt:/etc/letsencrypt \
  --volume /var/lib/letsencrypt:/var/lib/letsencrypt \
  --volume /var/www/letsencrypt:/var/www/letsencrypt \
  "${CERTBOT_IMAGE}" renew \
  --cert-name "${CERT_NAME}" \
  --preferred-profile shortlived \
  --webroot \
  --webroot-path /var/www/letsencrypt \
  --no-random-sleep-on-renew \
  --non-interactive

nginx -t
systemctl reload nginx
echo "Comprobacion de renovacion completada para ${expected_public_ip}."
