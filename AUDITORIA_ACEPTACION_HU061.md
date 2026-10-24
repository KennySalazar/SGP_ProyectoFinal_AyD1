# Auditoría de aceptación de HU-061

Actualización: **24 de octubre de 2026**, después de corregir D1 y ejecutar las verificaciones PWA pendientes. Referencia Git: `8463968c23acca90e9e1a58363a426b1913af578`, más la corrección D1 presente en el árbol de trabajo. No se hicieron commits.

## 1. Resumen ejecutivo

**HU-061 todavía no está lista para una aprobación completa.** La instalación y el arranque offline, la caché, el aviso de actualización y la carga 3G ya tienen evidencia de navegador. D1 está corregido. No se confirmaron defectos nuevos de producción.

La matriz queda en **10 CUMPLE y 2 PARCIAL**. No hay criterios clasificados actualmente como NO CUMPLE. Los pendientes son el recorrido conectado a Spring Boot de envío/publicación y la recuperación de sesión con pendientes: su comportamiento en Angular pasó contra la API simulada, pero el entorno real disponible no permitió completar el recorrido.

Bloqueo observado mediante consultas de solo lectura:

- `http://127.0.0.1:8088/healthz`: HTTP 200.
- `http://127.0.0.1:8088/api/v1/auth/me`: **HTTP 502**.
- `http://127.0.0.1:8090/actuator/health`: conexión rechazada, código HTTP 000 de curl.

Esto se clasifica como **bloqueo del entorno para verificar**, no como un defecto demostrado de HU-061. No se iniciaron servicios sobre bases persistentes ni se modificaron datos reales para sortearlo.

En esta continuación se habilitó Playwright con la versión ya declarada, se ejecutaron primero las **dos pruebas PWA originales: 2/2 aprobadas**, y luego la suite ampliada mínima: **5/5 aprobadas en 28,3 s**. Las tres cargas frías 3G tardaron **2,8052 s; 2,6169 s; 2,8197 s**, todas inferiores a cinco segundos bajo el perfil documentado.

La única edición de esta continuación afecta este informe y dos archivos de pruebas: `sgp-client/e2e/hu061/hu061.spec.mjs` y `server.mjs`. Se preservaron los cambios anteriores de D1 y de auditoría. No se modificaron producción, backend, dependencias declaradas, lockfile, arquitectura ni migraciones.

## 2. Matriz de criterios de aceptación

Una fila por escenario original de [`HUS/HU-061.md`](../HUS/HU-061.md). P0/P1/P2 identifican ejecuciones descritas en la sección 3. F/B/D1 son la evidencia previa, conservada e identificada expresamente como histórica. PARCIAL señala qué parte está demostrada y cuál continúa pendiente; no implica un defecto confirmado.

| Criterio original | Estado actual | Implementación localizada | Evidencia y observaciones |
|---|---|---|---|
| **CA1. Instalable y con arranque sin red** | **CUMPLE** | `sgp-client/public/manifest.webmanifest`, iconos; `src/app/app.config.ts`, `provideServiceWorker()`; `ngsw-config.json`. | P2 instala realmente mediante `PWA.install` en Chromium completo, sin errores de instalabilidad; registra la opción de apertura standalone, cierra el navegador, reabre el mismo perfil y lanza la aplicación instalada con la red deshabilitada mediante `PWA.launch`. Comprueba shell visible, modo standalone, control del worker y «Sin conexion». La aplicación se desinstala al finalizar el test. No se simula standalone mediante CSS ni emulación del navegador. |
| **CA2. Shell precargado, referencias SWR e inspecciones fuera del worker** | **CUMPLE** | `sgp-client/ngsw-config.json`; `src/app/offline/services/referencias-locales.service.ts`, `misPuentesAsignados()`. | P0/P1 comprueban SWR de departamentos: entrega el valor cacheado y después el actualizado. P1 obtiene formulario activo y municipios desde caché al desconectar. Antes de cortar la red verifica que los **128 recursos prefetch** del manifiesto generado existen en CacheStorage, incluidos JS de rutas lazy, traducciones, CSS e iconos. El test existente conserva la comprobación de ausencia de inspecciones en las cachés del worker. F ya verifica SWR privado por propietario para puentes asignados; no se repitió. |
| **CA3. Obligatorios/fotografías faltantes impiden encolar e informan al usuario** | **CUMPLE** | `src/app/features/inspecciones/pages/completar-inspeccion`, `abrirConfirmacion()`/`confirmarAccion()`; formulario dinámico, `revisar()`; `offline/services/inspecciones-locales.service.ts`, `encolarAccionFinal()`. Rutas bajo `sgp-client/`. | Se conserva la evidencia F/B de revisión inválida sin encolado, categorías confirmadas y pendientes, permisos, condiciones/grupos/arreglos y validación real 422 del backend. P1 no sustituye esos tests: su formulario es deliberadamente mínimo. |
| **CA4. Enviar/publicar offline con clave, pendiente y ejecución al reconectar** | **PARCIAL** | `offline/services/inspecciones-locales.service.ts`, `encolarAccionFinal()`; `sincronizacion.service.ts`, `iniciar()`/`ejecutarCola()`/`confirmarFinal()`; página completar inspección. | P1 verifica publicación de Catedrático y envío de Estudiante: confirma offline, reabre otra página offline, conserva cola/propietario/clave, recupera sesión y observa PUBLICADA/ENVIADA y cola vacía tras la respuesta HTTP. La API simulada registra una única clave final. F/B ya prueban dependencias e idempotencia con PostGIS real por separado. **Falta un recorrido navegador → Spring Boot real conectado; bloqueado por el HTTP 502 local.** |
| **CA5. Rechazo posterior con motivo y edición sin pérdida** | **CUMPLE** | `sgp-client/src/app/offline/services/sincronizacion.service.ts`, `procesar()`; servicio local, `exigirEditable()`; página completar inspección y bloque HTML de intención rechazada. | **D1 corregido**, con 203/203 regresiones aprobadas en la intervención anterior: BORRADOR propio confirmado más publicación 404 permite RECHAZADA, conserva respuestas/fotografías y motivo, libera edición y admite otra confirmación con nueva clave. Conserva 422, resultados inciertos, operaciones en proceso y publicaciones confirmadas. La prueba de componente comprueba el motivo en un elemento `role=alert`. No se repitieron esas 203 pruebas en esta continuación. |
| **CA6. IC disponible al sincronizar; fórmula solo en backend** | **CUMPLE** | Componente `confirmacion-estado-ic`, `calculoVisible`/`consultar()`/`guardar()`; página completar, `icVisible`; `sgp-client/public/i18n/es.json`; backend `CalculoIndiceCondicionService`/`PublicacionIndiceCondicionService`. | F/D1 comprueban ocultación del valor anterior y ausencia de HTTP al consultar/guardar offline; B ejecuta cálculo/publicación reales y rollback por confirmación desactualizada. P0/P1 observan «IC disponible al sincronizar» en la página offline. No se introdujo una fórmula Angular. |
| **CA7. Logout o expiración conservan pendientes y sincronizan tras login** | **PARCIAL** | `core/services/auth.store.ts`, `clear()`/`setUser()`; `session-bootstrap.service.ts`, `initialize()`; shell, `logout()`; sincronizador, eventos de sesión. Rutas bajo `sgp-client/src/app/`. | P1 cierra sesión offline como Catedrático, verifica eliminación del perfil visible y conservación exacta de IDs, propietarios y claves de la cola. Como Estudiante, simula expiración del servidor y recarga al reconectar: HTTP 401 conduce al login sin vaciar la cola. Ambos se autentican mediante el formulario real y sincronizan con las claves previas. **Login/refresh son simulados: falta verificar el ciclo con cookies/JWT de Spring Boot real, por el mismo bloqueo 502.** |
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

Sin API real accesible no se ejecutó el recorrido conectado de CA4/CA7. No se crearon cuentas en bases reales ni se presentaron los resultados del simulador como autenticación JWT o publicación Spring Boot.

### Evidencia anterior conservada, no repetida

- **F:** selección inicial de frontend, 324/324 aprobadas; complemento de 110 con el fallo histórico D1. Incluye motor/formulario, fotos, Dexie, preparación y aislamiento.
- **B:** 45/45 backend con Java 21 y PostgreSQL/PostGIS efímero: `Hu061Fase0IntegrationTest` (8), `IdempotenciaHttpFilterTest` (10), `PublicacionRealIcIntegrationTest` (11), `InspeccionControllerSecurityTest` (6) y `TransicionInspeccionControllerSecurityTest` (10). Rutas bajo `sgp-api/src/test/java/gt/usac/cunoc/sgp/`.
- El replay de publicación HTTP real conserva cuerpo/status, IC, historial y un solo evento. E10 no se reproduce: se mantiene la coordinación `SolicitudIdempotente` entre filtro y controlador.
- **D1:** intervención posterior con 203/203 pruebas, lint, formato y build correctos. Comprueba la corrección, 422, incertidumbre, publicación confirmada, claves y motivo visible. El fallo histórico de 110 pruebas ya no describe el estado actual.

Estos números no se suman como pruebas distintas: hay solapamiento. No se repitió la auditoría de código, las suites ajenas ni el backend ya validado.

### Comprobaciones de esta continuación

`node --check` de ambos archivos E2E, Prettier de esos archivos y `git diff --check`: correctos. No hubo cambios de producción que requirieran otra compilación: la suite usa el build de producción posterior a D1, previamente verificado.

Registros de esta sesión: `/tmp/hu061-aceptacion-final/`, especialmente `pwa-existentes-habilitado.log`, `pwa-final.log`, `resultados-pwa.json`, `instalacion.json` y `medicion-3g.json`. Playwright conserva trazas de fallos de diagnóstico en `sgp-client/test-results/`; son artefactos temporales, no evidencia de staging.

## 4. Problemas confirmados

**No se confirmaron defectos nuevos de producción.**

**D1 — corregido:** la reconciliación de un rechazo 409 dejaba intención PENDIENTE y edición bloqueada aun con BORRADOR propio confirmado. La corrección reutiliza la transacción existente de rechazo cuando además se confirma ausencia de publicación mediante 404: registra RECHAZADA/motivo y retira solo la acción final. Las regresiones posteriores comprueban conservación y nueva confirmación. No se reabre D1 por la falta de un entorno conectado.

El 502 local es un bloqueo observable de infraestructura para esta verificación; no se atribuye a una causa ni se clasifica como defecto funcional de HU-061 sin evidencia adicional.

## 5. Riesgos y bloqueos pendientes

1. **CA4:** falta recorrer con un inspector de prueba y API real accesible la confirmación offline, reconexión y resultado ENVIADA/PUBLICADA. Se verificaron navegador y backend por separado, pero no su integración conectada en esta continuación.
2. **CA7:** falta el ciclo real de autenticación/cookies/refresh tras cierre o expiración con pendientes. El navegador real ya conserva y sincroniza correctamente contra el contrato simulado; esto no verifica el mecanismo de sesión del backend desplegado.

La API local responde 502 y el puerto directo 8090 no está accesible. No hay un recorrido E2E conectado listo para reutilizar entre las pruebas existentes: las suites Java usan MockMvc/Testcontainers y la PWA usa su API simulada. No se creó otro harness ni se alteró una base persistente para cerrar artificialmente la evidencia.

Límites de los resultados aprobados: instalación de escritorio Chromium en Linux, no todos los navegadores/dispositivos; carga inicial frontend bajo el perfil 3G declarado, sin carga de backend real. No se agregan como requisitos una certificación general de navegadores, funcionamiento de ocho horas, background sync con la aplicación cerrada o staging.

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

**No se requiere otra corrección de código demostrada por estas pruebas.** No se modificaron funcionalidades que ya funcionaban.

Para cerrar CA4/CA7 hace falta un entorno de pruebas con API real disponible y un inspector/datos de inspección preparados, y ejecutar allí los mismos pasos de confirmación offline y recuperación de sesión. Debe comprobarse el resultado remoto, las claves conservadas y la conservación local. No es necesario añadir otro framework, reescribir el sincronizador ni refactorizar la aplicación.

El objetivo pendiente es obtener evidencia conectada; el 502 no autoriza a deducir un fallo de idempotencia o pérdida de información. Cualquier corrección posterior debe responder a un defecto reproducido.

## 9. Veredicto final

1. **¿Cumple HU-061 todos los criterios?** No puede certificarse por completo: **10 CUMPLE y 2 PARCIAL**. Ya no hay un incumplimiento funcional confirmado pendiente de corrección.
2. **¿Cuáles están completamente demostrados dentro del alcance indicado?** CA1, CA2, CA3, CA5, CA6, CA8, CA9, CA10, CA11 y CA12. CA11 se acredita para el perfil 3G explícito y la carga inicial medida.
3. **¿Cuáles no cumplen?** Ninguno clasificado actualmente NO CUMPLE. D1 está corregido y no se confirmaron defectos nuevos.
4. **¿Cuáles siguen pendientes de verificación?** La parte conectada a Spring Boot de CA4 y CA7. El navegador pasó con API simulada; el entorno real respondió 502.
5. **¿Existen riesgos de pérdida de información confirmados?** No se reprodujo pérdida. Se conserva la evidencia anterior de E12, limpieza y rechazo, y P1 verifica los mismos IDs/claves/propietarios de pendientes antes y después de cierre/expiración. Falta contrastar el ciclo con la API real.
6. **¿Existen vulnerabilidades entre usuarios confirmadas?** No en los recorridos ya verificados; se conserva la evidencia E13 sin presentar una certificación general de seguridad.
7. **¿La sincronización es segura ante reintentos?** Se mantiene evidencia de claves estables en navegador y replay real en backend por separado. E10 sigue descartado; no se amplió ni alteró la idempotencia.
8. **¿Hay regresiones nuevas?** No se confirmó una en las pruebas ejecutadas.
9. **¿Está lista para aprobarse?** **Todavía no para aprobación completa**, por el bloqueo de evidencia conectada de CA4/CA7; instalación, caché, actualización y 3G ya no están pendientes.
10. **¿Qué falta exactamente?** Restablecer/disponer de una API de pruebas accesible y verificar envío/publicación y cierre/expiración → login → sincronización con ese backend real, registrando resultados. Si pasan, no queda otra corrección obligatoria demostrada por esta auditoría.
