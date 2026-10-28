# Revisión Académica: preparación y validación funcional

## A. Objetivo y alcance

Esta guía cubre exclusivamente **REV-01 a REV-04: cinco puntos**. SQL prepara
precondiciones y comprueba persistencia; las acciones calificadas se realizan en
la interfaz o API. Una fila insertada por SQL nunca demuestra una aprobación,
rechazo, resolución o alerta ejecutada por la aplicación.

Referencias: `.context/sprints/sprint3_sgp.md`, HU-035 a HU-039, y
`.context/ENUNCIADO_SGP_2026_Codex.md`, RN-REV-01 a RN-REV-08. La planificación
asigna H a Brígido, Melvin y Mushin; en este entorno corresponde a **Brígido,
Kevin y Melvin**. No se asignan tareas con base en la composición histórica.
La alerta se planificó para Sprint 4, pero **sí existe en el código actual**.

Archivos, relativos a la raíz:

| Archivo | Uso |
|---|---|
| `tests/fixtures/revision-academica/seed_revision_academica.sql` | Preparar sin restablecer avances |
| `tests/fixtures/revision-academica/fixture_revision_academica.sql` | Manifiesto compartido de identidades y precondiciones; no ejecutar solo |
| `tests/fixtures/revision-academica/cleanup_revision_academica.sql` | Limpieza explícita y transaccional |
| `tests/fixtures/revision-academica/verificar_revision_academica.sql` | Consultas de solo lectura |

### Implementación inspeccionada y límites de aceptación

Los cuatro criterios tienen implementación backend y pantallas relacionadas. Esto
es una conclusión de lectura del código, **no un resultado funcional aprobado**.

| Criterio | Implementación localizada | Límite que debe evaluarse |
|---|---|---|
| REV-01 | `RevisionController`, `RevisionService`, `RevisionRepository`, `CicloRevisionService`, `VeredictoRevisionController` | Historial precargado y ciclos creados realmente deben distinguirse |
| REV-02 | `ObservacionController`, `ObservacionAutorController`, `ObservacionService`, `AtencionObservacionesService`, `ValidadorAnclajeFormulario` | Resolver exige último ciclo con cambios y borrador |
| REV-03 | `VeredictoRevisionService`, `TransicionInspeccionService`, `ConfirmacionEstadoIcService`, `PublicacionIndiceCondicionService` | Se exige nota 0–100 para todos los veredictos; cambios no devuelve inmediatamente a borrador |
| REV-04 | `AlertaRevisionesSinRevisarService`, `RevisionRepository.findSinRevisarDesde`, `ProcesosProgramados` | Incluye ciclos `EN_CURSO`; mide 15 períodos de 24 horas desde `enviada_en` |

**Discrepancia RN-REV-06:** el veredicto deja `CAMBIOS_SOLICITADOS`; después el
autor debe ejecutar `atender-cambios` para llegar a `BORRADOR`. La planificación
describe esos dos pasos, mientras el enunciado y la hoja requieren regreso a
borrador con el veredicto. Registrar **No cumple en REV-03 B** si se aplica la
exigencia literal de retorno inmediato; no ocultarlo detrás del paso adicional.
No se cambió código para adaptar esta diferencia.

No se encontró un endpoint para ejecutar manualmente el job. No se agrega uno.
No se encontró una funcionalidad principal completamente ausente; quedan por
validar comportamiento e interfaz y resolver la discrepancia indicada.

## B. Requisitos previos

- Entorno **expresamente destinado a pruebas**, identificado antes de escribir.
  Ni el nombre `sgp_db` ni estar en localhost demuestran aislamiento. Para desplegado,
  usar una instancia/base de pruebas autorizada por su responsable.
- PostgreSQL 16 con PostGIS 3.5, `uuid-ossp` y `pg_trgm`; cliente `psql`, Bash,
  `curl`, `jq`, Python 3 y `htpasswd` (apache2-utils) para generar BCrypt fuera del repositorio.
- Java 21, Maven, Node y npm si se ejecutan backend/frontend fuera de Compose;
  Docker y Compose para la segunda modalidad.
- Migraciones **V1 a V25** aplicadas por Flyway, sin fallos. No colocar estos SQL
  dentro de `sgp-api/src/main/resources/db/migration`.
- Aplicación accesible, almacenamiento local o S3/MinIO disponible para las fotos
  que exige el flujo de publicación/envío. Las fotos se cargan por la aplicación;
  el seed no inventa objetos ni metadatos de archivos disponibles.
- Usuario SQL de preparación con permisos de inserción/borrado sobre las tablas
  indicadas, lectura de roles y `flyway_schema_history`. En Compose es
  `sgp_migrator`; la API usa `sgp_user`. No utilizar una cuenta real como autor.

Spring Boot importa `sgp-api/.env` al iniciarse desde `sgp-api`. Crear ese archivo
solo si falta, a partir de `.env.example`, y completar los valores localmente.
No sobrescribir un `.env` existente. Revisar `docs/DEVELOPMENT.md`.

Variables reales: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`,
`MIGRATION_DATABASE_USERNAME`, `MIGRATION_DATABASE_PASSWORD`, `SECRET_KEY_JWT`,
`INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`;
las restantes están en `.env.example` y `application.properties`.
Para almacenamiento: `ARCHIVOS_PROVEEDOR=local` o la configuración S3/MinIO
existente. `FORMULARIO_SIEMBRA_ENABLED` controla la siembra oficial al arrancar;
este fixture **no modifica** ese mecanismo ni el formulario activo.

Generar un hash para una contraseña elegida exclusivamente para estas cuentas:

```bash
# htpasswd solicita la contraseña sin escribirla en el historial.
FIXTURE_HASH=$(htpasswd -nBC 10 fixture | cut -d: -f2)
export FIXTURE_HASH
```

No guardar contraseña, hash, tokens ni respuestas de login en el repositorio.
El script exige BCrypt e inserta ese hash **solo en usuarios nuevos**. Repetirlo
con otro hash no cambia contraseñas existentes. Tras terminar: `unset FIXTURE_HASH`.

### Inventario inicial y propiedad

UUID deterministas: `uuid_generate_v5('b58c8ec5-3bb8-4f04-b0d3-9c287ee90635',
'SGP-TEST-REV:' || clave)`. El manifiesto conserva las claves (`u:cat-a`,
`i:aprobar`, etc.). No basta que coincida el UUID: se validan también marcadores,
autores, padres y demás identidad estable. Una colisión o modificación de esa
identidad aborta toda la operación. Los conflictos en cualquier otro índice único
abortan y revierten; no se adoptan registros por compartir correo o código.

| Tabla | Cantidad | Tipo |
|---|---:|---|
| `usuario` | 6 | Dos catedráticos, dos estudiantes, externo y administrador ficticio |
| `usuario_profesional` | 1 | Colegiado externo verificado por administrador ficticio |
| `departamento`, `municipio` | 1 cada una | Territorio ficticio 99/9999, marcado SGP-TEST-REV |
| `asignatura` | 1 | Dependencia académica exclusiva |
| `curso`, `curso_estudiante` | 2 cada una | A y B, vigentes desde fecha de primera carga |
| `version_formulario`, `configuracion_ic_elemento` | 1 cada una | Versión privada publicada, inactiva, un elemento con peso 1 |
| `puente` | 10 | Un puente por escenario |
| `asignacion_puente` | 8 | Una por inspección estudiantil |
| `inspeccion` | 10 | Siete borradores y tres enviadas |
| `revision` | 5 | Dos resueltas históricas y tres pendientes |
| `historial_estado_inspeccion` | 21 | Precondiciones, no evidencia de ejecución real |
| **Total inicial** | **70** | Sin observaciones, archivos, notas nuevas ni alertas simuladas |

Los códigos de puente respetan el formato `GT-99-9999-0001` a `0010`; sus nombres,
ruta y territorio tienen marca `SGP-TEST-REV`. El municipio exclusivo evita
modificar correlativos de catálogos oficiales. Es ficticio y no tiene límite
municipal cartográfico: **no usar estos datos para probar altas territoriales**.
Ubicación sintética: latitud 14.481, longitud -90.615, dentro de Guatemala.

La versión `SGP-TEST-REV-v1` está publicada pero **no activa**. Contiene el
subconjunto real de rutas y enumeraciones SIECA:
`/subestructura/estribo_entrada/cortina_superior/{existencia,material,grietas_una_direccion}`.
No pretende representar una visita técnica completa ni validar/calibrar el IC del
manual; únicamente satisface dependencias del módulo bajo prueba. No cambia la
versión oficial. Los snapshots precargados tienen hash sintético marcado por el
manifiesto; no se usan como prueba de la generación de huellas del envío real.

Las PK son UUID; `usuario_profesional` usa `usuario_id`. FK: usuarios→roles;
curso→asignatura/catedrático; inscripción→curso/estudiante; asignación→inscripción/
puente; inspección→puente/versión/autor/asignación; revisión→inspección/revisor;
observación e historial→revisión/inspección/actor. La nota está en
`revision.calificacion NUMERIC(6,2)`, no en una tabla de calificaciones separada.
Los estados son `VARCHAR` con CHECK, no ENUM PostgreSQL.

Restricciones relevantes: correos minúsculos y únicos; nombre de usuario único
sin distinguir mayúsculas; curso único por asignatura/período (V15 eliminó sección
del curso: está en inscripción); asignación activa única por inscripción/puente;
versión activa única; revisión única por inspección/ciclo y una abierta por
inspección; historial único por inspección/secuencia; observación resuelta exige
actor/fecha; revisión resuelta exige veredicto/fecha y texto para cambios/rechazo;
alerta única por usuario/clave de evento. Borradores usan defaults de versión 0,
datos `{}`, estado `BORRADOR`; las fechas por defecto usan `CURRENT_TIMESTAMP`.
El verificador incluye columnas obligatorias, defaults, CHECK, FK e índices exactos
obtenidos del catálogo conectado, para no depender de una transcripción incompleta.

Se revisó la secuencia Flyway: V1–V4 seguridad/perfiles; V5–V6 academia/inventario;
V7 formulario; V8 inspecciones; V9 revisión/historial/IC; V10 archivos/mantenimiento;
V11 notificaciones/foro/idempotencia; V12–V14 territorio; V15 sección por inscripción;
V16 auditoría automática; V17–V19 estado de puente/confirmación/huella de inicio;
V20 categoría de observación; V21 hilo único; V22–V23 calibración/avisos;
V24 recalculo único; V25 planes rutinarios. No se modifica ninguna migración.

### Cuentas y escenarios

Todos los correos terminan en `@example.invalid`; contraseña: la elegida al generar
`FIXTURE_HASH`. Están activos, activados y verificados, sin 2FA.

| Alias | Correo | Rol/uso |
|---|---|---|
| CAT_A | `sgp-test-rev-cat-a@example.invalid` | Catedrático A |
| CAT_B | `sgp-test-rev-cat-b@example.invalid` | Catedrático B, tercero para privacidad |
| EST_A | `sgp-test-rev-est-a@example.invalid` | Autor de curso A |
| EST_B | `sgp-test-rev-est-b@example.invalid` | Autor de curso B y tercero |
| EXT | `sgp-test-rev-externo@example.invalid` | Profesional externo verificado |
| ADMIN | `sgp-test-rev-admin@example.invalid` | Dependencia y tercero para privacidad |

| Sufijo de `SGP-TEST-REV-` en puente/dispositivo | Estado inicial | Uso |
|---|---|---|
| `bandeja` | BORRADOR | Envío real y entrada a bandeja |
| `historial` | ENVIADA | Ciclos 1 y 2 históricos resueltos; ciclo 3 pendiente |
| `anclaje` | BORRADOR | Observación y resolución reales |
| `aprobar`, `cambios`, `rechazar` | BORRADOR | Veredictos independientes |
| `directa-cat`, `directa-ext` | BORRADOR | Publicación por CAT_A / EXT |
| `antigua` | ENVIADA | Pendiente desde hace 16 días al cargar por primera vez |
| `control-b` | ENVIADA | Reciente, autor EST_B, revisor CAT_B |

Fechas, cursos, credenciales y estados se conservan al repetir seed. Si el curso
ha vencido o el control ya tiene 15 días, limpiar y recrear explícitamente.

## C. Ejecución con PostgreSQL local

Desde la raíz; configurar la base de pruebas real (5432 es ejemplo para instalación
nativa, 55432 corresponde al puerto publicado por Compose):

```bash
export PGHOST=127.0.0.1 PGPORT=5432 PGDATABASE=sgp_revision_test PGUSER=sgp_migrator
# Autenticarse mediante .pgpass con permisos 0600 o solicitud interactiva de psql.
psql -X -v ON_ERROR_STOP=1 -c 'SELECT current_database(),current_user,inet_server_addr(),inet_server_port();'
psql -X -v ON_ERROR_STOP=1 -c 'SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
```

Si falta el esquema, iniciar el backend **apuntando a esta base** para que Flyway
aplique las migraciones. Para PostgreSQL nativo crear previamente los roles y
extensiones con ayuda de `infra/postgres/init/00-create-app-user.sh` y
`docs/DEVELOPMENT.md`, adaptando credenciales localmente. No ejecutar un init de
roles sobre una base compartida. Verificar que no falten versiones V1–V25.

```bash
# En otra terminal, después de configurar sgp-api/.env para esta misma base:
cd sgp-api
java -version
mvn -version
mvn spring-boot:run
```

API: `http://localhost:8090`; Swagger: `/swagger-ui/index.html`;
OpenAPI: `/api/docs`. Cliente local: desde `sgp-client`, `npm ci` y `npm start`
(según `docs/DEVELOPMENT.md`), normalmente `http://localhost:4200`.

```bash
# De vuelta en la raíz. fixture_db expresa deliberadamente el destino autorizado.
psql -X -v ON_ERROR_STOP=1 -v fixture_db=sgp_revision_test \
  -v fixture_password_hash="$FIXTURE_HASH" \
  -f tests/fixtures/revision-academica/seed_revision_academica.sql
psql -X -v ON_ERROR_STOP=1 \
  -f tests/fixtures/revision-academica/verificar_revision_academica.sql
```

Ejecutar E. Al finalizar o restablecer, detener la API que utiliza esa base y:

```bash
psql -X -v ON_ERROR_STOP=1 -v fixture_db=sgp_revision_test \
  -v cleanup_fixture=SGP-TEST-REV \
  -f tests/fixtures/revision-academica/cleanup_revision_academica.sql
```

## D. Ejecución con Docker Compose

El archivo raíz es `docker-compose.yml`. Servicio real: **`postgres`**, imagen
`postgis/postgis:16-3.5`, base `sgp_db`, propietario `sgp_migrator`, usuario de API
`sgp_user`. El volumen `postgres_data` persiste en `/var/lib/postgresql/data`;
`minio_data` es independiente. Los valores de base del Compose raíz están
configurados allí; `backend` carga además `sgp-api/.env` y sobrescribe la conexión
para usar `postgres:5432`. Un `.env` de la raíz para interpolación Compose y
`sgp-api/.env` son mecanismos diferentes. No copiar secretos a esta guía.

**Usar estos comandos solo después de identificar esta instancia como pruebas.**
No ejecutar SQL sobre contenedores existentes cuya función se desconozca.

```bash
docker compose config --services
docker compose up -d postgres
docker compose ps postgres
docker compose exec -T postgres pg_isready -U sgp_migrator -d sgp_db
# Con sgp-api/.env configurado:
docker compose up -d --build backend nginx
# minio solo si ARCHIVOS_PROVEEDOR=s3 apunta a ese servicio:
# docker compose up -d minio
docker compose logs --tail=80 backend
curl -fsS http://localhost:8088/healthz
curl -fsS http://localhost:8088/api/docs >/dev/null
```

Nginx publica `http://localhost:8088`; el backend del Compose **no publica 8090**.
Nginx reenvía `/api/` y bloquea `/actuator/` con 403. `/healthz` verifica Nginx;
`/api/docs` verifica conectividad al backend cuando `OPENAPI_PUBLIC=true`.
Revisar también los logs de Flyway y arranque: el health de Nginx solo no demuestra
que la API esté lista.

```bash
docker compose exec -T postgres psql -X -U sgp_migrator -d sgp_db \
  -v ON_ERROR_STOP=1 -c 'SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
```

Los SQL usan `\ir` para compartir el manifiesto. Copiar **el directorio completo**
a una ruta temporal del contenedor (no es un volumen de datos):

```bash
docker compose cp tests/fixtures/revision-academica postgres:/tmp/sgp-test-rev-fixture
docker compose exec -T postgres psql -X -U sgp_migrator -d sgp_db \
  -v ON_ERROR_STOP=1 -v fixture_db=sgp_db -v fixture_password_hash="$FIXTURE_HASH" \
  -f /tmp/sgp-test-rev-fixture/seed_revision_academica.sql
docker compose exec -T postgres psql -X -U sgp_migrator -d sgp_db \
  -v ON_ERROR_STOP=1 -f /tmp/sgp-test-rev-fixture/verificar_revision_academica.sql
```

No repetir `compose cp` sobre un directorio ya existente sin comprobar dónde quedó
el contenido: `-f` debe señalar el archivo copiado y su manifiesto hermano.
Una alternativa más sencilla es **psql desde el anfitrión**:

```bash
export PGHOST=127.0.0.1 PGPORT=55432 PGDATABASE=sgp_db PGUSER=sgp_migrator
psql -X -v ON_ERROR_STOP=1 -v fixture_db=sgp_db \
  -v fixture_password_hash="$FIXTURE_HASH" \
  -f tests/fixtures/revision-academica/seed_revision_academica.sql
```

En el anfitrión se usa TCP y el puerto 55432, con autenticación del servidor;
en `compose exec` se usa `psql` dentro de `postgres`, puerto interno/socket y rutas
del contenedor. `postgres:5432` es DNS interno de Compose, no del anfitrión.
No transmitir solo el seed por stdin: `\ir` necesita el archivo auxiliar.

Ejecutar E usando `http://localhost:8088`. Limpieza:

```bash
docker compose stop backend
docker compose exec -T postgres psql -X -U sgp_migrator -d sgp_db \
  -v ON_ERROR_STOP=1 -v fixture_db=sgp_db -v cleanup_fixture=SGP-TEST-REV \
  -f /tmp/sgp-test-rev-fixture/cleanup_revision_academica.sql
docker compose start backend
```

No usar `docker compose down -v`. Para apagar conservando datos: `docker compose stop`.
Staging y producción tienen Compose propios en `infra/staging` y `infra/production`;
no aplicarles automáticamente comandos de desarrollo. Para repetir en desplegado,
usar la misma revisión del código y los mismos SQL sobre una base de pruebas
identificada, ajustar conexión/URL, conservar los resultados locales y registrar
los desplegados separadamente.

## E. Guía paso a paso por criterio

### Preparación común (dependencias de los cuatro criterios)

Iniciar sesión desde la interfaz en ventanas/perfiles separados; preferir las
pantallas `/revisiones`, `/revisiones/{revisionId}`, `/observaciones` e
`/inspecciones/{id}/borrador`. Registrar URL, revisión Git, entorno y fecha.
Si una pantalla no admite el subconjunto privado, ejecutar el paso mediante los
endpoints siguientes y consignar **interfaz no validada**. No afirmar que la UI
funciona porque la API o SQL respondió correctamente.

Autenticación API real: `POST /api/v1/auth/login`, cuerpo
`{"email":"sgp-test-rev-cat-a@example.invalid","password":"<contraseña elegida>"}`.
Obtener `accessToken` de la respuesta, sin incluirlo en evidencias. Se pueden
obtener tokens desde los perfiles de navegador o mediante Swagger. Ejemplo
interactivo sin escribir contraseña en el historial:

```bash
export API=http://localhost:8090/api/v1 # Compose: http://localhost:8088/api/v1
read -r -p 'Correo del fixture: ' LOGIN_EMAIL
read -r -s -p 'Contraseña del fixture: ' LOGIN_PASSWORD; echo
TOKEN=$(jq -n --arg email "$LOGIN_EMAIL" --arg password "$LOGIN_PASSWORD" \
  '{email:$email,password:$password}' | \
  curl -fsS "$API/auth/login" -H 'Content-Type: application/json' --data-binary @- | \
  jq -er '.accessToken')
unset LOGIN_PASSWORD
# Guardar TOKEN en CAT_A, EST_A, etc., según la cuenta usada:
# CAT_A=$TOKEN
```

Usar variables `CAT_A`, `CAT_B`, `EST_A`, `EST_B`, `EXT`, `ADMIN` con sus tokens;
no imprimirlas. `verificar_revision_academica.sql` devuelve IDs reales. También:

```sql
SELECT p.nombre,i.id,r.id AS revision_id,r.numero_ciclo
FROM inspeccion i JOIN puente p ON p.id=i.puente_id
LEFT JOIN revision r ON r.inspeccion_id=i.id
WHERE i.dispositivo_id LIKE 'SGP-TEST-REV-%' ORDER BY p.nombre,r.numero_ciclo;
```

Para los borradores, subir previamente cuatro imágenes ficticias JPEG/WebP
válidas, una de cada categoría: `ACCESO`, `SUPERESTRUCTURA`, `SUBESTRUCTURA`,
`CAUCE`. Usar la interfaz del borrador. Es una precondición, no una quinta prueba.
API alternativa, **como autor**, con `I` igual al UUID de la inspección y
`AUTOR` al token correspondiente:

```bash
# Repetir para cada categoría con una clave distinta; conservarla al reintentar.
CATEGORIA=ACCESO
# UUID v7 RFC 9562: timestamp de 48 bits, versión 7, variante RFC y bits aleatorios.
FOTO_ID=$(python3 -c 'import uuid,time,secrets; print(uuid.UUID(int=(int(time.time()*1000)<<80)|(7<<76)|(secrets.randbits(12)<<64)|(2<<62)|secrets.randbits(62)))')
CAPTURADA_EN=$(date -u +%Y-%m-%dT%H:%M:%SZ)
CLAVE="SGP-TEST-REV-foto-$I-$CATEGORIA"
curl -sS -i -X POST "$API/inspecciones/$I/fotografias" \
  -H "Authorization: Bearer $AUTOR" -H "Idempotency-Key: $CLAVE" \
  -F "metadatos={\"id\":\"$FOTO_ID\",\"categoria\":\"$CATEGORIA\",\"latitud\":14.481,\"longitud\":-90.615,\"capturadoEn\":\"$CAPTURADA_EN\"};type=application/json" \
  -F 'archivo=@/ruta/a/foto-ficticia.jpg;type=image/jpeg'
```

Conservar también `FOTO_ID`, `CAPTURADA_EN` y el mismo archivo al reintentar;
la clave sola no basta si cambia el contenido. Debe devolver 201 y `GET /inspecciones/{I}/fotografias` mostrar cuatro categorías
`DISPONIBLE`. No insertar filas `archivo` para eludir esta precondición.
Al usar las solicitudes de envío/reenvío de esta guía, **omitir**
`Idempotency-Key`: es opcional y así la limpieza no debe adoptar operaciones
polimórficas adicionales. La subida sí lo exige; la limpieza reconoce únicamente operaciones completadas
de fotos con archivo, inspección y usuario exclusivos y ruta HTTP correspondiente.

Formato para llamadas siguientes: `Authorization: Bearer <token del actor>`;
JSON con `Content-Type: application/json`. `I` identifica inspección, `R` revisión,
`O` observación. Los POST sin cuerpo no requieren JSON. Ejemplo de envío:

```bash
curl -sS -i -X POST "$API/inspecciones/$I/enviar" -H "Authorization: Bearer $EST_A"
curl -sS "$API/revisiones/bandeja?pagina=0&tamanio=100&puente=SGP-TEST-REV" \
  -H "Authorization: Bearer $CAT_A" | jq .
```

### REV-01 — Bandeja e historial completo — 1 punto

**Objetivo:** comprobar bandeja asignada, inicio y conservación de ciclos.
**Datos:** `bandeja` e `historial`, EST_A/CAT_A y control EST_B/CAT_B.
**Precondiciones:** seed y API activos; fotos del borrador `bandeja` cargadas.
No iniciar `antigua` ni `control-b` antes de terminar REV-04.

1. Como EST_A, abrir `bandeja` y **Enviar** (`POST /inspecciones/{I}/enviar`).
   Esperar 200, inspección `ENVIADA` y un ciclo `PENDIENTE` nuevo.
2. Como CAT_A, abrir `/revisiones`. Filtrar por `SGP-TEST-REV-bandeja` y verificar
   su presencia. El control B debe aparecer en CAT_B y no en CAT_A.
   API: `GET /revisiones/bandeja`, parámetros opcionales `estado` (`ENVIADA` o
   `EN_REVISION`), `puente`, `estudiante`, `curso`, `fechaDesde`, `fechaHasta`
   (ISO date), `pagina=0`, `tamanio=20` (máximo 100).
3. Abrir e **Iniciar revisión**. API: `POST /revisiones/{R}/iniciar`, sin cuerpo;
   `GET /revisiones/{R}` devuelve formulario/snapshot. Esperar 200,
   revisión `EN_CURSO`, inspección `EN_REVISION`.
4. Abrir `historial`. Como CAT_A y EST_A, consultar
   `GET /inspecciones/{I}/revisiones`; aparecen ciclos 3, 2, 1. Guardar los JSON de
   ciclos 1 y 2 como evidencia **histórica precargada**, no como transiciones probadas.
5. Como CAT_A iniciar ciclo 3, pedir cambios mediante
   `POST /revisiones/{R}/veredicto` con
   `{"veredicto":"CAMBIOS_SOLICITADOS","calificacion":75,"observacionGeneral":"SGP-TEST-REV-Corregir material"}`.
6. Como EST_A ejecutar `POST /revisiones/{R}/atender-cambios`, corregir material
   por la aplicación, cargar las cuatro fotos si faltan y reenviar:
   `POST /inspecciones/{I}/reenviar`. Para corrección API usar
   `PUT /inspecciones/{I}/datos`, cuerpo `{"datos":<objeto completo corregido>}`;
   conservar las otras propiedades. La nota no se modifica en ese endpoint.
7. Como CAT_A abrir e iniciar ciclo 4. Volver a consultar historial y los detalles
   de los ciclos anteriores. Deben existir cuatro ciclos; los resueltos y sus
   snapshots/veredictos/notas permanecen, con IDs distintos para ciclos nuevos.

Persistencia:

```sql
SELECT r.id,r.numero_ciclo,r.estado,r.veredicto,r.calificacion,
 r.observacion_general,r.hash_contenido,r.contenido_enviado
FROM revision r JOIN inspeccion i ON i.id=r.inspeccion_id
WHERE i.dispositivo_id='SGP-TEST-REV-historial' ORDER BY r.numero_ciclo;
SELECT h.secuencia,h.evento,h.estado_anterior,h.estado_nuevo,h.revision_id
FROM historial_estado_inspeccion h JOIN inspeccion i ON i.id=h.inspeccion_id
WHERE i.dispositivo_id='SGP-TEST-REV-historial' ORDER BY h.secuencia;
```

**Evidencia:** bandejas A/B, formulario abierto, historial antes/después y SQL.
**Cumple:** bandeja e inicio correctos e historial conservado tras el ciclo real.
**No cumple:** falta una inspección correspondiente o se sobrescribe algún ciclo.
**Bloqueado:** faltan conexión, fotos u otra precondición; consignar causa exacta.

### REV-02 — Observaciones ancladas y pendientes — 1 punto

**Objetivo:** crear un pendiente sobre un campo real y resolverlo como autor.
**Datos:** `anclaje`, EST_A y CAT_A. **Precondiciones:** fotos, envío real e inicio
como en REV-01; usar el `revisionId` que produjo ese envío.

1. Como CAT_A abrir el detalle en revisión. Seleccionar el campo **Material** de
   Cortina superior y crear `SGP-TEST-REV-Confirmar material de cortina`.
   API: `POST /revisiones/{R}/observaciones`:
   `{"destino":{"tipo":"ELEMENTO","elementoRef":"/subestructura/estribo_entrada/cortina_superior/material"},"texto":"SGP-TEST-REV-Confirmar material de cortina"}`.
   Esperar 201 y guardar `O`. No enviar UUID de instancia: no es repetible.
2. Consultar `GET /revisiones/{R}/observaciones`. Verificar texto y
   `elementoRef` exacto; el estado es `PENDIENTE`. En UI comprobar que lleva al
   mismo campo, no solo que existe un comentario general.
3. Como CAT_A solicitar cambios con texto general y nota:
   `POST /revisiones/{R}/veredicto`,
   `{"veredicto":"CAMBIOS_SOLICITADOS","calificacion":70,"observacionGeneral":"SGP-TEST-REV-Atender material observado"}`.
4. Como EST_A abrir `/observaciones` y la revisión; API:
   `GET /observaciones/recibidas?pagina=0&tamanio=20`, seguido de
   `GET /revisiones/{R}/observaciones`. Debe mostrarse el pendiente junto al campo.
5. Pulsar **Atender cambios** (`POST /revisiones/{R}/atender-cambios`), esperar
   `BORRADOR`, corregir material conservando el resto del objeto y guardar.
6. Marcar **Resuelta** con respuesta `SGP-TEST-REV-Material contrastado`.
   API: `PUT /revisiones/{R}/observaciones/{O}/resolver`, cuerpo
   `{"respuesta":"SGP-TEST-REV-Material contrastado"}`. Esperar 200.
7. Como ambos usuarios consultar nuevamente observaciones. El texto original,
   ancla, respuesta, autor de resolución y fecha se conservan. Si se reenvía,
   el nuevo ciclo no debe borrar la observación del anterior.

Persistencia:

```sql
SELECT o.id,o.elemento_ref,o.contenido,o.estado,o.respuesta_autor,
 o.resuelto_por_id=i.autor_id AS resuelta_por_autor,o.resuelto_en
FROM observacion_revision o JOIN revision r ON r.id=o.revision_id
JOIN inspeccion i ON i.id=r.inspeccion_id
WHERE i.dispositivo_id='SGP-TEST-REV-anclaje';
```

**Evidencia:** campo seleccionado, pendiente del estudiante y resolución, respuestas
HTTP y SQL sin tokens. **Cumple:** ancla correcta, pendiente visible y resolución
persistida por el autor. **No cumple:** ancla incorrecta o pérdida de observación/
resolución. **Bloqueado:** no fue posible completar las precondiciones del flujo.

### REV-03 — Veredictos, publicación directa y privacidad — 2 puntos

**Objetivo:** evaluar los seis subcasos del criterio. **Datos:** `aprobar`,
`cambios`, `rechazar`, `directa-cat`, `directa-ext`; cuentas indicadas debajo.
**Precondiciones:** fotos reales cargadas por sus autores. En los tres primeros,
EST_A envía y CAT_A inicia. Conservar IDs y estados antes de cada solicitud.

1. **A. Aprobación — CAT_A.** En `aprobar`, emitir
   `POST /revisiones/{R}/veredicto` con
   `{"veredicto":"APROBADA","calificacion":87.65,"observacionGeneral":"SGP-TEST-REV-Aprobada"}`.
   Esperar 200, ciclo `RESUELTA/APROBADA`, inspección `PUBLICADA` y `publicado_en`.
   Ver la inspección publicada en UI. Si faltan fotos o falla la dependencia IC,
   registrar el error y el bloqueo; no publicar mediante SQL.
2. **B. Cambios — CAT_A y EST_A.** En `cambios`, primero enviar
   `{"veredicto":"CAMBIOS_SOLICITADOS","calificacion":60,"observacionGeneral":"   "}`.
   Esperar 422 `observacion_general_obligatoria`, revisión aún `EN_CURSO`.
   No guardar previamente observación general, porque `null` puede reutilizarla.
   Repetir con `"observacionGeneral":"SGP-TEST-REV-Corregir material"`:
   esperar veredicto persistido. **La hoja exige BORRADOR inmediatamente**;
   consultar estado antes de hacer otra acción. El código actual devuelve
   `CAMBIOS_SOLICITADOS`: registrar la diferencia. Después, EST_A ejecuta
   `POST /revisiones/{R}/atender-cambios`, y entonces sí queda `BORRADOR` editable.
3. **C. Rechazo — CAT_A.** En `rechazar`, emitir
   `{"veredicto":"RECHAZADA","calificacion":25,"observacionGeneral":""}`:
   esperar 422 y ningún cambio. Repetir con texto
   `SGP-TEST-REV-Evidencia incompatible`: esperar 200, `RECHAZADA`, ciclo resuelto,
   texto y nota preservados en historial.
4. **D. Revisor distinto del autor — EST_A.** Antes del veredicto de uno de esos
   casos, EST_A intenta `POST /revisiones/{R}/iniciar` y
   `POST /revisiones/{R}/veredicto` sobre su propia inspección. Esperar 403,
   sin cambio de estado, revisor ni nota. CAT_A sí puede revisarla.
   El modelo asigna un rol actual por usuario: no hay una cuenta legítima que sea
   estudiante autor y catedrático simultáneamente. No cambiar roles ni crear un
   ciclo imposible para forzar el caso. La prohibición explícita `revisor_es_autor`
   existe además en `TransicionInspeccionService`; este recorrido prueba la
   prohibición por API mediante roles, sin demostrar por sí solo esa rama interna.
   Si se exige demostrar una cuenta de catedrático revisándose tras un cambio
   histórico de rol, marcar ese subcaso **Bloqueado** por ausencia de un escenario
   natural soportado por estas cuentas, no declararlo probado mediante SQL.
5. **E. Publicación directa — CAT_A y EXT.** Con sus respectivos borradores
   `directa-cat` y `directa-ext`, pulsar **Publicar**; API:
   `POST /inspecciones/{I}/publicar`, sin cuerpo. Esperar 200 y `PUBLICADA` con
   cero filas `revision`. El servicio pasa por `ENVIADA` y `PUBLICADA` en la misma
   transacción; no crea revisión académica. EXT tiene colegiado verificado.
   Para confirmar explícitamente el estado desde la pantalla, usar la acción
   correspondiente. API como autor: `GET /inspecciones/{I}/confirmacion-estado`,
   seguido de `PUT` en la misma ruta con
   `{"estadoConfirmado":"<calculo.estadoCalculado>","hashEntrada":"<calculo.hashEntrada>"}`.
   Es una dependencia de publicación; nunca insertar confirmaciones por SQL. No usar `/veredicto` para este subcaso.
6. **F. Nota privada — CAT_A, EST_A y terceros.** En la revisión aprobada del
   paso A, CAT_A y EST_A consultan `GET /revisiones/{R}/resultado` y
   `GET /inspecciones/{I}/revisiones`: ambos ven **87.65**.
   CAT_B y EST_B hacen las mismas consultas: esperar 404; EXT y ADMIN: 403.
   Comprobar también que la ficha pública, avisos y respuestas públicas no
   incluyen la nota. SQL solo confirma que está almacenada; la privacidad se
   evalúa con solicitudes autenticadas de cada cuenta, sin usar privilegios SQL.

Consultas:

```sql
SELECT p.nombre,i.estado,i.publicado_en,r.id,r.revisor_id,
 r.revisor_id<>i.autor_id AS revisor_distinto,r.veredicto,r.observacion_general,r.calificacion
FROM inspeccion i JOIN puente p ON p.id=i.puente_id
LEFT JOIN revision r ON r.inspeccion_id=i.id
WHERE i.dispositivo_id IN ('SGP-TEST-REV-aprobar','SGP-TEST-REV-cambios',
 'SGP-TEST-REV-rechazar','SGP-TEST-REV-directa-cat','SGP-TEST-REV-directa-ext')
ORDER BY p.nombre,r.numero_ciclo;
SELECT i.dispositivo_id,count(r.id) AS revisiones
FROM inspeccion i LEFT JOIN revision r ON r.inspeccion_id=i.id
WHERE i.dispositivo_id IN ('SGP-TEST-REV-directa-cat','SGP-TEST-REV-directa-ext')
GROUP BY i.dispositivo_id;
```

**Evidencia:** solicitudes inválidas/válidas y estados antes/después, historial,
publicaciones directas sin ciclos, nota visible para los dos autorizados y
respuestas a terceros; ocultar tokens. **Cumple:** todos los subcasos exigidos
satisfacen la hoja. **No cumple:** cualquier incumplimiento observable, incluida
la diferencia de B si se exige retorno inmediato. **Bloqueado:** dependencia
impide ejecutar un subcaso; listar cuáles sí se ejecutaron. No conceder los dos
puntos por aprobación solamente.

### REV-04 — Alerta automática de 15 días — 1 punto

**Objetivo:** generar alerta real al revisor correcto y evitar duplicados.
**Datos:** `antigua` (16 días), revisor CAT_A; `control-b` reciente, CAT_B.
**Precondiciones:** no revisar ni resolver esos casos; backend activo y scheduler
habilitado por `SchedulingConfiguration` con ShedLock. El seed no crea alertas.

1. Consultar `revision.enviada_en` y estado con el SQL siguiente. Esa fecha es la
   usada por el proceso; `fecha_inspeccion` o `enviado_dispositivo_en` no la
   sustituyen. Guardar evidencia antes del primer job.
2. Identificar `AlertaRevisionesSinRevisarService.ejecutar()`, anotaciones
   `@Scheduled` y `@SchedulerLock(name="alerta-revisiones-sin-revisar")`.
   Cron por defecto `0 0 2 * * *`, zona **America/Guatemala** (02:00 diaria).
   No hay disparador manual seguro expuesto por API: observar la ejecución normal.
3. En una instancia/base **aislada**, si se quiere acortar la espera, reiniciar
   solamente esa instancia con la variable existente
   `PROCESOS_CRON_DIARIO='0 */10 * * * *'` en su configuración de pruebas.
   Esto ejecuta **todos** los procesos que comparten el cron cada diez minutos;
   no usarlo en bases compartidas. ShedLock mantiene el bloqueo mínimo cinco
   minutos y máximo treinta. No cambiar el reloj ni borrar filas de `shedlock`.
   En desplegado, observar el horario normal salvo que su entorno aislado permita
   esta configuración. Restaurar la configuración después.
4. Como CAT_A abrir `/avisos`; debe aparecer «Inspección sin revisar» asociada a
   la revisión de `antigua`. API: `GET /notificaciones?pagina=0&tamanio=100`.
   Verificar destinatario y enlace. CAT_B no recibe alerta por `control-b`.
   Otras revisiones del fixture también pueden alcanzar el plazo con los días;
   filtrar por ID en vez de contar todos los avisos de la cuenta.
5. Esperar una segunda ejecución efectiva (normalmente el día siguiente o diez
   minutos en aislamiento). Comprobar el segundo resumen de auditoría y que
   continúa habiendo **una** alerta por usuario/clave del ciclo antiguo y cero del
   control reciente. Si solo transcurrió el cron pero ShedLock impidió ejecutar,
   no considerarlo una segunda comprobación.

```sql
SELECT i.dispositivo_id,r.id,r.enviada_en,r.estado,r.revisor_id,
 CURRENT_TIMESTAMP-r.enviada_en AS espera,
 r.enviada_en<=CURRENT_TIMESTAMP-interval '15 days' AS elegible
FROM revision r JOIN inspeccion i ON i.id=r.inspeccion_id
WHERE i.dispositivo_id IN ('SGP-TEST-REV-antigua','SGP-TEST-REV-control-b');
SELECT i.dispositivo_id,r.revisor_id,n.usuario_id,n.tipo,n.clave_evento,count(*)
FROM notificacion n JOIN revision r ON n.entidad='revision' AND n.entidad_id=r.id
JOIN inspeccion i ON i.id=r.inspeccion_id
WHERE i.dispositivo_id IN ('SGP-TEST-REV-antigua','SGP-TEST-REV-control-b')
 AND n.tipo='REVISION_SIN_REVISAR_15_DIAS'
GROUP BY i.dispositivo_id,r.revisor_id,n.usuario_id,n.tipo,n.clave_evento;
SELECT creado_en,proceso_automatico,valores_posteriores
FROM auditoria WHERE proceso_automatico='alerta-revisiones-sin-revisar'
ORDER BY creado_en DESC LIMIT 5;
SELECT * FROM shedlock WHERE name='alerta-revisiones-sin-revisar';
```

La clave real es `revision-sin-revisar-15-dias:<revisionId>` y el índice único es
`(usuario_id,clave_evento)`. El umbral backend es `<= ahora - interval '15 days'`,
incluye `PENDIENTE` y `EN_CURSO`, y excluye inspecciones eliminadas o fuera de
`ENVIADA/EN_REVISION`. La marca visual de bandeja usa fechas Guatemala y **>15**;
la marca visual sola no demuestra una alerta automática.

**Evidencia:** fechas antes, aviso dentro de la aplicación, destinatario/clave,
dos ejecuciones efectivas y ausencia de duplicados. **Cumple:** alerta automática
correcta, control sin aviso y segunda ejecución sin duplicación. **No cumple:**
no genera tras ejecución efectiva, destinatario equivocado o duplica.
**Bloqueado:** no se pudo observar el job o falta segunda ejecución; no simular
el aviso por SQL.

## F. Matriz de resultados

| ID | Criterio | Puntos | Local | Desplegado | Evidencia | Observaciones |
|---|---|---:|---|---|---|---|
| REV-01 | Bandeja e historial | 1 | Pendiente | Pendiente | | |
| REV-02 | Observaciones y pendientes | 1 | Pendiente | Pendiente | | |
| REV-03 | Veredictos, publicación y privacidad | 2 | Pendiente | Pendiente | | |
| REV-04 | Alerta de 15 días | 1 | Pendiente | Pendiente | | |

Conservar evidencias por entorno, fecha, revisión Git, criterio y subcaso.
No copiar el resultado local a desplegado. Una comprobación del fixture no cambia
esta matriz a Cumple. Registrar causas de bloqueo y discrepancias observadas.

## G. Restablecimiento del escenario

**Repetir seed:** reconoce identidades existentes e inserta únicamente faltantes.
No actualiza datos, fechas, contraseñas, roles, veredictos, notas, observaciones,
notificaciones ni ciclos ya creados por API. Una inspección publicada sigue
publicada. No es un restablecimiento ni una reparación de datos parcialmente
borrados; inconsistencias o colisiones hacen rollback.
El seed bloquea brevemente las tablas de preparación para que la comprobación
de propiedad y la inserción no compitan con otras escrituras; si no obtiene el
bloqueo en cinco segundos, revierte y se debe reintentar en un momento sin actividad.

**Limpiar exclusivamente fixture:** ejecutar el archivo de limpieza, con base
explícita y `cleanup_fixture=SGP-TEST-REV`, después de detener el backend y guardar
evidencias. Comparte el manifiesto de propiedad con el seed. También reconoce
hijos de aplicación únicamente cuando padre y actor son del fixture: revisiones,
observaciones, historial, resultados/confirmaciones IC, archivos vinculados,
hilo automático sin cierre, operaciones completadas de subida de fotos,
notificaciones, auditorías y tokens de esas cuentas.
Antes de borrar inspecciona **todas las FK entrantes**, incluidas compuestas y
`ON DELETE CASCADE`, y relaciones polimórficas de avisos/auditorías/operaciones.
Bloquea tablas durante la comprobación y eliminación; un timeout revierte todo.
No usa cascadas, desactiva restricciones ni toca catálogos compartidos.

Una inscripción ajena, comentario nuevo de foro, OTP, orden de mantenimiento,
invitación, operación idempotente no demostrable o auditoría de un tercero hace
que **aborte sin borrar parcialmente**. No eliminar esa relación para obligar al
script a pasar. Conservar la base y revisar su procedencia con el responsable;
para repetir inmediatamente usar otra base de pruebas aislada y migrada.
La limpieza mantiene resúmenes globales del scheduler y sus bloqueos, que no son
propiedad exclusiva del fixture. SQL elimina metadatos de fotos demostradas, pero
no objetos físicos; no borrar directorios/buckets compartidos. El responsable del
almacenamiento puede retirar objetos identificados en una instancia desechable.

**Recrear desde cero:** después de una limpieza satisfactoria, ejecutar nuevamente
seed y cargar de nuevo las fotos por la aplicación. Se conservan UUID y nombres;
las fechas relativas se calculan en la nueva carga. No se ejecuta limpieza al
arrancar Spring Boot, al migrar ni al repetir seed.

### Validación de estos entregables

Comprobaciones realizadas en un contenedor temporal `postgis/postgis:16-3.5`,
sin red, puertos publicados ni volúmenes existentes. Se ejecutaron los archivos
SQL oficiales V1–V25 en orden, con transacción por archivo. Para comprobar el
guard del fixture se preparó un historial de versiones exclusivo de ese
contenedor; **no se ejecutó el runner de Flyway ni se arrancó Spring Boot**.

| Comprobación | Resultado |
|---|---|
| SQL de las 25 migraciones y primer seed, con restricciones activas | Correcto |
| Segundo seed, comparación de todas las filas exportadas | Sin duplicados ni cambios |
| Repetición tras modificar actividad/versión de una inspección del fixture | Conserva el avance |
| Consultas del archivo verificador | Ejecutadas correctamente |
| Limpieza con observación pendiente y token adicionales exclusivos | Correcta |
| Inscripción ajena vinculada a curso del fixture | Aborta sin cambios |
| Limpieza dos veces, comparación con datos anteriores a la carga | Datos ajenos intactos |
| UUID reservado ocupado por otra identidad | Aborta sin cambios |
| Correo reservado ocupado con otro UUID | Aborta sin cambios |
| Nombre de base distinto del destino indicado | Aborta sin cambios |
| Sintaxis de bloques Bash (`bash -n`) y `git diff --check` | Correcta |

Las comparaciones incluyeron catálogos y un registro centinela ajeno. El contenedor
de validación se eliminó al terminar. No se probaron aquí todos los posibles
hijos posteriores de la aplicación ni concurrencia bajo carga: las FK y los
guardas conservadores siguen siendo la barrera de la limpieza ante esos casos.

Las pruebas funcionales de E, el runner Flyway, la autenticación con la contraseña
elegida, las fotos físicas y las dos ejecuciones reales del scheduler permanecen
pendientes. La matriz F conserva todos los resultados en Pendiente. No se han
ejecutado escrituras en la base local existente ni en un entorno compartido.
