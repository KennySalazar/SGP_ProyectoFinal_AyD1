# HU-062 — Resultados de fase 3

Fecha: 24 de octubre de 2026, America/Guatemala. Alcance: interrupción fotográfica, pérdida de confirmación, integridad/idempotencia, evidencias y documentación. Se mantienen como antecedentes los resultados de [fase 1](FASE_1_HU062_RESULTADOS.md) y [fase 2](FASE_2_HU062_RESULTADOS.md).

**La pérdida literal de paquetes continúa NO VERIFICADA.** Esta entrega no fuerza netem ni modifica permisos o interfaces del anfitrión. No se declara HU-062 completamente aprobada.

## 1. Escenarios implementados

Se reutilizan Playwright, Service Worker de producción, preparación offline por UI con cuatro fotos reales, proxy, PostGIS desechable, Spring Boot y almacenamiento local. No se modifica código de producción ni el sincronizador.

| Caso | Inyección y puntos de comprobación |
|---|---|
| Corte al 50 % binario | El proxy recibe el FormData existente, identifica metadatos y parte `archivo`, transmite el prefijo hasta `floor(bytesFoto/2)` manteniendo Content-Length total y sin terminar el multipart. En una barrera se verifica que TCP_INFO del socket backend recibió el prefijo. Después se destruye upstream, se pasa el navegador a offline, se comprueba PENDIENTE y se reconecta. El segundo intento se retiene para verificar Blob, UUID, clave y metadatos antes de reenviar completo. |
| Confirmación fotográfica perdida | Multipart completo, fotografía persistida por Spring Boot y 201 real. Se retiene su JSON, se verifica persistencia remota única mientras Blob/cola siguen pendientes y se interrumpe la entrega tras el primer byte. El segundo intento conserva identidad/contenido/metadatos y devuelve 201 idéntico, sin crear otro archivo, vínculo o registro idempotente. |

El parser del harness está acotado a las dos partes del FormData actual; no es un nuevo protocolo ni una implementación general de uploads. El corte afecta únicamente al primer intento de la primera foto del UUID seleccionado. No se usa un aborto previo a transmitir como evidencia de subida parcial.

## 2. Archivos modificados en esta fase

| Archivo | Cambio |
|---|---|
| `sgp-client/e2e/hu061/server.mjs` | Perfiles `corte-foto`/`confirmacion-foto`, conteo binario/multipart/wire, identificación/hashes de foto y metadatos; conexión exclusiva para medir TCP y barreras del reintento. |
| `sgp-client/e2e/hu061/hu062-fase3.spec.mjs` | Dos casos, verificación TCP/SQL/API/Dexie, oráculo final compartido, temporales y diagnóstico incluso ante fallos; bandera de aserción deliberada. |
| `sgp-client/e2e/hu061/hu062-helpers.mjs` | Controles de red extraídos sin cambiar su comportamiento para compartirlos entre fases 2/3. |
| `sgp-client/e2e/hu061/hu062-fase2.spec.mjs` | Importa esos controles; conserva escenarios y expectativas. |
| `sgp-client/e2e/hu061/run-hu062-fase1.sh` | Modo `fase3`, fallo controlado/verificador de artefactos, restauración y regresiones; temporales Java privados y limpieza. |
| `sgp-client/playwright.config.mjs` | Descubrimiento optativo de fase 3; conserva selección habitual HU-061 y fases anteriores. |
| `docs/adr/0008-hu062-pruebas-e2e-red-degradada.md` | Elección técnica de Playwright y alcance/limitaciones, **propuesto para ratificación**. |
| `docs/testing/HU-062.md`, `README.md` | Guía única de ejecución en la estructura técnica existente y enlace desde el índice. |
| `docs/testing/HU-061.md` | Aclara modos simulado/real y enlaza la aceptación conectada y preparación reutilizable. |
| `FASE_3_HU062_RESULTADOS.md` | Este informe y matriz actualizada. |

Se conservan los cambios previos de fases 1/2. No se modifican producción, migraciones, CI, dependencias, Dockerfiles ni protocolos de sincronización; no se crean commits.

## 3. Pruebas y resultados

Procedimiento reproducible, desde `sgp-client`, con Node 24, Java 21, Maven, Docker/PostGIS/Chromium y paquetes existentes:

```bash
bash e2e/hu061/run-hu062-fase1.sh fase3
```

En esta máquina se selecciona el binario Node ya disponible mediante `PATH=/tmp/hu062-bin:$PATH`. Se requieren también `ss` y Python 3 estándar, ya presentes; no se instala nada.

El modo `fase3` ejecuta primero un fallo intencional **posterior a la verificación remota correcta**, valida sus artefactos y vuelve a ejecutar con la bandera desactivada. Después corre ambos escenarios nuevos, los tres casos fase 2 una vez, las dos regresiones fase 1 y las cinco HU-061 afectadas. No repite instalación ni carga 3G. Los casos PWA con API simulada son regresiones del harness, no evidencia de persistencia remota.

Ejecución final: **24/10/2026, 20:46–20:49 America/Guatemala** (25/10, 02:46–02:49 UTC). El runner terminó con código 0. Reportes y logs privados conservados en `/tmp/hu062-fase3.6lRqaF`.

| Lote / reporte JSON | Resultado | Duración del lote |
|---|---|---|
| `fallo-controlado.json` | Una aserción deliberada detectada; artefactos y éxito remoto verificados. Se restaura la bandera antes del siguiente lote. | 19,26 s |
| `fase3.json` | **2/2 aprobadas**: corte parcial y pérdida del 201 fotográfico. | 26,28 s |
| `fase2-regresion.json` | **3/3 aprobadas**: latencia 1500 + 1500 ms, fallo previo y respuesta de creación perdida. | 73,72 s |
| `fase1.json` | **2/2 aprobadas**: persistencia offline/recarga y sincronización normal. | 19,88 s |
| `hu061-real.json` | **2/2 aprobadas**: Catedrático y Estudiante contra backend real. | 17,04 s |
| `hu061-pwa.json` | **3/3 aprobadas**: recorridos PWA de ambos roles y actualización SWR, con API simulada. | 13,72 s |

Total funcional: **12/12 aprobadas, cero omitidas, cero flaky y sin reintentos del runner**. El fallo controlado se informa separado: no se cuenta como aprobado ni se oculta dentro del total. La recuperación de operaciones dentro de los escenarios sí usa los reintentos existentes del sincronizador.

Entorno utilizado: Node 24.21.0, Java 21.0.12.1, Maven 3.8.7 y Playwright 1.64.1/Chromium disponible. El runner completó `mvn -o -DskipTests package` y `npm run build`; ese empaquetado no representa ejecución de tests unitarios Java. También pasaron `npm run lint`, `npm run format:check`, comprobación Prettier de los módulos E2E/configuración modificados, sintaxis Node/Bash y `git diff --check`. El descubrimiento habitual sigue listando los siete casos HU-061 originales.

## 4. Integridad, recuperación e idempotencia

Antes de confirmar, se compara cada foto local con su snapshot original: ID, Blob/tamaño/SHA-256, metadatos y clave. La inspección conserva respuestas `PRESENTE/MODERADO/NO` e intención PENDIENTE. El segundo intento debe incrementar el contador a 2 manteniendo la misma operación; no se altera Dexie desde el test.

Para el corte parcial, el test correlaciona `socket.bytesWritten` de una conexión nueva proxy→18090 con `bytes_received` de su peer backend leído mediante `ss -tinH`, usando ambos puertos. El prefijo HTTP incluye metadatos completos y media imagen; se exige recepción de todos esos bytes antes del corte. Las lecturas TCP son de diagnóstico y no cambian interfaces ni capacidades.

Durante esa interrupción se exige SQL sin foto DISPONIBLE/vínculo/reserva completada para el ID objetivo, contenido por API 404 e inventario físico sin objetos nuevos. Tras guardar y perder el 201 se exige exactamente una foto DISPONIBLE, un vínculo y una operación COMPLETADA con respuesta 201, conservando el Blob local hasta recibir confirmación válida. El multipart no tiene por contrato cabecera de replay JSON; su replay se acredita por igualdad de respuesta y persistencia única.

El oráculo compartido exige PUBLICADA/CONFIRMADA, cuatro IDs únicos/categorías correctas, cola de inspección y fotos vacía, incremento de una sola inspección y replay de publicación sin cambiar conteos. Originales y miniaturas deben descargarse, decodificarse y coincidir con archivos reales; original normalizado con tamaño/SHA-256 de PostgreSQL. Inventario final: previo más ocho objetos, sin objetos extra ni `.pendiente`. También se inspeccionan temporales de multipart/fotografía bajo `java.io.tmpdir` exclusivo, sin tocar temporales compartidos.

Mediciones de los dos casos aprobados en la ejecución final:

| Evidencia | Corte parcial | Confirmación perdida |
|---|---|---|
| Inspección | `01a170f3-db91-77fa-8bc6-ff441dc0222f` | `01a170f4-0c4e-711a-b0a3-c1430c8d1c1a` |
| Fotografía objetivo | `01a170f3-e02d-7ea3-b695-20f493a0dcb8` | `01a170f4-10f0-7586-a635-74c63962e90f` |
| Bytes binarios del primer intento | **385 de 771** (49,94 %); multipart enviado **836 de 1268** bytes. | 771 completos. |
| Recepción real del prefijo | **1987 bytes** HTTP escritos en el socket exclusivo y **1987 recibidos** por TCP del backend, incluidos encabezados. | Backend confirma almacenamiento con 201 antes de interrumpir la respuesta. |
| Antes de reenviar el intento 2 | Cuatro Blobs y cinco operaciones locales conservados; foto objetivo con cero vínculos/fotos disponibles/operaciones completadas. | Cuatro Blobs y cinco operaciones locales conservados; foto objetivo con un vínculo/foto disponible/operación completada. |
| Respuestas reales de la foto objetivo | Un 201 en el segundo intento. | Dos 201 con el mismo SHA-256 de respuesta. |
| Resultado remoto final | Un vínculo, una foto DISPONIBLE y una operación COMPLETADA para el objetivo. | Un vínculo, una foto DISPONIBLE y una operación COMPLETADA para el objetivo. |

Ambos casos terminaron con cuatro fotografías de categorías ACCESO/SUPERESTRUCTURA/SUBESTRUCTURA/CAUCE, cuatro originales y cuatro miniaturas íntegros, dos transiciones de historial, un IC y ninguna operación pendiente de la inspección. El conteo acumulado de inspecciones del autor/puente fue 2 y 3 respectivamente, porque el caso controlado previo había creado una; cada caso añadió exactamente una. No se interpreta ese acumulado como duplicación. No quedaron temporales fotográficos ni objetos incompletos accesibles.

## 5. Evidencias y documentación

El fallo controlado se activa solo con `HU062_FALLO_CONTROLADO=true`; ocurre después del éxito remoto y se exige el marcador `HU062_FALLO_CONTROLADO` en el error. El verificador no acepta fallos funcionales distintos ni omitidas como sustituto. Comprueba PNG/dimensiones, ZIP íntegro con eventos `.trace`/`.network`, diagnóstico local/remoto PUBLICADA, persistencia fotográfica única y cola vacía. Después el runner restaura `HU062_FALLO_CONTROLADO=false` y exige los casos normales aprobados.

Los adjuntos `fotografia-y-recuperacion.json` y `diagnostico-fotografico.json` relacionan eventos, pruebas TCP, snapshots, hashes, errores de navegador y estado SQL. Se conservan logs del backend/proxy y reportes JSON. Las trazas son privadas y pueden contener sesión: no se publican ni versionan. Los informes y la guía no incluyen credenciales, cookies o tokens.

`/tmp/hu062-fase3.6lRqaF/verificacion-evidencias.json` acredita captura PNG de **1280 × 720**, `trace.zip` íntegro mediante CRC y presencia de eventos de traza/red, además de diagnóstico local/remoto válido. Captura y traza están bajo `fallo-controlado/hu062-fase3-HU-062-fase-3--6b27e-erdida-después-del-201-real/`. El único error de ese caso contiene el marcador deliberado; posteriormente ambos casos fotográficos pasaron con la bandera desactivada.

La limpieza terminó con código 0: se eliminó el contenedor creado `a7c5565c205278e8ff45859b470054dcb649dd0e903228ba3c80b1ab5de499f0`, se comprobó ausente `hu061-aceptacion-postgis`, sin listeners en 18090/4173 y sin directorios de objetos/temporales de esta ejecución. Los seis contenedores habituales ajenos al fixture siguieron activos. `limpieza.log` conserva el ID y resultado; se mantienen solo evidencias, no DB ni fotografías de prueba. No se alteraron interfaces o configuraciones de red.

El [ADR-0008](docs/adr/0008-hu062-pruebas-e2e-red-degradada.md) registra la reutilización de Playwright y del ADR-0007, el proxy, los perfiles y la necesidad de herramientas externas para descartes reales. Su estado es **propuesto para ratificación**: no se afirma que se encontró o aprobó la Decisión 6. La [guía HU-062](docs/testing/HU-062.md) concentra requisitos/comandos y enlaza los resultados de fases; no se crea otro manual.

## 6. Incidencias

La primera comprobación de artefactos detectó un error del verificador: asumía que el adjunto JSON del reportero siempre tenía `path`; en esta versión podía venir embebido como base64. Se corrigió únicamente esa lectura, manteniendo las exigencias de PNG/traza/estado remoto. La corrida llegó al fallo deliberado esperado y su entorno fue eliminado. No constituye un defecto de producción.

Una ejecución posterior pasó ambos casos fotográficos y validó sus artefactos, pero la primera regresión fase 2 obtuvo un 429 en `/auth/refresh`, dejando la ficha pública sin botón de inspección. La traza conserva el 200 del login y el 429 del refresh. `AuthRateLimitFilter` mantiene 20 peticiones por IP/ruta en 60 segundos: las suites rápidas acumulaban ese presupuesto. Se aislaron los lotes reiniciando solo el Java desechable antes de fases 2 y 1, conservando DB/objetos. No se corrigió ni desactivó un control de producción ni se cambió la expectativa del test.

Ambos intentos previos terminaron limpiando sus recursos y no forman parte del total final aprobado. No se confirmó un defecto funcional de producción en los escenarios solicitados. Tras la ejecución completa se ajustaron solo la acumulación de logs entre reinicios del mismo TTL y la exigencia de exactamente un error deliberado: se volvió a ejecutar el verificador final contra los artefactos reales y se comprobó sintaxis; no se repitió innecesariamente la suite funcional.

## 7. Matriz actualizada y bloqueos

La numeración y alcance corresponden a CA1–CA11 de la matriz del [plan](PLAN_IMPLEMENTACION_HU062.md#5-matriz-de-criterios-de-aceptación), basada en la solicitud de análisis porque la historia original no estaba disponible. Los resultados históricos de fases 1/2 siguen identificados en sus informes.

| Criterio | Estado actualizado | Evidencia / pendiente |
|---|---|---|
| CA1 — Red normal | **Cubierto** | Fase 1 y regresión aprobada: respuestas y cuatro fotos originalmente pendientes; publicación única y cola vacía. |
| CA2 — Sin conexión | **Cubierto** | Fase 1 y regresión aprobada: creación, Blobs, claves y respuestas conservados tras recarga mediante Service Worker; sin escrituras remotas. |
| CA3 — Latencia alta | **Cubierto** | Fase 2 y regresión aprobada del perfil 1500 + 1500 ms, conservación intermedia y resultado real. |
| CA4 — Pérdida/fallos | **Parcial** | Fallo previo, respuesta de creación perdida y recuperación aprobados. **Pérdida literal de paquetes NO VERIFICADA**, sin contadores de descartes; bloquea cierre. |
| CA5 — Corte fotográfico | **Cubierto** | Dos casos nuevos aprobados: recepción TCP de imagen parcial y pérdida de 201 tras persistencia, recuperación sin duplicados. |
| CA6 — Conservación de pendientes | **Cubierto para perfiles ejecutados; parcial global** | Blobs, hashes, respuestas, IDs, metadatos y claves estables antes del acuse, incluyendo filas FOTO. Falta comprobarlo bajo pérdida literal de paquetes. |
| CA7 — Integridad del servidor | **Cubierto para perfiles ejecutados; parcial global** | SQL/API/disco, cuatro originales/miniaturas, tamaños/checksums, categorías, publicación única y ausencia de parciales. Falta perfil de paquetes. |
| CA8 — Reproducibilidad | **Parcial** | Perfiles por UUID/ordinal, barreras, medidas TCP y preparación/limpieza automática comprobadas; perfil de paquetes sin ejecutar. Backoff/jitter original conservado. |
| CA9 — Evidencias | **Cubierto** | Fallo deliberado verificado con captura, traza íntegra, logs y diagnóstico local/remoto; restauración y 12 pruebas funcionales aprobadas. |
| CA10 — Herramienta/ejecución | **Parcial** | Playwright reutilizado, ADR-0008 y comandos manuales ejecutados. **Decisión 6 no localizada y ratificación formal del ADR pendiente**; no se afirma su aprobación. |
| CA11 — Documentación | **Cubierto** | Guía HU-062 enlazada en README, referencia puntual HU-061, ADR e informes con resultados/limitaciones, sin duplicar manuales. |

Permanecen fuera de esta ejecución el desbloqueo de netem, otros navegadores/proveedores, staging y la medición manual de autonomía de ocho horas. La fuente original HU-062 y Decisión 6 no fueron localizadas anteriormente; la ratificación formal de la herramienta queda pendiente. La pérdida literal de paquetes seguirá siendo un bloqueo de cierre, aunque los escenarios fotográficos pasen.
