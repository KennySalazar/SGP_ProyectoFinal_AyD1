# Auditoría de aceptación de HU-061

Actualización de cierre: **24 de octubre de 2026**, exclusivamente CA4 y CA7 contra Spring Boot real. Referencia del código: `35b5aca`, más los ajustes del fixture de aceptación descritos abajo. Se conserva la evidencia anterior de los otros diez criterios. No se hicieron commits.

## 1. Resumen ejecutivo

**HU-061 puede aprobarse con la evidencia de aceptación documentada.** CA4 y CA7 pasaron contra Spring Boot real, con autenticación JWT/cookie, almacenamiento de fotografías y PostGIS desechable: **2/2 pruebas, 22,2 s, sin omitidas ni fallos**. No se modificaron funcionalidades de producción ni se confirmaron defectos nuevos.

La matriz queda en **12 CUMPLE, 0 PARCIAL, 0 NO CUMPLE y 0 NO VERIFICADO**. Únicamente se actualizan CA4 y CA7; los otros diez criterios mantienen su evidencia previa y no se repitieron sus pruebas. La publicación del Catedrático y el envío del Estudiante conservan sus claves y no duplican inspección, historial, fotografías ni IC durante el replay.

Bloqueo original del entorno habitual, diagnosticado sin modificarlo:

- `http://127.0.0.1:8088/healthz`: HTTP 200.
- `http://127.0.0.1:8088/api/v1/auth/me`: **HTTP 502**.
- `http://127.0.0.1:8090/actuator/health`: conexión rechazada, código HTTP 000 de curl.

La causa confirmada es el arranque fallido del contenedor backend por `FlywayValidateException: Detected applied migration not resolved locally: 15`. Nginx resuelve el upstream `backend:8090`, pero recibe conexión rechazada porque el backend reinicia. La base habitual está healthy. **El bloqueo de verificación se resolvió en un entorno aislado** con el jar actual, las 22 migraciones existentes y PostGIS en tmpfs; no se ejecutó repair, se deshabilitó validación ni se tocó la base habitual. Los detalles y resultados reales están en el cierre CA4/CA7 de la sección 3.

En la verificación PWA anterior se habilitó Playwright con la versión ya declarada, se ejecutaron primero las **dos pruebas PWA originales: 2/2 aprobadas**, y luego la suite ampliada mínima: **5/5 aprobadas en 28,3 s**. Las tres cargas frías 3G tardaron **2,8052 s; 2,6169 s; 2,8197 s**, todas inferiores a cinco segundos bajo el perfil documentado.

La preparación del cierre reutiliza `sgp-client/e2e/hu061/server.mjs` en modo proxy, `hu061-real.spec.mjs` y `real-fixture.sql`. Los últimos ajustes se limitan al contrato de datos de prueba: UUID v7 existente, fecha Guatemala, WebP del backend y esquema coherente con sus respuestas. No se modificaron producción, dependencias declaradas, lockfile, arquitectura ni migraciones; solo se cargaron datos en la base desechable.

## 2. Matriz de criterios de aceptación

Una fila por escenario original de [`HUS/HU-061.md`](../HUS/HU-061.md). P0/P1/P2 identifican ejecuciones descritas en la sección 3. F/B/D1 son la evidencia previa, conservada e identificada expresamente como histórica. PARCIAL señala qué parte está demostrada y cuál continúa pendiente; no implica un defecto confirmado.

| Criterio original | Estado actual | Implementación localizada | Evidencia y observaciones |
|---|---|---|---|
| **CA1. Instalable y con arranque sin red** | **CUMPLE** | `sgp-client/public/manifest.webmanifest`, iconos; `src/app/app.config.ts`, `provideServiceWorker()`; `ngsw-config.json`. | P2 instala realmente mediante `PWA.install` en Chromium completo, sin errores de instalabilidad; registra la opción de apertura standalone, cierra el navegador, reabre el mismo perfil y lanza la aplicación instalada con la red deshabilitada mediante `PWA.launch`. Comprueba shell visible, modo standalone, control del worker y «Sin conexion». La aplicación se desinstala al finalizar el test. No se simula standalone mediante CSS ni emulación del navegador. |
| **CA2. Shell precargado, referencias SWR e inspecciones fuera del worker** | **CUMPLE** | `sgp-client/ngsw-config.json`; `src/app/offline/services/referencias-locales.service.ts`, `misPuentesAsignados()`. | P0/P1 comprueban SWR de departamentos: entrega el valor cacheado y después el actualizado. P1 obtiene formulario activo y municipios desde caché al desconectar. Antes de cortar la red verifica que los **128 recursos prefetch** del manifiesto generado existen en CacheStorage, incluidos JS de rutas lazy, traducciones, CSS e iconos. El test existente conserva la comprobación de ausencia de inspecciones en las cachés del worker. F ya verifica SWR privado por propietario para puentes asignados; no se repitió. |
| **CA3. Obligatorios/fotografías faltantes impiden encolar e informan al usuario** | **CUMPLE** | `src/app/features/inspecciones/pages/completar-inspeccion`, `abrirConfirmacion()`/`confirmarAccion()`; formulario dinámico, `revisar()`; `offline/services/inspecciones-locales.service.ts`, `encolarAccionFinal()`. Rutas bajo `sgp-client/`. | Se conserva la evidencia F/B de revisión inválida sin encolado, categorías confirmadas y pendientes, permisos, condiciones/grupos/arreglos y validación real 422 del backend. P1 no sustituye esos tests: su formulario es deliberadamente mínimo. |
| **CA4. Enviar/publicar offline con clave, pendiente y ejecución al reconectar** | **CUMPLE** | `offline/services/inspecciones-locales.service.ts`, `encolarAccionFinal()`; `sincronizacion.service.ts`, `iniciar()`/`ejecutarCola()`/`confirmarFinal()`; página completar inspección. | **R (sección 3): navegador → proxy → Spring Boot completo → PostGIS y archivos reales**, sin mocks de API/servicios. Catedrático PUBLICADA y Estudiante ENVIADA tras confirmar offline y reconectar. BORRADOR/PENDIENTE antes de la respuesta; después CONFIRMADA y cola vacía, con respuestas y cuatro fotografías conservadas. Clave final idéntica a la guardada offline. Replay 200, mismo cuerpo e `Idempotent-Replay: true`; conteos remotos sin cambios. |
| **CA5. Rechazo posterior con motivo y edición sin pérdida** | **CUMPLE** | `sgp-client/src/app/offline/services/sincronizacion.service.ts`, `procesar()`; servicio local, `exigirEditable()`; página completar inspección y bloque HTML de intención rechazada. | **D1 corregido**, con 203/203 regresiones aprobadas en la intervención anterior: BORRADOR propio confirmado más publicación 404 permite RECHAZADA, conserva respuestas/fotografías y motivo, libera edición y admite otra confirmación con nueva clave. Conserva 422, resultados inciertos, operaciones en proceso y publicaciones confirmadas. La prueba de componente comprueba el motivo en un elemento `role=alert`. No se repitieron esas 203 pruebas en esta continuación. |
| **CA6. IC disponible al sincronizar; fórmula solo en backend** | **CUMPLE** | Componente `confirmacion-estado-ic`, `calculoVisible`/`consultar()`/`guardar()`; página completar, `icVisible`; `sgp-client/public/i18n/es.json`; backend `CalculoIndiceCondicionService`/`PublicacionIndiceCondicionService`. | F/D1 comprueban ocultación del valor anterior y ausencia de HTTP al consultar/guardar offline; B ejecuta cálculo/publicación reales y rollback por confirmación desactualizada. P0/P1 observan «IC disponible al sincronizar» en la página offline. No se introdujo una fórmula Angular. |
| **CA7. Logout o expiración conservan pendientes y sincronizan tras login** | **CUMPLE** | `core/services/auth.store.ts`, `clear()`/`setUser()`; `session-bootstrap.service.ts`, `initialize()`; shell, `logout()`; sincronizador, eventos de sesión. Rutas bajo `sgp-client/src/app/`. | **R (sección 3): sesión Spring Boot real.** Catedrático: logout offline elimina perfil visible y conserva íntegra la cola; login real por formulario y publicación posterior. Estudiante: JWT real vence offline (TTL de prueba 5 s), `/auth/me` con ese JWT devuelve 401; al reconectar, refresh mediante cookie HttpOnly recupera sesión y ejecuta el envío pendiente. En ambos se conservan datos, cuatro fotos y claves. |
| **CA8. Otro usuario no ve ni sincroniza pendientes ajenos** | **CUMPLE** | Servicios locales de inspecciones, fotos, referencias y preparación; filtros de propietario del sincronizador. | Se conserva F: AuthStore y Dexie reales con IndexedDB en memoria comprueban listado, ID directo, página offline, referencias/esquemas, fotos y no enviar/eliminar cola ajena. También pasa la respuesta tardía de asignaciones de A bajo B. No se reabrió una auditoría general de seguridad. |
| **CA9. index.html sin caché y aviso de versión** | **CUMPLE** | `sgp-client/nginx.conf`, `location = /index.html`; `core/services/pwa-update.service.ts`, `VERSION_READY`; shell y `ngsw-config.json`. | P0/P1 observan el aviso tras cambiar la versión del manifiesto del worker y comprueban «Más tarde». El test verifica `no-store`. Adicionalmente, HEAD al **Nginx local real** `/index.html` devuelve 200 y `Cache-Control: no-store, no-cache, must-revalidate`. Se mantiene la copia versionada del worker, necesaria para CA1, conforme al ADR-0007. |
| **CA10. Inicial inferior a 500 KB comprimidos** | **CUMPLE** | Build de producción en `sgp-client/dist/sgp-client/browser`. | Se conserva y contrasta la medición de los 15 archivos iniciales actuales: **443.314 bytes sin comprimir; 138.481 bytes gzip nivel 6**. Con index comprimido: 139.475 bytes. Es medición de archivos emitidos, no del presupuesto Angular. P2 añade transferencia HTTP instrumentada de la página inicial; véase sección 7. |
| **CA11. Carga inicial menor de 5 s en 3G simulada** | **CUMPLE** | Build de producción; test 3G en `sgp-client/e2e/hu061/hu061.spec.mjs`; servidor existente con gzip nivel 6. | P2 mide tres contextos fríos independientes, sin caché HTTP/worker previa, red de **1,6 Mbps/750 kbps, latencia 150 ms y CPU 4×**. Interfaz lista en 2,8052 / 2,6169 / 2,8197 s; máximo **2,8197 s**. El cumplimiento queda acotado a este perfil explícito y a la página inicial de login; no es una medición de latencia/carga del backend real. |
| **CA12. Hay pruebas de worker, validación, cola, rechazo y arranque offline** | **CUMPLE** | Pruebas `e2e/hu061`, integración local `hu061-fase0.integration.spec.ts`, completar inspección/formulario y suites backend citadas debajo. | Todos los tipos de prueba requeridos están localizados. Actualmente P0 pasa 2/2 y P1/P2 pasan 5/5; D1 pasa 203/203. Se mantiene la distinción entre navegador real con API simulada e integración backend real. |

## 3. Pruebas ejecutadas y evidencia

### Preparación del entorno

Se respetaron `AGENTS.md`, `README.md`, `docs/ARCHITECTURE.md`, la historia y el informe anterior. Node utilizado: **24.19.0**. `@playwright/test`, `playwright` y `playwright-core`: **1.64.1**, exactamente las versiones del lockfile existente.

La instalación offline no pudo resolverse desde la caché y la red del sandbox devolvió `EAI_AGAIN`. Con ejecución autorizada fuera del sandbox se descargaron **solo esos tres paquetes** a `/tmp/hu061-playwright` y se copiaron al `node_modules` ignorado del cliente, habilitando sus ejecutores. `package.json` y `package-lock.json` permanecen sin cambios.

```bash
# Instalación aislada de lo ya declarado; sin scripts de instalación:
npm install --prefix /tmp/hu061-playwright --cache /tmp/hu061-npm-cache \
  --ignore-scripts --no-audit --no-fund --package-lock=false @playwright/test@1.64.1
# Tras habilitar esos paquetes en sgp-client/node_modules:
npx playwright install chromium --only-shell
npx playwright install chromium
```

Se descargaron los navegadores compatibles con esa versión: **Chrome/Chromium 156.0.8078.4, revisión 1248**, headless shell para la suite y Chromium completo para instalación. Se utilizaron perfiles desechables con datos ficticios. No se instalaron frameworks adicionales ni se cambió la configuración de producción.

### P0. Pruebas PWA originales

Desde `sgp-client`, con Node 24 en PATH:

```bash
npm run test:pwa
```

**2/2 aprobadas en 8,9 s**, antes de ampliar los escenarios. Verifican publicación durable después de abrir otra página offline, reconexión, SWR y aviso de actualización. El intento anterior a instalar Chromium falló porque no existía el ejecutable; no fue un fallo de la aplicación.

### P1/P2. Suite final: cinco escenarios, sin duplicar los originales

Se parametrizó el recorrido original para Catedrático/Estudiante y se añadió recuperación de sesión. Se completaron las referencias públicas del test de caché y se agregaron únicamente instalación real y medición 3G, que no existían. El servidor de prueba incorpora sesión expirada/login, envío y gzip para poder comprobar esos contratos y medir transferencia.

```bash
PLAYWRIGHT_JSON_OUTPUT_FILE=/tmp/hu061-aceptacion-final/resultados-pwa.json \
  npm run test:pwa -- --reporter=list,json
```

| Escenario final | Resultado | Duración |
|---|---|---:|
| Catedrático: precarga, publicación pendiente, reapertura offline, logout, login y sincronización | PASA | 7,724 s |
| Estudiante: precarga, envío pendiente, reapertura offline, expiración, login y sincronización | PASA | 4,569 s |
| SWR de referencias, recursos disponibles offline y aviso/posposición de versión | PASA | 2,521 s |
| Instalación real, cierre de navegador y lanzamiento offline desde perfil persistente | PASA | 4,023 s |
| Tres cargas iniciales frías bajo simulación 3G, cada una inferior a cinco segundos | PASA | 8,487 s |
| **Suite completa** | **5/5 aprobadas, 0 omitidas** | **28,3 s** |

La duración de cada test incluye preparación; **no es el tiempo de carga 3G**, medido por separado en la sección 7.

Los escenarios de sesión verifican que el servidor simulado no recibió una acción final mientras no había sesión válida, y que la clave recibida tras login ya existía en la cola anterior al cierre/expiración. Tras confirmar ENVIADA/PUBLICADA, la cola queda vacía.

La instalación utiliza `Page.getInstallabilityErrors` (lista vacía), `PWA.install`, `PWA.getOsAppState` y la opción de usuario `PWA.changeAppUserSettings(displayMode: standalone)`, equivalente a abrir como ventana. Después cierra el proceso, reabre el perfil, deshabilita red y usa `PWA.launch`; no conserva únicamente una página en memoria. Comprueba `display-mode: standalone`, control del worker y shell offline. `PWA.uninstall` limpia la instalación al terminar. Se conserva una captura en `sgp-client/test-results/hu061-instala-la-PWA-y-la--0142c-desde-un-perfil-persistente/pwa-instalada-offline.png`.

**Condición de prueba corregida:** `navigator.serviceWorker.ready` y tener controller no demuestran que Angular haya terminado su precarga. Antes de desconectar, el test ahora comprueba en CacheStorage los 128 recursos declarados prefetch. Esto verifica la precondición de instalación/carga inicial completa de la historia. Los ajustes de selectores y preparación de fixtures durante el desarrollo de estos tests no se clasificaron como defectos de producción.

### Verificaciones del entorno real, sin cambios de datos

```bash
curl -s -o /dev/null --connect-timeout 2 --max-time 3 \
  -w '%{http_code}' http://127.0.0.1:8088/healthz
# 200
curl -s -o /dev/null --connect-timeout 2 --max-time 3 \
  -w '%{http_code}' http://127.0.0.1:8088/api/v1/auth/me
# 502
curl -s -o /dev/null --connect-timeout 2 --max-time 3 \
  -w '%{http_code}' http://127.0.0.1:8090/actuator/health
# 000; conexión rechazada, exit 7
curl -sI http://127.0.0.1:8088/index.html
# 200; Cache-Control: no-store, no-cache, must-revalidate
```

En aquella ejecución no se pudo completar el recorrido conectado. Este bloqueo quedó superado para la aceptación mediante la ejecución aislada R descrita a continuación; el entorno habitual no se modificó.

### Evidencia anterior conservada, no repetida

- **F:** selección inicial de frontend, 324/324 aprobadas; complemento de 110 con el fallo histórico D1. Incluye motor/formulario, fotos, Dexie, preparación y aislamiento.
- **B:** 45/45 backend con Java 21 y PostgreSQL/PostGIS efímero: `Hu061Fase0IntegrationTest` (8), `IdempotenciaHttpFilterTest` (10), `PublicacionRealIcIntegrationTest` (11), `InspeccionControllerSecurityTest` (6) y `TransicionInspeccionControllerSecurityTest` (10). Rutas bajo `sgp-api/src/test/java/gt/usac/cunoc/sgp/`.
- El replay de publicación HTTP real conserva cuerpo/status, IC, historial y un solo evento. E10 no se reproduce: se mantiene la coordinación `SolicitudIdempotente` entre filtro y controlador.
- **D1:** intervención posterior con 203/203 pruebas, lint, formato y build correctos. Comprueba la corrección, 422, incertidumbre, publicación confirmada, claves y motivo visible. El fallo histórico de 110 pruebas ya no describe el estado actual.

Estos números no se suman como pruebas distintas: hay solapamiento. No se repitió la auditoría de código, las suites ajenas ni el backend ya validado.

### Comprobaciones de esta continuación

`node --check` de ambos archivos E2E, Prettier de esos archivos y `git diff --check`: correctos. No hubo cambios de producción que requirieran otra compilación: la suite usa el build de producción posterior a D1, previamente verificado.

Registros de esta sesión: `/tmp/hu061-aceptacion-final/`, especialmente `pwa-existentes-habilitado.log`, `pwa-final.log`, `resultados-pwa.json`, `instalacion.json` y `medicion-3g.json`. Playwright conserva trazas de fallos de diagnóstico en `sgp-client/test-results/`; son artefactos temporales, no evidencia de staging.

### R. Cierre exclusivo de CA4/CA7 contra Spring Boot real

**Diagnóstico y aislamiento.** El contenedor habitual `sgp_proyectofinal_ayd1-backend-1` estaba reiniciando (215 reinicios observados). Su log informa que V15 está aplicada en la base pero no se resuelve en el artefacto desplegado. El código actual sí incluye `sgp-api/src/main/resources/db/migration/V15__seccion_por_inscripcion.sql`. El proxy efectivo es `http://backend:8090/api/` y el log Nginx registra `connect() failed (111: Connection refused)`. Se identifica un artefacto backend desalineado con la base, no un defecto del flujo offline ni un fallo de la regla de proxy.

No se recrearon los contenedores habituales, no se cambió la base ni se ejecutó `flyway repair`. La resolución necesaria para probar fue usar el código actual en un entorno independiente:

- Java **21.0.12.1**, Maven **3.8.7**; `mvn -o -DskipTests package`: **BUILD SUCCESS**. Es empaquetado, no una ejecución adicional de suites ya verificadas.
- PostGIS `postgis/postgis:16-3.5`, contenedor `hu061-aceptacion-postgis`, base **`hu061_aceptacion`**, datos en **tmpfs**, sin volumen persistente. Puerto loopback asignado: 32768. Se confirmó migración máxima **22** en esa base.
- Jar actual en puerto **18090**, arrancado desde `/tmp/hu061-real`, con propiedades exclusivamente de prueba y `spring.config.import=` para no cargar `.env`. Se usan almacenamiento local `/tmp/hu061-real/archivos`, SMTP ficticio y siembra automática de formulario deshabilitada. Salud real: `{"status":"UP"}`.
- `server.mjs`, con `HU061_API_ORIGIN=http://127.0.0.1:18090`, reenvía **todas** las rutas `/api/` antes de cualquier handler simulado. El build Angular y su worker son los reales. El test no intercepta ni fabrica respuestas de API.
- `real-fixture.sql` reutiliza el esquema/IC mínimo de `PublicacionRealIcIntegrationTest`, con usuarios Catedrático/Estudiante, curso, inscripción y puente asignado. Su guardia rechaza cualquier base distinta de `hu061_aceptacion`. Las inspecciones se crean y completan por la API real; las cuatro fotografías por inspección se suben como multipart real reutilizando `sgp-api/src/test/resources/archivo/imagen.webp`.

**Comando ejecutado**, desde `sgp-client`, con Node 24 en PATH y el entorno aislado activo:

```bash
HU061_API_ORIGIN=http://127.0.0.1:18090 \
PLAYWRIGHT_JSON_OUTPUT_FILE=/tmp/hu061-real/resultados.json \
npm run test:pwa -- --grep 'CA4/CA7 backend real' --reporter=list,json
```

Se ejecutaron exclusivamente estos dos escenarios, no los cinco de PWA ni las 203 regresiones anteriores:

| Escenario real | Resultado | Duración | Comprobación de sesión |
|---|---|---:|---|
| `CA4/CA7 backend real: CATEDRATICO` | **PASA** | 10,190 s | Login y cookie HttpOnly reales; confirmación offline; logout sin red borra perfil visible y conserva la cola completa; nuevo login real y publicación confirmada |
| `CA4/CA7 backend real: ESTUDIANTE` | **PASA** | 10,471 s | JWT real de 5 s vence estando offline; GET real `/auth/me` con el JWT vencido responde 401; refresh por cookie HttpOnly recupera la sesión y confirma el envío pendiente |
| **Total** | **2/2, 0 fallos, 0 omitidas, 0 flaky** | **22,238 s** | No se redujeron controles del backend ni se simularon JWT/respuestas |

El TTL de cinco segundos se configuró **solo** en este entorno para provocar expiración real. El Catedrático comprueba el login explícito posterior al cierre; el Estudiante comprueba renovación automática de sesión tras expirar el access token, sin cerrar una sesión de refresh todavía válida.

**Resultados remotos y ausencia de duplicados**, consultados directamente en la base desechable. Cada celda de conteos es **antes del replay → después del replay**:

| Operación | Inspección | Estado final | Inspecciones | Historial | IC PUBLICACION | Fotografías | Reservas con la clave final |
|---|---|---|---:|---:|---:|---:|---:|
| Publicar | `01a17076-ff6e-7aca-a57f-7f54337b714e` | PUBLICADA | 1 → 1 | 2 → 2 | 1 → 1 | 4 → 4 | 1 → 1 |
| Enviar | `01a17077-2516-7627-8326-db99f77e681e` | ENVIADA | 1 → 1 | 1 → 1 | 0 → 0 | 4 → 4 | 1 → 1 |

El IC persistido para la publicación es **50,00000**. Las dos transiciones de publicación son las esperadas del flujo existente (ENVIADA y PUBLICADA), no duplicados. El envío no genera un IC definitivo de publicación.

Se comprobó que la petición Angular final utiliza la clave persistida offline: publicación `01a17077-092b-7ea3-a928-557139f8008d`; envío `01a17077-2b4d-7596-94a3-2dcccb3c27b1`. Al repetir cada POST con su misma clave, el backend devuelve **200**, `Idempotent-Replay: true` y **exactamente el cuerpo de la confirmación original**. Los conteos de la tabla permanecen idénticos.

Antes de la confirmación remota, la inspección local y remota siguen BORRADOR y la intención local PENDIENTE. Después, la intención es CONFIRMADA, la cola de la inspección está vacía, las cuatro fotografías siguen en su inventario y las respuestas `vigas.existencia`, `vigas.dano` y `vigas.cortado` se conservan exactamente. La prueba exige esos resultados; no se relajaron sus expectativas.

Durante la preparación se corrigieron datos del fixture que el contrato real rechazó: fecha UTC que ya correspondía al día siguiente en Guatemala, UUID v4 fotográfico, imagen PNG no aceptada y un campo no declarado en el esquema mínimo. Se reutilizó `uuidV7()` del proyecto, una fecha `America/Guatemala`, la imagen WebP existente y se declaró el campo del propio fixture. **No se clasifican esos errores de preparación como defectos de producción.** La primera instrumentación también renovaba una cookie en paralelo con Angular; se retiró esa renovación artificial y se utiliza la sesión real del navegador.

Evidencia: `/tmp/hu061-real/resultados.json`, `evidencia-catedratico.json`, `evidencia-estudiante.json`, `playwright.log`, `backend-reanudado.log` y registros de empaquetado. Son artefactos de esta ejecución, con datos ficticios. Los tests y el fixture quedan en `sgp-client/e2e/hu061/hu061-real.spec.mjs` y `real-fixture.sql` para reproducirlos en otro entorno aislado.

**Limpieza final:** se detuvo el proceso Java de prueba y se eliminó únicamente el contenedor etiquetado `hu061.aislado=true`; sus datos tmpfs fueron descartados. Se conservaron los registros. Los servicios y bases habituales permanecieron intactos; su imagen desalineada no se reparó sobre datos persistentes.

## 4. Problemas confirmados

**No se confirmaron defectos nuevos de producción.**

**D1 — corregido:** la reconciliación de un rechazo 409 dejaba intención PENDIENTE y edición bloqueada aun con BORRADOR propio confirmado. La corrección reutiliza la transacción existente de rechazo cuando además se confirma ausencia de publicación mediante 404: registra RECHAZADA/motivo y retira solo la acción final. Las regresiones posteriores comprueban conservación y nueva confirmación. No se reabre D1 por la falta de un entorno conectado.

El 502 habitual quedó diagnosticado como fallo de arranque del artefacto backend por validación Flyway de V15. Se resolvió el bloqueo de aceptación mediante un entorno aislado actualizado, sin alterar datos persistentes. No es un defecto funcional confirmado de HU-061.

## 5. Riesgos y bloqueos pendientes

**CA4 y CA7 ya no tienen una verificación obligatoria pendiente.** Ambos recorridos conectados y sus replays pasaron en el entorno aislado R. No se detectaron defectos nuevos que requieran correcciones de producción.

El entorno habitual continúa con su artefacto backend incompatible con la migración V15 aplicada. No se lo reparó porque esta tarea prohíbe modificar bases de desarrollo/producción. Esto es una limitación operativa del entorno habitual, no un bloqueo restante de la aceptación ejecutada con el código actual y PostGIS desechable.

Se mantienen los límites previamente documentados de los otros diez criterios: instalación Chromium en Linux y perfil 3G explícito, sin ampliar el alcance a otros navegadores, ocho horas de autonomía o sincronización con la aplicación cerrada.

## 6. Regresiones detectadas

**Ninguna regresión nueva confirmada en los escenarios ejecutados.** P0 pasa en su forma original y la suite ampliada pasa 5/5. Los controles anteriores de D1/E12/E13 permanecen como evidencia aceptada; no se modificaron para relajar expectativas ni se repitieron sin necesidad.

No se extrapola este resultado a funcionalidades ajenas a HU-061.

## 7. Rendimiento y PWA

### Condiciones y resultados 3G

- Chromium headless **156.0.8078.4**, Linux x86_64; viewport 1365 × 768.
- Perfil explícito: descarga **200.000 bytes/s (1,6 Mbps)**; subida **93.750 bytes/s (750 kbps)**; latencia CDP **150 ms**; `connectionType: cellular3g`.
- CPU de la página ralentizada **4×** mediante CDP. El perfil de red no estaba especificado numéricamente en la historia; se registra para que el resultado sea reproducible, sin afirmar que representa cualquier conexión 3G.
- Tres contextos nuevos independientes; caché HTTP deshabilitada y sin service worker/IndexedDB/CacheStorage previos; worker permitido como en una primera visita real. **Todas son cargas frías**; no se hizo una medición con caché para sustituirlas.
- Navegación inicial a `/`, sesión simulada no válida y redirección normal a `/login`. Fin medido: campos de acceso y botón del formulario visibles. Se toma `performance.now()` desde el inicio de navegación; no se confunde DOMContentLoaded con interfaz lista.
- Servidor de pruebas sirve los archivos de producción con gzip nivel 6, igual que `sgp-client/nginx.conf`. API simulada, sin medir tiempo de procesamiento de Spring Boot.

| Muestra fría | DOMContentLoaded | Primera pintura de contenido | Interfaz lista | Documento + recursos transferidos hasta la medición |
|---|---:|---:|---:|---:|
| 1 | 1.328,8 ms | 2.516 ms | **2.805,2 ms** | 186.052 bytes |
| 2 | 1.333,4 ms | 2.580 ms | **2.616,9 ms** | 186.052 bytes |
| 3 | 1.334,2 ms | 2.532 ms | **2.819,7 ms** | 186.052 bytes |

Media: **2.747,3 ms**; máximo: **2.819,7 ms**. Las tres muestras cumplen **< 5.000 ms**. La transferencia es la suma de `transferSize` del documento (1.293) y recursos finalizados (184.759) en Resource Timing; incluye cabeceras y recursos de esta ruta. No representa la descarga completa de los 128 recursos de precarga en segundo plano.

El paquete inicial continúa en **443.314 bytes / 138.481 bytes gzip**, quince archivos JS/CSS; index gzip: 994 bytes. No se confunde ese conjunto con todos los recursos de una página o de la instalación PWA.

### Instalación, caché y actualización

La instalación, reapertura del perfil y lanzamiento sin red están comprobados en navegador real. Los recursos del shell y traducciones se obtienen sin depender solo de memoria de la primera página. La caché de referencias responde offline y revalida departamentos al volver a consultar; las asignaciones privadas continúan en Dexie por propietario.

La nueva versión del worker muestra el aviso y permite posponerlo. El Nginx local entrega el index con `no-store, no-cache, must-revalidate`. La copia versionada del index dentro del app shell es necesaria para arrancar offline: se mantiene la interpretación de caché **HTTP** del [ADR-0007](docs/adr/0007-hu061-publicacion-offline-y-cache-privada.md), sin modificar el criterio ni retirar el shell del worker.

## 8. Correcciones mínimas y acciones pendientes

**No queda una corrección obligatoria demostrada para CA4/CA7.** Se desbloqueó el entorno de pruebas y se adecuó el fixture a los contratos existentes; no se cambió producción, proxy de producción, JWT de producción, idempotencia ni lógica de negocio.

La actualización del artefacto del entorno habitual y su coordinación con la base persistente corresponden a una operación separada: no se hizo repair, no se alteraron migraciones aplicadas ni se aplicaron migraciones sobre desarrollo/producción para completar esta aceptación.

## 9. Veredicto final

1. **¿Cumple HU-061 todos los criterios?** Sí, dentro de las condiciones de aceptación documentadas: **12 CUMPLE**. El cierre actual añade exclusivamente evidencia real para CA4 y CA7.
2. **¿Cuáles están demostrados?** CA1–CA12. Se conserva la evidencia anterior de los diez criterios ya aprobados; CA4/CA7 pasan ahora en navegador conectado a Spring Boot y PostGIS reales.
3. **¿Cuáles no cumplen?** Ninguno. D1 está corregido y no se confirmaron defectos nuevos de producción.
4. **¿Cuáles siguen pendientes de verificación obligatoria?** Ninguno de los criterios originales bajo el alcance documentado. No se confunde el estado del despliegue habitual con el resultado del entorno de aceptación aislado.
5. **¿Se confirmó pérdida de información?** No. Además de la evidencia anterior, R conserva la cola tras logout offline, respuestas completas y cuatro fotografías después de ambas sincronizaciones.
6. **¿Se confirmaron vulnerabilidades entre usuarios?** No en los recorridos probados; se conserva la evidencia E13 sin ampliar esta tarea a una auditoría general.
7. **¿Son seguros los reintentos comprobados?** Sí: clave offline conservada, replay real 200 con mismo cuerpo y conteos remotos sin duplicación de inspección, historial, fotos ni IC. E10 sigue descartado.
8. **¿Hay regresiones nuevas confirmadas?** No. En este cierre se ejecutaron solo los dos escenarios pertinentes; no se repitieron las suites que ya tenían evidencia suficiente.
9. **¿Puede aprobarse HU-061?** **Sí, con la evidencia acumulada y las condiciones documentadas.** El resultado no implica despliegue ni autorización para intervenir bases persistentes.
10. **¿Qué falta para su aceptación técnica?** No queda una corrección o verificación obligatoria de CA4/CA7 pendiente. El mantenimiento del artefacto del entorno habitual se dejó separado y documentado, respetando la restricción de no modificar su base.
