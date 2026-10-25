# HU-062 — Resultados de fase 1

Fecha: 24 de octubre de 2026, America/Guatemala. Alcance: conexión normal y funcionamiento/persistencia offline. Base del repositorio: `eb13093`; el plan ya estaba presente como archivo sin seguimiento al comenzar.

## Funcionalidades implementadas

Se reutilizan Playwright, configuración PWA, servidor/proxy y fixture SQL de HU-061. Los casos nuevos usan Angular de producción, Service Worker e IndexedDB reales, Spring Boot completo, PostGIS desechable y el proveedor de archivos local existente. No se simulan respuestas de negocio.

- **Offline:** login y preparación del formulario con conexión; crear inspección por interfaz sin red, completar respuestas, adjuntar cuatro fotos por galería y confirmar publicación pendiente. Recargar la PWA con el navegador aún offline; comprobar origen Service Worker, conservación del borrador, respuestas, intención, cola, metadatos, claves y hashes/tamaños de los Blob. Comparar registro del proxy y conteos SQL para descartar escrituras remotas.
- **Red normal:** preparar los mismos pendientes offline y reconectar; comprobar creación, respuestas, cuatro fotografías e intención final CONFIRMADA/PUBLICADA. Verificar también operaciones FOTO al exigir cola vacía, listado real, descarga/decodificación de originales y miniaturas, archivos físicos y checksum del JPEG normalizado. Contar inspecciones del autor/puente, vínculos, historial e IC; repetir publicación con su clave para descartar duplicación.

Los helpers de login/SQL/lectura local se extraen del test real HU-061. Sus expectativas de aceptación se mantienen. La lectura local incluye fotos/operaciones fotográficas, además de la cola de inspección.

## Archivos

| Archivo | Cambio |
|---|---|
| `sgp-client/e2e/hu061/hu062-fase1.spec.mjs` | Dos escenarios reales; lecturas de diagnóstico, oráculos y adjuntos JSON. Captura de fallo y contextos gestionados por Playwright. |
| `sgp-client/e2e/hu061/real-helpers.mjs` | Extracción reutilizable de login, consulta SQL e instantánea local de HU-061; ampliación para fotos, hashes y cola FOTO; espera de precarga del worker. |
| `sgp-client/e2e/hu061/hu061-real.spec.mjs` | Importa esos helpers; mantiene casos, datos y expectativas originales. |
| `sgp-client/e2e/hu061/server.mjs` | Registro de método/ruta/clave de escrituras de inspecciones en modo real; lectura por `/__e2e/real-writes`, solo en el servidor de pruebas. No altera ni simula las respuestas Spring. |
| `sgp-client/e2e/hu061/foto-exif.jpg` | Fixture JPEG de 873 bytes derivada exactamente de los bytes existentes en `metadatos-exif.integration.spec.ts`; GPS/fecha válidos para galería. |
| `sgp-client/e2e/hu061/run-hu062-fase1.sh` | Preparación, selección de pruebas, evidencias y limpieza de recursos desechables. |
| `sgp-client/playwright.config.mjs` | Descubrimiento de HU-062 con `HU062_FASE1=true`, manteniendo la selección habitual de HU-061 cuando esa variable no está activa. |
| `FASE_1_HU062_RESULTADOS.md` | Este informe y procedimiento reproducible. |

No se modificaron producción, migraciones, mecanismos offline/idempotentes, Dockerfiles/Compose, dependencias ni CI. El plan preexistente se conserva sin editar. No se crearon commits.

## Ejecución reproducible

Requisitos: Node 24 en PATH, Java 21, Maven, Docker accesible, imagen `postgis/postgis:16-3.5`, dependencias bloqueadas y Chromium compatible ya disponibles. El script no instala paquetes ni navegadores y Maven trabaja con `-o`.

Desde `sgp-client`:

```bash
bash e2e/hu061/run-hu062-fase1.sh todo
```

`fase1` selecciona solo los casos nuevos; `regresion` selecciona los dos recorridos reales HU-061 y los tres casos pertinentes de PWA (dos roles y SWR/actualización). `todo` ejecuta ambos conjuntos. El script compila el cliente y empaqueta el backend actual antes de preparar los recursos. Activa `HU062_FASE1=true` para descubrir los casos nuevos; dentro de ellos, origen real/etiqueta/base/directorio son requisitos obligatorios: no hay `skip` por falta de backend. La selección normal `npm run test:pwa` conserva los siete casos anteriores de HU-061.

Se reutilizan los nombres exigidos por el fixture y el test real: contenedor `hu061-aceptacion-postgis`, base `hu061_aceptacion`, origen `127.0.0.1:18090` y proxy `127.0.0.1:4173`. Si el contenedor o los puertos ya están ocupados, el script se detiene sin reemplazarlos. La base está en tmpfs, sin volúmenes habituales. El script guarda el ID exacto del contenedor recién creado y comprueba su etiqueta `hu062.aislado=true` antes de eliminarlo.

El backend arranca con `spring.config.import=` y variables ficticias exclusivas de prueba, sin cargar `.env`; proveedor local con directorio mktemp. El TTL es de 15 minutos para HU-062; para la regresión de expiración HU-061 se reinicia únicamente ese proceso Java con TTL de 5 segundos, manteniendo sus expectativas. No se manipulan sesiones de producción.

El script muestra la carpeta de evidencias creada en `/tmp/hu062-fase1.XXXXXX`: logs de build/empaquetado/backend, health, carga del fixture, reportes JSON, adjuntos y trazas/capturas de fallo. En `EXIT/INT/TERM` detiene su proceso Java, elimina su contenedor y descarta el directorio fotográfico exclusivo; conserva los registros. La presencia del administrador se comprueba después de health y antes del fixture, porque el listener de bootstrap puede terminar después de que health responda.

## Pruebas y resultados

**Fase 1 aprobada en los dos perfiles solicitados.** Ejecución final del **24 de octubre, aproximadamente 20:02–20:03, America/Guatemala**. Herramientas: Node **24.21.0**, Java **21.0.12.1**, Maven **3.8.7**, Playwright **1.64.1**, Chromium ya disponible revisión **1248**. No se instalaron paquetes.

Comando efectivo de esta máquina, desde `sgp-client`:

```bash
PATH=/tmp/hu062-bin:$PATH bash e2e/hu061/run-hu062-fase1.sh todo
```

`/tmp/hu062-bin/node` apunta al binario reutilizado; no es un requisito de ruta para otras máquinas. Basta seleccionar Node 24 en PATH.

| Grupo / escenario | Resultado | Duración |
|---|---|---:|
| HU-062: creación/persistencia offline y recarga Service Worker | PASA | 8,952 s |
| HU-062: sincronización normal completa y replay | PASA | 9,604 s |
| **Total nuevos** | **2/2; 0 fallos, 0 omitidas, 0 flaky** | **19,619 s** |
| HU-061 real: Catedrático, logout offline, login y publicación | PASA | 7,435 s |
| HU-061 real: Estudiante, expiración JWT y envío posterior | PASA | 8,684 s |
| **Total regresión real** | **2/2; 0 fallos, 0 omitidas, 0 flaky** | **16,981 s** |
| HU-061 PWA: reapertura/sesión Catedrático | PASA | 4,954 s |
| HU-061 PWA: reapertura/sesión Estudiante | PASA | 5,327 s |
| HU-061 PWA: SWR y aviso de actualización | PASA | 2,528 s |
| **Total regresión PWA con API simulada explícita** | **3/3; 0 fallos, 0 omitidas, 0 flaky** | **13,634 s** |

La ejecución final suma **7/7** escenarios, sin repetir automáticamente pruebas fallidas. Las tres pruebas PWA simuladas son regresión del servidor/worker; no sustituyen a las cuatro pruebas conectadas a Spring Boot. No se repitieron auditoría completa, instalación PWA ni carga 3G.

Además pasaron: empaquetado backend `mvn -o -DskipTests package` (no es suite backend), build Angular, `npm run lint`, `npm run format:check`, Prettier de los `.mjs`/configuración editados, `node --check`, `bash -n` y `git diff --check`. El build conserva advertencias existentes sobre dependencias CommonJS; no se modificaron para ocultarlas.

### Resultados observados en datos y archivos

| Verificación | Resultado real |
|---|---|
| Inspección creada y mantenida offline | `01a170cb-d78c-7923-a4c1-8c31b728c870`, BORRADOR e intención PENDIENTE, cuatro fotos con Blob y claves; **11 operaciones locales**. |
| Recarga sin red | Documento servido por Service Worker (`fromServiceWorker()=true`), indicador offline y snapshot íntegro idéntico antes/después, incluidos hashes de fotos y claves. |
| Escrituras durante desconexión | **0** nuevas escrituras de inspección recibidas por el proxy; SQL sin nuevas inspecciones del autor/puente y sin vínculos fotográficos del UUID offline. |
| Inspección sincronizada | `01a170cb-f60e-7470-afef-df2db9d4655d`, **PUBLICADA**, intención **CONFIRMADA**, respuestas `PRESENTE/MODERADO/NO` conservadas localmente y en PostgreSQL. |
| Procesamiento de pendientes | 11 operaciones: 1 creación, 5 guardados de respuestas, 4 fotos y 1 publicación. Cada clave se observó exactamente una vez en el perfil normal, en orden creación/datos/fotos/final. Los guardados sucesivos proceden de las ediciones/autoguardado existentes, no son duplicaciones de inspección. |
| Cola y Blob después de sincronizar | Cola de la inspección **y sus cuatro IDs fotográficos = 0**; fotos pendientes **0**; inventario confirmado con los cuatro IDs esperados. Liberación de Blob tras éxito, conforme al contrato existente. |
| Integridad | Cuatro fotos DISPONIBLES, una por categoría mínima, originales/miniaturas JPEG descargables y decodificables. Bytes descargados iguales a los archivos físicos; tamaño y SHA-256 original coinciden con los metadatos persistidos. Directorio con exactamente **8 objetos**, sin temporales ni archivos extra. |
| Replay de publicación | 200, `Idempotent-Replay: true`, cuerpo idéntico; conteos antes → después: inspecciones **1 → 1**, fotos **4 → 4**, historial **2 → 2**, IC de publicación **1 → 1**. |

Se reutiliza deliberadamente la misma imagen de fixture en cuatro categorías: la ausencia de duplicados se comprueba por identidades/operaciones y número de vínculos, no por exigir contenido visual diferente.

**Evidencias de la ejecución final:** `/tmp/hu062-fase1.OqpPzG/`, con `fase1.json`, `hu061-real.json`, `hu061-pwa.json`, adjuntos `offline.json`/`sincronizacion-real.json` y logs. Son temporales locales; el informe conserva los resultados y el script permite regenerarlos, sin depender de que esa carpeta sobreviva.

**Limpieza verificada:** `limpieza.log` termina con código **0**; se eliminó el contenedor creado `9a423b6e2dd2a8b36d736da244f6ea22cd9367aeb3fb8fdc97903637883728ec`, su tmpfs y el directorio fotográfico. Una comprobación independiente confirmó ausencia de `hu061-aceptacion-postgis` y del backend en 18090. Los cuatro contenedores habituales SGP permanecieron activos y saludables donde corresponde. No se intervino su base ni se ejecutó una limpieza sobre Compose.

## Problemas encontrados

Durante la preparación se corrigieron errores del harness nuevo: ruta relativa inicial del script, ubicación de la opción Playwright de capturas y espera del bootstrap antes de cargar el fixture SQL. Se ajustaron los selectores a las rutas DOM sin `/` inicial y al nombre accesible de las opciones fotográficas (`ACCESO`, etc.). Las ejecuciones fallidas limpiaron sus recursos. Ninguno de estos errores implica un defecto de producción. No se modificaron expectativas de pruebas HU-061 para hacerlas pasar.

Una iteración con timeout generó capturas/contexto y una traza ZIP válida del primer caso; el cierre del segundo emitió `file data stream has unexpected number of bytes` y no produjo una traza legible. No se desactivaron trazas ni se cambiaron dependencias para ocultarlo. La ejecución final no presentó ese error; la validación completa de evidencias ante fallos (CA9) sigue pendiente para una fase posterior. **No se confirmó ningún defecto funcional nuevo de producción.**

El Node predeterminado de la máquina era 18.20.8. Se reutilizó Node 24.21.0 de la imagen `node:24-slim` ya disponible, extrayendo su binario a `/tmp`; el contenedor temporal de extracción fue eliminado. No se instalaron dependencias ni se modificaron manifiesto/lockfile.

## Cobertura, riesgos y siguiente fase

| Criterio HU-062 | Estado tras esta fase |
|---|---|
| CA1: sincronización con red normal | **Cubierto** por creación/respuestas/cuatro fotos/publicación, cola completa vacía, verificación remota y ausencia de duplicados. |
| CA2: funcionamiento y persistencia sin conexión | **Cubierto** por creación por UI, conservación íntegra, cero escrituras y recarga real por worker. |
| CA6: conservación de pendientes | **Cubierto para normal/offline**; falta extenderlo a los perfiles degradados posteriores. |
| CA7: resultados verificables en servidor | **Cubierto para la línea base normal** con API/SQL/disco; falta verificación durante cortes/fallos. |
| CA8/CA9/CA10/CA11 | Aporte parcial: comandos y entorno repetibles, registro/evidencias, Playwright existente y este informe. No se declara cierre integral de HU-062. |
| CA3/CA4/CA5 | **Pendientes**, fuera de la implementación actual: latencia, pérdida y corte fotográfico. |

La cobertura de agotamiento de reintentos y conservación antes de una confirmación retrasada corresponde a fases posteriores. No se ejecutó la prueba manual de ocho horas.

La documentación original HU-062 y «Decisión 6» siguen sin estar disponibles, conforme al plan; la instrucción actual autoriza explícitamente reutilizar Playwright. Se mantiene el alcance del ADR-0007, sin crear una decisión adicional para esta fase de pruebas.

Para fase 2, reutilizar estos escenarios y oráculos y añadir únicamente los perfiles autorizados entonces. El contador del proxy añadido aquí solo observa escrituras; no introduce demoras, pérdidas ni cortes. La verificación actual se limita al proveedor local, no acredita S3/MinIO ni staging. Los logs/trazas de sesión son artefactos privados y no se versionan.
