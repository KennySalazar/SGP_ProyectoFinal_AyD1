# HU-062 — Resultados de fase 2

Fecha: 24 de octubre de 2026, America/Guatemala. Referencia Git al revisar los cambios: `9715518`. Alcance exclusivo: latencia y recuperación ante fallos de comunicación, evaluación de pérdida real de paquetes y regresiones pertinentes. No se implementan cortes de subida fotográfica.

**Resultado:** latencia y fallos de comunicación aprobados en dos corridas, **6/6 ejecuciones nuevas y 7/7 regresiones**. Pérdida real de paquetes **bloqueada/no verificada**; no se incluye en el total de aprobadas ni se declara cerrada HU-062 completa.

## 1. Escenarios y reutilización

Se amplía el servidor/proxy existente `e2e/hu061/server.mjs`. Se reutilizan Playwright, build PWA/Service Worker, creación por interfaz sin red, cuatro fotos de galería con EXIF, fixture SQL, Spring Boot completo, PostGIS tmpfs y almacenamiento local de fase 1. Las respuestas válidas de negocio y la verificación final proceden de Spring Boot real.

Se extraen únicamente la preparación y el oráculo final de fase 1 a `hu062-helpers.mjs`, para compartirlos con los casos nuevos. No se duplican escenarios de fase 1 ni se cambian sus expectativas: creación/respuestas/fotos/publicación, ausencia de duplicados, replay e integridad en API/disco siguen siendo obligatorios. El inventario físico final exige los archivos que ya existían al comenzar el caso más sus ocho objetos nuevos, para poder ejecutar varios casos publicados en la misma base desechable sin asumir directorio vacío.

| Perfil | Simulación exacta y alcance | Verificación intermedia |
|---|---|---|
| Latencia | **1500 ms antes de reenviar + 1500 ms antes de entregar respuesta**, para POST/PUT de la inspección seleccionada y sus fotos. Auth, health, lecturas, referencias y shell quedan fuera. | Primera foto retenida antes de reenviarla y después de recibir su 201 real. En ambos puntos persisten Blob/hash/metadatos/clave y operación local. SQL debe mostrar 0 fotos antes del envío y 1 después del commit, todavía sin confirmación local. |
| Fallo antes del backend | Primeros **dos intentos de creación** no se reenvían. El proxy inicia una respuesta JSON deliberadamente incompleta y cierra el transporte: no devuelve un JSON válido ni un error HTTP simulado como prueba de éxito. Tercer intento permitido. | Tres barreras antes de procesar; fila local y claves conservadas, contador de intentos del sincronizador esperado 1/2/3, cuatro fotos intactas y SQL con 0 inspecciones hasta permitir el tercer intento. |
| Respuesta perdida | Primera creación llega completa al backend, cuyo 201 se recibe y se confirma por SQL. Se interrumpe la entrega de su JSON real después del primer byte; la aplicación no recibe un cuerpo completo. Segundo intento permitido con misma clave/cuerpo. | Una inspección remota y creación todavía pendiente localmente; luego contador de intento 2. Replay real 201 con `Idempotent-Replay`, mismo hash de respuesta y ninguna inspección adicional. |
| Pérdida de paquetes IP/TCP | **No ejecutada: bloqueada en este entorno**. No se configura netem ni se sustituye por los fallos anteriores. | No hay contadores de descartes que acrediten este perfil; no se declara cumplido. |

Las barreras son puntos explícitos de diagnóstico con liberación por el test y límite de 15 segundos; su tiempo adicional se distingue del retardo fijo de red. Se liberan/cancelan al restaurar perfil normal. No se utiliza `waitForTimeout()` para decidir el fin de sincronización: se esperan barreras, respuesta final, estado Dexie y resultados reales. El presupuesto del caso es de 180 segundos; la respuesta final tiene límite explícito de 120 segundos para admitir operaciones secuenciales con retardo.

Los fallos se aplican a JSON de creación, **no a multipart ni a bytes de subida fotográfica**. Las fotos sí se sincronizan completas y con latencia en el perfil correspondiente. No se cambian reintentos, jitter, idempotencia o persistencia de producción.

## 2. Archivos modificados

| Archivo | Cambio de esta fase |
|---|---|
| `sgp-client/e2e/hu061/server.mjs` | Perfiles por UUID/método/clave, barreras, retardo fijo y fallos de transporte; registro de ordinal, tiempos, hashes de petición/respuesta y replay. Controles `__e2e/red` habilitados solo con backend real y `HU062_FASE2=true`. |
| `sgp-client/e2e/hu061/hu062-fase2.spec.mjs` | Tres casos con conservación intermedia, reintentos y oráculo real compartido; restauración del perfil en `finally`. |
| `sgp-client/e2e/hu061/hu062-helpers.mjs` | Preparación/oráculo extraídos de fase 1 y reutilizados. |
| `sgp-client/e2e/hu061/hu062-fase1.spec.mjs` | Usa esos helpers; conserva sus dos escenarios y comprobaciones del perfil normal/offline. |
| `sgp-client/e2e/hu061/run-hu062-fase1.sh` | Modo `fase2`: dos corridas independientes de navegador/proxy, después regresiones; mismo entorno efímero y limpieza por ID. Diagnóstico netem de solo lectura/creación fallida de namespace, sin modificar interfaces. |
| `sgp-client/playwright.config.mjs` | Descubrimiento explícito de fase 2 con variable; conserva selección habitual y activación de fase 1. |
| `FASE_2_HU062_RESULTADOS.md` | Este informe. |

No se modifican producción, Dexie, sincronizador, migraciones, dependencias, CI, Dockerfiles ni arquitectura. No se crean commits.

## 3. Ejecución y resultados

Desde `sgp-client`, con Node 24, Java 21, Maven, dependencias/navegador e imagen PostGIS ya disponibles:

```bash
bash e2e/hu061/run-hu062-fase1.sh fase2
```

En esta máquina se seleccionó Node 24 con `PATH=/tmp/hu062-bin:$PATH`, reutilizando el binario ya disponible desde fase 1. El script no instala paquetes/herramientas y Maven usa `-o`. Conserva los modos previos `fase1`, `regresion` y `todo` con su selección anterior.

El modo nuevo ejecuta tres casos dos veces (sin retries del runner), con contextos/proxy nuevos y UUID únicos; la base sigue siendo exclusiva y desechable. Después ejecuta los dos casos fase 1, los dos reales HU-061 y los tres PWA pertinentes. Estos últimos son regresión con API simulada explícita; no se usan como evidencia del servidor real de fase 2.

Ejecución final del **24 de octubre, 20:22–20:25, America/Guatemala**; Node **24.21.0**, Java **21.0.12.1**, Maven **3.8.7**, Playwright **1.64.1** y Chromium existente. Comando efectivo:

```bash
PATH=/tmp/hu062-bin:$PATH bash e2e/hu061/run-hu062-fase1.sh fase2
```

| Caso | Corrida 1 | Corrida 2 |
|---|---:|---:|
| Latencia 1500 + 1500 ms | PASA, 45,940 s | PASA, 43,129 s |
| Dos fallos antes del backend | PASA, 14,874 s | PASA, 14,701 s |
| Pérdida de confirmación tras commit | PASA, 11,552 s | PASA, 10,837 s |
| **Total fase 2 ejecutable** | **3/3, 73,472 s** | **3/3, 69,645 s** |

| Regresión | Resultado | Duración total |
|---|---|---:|
| Fase 1: offline/recarga y sincronización normal | **2/2 PASA** | 18,487 s |
| HU-061 real: Catedrático/logout/publicación y Estudiante/expiración/envío | **2/2 PASA** | 18,822 s |
| HU-061 PWA: reapertura de ambos roles y SWR/actualización | **3/3 PASA** | 13,123 s |

**Total final: 13/13 ejecuciones automatizadas aprobadas, 0 fallos, 0 omitidas, 0 flaky y sin retries del runner.** Netem no se ejecutó y se informa separadamente como bloqueo. No se repitieron instalación PWA, carga 3G ni auditorías completas.

También pasaron empaquetado `mvn -o -DskipTests package`, build Angular, lint y formato existentes, Prettier de los `.mjs` editados, comprobación sintáctica Node/Bash y `git diff --check`. El empaquetado no se presenta como una ejecución de suites backend. Se verificó por descubrimiento que la invocación habitual de HU-061 conserva sus siete casos.

Después de esa ejecución se hizo únicamente visible por consola, mediante `tee`, el aviso de netem que ya se guardaba en `netem-preflight.log`; se verificó su sintaxis sin repetir pruebas funcionales por ese cambio de salida.

## 4. Duplicados, conservación y evidencia

Cada caso nuevo exige los oráculos de fase 1: una inspección adicional del autor/puente frente al conteo inicial, cuatro IDs fotográficos únicos y categorías mínimas, datos `PRESENTE/MODERADO/NO`, estado PUBLICADA e intención CONFIRMADA, cola de inspección/fotos vacía, originales/miniaturas descargables y decodificables, bytes iguales a los archivos reales y SHA-256/tamaño del original normalizado iguales a PostgreSQL. La publicación repetida no cambia inspección, cuatro vínculos, dos transiciones de historial ni un IC de publicación.

Se añaden snapshots de Dexie durante cada barrera y `red-y-recuperacion.json`, que relaciona UUID, perfil, ordinal, clave, huella de petición, respuesta/replay, fallos observados por el navegador y resultado final. `red-al-final.json` permite diagnosticar un fallo antes de la restauración. Se mantienen trazas/capturas existentes y logs de backend. Ningún resultado simulado del proxy acredita el estado remoto: las consultas SQL/API y la lectura de archivos lo comprueban por separado.

Resultados concretos, consultados también por API y disco:

| Corrida / perfil | UUID de inspección | Conteo acumulado del autor/puente | Fotos / cola final |
|---|---|---:|---|
| 1 / latencia | `01a170dd-ddcd-76cc-b178-d74302ed8cd5` | 1 | 4 / 0 |
| 1 / antes del backend | `01a170de-8fda-7a4f-bbef-57310a0b129d` | 2 | 4 / 0 |
| 1 / respuesta perdida | `01a170de-c7f0-78ce-9fd0-09c8b890970b` | 3 | 4 / 0 |
| 2 / latencia | `01a170de-fb49-747a-9d29-cfb32cebac21` | 4 | 4 / 0 |
| 2 / antes del backend | `01a170df-a3cc-7d0f-94a1-abe2ef6c2ace` | 5 | 4 / 0 |
| 2 / respuesta perdida | `01a170df-dccf-76ba-b6b6-c5fd5dff195a` | 6 | 4 / 0 |

El conteo es acumulado porque las corridas comparten únicamente su base desechable; **cada caso incrementa exactamente una inspección**, no genera otras identidades duplicadas. Cada una queda PUBLICADA, con intención CONFIRMADA, cuatro fotos DISPONIBLES completas, dos entradas esperadas de historial y un IC de publicación. El replay manual de publicación deja esos conteos idénticos.

En latencia se observaron **cuatro Blob pendientes y cinco operaciones** (cuatro fotos y acción final) tanto antes de reenviar la primera foto como después de su commit, con hash/metadatos/claves conservados. La primera corrida registró retardo de ida observado de aproximadamente 1499,75–2367,39 ms y vuelta 1500,35–1845,81 ms: incluye resolución del temporizador y, para esa foto, tiempo de la barrera de diagnóstico. Los valores configurados son 1500 ms por sentido; estas mediciones no son un SLA de rendimiento.

En los dos fallos previos se conservaron **las 11 filas de cola y las cuatro fotos** en los puntos de control; contador del sincronizador **1 → 2 → 3**, SQL **0** inspecciones hasta liberar el tercer intento y **dos errores de transporte `net::ERR_ABORTED`** observados por el navegador. Las tres peticiones de creación conservaron clave y hash de cuerpo; solo una se reenviaba y confirmaba en Spring Boot.

Con respuesta perdida, SQL ya mostraba **1 inspección**, mientras la cola local mantenía creación, respuestas y fotos. El contador subió a **2**, con **un error de transporte**; la segunda creación conservó clave/cuerpo y produjo replay real **201**, cabecera de replay y hash de respuesta idéntico. No se duplicó inspección ni fotografía. En cada perfil solo la operación de creación fallada se repite; los demás elementos de cola se entregan una vez.

**Evidencias finales:** `/tmp/hu062-fase2.oIiw9i/`: `fase2-1.json`, `fase2-2.json`, `fase1.json`, `hu061-real.json`, `hu061-pwa.json`, adjuntos, logs y `netem-preflight.log`. La corrida de preparación fallida se conserva en `/tmp/hu062-fase2.YgJkmk/`; sus tres capturas/contextos y tres trazas ZIP se inspeccionaron, y las trazas pasaron comprobación de integridad. Son artefactos privados temporales; el script permite regenerar los resultados sin depender de esas rutas.

**Limpieza confirmada:** salida 0, contenedor propio `9d11c314818b85188987bfefb4a7234e8cbd8226d171af3e84bb1b12e9af3dfe` eliminado y directorio fotográfico descartado, con logs conservados. Una comprobación independiente confirmó ausencia del contenedor nominal y puertos de prueba 18090/4173 libres. Los perfiles se restauran a normal y sus barreras quedan vacías; después el proxy se detiene. No se ejecutó ninguna escritura `tc`; consulta final de qdisc sin netem. Los contenedores habituales SGP y los otros proyectos observados continúan activos; no se limpiaron sus datos ni se alteraron sus interfaces.

## 5. Evaluación netem y bloqueo

Comprobaciones efectuadas sin cambiar interfaces:

- `tc` disponible, **iproute2-6.1.0**; `nsenter` **2.39.3**, Docker accesible.
- Usuario de ejecución **UID 1001**, sin capacidades efectivas (`capsh`: `Current: =`); no dispone de CAP_NET_ADMIN/CAP_SYS_ADMIN efectivas en el anfitrión.
- `sudo -n -l`: **a password is required**. No se solicita ni se introduce una contraseña.
- `unshare --net true`: **Operation not permitted**; no se pudo crear ese espacio de red efímero.
- Imagen backend existente inspeccionada con un contenedor `--rm --network none --read-only`, sin arrancar la aplicación: tiene `nsenter`, **no tiene `tc`**. El contenedor de inspección terminó y se eliminó.
- El entorno reutilizado de fase 1 ejecuta Java en loopback del host; aplicar allí una qdisc afectaría el espacio de red anfitrión y queda excluido por la solicitud.

Con esas condiciones no se dispone del camino propuesto de `nsenter` + `tc` hacia un backend con espacio de red propio. No se construye una imagen con herramientas ni se conceden capacidades nuevas para forzarlo. Se deja **pérdida real NO VERIFICADA**, sin test omitido contado como aprobado ni sustitución por HTTP, latencia o interrupciones de JSON. Tampoco se afirma que se comprobó soporte de `sch_netem` o restauración de una qdisc: no se llegó a configurar ninguna.

Para cerrar ese punto hará falta un entorno de pruebas previamente habilitado con backend en namespace desechable, herramientas ya instaladas y permisos limitados a ese namespace. Antes de ejecutar habrá que verificar identidad/aislamiento, filtro exclusivo de respuestas API, limpieza automática y contadores reales antes/durante/después del descarte. No forma parte de esta ejecución instalar o habilitar esos requisitos.

## 6. Incidencias y límites

La primera corrida descubrió dos errores del harness: la espera final heredaba los 15 segundos de acciones (la secuencia degradada tarda más) y un cierre sin cabeceras podía provocar una repetición HTTP inmediata con el contador Dexie aún en 1. Se mantuvo la exigencia de reintento del sincronizador: se corrigió la espera del test y se hizo el fallo de transporte observable mediante cuerpo incompleto. No se modificaron expectativas de idempotencia, conservación o resultado remoto para ocultar un defecto.

Una revisión automática de permisos expiró antes de iniciar un intento del script; la repetición autorizada pudo arrancar. No se interpretó ese timeout como un riesgo funcional ni se modificaron permisos del sistema.

**No se confirmó un defecto nuevo de producción** en los perfiles ejecutados. La incidencia de la primera corrida y el bloqueo netem se conservan explícitos; no se desactivó el worker, no se alteraron mecanismos de reintento ni se omitieron casos requeridos para ocultar fallos.

## 7. Cobertura y pendientes de fase 3

| Criterio | Estado tras esta fase |
|---|---|
| CA3, latencia elevada | **Cubierto** en el perfil 1500 + 1500 ms con cuatro fotos/respuestas pendientes reales, conservación intermedia y resultado remoto íntegro. |
| CA4, fallos de comunicación y recuperación | **Cubiertos los dos fallos de transporte** con reintentos reales e idempotencia. **Parcial para CA4 completo**: pérdida literal de paquetes bloqueada, sin descartes certificados. |
| CA6/CA7, conservación y resultado del servidor | **Cubiertos para los perfiles ejecutados**, incluyendo Blob antes del acuse fotográfico, integridad y conteos sin duplicaciones. Pendientes durante cortes de subida. |
| CA8, reproducibilidad | Perfiles/ordinales fijos y dos corridas con navegador/proxy nuevos y UUID únicos. Jitter de producción conservado; no se prometen duraciones idénticas. |
| CA9, evidencias | Eventos/snapshots/adjuntos y diagnóstico de fallos con capturas/trazas válidas. No se incorpora observabilidad adicional. |
| CA1/CA2 y regresión HU-061 | Se mantienen aprobados en las siete regresiones pertinentes ejecutadas. |
| CA5 | **Pendiente**, fuera del alcance actual. |

Este informe no certifica otros proveedores, staging, todas las redes móviles ni autonomía de ocho horas.

Fase 3 queda pendiente: interrupción durante multipart fotográfico y recuperación segura de esa operación, sin protocolos nuevos. Conservar estos helpers, proxy y verificaciones; no se ha implementado ningún corte de subida. Mantener visible el bloqueo netem hasta disponer de un entorno apto y verificar sus descartes y restauración.
