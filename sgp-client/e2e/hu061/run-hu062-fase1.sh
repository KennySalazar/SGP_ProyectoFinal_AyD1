#!/usr/bin/env bash
# Solo HU-062 fase 1 y regresión HU-061. Sin instalaciones ni bases persistentes.
set -euo pipefail
raiz=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
modo=${1:-todo}
case "$modo" in fase1|regresion|todo) ;; *) echo 'Uso: bash e2e/hu061/run-hu062-fase1.sh [fase1|regresion|todo]' >&2; exit 2 ;; esac
cd "$raiz"
node -e 'if (Number(process.versions.node.split(".")[0]) < 24) throw Error("Selecciona Node 24 antes de ejecutar")'
java -XshowSettings:properties -version 2>&1 | rg -q 'java.specification.version = 21$'
mvn -version
docker info >/dev/null
pg=hu061-aceptacion-postgis
if docker container inspect "$pg" >/dev/null 2>&1; then
  echo "Ya existe $pg; no se toca. Finaliza su ejecución antes de continuar." >&2
  exit 1
fi
node --input-type=module -e 'import net from "node:net"; for (const port of [18090,4173]) { const s=net.createServer(); await new Promise((ok,no)=>s.once("error",no).listen(port,"127.0.0.1",ok)); await new Promise(ok=>s.close(ok)); }'
evidencias=$(mktemp -d /tmp/hu062-fase1.XXXXXX)
echo "Evidencias: $evidencias"
api_pid=
pg_id=
stop_api() {
  if [[ -n "$api_pid" ]]; then
    kill "$api_pid" 2>/dev/null || true
    wait "$api_pid" 2>/dev/null || true
    api_pid=
  fi
}
cleanup() {
  resultado=$?
  trap - EXIT
  stop_api
  if [[ -n "$pg_id" ]]; then
    # Solo el ID recién creado: nunca eliminar por un nombre reutilizado.
    etiqueta=$(docker inspect --format '{{index .Config.Labels "hu062.aislado"}}' "$pg_id")
    if [[ "$etiqueta" == true ]]; then
      docker rm -f "$pg_id" >>"$evidencias/limpieza.log" 2>&1 || resultado=1
      if docker inspect "$pg_id" >/dev/null 2>&1; then resultado=1; fi
    else
      echo 'Identidad de contenedor inesperada: no se elimina.' >&2
      resultado=1
    fi
  fi
  # Los archivos fotográficos son exclusivos de este mktemp; conservar solo logs/evidencias.
  rm -rf -- "$evidencias/archivos"
  echo "Limpieza terminada; resultado=$resultado; evidencias=$evidencias" | tee -a "$evidencias/limpieza.log"
  exit "$resultado"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
(cd ../sgp-api && mvn -o -DskipTests package) >"$evidencias/package.log" 2>&1
npm run build >"$evidencias/build.log" 2>&1
pg_id=$(docker run -d --name "$pg" --label hu062.aislado=true \
  --tmpfs /var/lib/postgresql/data -p 127.0.0.1::5432 \
  -e POSTGRES_DB=hu061_aceptacion -e POSTGRES_USER=hu061_test \
  -e POSTGRES_PASSWORD=hu061_test postgis/postgis:16-3.5)
limite=$((SECONDS + 60))
until docker exec "$pg_id" pg_isready -U hu061_test -d hu061_aceptacion >/dev/null 2>&1; do
  if (( SECONDS >= limite )); then echo 'PostGIS no quedó listo' >&2; exit 1; fi
  sleep 1
done
puerto=$(docker port "$pg_id" 5432/tcp)
puerto=${puerto##*:}
mkdir "$evidencias/archivos"
start_api() {
  local ttl=$1
  env -i PATH="$PATH" \
    DATABASE_URL="jdbc:postgresql://127.0.0.1:$puerto/hu061_aceptacion" \
    DATABASE_USERNAME=hu061_test DATABASE_PASSWORD=hu061_test \
    MIGRATION_DATABASE_USERNAME=hu061_test MIGRATION_DATABASE_PASSWORD=hu061_test \
    SECRET_KEY_JWT=hu062-clave-ficticia-solo-entorno-desechable-2026 \
    INITIAL_ADMIN_EMAIL=admin-hu061@example.test INITIAL_ADMIN_PASSWORD='Hu061-Prueba-2026!' \
    MAIL_USERNAME=hu062@example.test MAIL_PASSWORD=sin-correo MAIL_HOST=127.0.0.1 MAIL_PORT=9 \
    FORMULARIO_SIEMBRA_ENABLED=false ARCHIVOS_PROVEEDOR=local ARCHIVOS_DIRECTORIO="$evidencias/archivos" \
    ACCESS_EXPIRATION_TIME_JWT="$ttl" CORS_ALLOWED_ORIGINS=http://127.0.0.1:4173 \
    java -jar ../sgp-api/target/sgp-api-0.0.1-SNAPSHOT.jar \
      --spring.config.import= --server.address=127.0.0.1 --server.port=18090 \
      >"$evidencias/backend-$ttl.log" 2>&1 &
  api_pid=$!
  local limite=$((SECONDS + 120))
  until curl --fail --silent --max-time 2 http://127.0.0.1:18090/actuator/health >"$evidencias/health.json"; do
    if ! kill -0 "$api_pid" 2>/dev/null || (( SECONDS >= limite )); then
      echo "Spring Boot no quedó listo; revisar $evidencias/backend-$ttl.log" >&2
      exit 1
    fi
    sleep 1
  done
}
start_api 900000
# Health puede responder antes del listener ApplicationReadyEvent que crea el administrador.
limite=$((SECONDS + 30))
until [[ $(docker exec "$pg_id" psql -U hu061_test -d hu061_aceptacion -Atc \
  "SELECT count(*) FROM usuario WHERE email='admin-hu061@example.test'") == 1 ]]; do
  if (( SECONDS >= limite )); then echo 'Bootstrap de prueba incompleto' >&2; exit 1; fi
  sleep 1
done
docker exec -i "$pg_id" psql -U hu061_test -d hu061_aceptacion -v ON_ERROR_STOP=1 \
  <e2e/hu061/real-fixture.sql >"$evidencias/fixture.log"
if [[ "$modo" != regresion ]]; then
  HU062_FASE1=true HU061_API_ORIGIN=http://127.0.0.1:18090 HU062_ARCHIVOS="$evidencias/archivos" \
    PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/fase1.json" \
    npm run test:pwa -- --grep 'HU-062 fase 1' --reporter=list,json --output "$evidencias/fase1"
fi
if [[ "$modo" != fase1 ]]; then
  # La prueba existente de expiración HU-061 necesita TTL 5 s; no cambiar sus expectativas.
  stop_api
  start_api 5000
  HU061_API_ORIGIN=http://127.0.0.1:18090 \
    PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/hu061-real.json" \
    npm run test:pwa -- --grep 'CA4/CA7 backend real' --reporter=list,json --output "$evidencias/hu061-real"
  env -u HU061_API_ORIGIN PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/hu061-pwa.json" \
    npm run test:pwa -- --grep 'precarga, reabre offline|revalida referencias' --reporter=list,json --output "$evidencias/hu061-pwa"
fi
