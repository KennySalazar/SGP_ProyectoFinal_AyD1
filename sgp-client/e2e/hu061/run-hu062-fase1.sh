#!/usr/bin/env bash
# HU-062 fases 1/2/3 y regresión HU-061. Sin instalaciones ni bases persistentes.
set -euo pipefail
raiz=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
modo=${1:-todo}
case "$modo" in fase1|fase2|fase3|paquetes|regresion|todo) ;; *) echo 'Uso: bash e2e/hu061/run-hu062-fase1.sh [fase1|fase2|fase3|paquetes|regresion|todo]' >&2; exit 2 ;; esac
cd "$raiz"
node -e 'if (Number(process.versions.node.split(".")[0]) < 24) throw Error("Selecciona Node 24 antes de ejecutar")'
java -XshowSettings:properties -version 2>&1 | grep -q 'java.specification.version = 21$'
mvn -version
docker info >/dev/null
pg=hu061-aceptacion-postgis
if docker container inspect "$pg" >/dev/null 2>&1; then
  echo "Ya existe $pg; no se toca. Finaliza su ejecución antes de continuar." >&2
  exit 1
fi
node --input-type=module -e 'import net from "node:net"; for (const port of [18090,4173]) { const s=net.createServer(); await new Promise((ok,no)=>s.once("error",no).listen(port,"127.0.0.1",ok)); await new Promise(ok=>s.close(ok)); }'
prefijo=fase1
if [[ "$modo" == fase2 ]]; then prefijo=fase2; fi
if [[ "$modo" == fase3 ]]; then prefijo=fase3; command -v ss python3 >/dev/null; fi
if [[ "$modo" == paquetes ]]; then prefijo=paquetes; fi
evidencias=$(mktemp -d "/tmp/hu062-$prefijo.XXXXXX")
echo "Evidencias: $evidencias"
api_pid=
pg_id=
backend_id=
helper_id=
red_id=
stop_api() {
  if [[ -n "$helper_id" ]]; then
    docker rm -f "$helper_id" >>"$evidencias/limpieza.log" 2>&1
    helper_id=
  fi
  if [[ -n "$backend_id" ]]; then
    docker logs "$backend_id" >>"$evidencias/backend-contenedor.log" 2>&1 || true
    docker rm -f "$backend_id" >>"$evidencias/limpieza.log" 2>&1
    backend_id=
  fi
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
  if [[ -n "$red_id" ]]; then
    docker network rm "$red_id" >>"$evidencias/limpieza.log" 2>&1 || resultado=1
    if docker network inspect "$red_id" >/dev/null 2>&1; then resultado=1; fi
  fi
  # Los archivos fotográficos son exclusivos de este mktemp; conservar solo logs/evidencias.
  rm -rf -- "$evidencias/archivos" "$evidencias/java-tmp"
  echo "Limpieza terminada; resultado=$resultado; evidencias=$evidencias" | tee -a "$evidencias/limpieza.log"
  exit "$resultado"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
(cd ../sgp-api && mvn -o -DskipTests package) >"$evidencias/package.log" 2>&1
npm run build >"$evidencias/build.log" 2>&1
red_args=()
if [[ "$modo" == paquetes ]]; then
  # El operador tc tiene solo NET_ADMIN y comparte únicamente la red del backend propio.
  tc_bin=$(command -v tc || true)
  if [[ -z "$tc_bin" && -x /usr/sbin/tc ]]; then tc_bin=/usr/sbin/tc; fi
  test -x "$tc_bin"
  mkdir "$evidencias/tools"
  cp "$tc_bin" "$evidencias/tools/tc"
  ldd "$tc_bin" | awk '/=> \// {print $3} /^\s*\/.*ld-linux/ {print $1}' | while read -r biblioteca; do cp "$biblioteca" "$evidencias/tools/"; done
  # El intérprete ELF puede aparecer sin => en la salida de ldd.
  cp /lib64/ld-linux-x86-64.so.2 "$evidencias/tools/"
  imagen=$(docker image inspect sgp_proyectofinal_ayd1-backend:latest --format '{{.Id}}')
  red_id=$(docker network create --label hu062.aislado=true "hu062-paquetes-${evidencias##*.}")
  red_args=(--network "$red_id" --network-alias postgis)
  sha256sum ../sgp-api/target/sgp-api-0.0.1-SNAPSHOT.jar >"$evidencias/jar.sha256"
  printf '%s\n' "$imagen" >"$evidencias/imagen.txt"
fi
pg_id=$(docker run -d --name "$pg" --label hu062.aislado=true \
  "${red_args[@]}" \
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
mkdir "$evidencias/archivos" "$evidencias/java-tmp"
start_api() {
  local ttl=$1
  if [[ "$modo" == paquetes ]]; then
    backend_id=$(docker run -d --pull=never --label hu062.aislado=true --network "$red_id" \
      --cap-drop ALL --security-opt no-new-privileges --user "$(id -u):$(id -g)" \
      -p 127.0.0.1:18090:8090 \
      --mount "type=bind,source=$raiz/../sgp-api/target/sgp-api-0.0.1-SNAPSHOT.jar,target=/app/app.jar,readonly" \
      --mount "type=bind,source=$evidencias/archivos,target=/qa-archivos" \
      --mount "type=bind,source=$evidencias/java-tmp,target=/qa-tmp" \
      -e DATABASE_URL=jdbc:postgresql://postgis:5432/hu061_aceptacion \
      -e DATABASE_USERNAME=hu061_test -e DATABASE_PASSWORD=hu061_test \
      -e MIGRATION_DATABASE_USERNAME=hu061_test -e MIGRATION_DATABASE_PASSWORD=hu061_test \
      -e SECRET_KEY_JWT=hu062-clave-ficticia-solo-entorno-desechable-2026 \
      -e INITIAL_ADMIN_EMAIL=admin-hu061@example.test -e INITIAL_ADMIN_PASSWORD='Hu061-Prueba-2026!' \
      -e MAIL_USERNAME=hu062@example.test -e MAIL_PASSWORD=sin-correo -e MAIL_HOST=127.0.0.1 -e MAIL_PORT=9 \
      -e FORMULARIO_SIEMBRA_ENABLED=false -e ARCHIVOS_PROVEEDOR=local -e ARCHIVOS_DIRECTORIO=/qa-archivos \
      -e ACCESS_EXPIRATION_TIME_JWT="$ttl" -e CORS_ALLOWED_ORIGINS=http://127.0.0.1:4173 \
      --entrypoint java "$imagen" -Djava.io.tmpdir=/qa-tmp -jar /app/app.jar \
      --spring.config.import= --server.address=0.0.0.0 --server.port=8090)
  else
  env -i PATH="$PATH" \
    DATABASE_URL="jdbc:postgresql://127.0.0.1:$puerto/hu061_aceptacion" \
    DATABASE_USERNAME=hu061_test DATABASE_PASSWORD=hu061_test \
    MIGRATION_DATABASE_USERNAME=hu061_test MIGRATION_DATABASE_PASSWORD=hu061_test \
    SECRET_KEY_JWT=hu062-clave-ficticia-solo-entorno-desechable-2026 \
    INITIAL_ADMIN_EMAIL=admin-hu061@example.test INITIAL_ADMIN_PASSWORD='Hu061-Prueba-2026!' \
    MAIL_USERNAME=hu062@example.test MAIL_PASSWORD=sin-correo MAIL_HOST=127.0.0.1 MAIL_PORT=9 \
    FORMULARIO_SIEMBRA_ENABLED=false ARCHIVOS_PROVEEDOR=local ARCHIVOS_DIRECTORIO="$evidencias/archivos" \
    ACCESS_EXPIRATION_TIME_JWT="$ttl" CORS_ALLOWED_ORIGINS=http://127.0.0.1:4173 \
    java "-Djava.io.tmpdir=$evidencias/java-tmp" -jar ../sgp-api/target/sgp-api-0.0.1-SNAPSHOT.jar \
      --spring.config.import= --server.address=127.0.0.1 --server.port=18090 \
      >>"$evidencias/backend-$ttl.log" 2>&1 &
  api_pid=$!
  fi
  local limite=$((SECONDS + 120))
  until curl --fail --silent --max-time 2 http://127.0.0.1:18090/actuator/health >"$evidencias/health.json"; do
    if { [[ -n "$api_pid" ]] && ! kill -0 "$api_pid" 2>/dev/null; } || (( SECONDS >= limite )); then
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
if [[ "$modo" == paquetes ]]; then
  chmod g+rwx "$evidencias"
  helper_id=$(docker run -d --pull=never --label hu062.aislado=true \
    --network "container:$backend_id" --cap-drop ALL --cap-add NET_ADMIN \
    --security-opt no-new-privileges --user "0:$(id -g)" \
    --mount "type=bind,source=$evidencias/tools,target=/qa-tools,readonly" \
    --mount "type=bind,source=$evidencias,target=/qa-evidence" \
    --entrypoint /bin/sh "$imagen" -c 'exec sleep 2147483647')
  HU062_PAQUETES=true HU062_BACKEND_ID="$backend_id" HU062_HELPER_ID="$helper_id" \
    HU062_EVIDENCIAS="$evidencias" HU061_API_ORIGIN=http://127.0.0.1:18090 \
    HU062_ARCHIVOS="$evidencias/archivos" HU062_TEMP="$evidencias/java-tmp" \
    PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/paquetes.json" \
    npm run test:pwa -- --grep 'HU-062 paquetes' --reporter=list,json --output "$evidencias/paquetes"
  # Regresión pertinente al proxy compartido; el backend no acumula más lotes.
  HU062_FASE2=true HU061_API_ORIGIN=http://127.0.0.1:18090 HU062_ARCHIVOS="$evidencias/archivos" \
    PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/regresion-proxy.json" \
    npm run test:pwa -- --grep 'confirmación perdida después de commit' --reporter=list,json --output "$evidencias/regresion-proxy"
  exit 0
fi
if [[ "$modo" == fase3 ]]; then
  echo 'Pérdida literal de paquetes sigue NO VERIFICADA; fase 3 no reintenta netem.'
  # Fallo intencional solo tras completar el oráculo real; después se desactiva la bandera.
  if HU062_FASE3=true HU062_FALLO_CONTROLADO=true HU061_API_ORIGIN=http://127.0.0.1:18090 \
    HU062_ARCHIVOS="$evidencias/archivos" HU062_TEMP="$evidencias/java-tmp" \
    PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/fallo-controlado.json" \
    npm run test:pwa -- --grep 'confirmación fotográfica perdida' --reporter=list,json --output "$evidencias/fallo-controlado"; then
    echo 'La comprobación de evidencias debía fallar deliberadamente.' >&2; exit 1
  else
    codigo=$?
    if [[ "$codigo" != 1 ]]; then echo "Fallo de infraestructura: $codigo" >&2; exit "$codigo"; fi
  fi
  python3 - "$evidencias" <<'PY'
import sys, json, zipfile, struct, base64
from pathlib import Path
raiz = Path(sys.argv[1])
datos = json.loads((raiz / 'fallo-controlado.json').read_text())
def resultados(s):
    for spec in s.get('specs', []):
        for test in spec['tests']:
            yield from test['results']
    for suite in s.get('suites', []):
        yield from resultados(suite)
rs = list(resultados(datos))
assert len(rs) == 1 and rs[0]['status'] == 'failed'
assert datos['stats']['unexpected'] == 1 and datos['stats']['skipped'] == 0
assert len(rs[0]['errors']) == 1
assert any('HU062_FALLO_CONTROLADO' in e.get('message', '') for e in rs[0]['errors'])
adjuntos = rs[0]['attachments']
png = Path(next(a['path'] for a in adjuntos if a['contentType'] == 'image/png'))
assert png.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
ancho, alto = struct.unpack('>II', png.read_bytes()[16:24])
assert ancho > 0 and alto > 0
traza = Path(next(a['path'] for a in adjuntos if a['name'] == 'trace'))
with zipfile.ZipFile(traza) as z:
    assert z.testzip() is None
    assert any(n.endswith('.trace') for n in z.namelist())
    assert any(n.endswith('.network') for n in z.namelist())
adjunto_diag = next(a for a in adjuntos if a['name'] == 'diagnostico-fotografico.json')
diag = json.loads(Path(adjunto_diag['path']).read_bytes() if 'path' in adjunto_diag else base64.b64decode(adjunto_diag['body']))
assert diag['resultado']['sincronizada']['inspeccion']['estado'] == 'PUBLICADA'
assert diag['remoto'] == {'vinculos': 1, 'disponibles': 1, 'operaciones': 1}
assert diag['resultado']['sincronizada']['cola'] == []
resumen = {'falloControladoDetectado': True, 'png': str(png), 'dimensiones': [ancho, alto], 'traza': str(traza), 'zipIntegro': True, 'diagnosticoLocalYRemoto': True}
(raiz / 'verificacion-evidencias.json').write_text(json.dumps(resumen, indent=2) + '\n')
print('Captura, traza íntegra y evidencia real comprobadas; se restaura la ejecución normal.')
PY
  HU062_FASE3=true HU062_FALLO_CONTROLADO=false HU061_API_ORIGIN=http://127.0.0.1:18090 \
    HU062_ARCHIVOS="$evidencias/archivos" HU062_TEMP="$evidencias/java-tmp" \
    PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/fase3.json" \
    npm run test:pwa -- --grep 'HU-062 fase 3' --reporter=list,json --output "$evidencias/fase3"
  # Aislar el presupuesto de autenticación por lote (20 refresh/min por IP), sin desactivarlo.
  stop_api
  start_api 900000
  HU062_FASE2=true HU061_API_ORIGIN=http://127.0.0.1:18090 HU062_ARCHIVOS="$evidencias/archivos" \
    PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/fase2-regresion.json" \
    npm run test:pwa -- --grep 'HU-062 fase 2' --reporter=list,json --output "$evidencias/fase2-regresion"
fi
if [[ "$modo" == fase2 ]]; then
  # Diagnóstico solamente: no se ejecuta tc ni se toca una interfaz del anfitrión.
  {
    command -v tc nsenter unshare
    tc -V
    capsh --print | head -5
    sudo -n -l
    unshare --net true
  } >"$evidencias/netem-preflight.log" 2>&1 || true
  echo 'Pérdida real NO VERIFICADA: consultar bloqueo y requisitos en FASE_2_HU062_RESULTADOS.md.' | tee -a "$evidencias/netem-preflight.log"
  for corrida in 1 2; do
    HU062_FASE2=true HU061_API_ORIGIN=http://127.0.0.1:18090 HU062_ARCHIVOS="$evidencias/archivos" \
      PLAYWRIGHT_JSON_OUTPUT_FILE="$evidencias/fase2-$corrida.json" \
      npm run test:pwa -- --grep 'HU-062 fase 2' --reporter=list,json --output "$evidencias/fase2-$corrida"
  done
fi
if [[ "$modo" != regresion ]]; then
  if [[ "$modo" == fase3 ]]; then stop_api; start_api 900000; fi
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
