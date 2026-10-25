# Plan de implementación de HU-062: pruebas con red degradada

Fecha: **24 de octubre de 2026**, America/Guatemala. Revisión analizada: `eb13093`. Alcance de esta entrega: análisis y planificación; no se implementaron pruebas, cambios funcionales, dependencias ni infraestructura.

## 1. Resumen ejecutivo

**Conservar Playwright y extender el entorno de aceptación real de HU-061.** La necesidad es añadir escenarios y verificaciones sobre la sincronización existente, junto con una preparación reproducible del entorno aislado. No hay evidencia que justifique modificar el motor offline, la idempotencia o la subida fotográfica de producción.

HU-061 tiene una auditoría de **12/12 criterios cumplidos** y dos recorridos documentados contra Spring Boot/PostGIS reales. Eso es evidencia previa de HU-061, no aceptación automática de HU-062. En particular, `hu061-real.spec.mjs` crea la inspección, guarda respuestas y sube cuatro fotografías **por API antes de desconectarse**: demuestra la acción final offline y su replay, pero no la subida de fotos pendientes bajo fallos de comunicación.

Trabajo nuevo mínimo:

1. Reutilizar datos y autenticación reales, creando por interfaz una inspección offline con respuestas y cuatro fotografías pendientes.
2. Ejecutar ese recorrido con conexión normal, desconexión, latencia fija y fallos de transporte controlados.
3. Cortar un multipart parcialmente transmitido y perder una confirmación ya emitida por Spring Boot; recuperar con las mismas identidades y claves.
4. Verificar Dexie, API, conteos SQL y objetos fotográficos reales; conservar evidencias de fallo.
5. Documentar herramienta, perfiles, comandos, resultados y límites dentro de la documentación técnica existente.

Para pérdida **literal de paquetes**, añadir una comprobación acotada con `tc/netem` en el espacio de red del backend desechable. Los abortos HTTP/TCP del proxy prueban recuperación de comunicaciones, pero no sustituyen esa evidencia. No se propone otro proxy, otro framework E2E, otra base tecnológica ni una nueva arquitectura de sincronización.

## 2. Fuentes verificadas y límites de la auditoría

Todas las rutas siguientes son relativas a la raíz Git `SGP_ProyectoFinal_AyD1/`; `../HUS/` pertenece al directorio contenedor del repositorio.

| Fuente real | Conclusión y uso |
|---|---|
| [AGENTS.md](AGENTS.md), [README.md](README.md), [arquitectura](docs/ARCHITECTURE.md) | Respetar capa `offline/`, servicios, contratos `/api/v1`, cambios pequeños y ADR para decisiones relevantes. Estado y diff iniciales sin cambios. |
| [HU-061](../HUS/HU-061.md), [auditoría final](AUDITORIA_ACEPTACION_HU061.md) | Evidencia previa de durabilidad, autorización, sesión, PWA e idempotencia; cierre real de CA4/CA7. No se repitieron esas ejecuciones en este análisis. |
| [guía de pruebas HU-061](docs/testing/HU-061.md) | Procedimiento existente y distinción entre API simulada y real. Parte del texto quedó anterior al cierre real: actualizar ese límite por referencia a la auditoría. |
| [ADR-0007](docs/adr/0007-hu061-publicacion-offline-y-cache-privada.md), [ADR base](docs/adr/0001-base-arquitectonica.md) | Playwright aceptado para HU-061; cola, caché privada, recuperación e idempotencia existentes. Se inspeccionó el catálogo `docs/adr/`; no hay un ADR específico de HU-062. |
| [historias consolidadas](docs/Jira_HUs_completas.md), HU-044, escenario «Prueba E2E base con Playwright» | Antecedente explícito de Playwright para la sincronización. No equivale a la «Decisión 6 del responsable». |
| `HUS/HU-062.md` | **No localizado**, ni en `../HUS/` ni dentro del repositorio. La matriz de este plan usa exclusivamente CA1–CA11 de la solicitud adjunta. No se inventa el contenido de una historia ausente. |
| «Decisión 6 del responsable» y enunciado original | **No localizados** en los archivos disponibles, incluidos los ignorados revisados por nombre. `.gitignore` menciona `enunciado_ayd1.md`, pero no se encontró el archivo. ADR-0006 trata mantenimiento, no elección E2E. |
| Manual técnico | No se encontró un archivo llamado manual técnico. `README.md` presenta `docs/` como documentación técnica; `docs/INVENTARIO_AUTORIZACION_ENDPOINTS.md` se declara parte del manual. Incorporar HU-062 ahí mediante guía enlazada, sin crear otro manual. |

**Decisión provisional sustentada:** seguir el ADR-0007 vigente para HU-061 y recomendar extender Playwright a HU-062. No afirmar que se verificó la Decisión 6 ni que Cypress es opcional en ese documento ausente. Si aparece una decisión vinculante que obliga Cypress, prevalece: se debe revisar este punto antes de implementar. La ausencia documental permite planificar, pero deja pendiente confirmar la conformidad con esa autoridad para cerrar CA10.

## 3. Infraestructura actual y componentes reutilizables

| Elemento | Evidencia real | Reutilización / cambio mínimo |
|---|---|---|
| Playwright | [package.json](sgp-client/package.json), lockfile y [configuración](sgp-client/playwright.config.mjs) | `@playwright/test` declarado; paquete instalado 1.64.1. Script `test:pwa`. Un worker, timeout 90 s, `trace: retain-on-failure`, servidor 4173. Actualmente descubre solo `e2e/hu061`; ampliar descubrimiento y capturas para HU-062. |
| E2E PWA simulada | [hu061.spec.mjs](sgp-client/e2e/hu061/hu061.spec.mjs), [fixture.mjs](sgp-client/e2e/hu061/fixture.mjs) | Reutilizar espera de precarga, control del worker, reapertura, consulta IndexedDB y evidencia. Sus cinco casos cubren PWA/sesión, SWR, instalación y carga fría 3G. No usar el simulador como oráculo del servidor real. |
| E2E real | [hu061-real.spec.mjs](sgp-client/e2e/hu061/hu061-real.spec.mjs) | Reutilizar login por formulario, captura de tokens, claves UUID v7, consultas SQL, replay y adjuntos JSON. Sus dos casos se omiten sin `HU061_API_ORIGIN`; HU-062 debe fallar en preparación si falta backend, nunca aprobar omitiendo casos. |
| Proxy y servidor estático | [server.mjs](sgp-client/e2e/hu061/server.mjs) | Sirve build de producción/gzip; con `HU061_API_ORIGIN` reenvía `/api/` antes de los mocks. Extender únicamente este servidor de pruebas con perfiles y puntos de corte; hoy no contiene inyección de fallos ni cortes multipart. |
| Cypress histórico | [configuración](sgp-client/e2e/cypress.config.cjs), [revisión](sgp-client/e2e/revision-detalle.cy.cjs) | Suite de presentación con HTTP interceptado sobre `localhost:4200`, video desactivado. No hay dependencia Cypress en manifiesto/lockfile ni paquete local `node_modules/cypress`. No activarla ni migrarla como parte de HU-062. |
| Offline y persistencia | [sgp-db.ts](sgp-client/src/app/offline/db/sgp-db.ts), [inspecciones locales](sgp-client/src/app/offline/services/inspecciones-locales.service.ts), [fotografías locales](sgp-client/src/app/offline/services/fotografias-locales.service.ts) | DB `sgp_offline`, versión Dexie 4, tablas `inspecciones`, `fotos`, `cola_sync`. `iniciarInspeccionLocal`, guardado e intención final transaccionales. Fotos conservan Blob, metadatos, propietario y clave hasta `confirmar()`, que retira foto/cola y actualiza inventario. Reutilizar sin cambios. |
| Sincronización | [sincronizacion.service.ts](sgp-client/src/app/offline/services/sincronizacion.service.ts) | Orden creación → datos → fotos → acción final; sesión validada antes de procesar; Web Locks si existen. Disparos por conexión/login/cola y comprobación cada 30 s. Reutilizar. |
| Reintentos | Mismo sincronizador; [operación idempotente cliente](sgp-client/src/app/features/inspecciones/services/operacion-idempotente.service.ts) | Hasta cinco intentos, backoff `min(16000, 1000 * 2^intento) + Math.random()*350` ms. HTTP 0/5xx reintentables; 401 y 409 `operacion_en_proceso` conservan pendiente. `ENVIANDO` se recupera tras más de 60 s. Cinco fallos llevan a `INTERVENCION`; existe `reintentar()`. No prometer reintentos ilimitados. |
| Idempotencia servidor | [filtro JSON](sgp-api/src/main/java/gt/usac/cunoc/sgp/sync/http/IdempotenciaHttpFilter.java), [FotografiaService](sgp-api/src/main/java/gt/usac/cunoc/sgp/inspeccion/service/FotografiaService.java) | Creación, borrador y acción final usan filtro JSON. Fotografías usan circuito multipart con huella de contenido/metadatos y clave persistida. No exigir cabecera `Idempotent-Replay` al multipart: su controller devuelve 201 y la recuperación se verifica por identidad, cuerpo y conteos. |
| Pruebas locales de cola | [hu061-fase0.integration.spec.ts](sgp-client/src/app/offline/hu061-fase0.integration.spec.ts), [fotografías locales spec](sgp-client/src/app/offline/services/fotografias-locales.service.spec.ts) | Ya prueban persistencia al reabrir, orden crear/datos/cuatro fotos/final, petición fijada, resultado incierto y foto en intervención conservada. No reescribir estas pruebas; ampliar la cobertura de navegador/servidor. |
| API de verificación | [InspeccionController](sgp-api/src/main/java/gt/usac/cunoc/sgp/inspeccion/controller/InspeccionController.java), [FotografiaController](sgp-api/src/main/java/gt/usac/cunoc/sgp/inspeccion/controller/FotografiaController.java) | Obtener borrador, publicada, listado propio paginado, fotos y contenido/miniatura. SQL complementa conteos e integridad usando el patrón de HU-061; no requiere endpoints nuevos. |
| Almacenamiento | [AlmacenamientoLocal](sgp-api/src/main/java/gt/usac/cunoc/sgp/archivo/service/AlmacenamientoLocal.java), [FotografiaService](sgp-api/src/main/java/gt/usac/cunoc/sgp/inspeccion/service/FotografiaService.java), [propiedades](sgp-api/src/main/resources/application.properties) | Proveedor local existente por defecto, escritura temporal `.pendiente` y publicación del objeto completo; procesamiento, compensación e idempotencia existentes. Usar directorio efímero exclusivo. S3/MinIO ya existen, pero no son necesarios para estos CA. |
| Integraciones backend | `sgp-api/src/test/java/gt/usac/cunoc/sgp/integration/{PublicacionRealIcIntegrationTest,FotografiaFlujoIntegrationTest,AlmacenamientoS3IntegrationTest}.java` | PostGIS/Testcontainers, replay/IC real, compensación de objetos y S3. `FotografiaFlujoIntegrationTest` usa almacenamiento instrumentado; `FotografiaHttpLimitesIntegrationTest` simula `FotografiaService`. No presentar esas pruebas como E2E completo con almacenamiento real. |
| Datos aislados | [real-fixture.sql](sgp-client/e2e/hu061/real-fixture.sql), auditoría final §3.R | Fixture con guardia `current_database()='hu061_aceptacion'`, roles, puentes, curso, asignación, formulario mínimo y reglas IC. Reutilizarlo en una nueva instancia desechable; depende del bootstrap de `admin-hu061@example.test`. |
| Docker / entornos | [Compose local](docker-compose.yml), [Dockerfile API](sgp-api/Dockerfile), `infra/staging/compose.yml`, `infra/production/compose.yml` | Reutilizar imagen PostGIS y Dockerfile backend. Compose habitual contiene volúmenes persistentes y carga `.env`: **no es un entorno desechable por cambiar solo el nombre del proyecto**. No usar staging/producción ni `down -v` sobre esos entornos. |
| CI | [.github/workflows/ci.yml](.github/workflows/ci.yml) | Backend: Java 21 y `mvn spotless:check verify`. Frontend: Node 24, instalación bloqueada, lint/formato/build. No ejecuta E2E. Evaluar viabilidad, sin modificar workflow en esta HU. |

### Disponibilidad observada en esta sesión

Se verificaron Java **21.0.12.1**, Maven **3.8.7** usando Java 21, `docker`, `tc` **iproute2-6.1.0** y `nsenter` **2.39.3** en PATH. No se comprobó acceso al daemon, permisos de red, módulo `sch_netem`, navegadores ni salud de servicios.

El Node activo es **18.20.8**. Al intentar cargar el ejecutor instalado, Playwright indicó que requiere Node 20 o superior; Angular tiene requisitos más concretos en [DEVELOPMENT.md](docs/DEVELOPMENT.md). Seleccionar **Node 24** para implementar/ejecutar. Esta comprobación de disponibilidad no ejecutó una suite ni instaló paquetes.

La auditoría HU-061 documenta un backend habitual con fallo Flyway V15 y el uso de un entorno independiente ya eliminado. Es evidencia histórica; no se volvió a diagnosticar ese despliegue. El plan requiere levantar un entorno limpio con el código actual y mantener la validación Flyway.

## 4. Selección E2E y alcance del ADR

| Aspecto | Playwright | Cypress |
|---|---|---|
| Coste en este repositorio | Dependencia, configuración, script y suites PWA/real disponibles. | Solo configuración y suite histórica de revisión; habilitar ejecutor requeriría dependencia y adaptación. |
| Desconexión y fallos | `BrowserContext.setOffline()` y `Route.abort()` existen en los tipos instalados; la suite usa desconexión real del contexto. Para esta HU, fallos del proxy evitan interferir con el worker. | `cy.intercept` ofrece `forceNetworkError`/`req.destroy()`. Esto simula error de comunicación, no pérdida literal de paquetes. |
| Latencia | CDP ya utilizado para 3G; para sincronización se recomienda retardo en el proxy real, observable incluso con worker. | `cy.intercept` permite demora/throttle y continuar al servidor real. Exigiría adaptar PWA y el entorno actual. |
| Pérdida de paquetes HTTP | Ni `abort()` ni `packetLoss` de CDP demuestran esta condición: el campo instalado se documenta para **WebRTC**. Usar netem acotado si se certifica pérdida literal. | Un error de `intercept` tampoco equivale a pérdida de paquetes IP/TCP; requeriría mecanismo de red externo al framework. |
| Spring Boot real | Recorrido ya localizado y auditado. | Técnicamente posible mediante red/API reales, pero sin fixture E2E conectado reutilizable equivalente en la suite Cypress encontrada. |
| Evidencias | Trazas configuradas; adjuntos JSON y captura explícita ya usados. Añadir capturas automáticas de fallos. | Hay carpeta de capturas configurada; no hay trazas Playwright ni ejecutor Cypress instalado. |
| CI / mantenimiento | Runner sin interfaz y configuración existente; integrar sería trabajo posterior y no necesario para ejecución manual reproducible. | También dispone de ejecución sin interfaz, pero supondría habilitar y mantener un segundo ejecutor. |

Capacidades contrastadas con los tipos locales `sgp-client/node_modules/playwright-core/types/{types.d.ts,protocol.d.ts}` y `playwright/types/test.d.ts`, el código E2E existente y documentación primaria: [red Playwright](https://playwright.dev/docs/network), [Cypress intercept](https://docs.cypress.io/api/commands/intercept), [grabación Playwright](https://playwright.dev/docs/test-use-options#recording-options). La comparación describe posibilidades; no asegura ejecución Cypress en este proyecto.

**ADR previsto:** `docs/adr/0008-hu062-pruebas-e2e-red-degradada.md`, estado propuesto hasta resolver la referencia a Decisión 6 y validar la ejecución. Verificar el siguiente número disponible al implementarlo. Complementa ADR-0007 sin alterar su alcance o fingir una aprobación nueva.

Contenido mínimo del ADR: fuentes/autoridad de la decisión, elección de Playwright, reutilización del entorno real, descarte de habilitar Cypress, perfiles exactos, diferencia entre error de transporte y pérdida real, alcance del proveedor local, ejecución manual, evidencias y límites. El archivo ADR **no se crea en esta entrega**, que produce solo este plan.

## 5. Matriz de criterios de aceptación

Estados: **Ya cubierto** = evidencia previa suficiente para esa parte concreta; **Parcialmente cubierto** = mecanismos y alguna evidencia, falta el recorrido exigido; **Pendiente** = falta escenario/artefacto específico; **No verificado** = falta fuente o no se ejecutó la comprobación necesaria. Son estados de evidencia, no diagnósticos de defectos.

| CA | Estado actual | Evidencia existente | Verificación mínima que falta |
|---|---|---|---|
| CA1 Normal | Parcialmente cubierto | `hu061-real.spec.mjs`: acción final y cola de inspección vacía con cuatro fotos previamente subidas; auditoría §3.R. | Crear offline también datos/fotos pendientes; reconectar; verificar 1 inspección, 4 fotos disponibles, datos exactos, estado final y cola completa sin pendientes ni duplicados. |
| CA2 Sin conexión | Parcialmente cubierto | `hu061.spec.mjs`, `hu061-fase0.integration.spec.ts`: reapertura, intención y persistencia; fotos locales spec conserva Blob. | Creación por UI offline, respuestas/fotos pendientes, recarga, instantáneas equivalentes y cero escrituras recibidas por el proxy/backend para esos UUID. |
| CA3 Latencia alta | Pendiente | `hu061.spec.mjs` prueba carga inicial 3G con API simulada; no sincronización lenta real. | Sincronizar con retardo fijo, observar pendientes antes de confirmación y resultado real sin duplicados. Si la lentitud no causa reintento, no inventarlo: exigir finalización segura y combinar la evidencia de reintento con CA4. |
| CA4 Pérdida/fallos | Pendiente | Sincronizador y pruebas locales tienen reintentos y resultado incierto. | Error de transporte antes del backend y pérdida de respuesta después de commit; claves estables, recuperación y conteos reales. Añadir comprobación netem para afirmar pérdida literal de paquetes. |
| CA5 Corte de fotografía | Pendiente | `FotografiaService`, almacenamiento local, pruebas de compensación y foto en intervención en `hu061-fase0.integration.spec.ts`. | Cortar multipart con bytes de imagen enviados pero cuerpo incompleto; restaurar; verificar Blob/clave, una sola foto completa, miniatura válida y ninguna operación abandonada. Perder además una confirmación 201 verifica el caso incierto. |
| CA6 Pendientes | Parcialmente cubierto; **Ya cubierto** el contrato local básico | `fotografias-locales.service.spec.ts`: solo elimina tras confirmar; integración fase 0: datos/cuerpo fijado, Blob en intervención, reapertura. | Reutilizar aserciones sobre navegador real en CA1–CA5; comprobar también filas FOTO, cuyo `entityId` es ID de foto, no ID de inspección. |
| CA7 Servidor | Parcialmente cubierto | SQL de `hu061-real.spec.mjs`: inspección, historial, IC, cuatro vínculos y reservas finales sin duplicación. | Fotos creadas por sincronización bajo fallos, listado y descarga real, longitud/checksum del JPEG almacenado, miniaturas y ausencia de objetos parciales disponibles. |
| CA8 Reproducibilidad | Parcialmente cubierto | `playwright.config.mjs`: un worker; perfil 3G numérico y precarga observable en suite HU-061. | Perfiles §6, fallos por ordinal/UUID, eventos de bytes/commit y límites de espera documentados. Preparación y limpieza automatizadas del entorno aislado. |
| CA9 Evidencia | Parcialmente cubierto | `trace: retain-on-failure`, `testInfo.attach`, captura de instalación. | Captura de fallo en los casos nuevos, traza efectiva con contextos gestionados por el runner y adjuntos de cola/red/resultados. Validarlo provocando un fallo de aserción controlado en el entorno de prueba. |
| CA10 Herramienta/ejecución | Parcialmente cubierto; **No verificado** Decisión 6 y disponibilidad completa | ADR-0007, manifiesto, configuración y CI. | Resolver autoridad documental, ADR de extensión, runner/guía reproducibles, Node 24, navegador compatible y entorno real sin omitidas. No exige cambiar CI. |
| CA11 Documentación | Pendiente para HU-062 | README enlaza documentación técnica y guía HU-061. | Una guía HU-062 enlazada al índice técnico con procedimiento, resultados por perfil, evidencias y limitaciones; evitar duplicar auditoría HU-061. |

Ningún CA completo de HU-062 se declara aprobado por inspección estática. Las partes ya cubiertas de HU-061 se citan y reutilizan; los escenarios nuevos necesitan ejecución.

## 6. Estrategia mínima de simulación de red

### Principio común

Usar Chromium, build Angular de producción y worker permitido. La precarga y la preparación de referencias terminan **antes** de degradar la red. Mantener una ventana abierta para sincronizar, conforme al ADR-0007.

Extender `e2e/hu061/server.mjs` solo en modo explícito HU-062 + `HU061_API_ORIGIN`. Configurar fallos por método, ruta, UUID/clave y ordinal; reiniciar estado entre casos. Los controles locales del harness se quedan fuera de `/api/v1`, ligados a loopback, y no se incorporan a Spring Boot. Los endpoints `__e2e` actuales ya establecen ese patrón. Las respuestas de negocio siguen proviniendo del backend real.

Preferir el proxy al enrutamiento del navegador para latencia/corte: el tráfico de service workers puede requerir tratamiento distinto en la interceptación. No desactivar el worker para hacer pasar el escenario PWA; véase [documentación primaria de service workers](https://playwright.dev/docs/service-workers).

| Perfil fijo propuesto | Parámetros y mecanismo | Señal observable / restauración |
|---|---|---|
| N: normal | Proxy transparente, retardo añadido 0 ms, cero fallos programados, sin qdisc de degradación. No se promete latencia física cero. | Confirmaciones reales y oráculo §7. |
| O: offline | `context.setOffline(true)` y comprobar indicador de desconexión. No tocar la conectividad de la máquina anfitriona. | UI y lecturas locales terminan; recarga por worker; contador de escrituras del proxy para esos IDs permanece 0. Restaurar con `setOffline(false)` y el evento de conexión existente si fuese necesario. |
| L: latencia alta | En operaciones de inspección/fotos, **1.500 ms antes de reenviar + 1.500 ms antes de entregar la respuesta**: 3.000 ms añadidos por operación, sin jitter ni fallos. Auth/health/controles quedan fuera. | Proxy informa petición retenida y confirmación retenida; tomar instantáneas locales en esas barreras. Liberar y esperar respuestas/cola, no dormir una cantidad aproximada. |
| F: fallos de comunicación | Primera operación de guardado: fallar los **dos primeros intentos antes de reenviar**, cerrando el transporte hacia el navegador. Luego permitir. En otro punto del mismo recorrido, perder **la primera respuesta 2xx de creación** después de recibirla íntegra del backend. | Registrar fallo/commit/ordinal; bloquear temporalmente el siguiente intento hasta capturar cola y conteos; después permitir. Deben existir reintentos Angular con la misma clave/cuerpo. No devolver 500 artificial como sustituto de desconexión. |
| P: pérdida literal de paquetes | Un único episodio de `netem loss 100%` en el flujo de respuestas API del backend aislado, durante el primer guardado seleccionado. Al observar **contador de descartes > 0**, retirar la pérdida y dejar TCP recuperar. Sin jitter; sin aleatoriedad ni semilla necesaria para 100%. Tope de activación/observación: 10 s; si no se descarta ningún paquete, falla la preparación. | Adjuntar qdisc/filtro, contador antes/durante/después y resultado real. Es una ráfaga de pérdida total de paquetes, no una simulación estadística de todas las redes móviles. TCP puede recuperar sin un nuevo intento HTTP; los reintentos de aplicación los demuestra F. |
| U: corte de subida | Solo primera fotografía seleccionada y primer intento: proxy transmite metadatos y **50% de los bytes de la parte binaria**, sin enviar el final del multipart, y destruye upstream/downstream. Mantiene longitud/cuerpo original de la petición; siguiente intento sin corte. | Registrar `0 < bytesImagenEnviados < bytesImagenTotales`, total multipart incompleto y evento de corte; comprobar pendiente antes de permitir retry. No usar solo `route.abort()` antes de enviar como evidencia de corte durante subida. |
| U-confirmación, variante de U | Primera subida llega completa a Spring Boot; retener su respuesta 201 sin entregarla; capturar foto/cola local y resultado remoto, destruir respuesta y permitir retry idéntico. | Foto disponible remota mientras Blob local continúa pendiente; tras replay queda exactamente una foto. Comprueba incertidumbre de confirmación, además de U. |

No mezclar todos los fallos en una sola ejecución: N es el control, O prueba persistencia y L/F/P/U aíslan la causa. CA6/CA7/CA9 se comprueban dentro de esos casos; no necesitan suites duplicadas.

### Pérdida literal: mecanismo acotado y justificación

Los tipos instalados de CDP describen `packetLoss` como pérdida **WebRTC**. No usar ese parámetro para certificar las peticiones REST. `Route.abort`, `forceNetworkError`, `req.destroy` o un error HTTP operan sobre otra capa. `tc/netem` sí permite descartar paquetes: [manual de netem](https://man7.org/linux/man-pages/man8/tc-netem.8.html). Se usa solo para esa brecha concreta.

El entorno de prueba ejecutará el backend con el **Dockerfile ya existente**, PostGIS desechable y archivos efímeros. Para P se accede únicamente al espacio de red de ese contenedor mediante `nsenter` y `tc` del host. Aplicar una clase/filtro en `eth0` que seleccione **TCP con puerto origen 8090**, hacia el proxy; no degradar el tráfico backend→PostGIS ni la interfaz del host. El bloque netem tendrá `loss 100%`; el script deberá mostrar y validar el filtro seleccionado antes de iniciar la petición. Desmontar la qdisc de prueba en `finally`/trap incluso ante fallo.

Este control necesita permisos de administración del espacio de red y kernel con netem; la presencia de los binarios no los demuestra. No instalar `tc` dentro de la imagen de producción ni añadir Toxiproxy, contenedores auxiliares permanentes o dependencias npm. Por ser una prueba de corrección y recuperación, no se extrapola el resultado a mediciones de rendimiento TCP.

Si el equipo ejecutor no tiene estos permisos, N/O/L/F/U siguen siendo ejecutables con el proxy y se registra P como **No verificado**. **No declarar CA4 terminado por pérdida literal** hasta correr P en una máquina Linux aislada apta. Cambiar a Cypress no elimina este bloqueo.

### Esperas y reintentos

Usar `expect.poll`, respuestas por método/ruta/clave, estado `ENVIANDO/PENDIENTE`, barreras del proxy, hashes locales y conteos del backend. Los retardos de 1.500 ms son parámetros de red, no esperas para adivinar cuándo acabó la aplicación. No introducir `waitForTimeout()` ni `networkidle` como condición de cola sincronizada.

Proponer presupuesto **180 s por caso HU-062**, sin aumentar el timeout global de HU-061, y un límite global del runner para garantizar limpieza. El backoff de producción conserva su jitter; se prueban orden, claves y eventual resultado, no milisegundos exactos. En F/U provocar menos de cinco fallos por operación para observar recuperación automática. La intervención tras agotamiento ya tiene pruebas locales: no convertir esta HU en otro requisito de reintentos ilimitados.

## 7. Verificación de resultados reales en Spring Boot

### Oráculo local común (CA6)

Extender la lectura IndexedDB usada en `hu061-real.spec.mjs` para incluir `fotos`, no crear otra implementación Dexie. Tomar instantáneas: offline, petición retenida, fallo, confirmación remota retenida y final.

Por inspección recoger ID/propietario/estado, respuestas, intención final y operaciones `entityId=inspeccionId`; además recoger fotos `inspeccionId=id` y sus operaciones FOTO por **cada ID de foto**. El filtro de cola solo por ID de inspección del test actual no basta para demostrar cola fotográfica vacía.

Antes de confirmar cada operación: comparar UUID, clave, metadatos, respuestas y hash SHA-256/tamaño del Blob. Permitir cambiar `intentos`, `estado` o `ultimoError`; no exigir igualdad ciega de la fila completa durante reintentos. Tras éxito, la eliminación del Blob pendiente es correcta si la foto ya figura en el inventario confirmado y existe completa en el servidor. No exigir conservar indefinidamente todas las filas que ya se confirmaron.

### Oráculo remoto común (CA7)

| Comprobación | Contrato o mecanismo ya existente |
|---|---|
| Respuestas y estado | `GET /api/v1/inspecciones/{id}/borrador` durante edición; `GET /api/v1/inspecciones/{id}/publicada` después de publicar; listado propio `GET /api/v1/inspecciones?pagina=0&tamanio=100`, recorriendo páginas si es necesario. Elegir lectura permitida por estado/rol. |
| Conteo de inspecciones | SQL de solo lectura sobre `inspeccion`, acotado al conjunto de UUID creado, autor y puente de fixture. Exigir 1 por escenario y ausencia de otras inspecciones inesperadas del actor/puente frente al inventario inicial. Contar solo una PK conocida no detecta otro UUID duplicado. |
| Fotos disponibles | `GET /api/v1/inspecciones/{id}/fotografias`: exactamente cuatro IDs esperados, categorías ACCESO/SUPERESTRUCTURA/SUBESTRUCTURA/CAUCE, sin duplicación. SQL de `archivo_inspeccion` unido a `archivo` confirma cuatro vínculos y estado DISPONIBLE. |
| Integridad del archivo | Descargar `/fotografias/{fotoId}/contenido` y `/miniatura`: 200, MIME JPEG, bytes no vacíos y decodificables. Comparar longitud del original con `tamanoBytes` y SHA-256 con `archivo.checksum_sha256`, mediante SQL de solo lectura. |
| Objetos incompletos | Antes de terminar un multipart cortado, no existe foto DISPONIBLE con ese ID ni contenido descargable. Al terminar, todo archivo DISPONIBLE tiene original/miniatura válidos; inspeccionar directorio efímero por claves SQL y temporales `.pendiente`. Comparar inventario previo/final para detectar objetos sobrantes del intento. |
| Idempotencia | Reintentos con IDs/claves constantes, misma petición JSON fijada o mismos bytes/metadatos fotográficos. Reutilizar conteos de `operacion_idempotente`, historial y resultado IC del test HU-061, acotados al escenario. Replay JSON con `Idempotent-Replay: true`; multipart por respuesta/ID/conteos. |
| Estado definitivo | Línea base y perfiles: Catedrático PUBLICADA, intención local CONFIRMADA, cuatro fotos confirmadas, ninguna cola de inspección **ni de sus fotos**. El envío ESTUDIANTE→ENVIADA ya tiene cobertura HU-061; conservar su regresión sin multiplicar todos los perfiles por roles. |

El backend **normaliza JPEG/WebP a JPEG y elimina EXIF**, según [ProcesadorImagenes](sgp-api/src/main/java/gt/usac/cunoc/sgp/archivo/service/ProcesadorImagenes.java). Por eso el hash de la descarga no se compara con el WebP/JPEG de entrada: comparar con el checksum del objeto normalizado persistido. El hash del Blob local sirve para probar conservación **local entre intentos**.

La lectura remota se hace por API real con sesión válida y consultas SQL del fixture desechable. Las consultas de comprobación pueden seguir funcionando mientras el navegador está offline, pero quedan separadas del registro de escrituras de la aplicación. No renovar cookies de Angular en paralelo desde la instrumentación: la auditoría HU-061 ya documenta esa interferencia. Capturar el token vigente como hace su test o usar sesión de verificación independiente.

Ningún `__e2e/resultados` del simulador, objeto JavaScript del navegador ni HTTP 201 por sí solo acredita CA7. No añadir endpoints Spring exclusivos de pruebas.

## 8. Estrategia de datos y aislamiento

**Preparación reutilizable:** ejecutar Flyway con código actual sobre PostGIS nuevo; bootstrap del administrador de prueba; cargar una sola vez `e2e/hu061/real-fixture.sql`. Mantener su guardia de nombre **`hu061_aceptacion`**, aunque la ejecución corresponda a HU-062: nombre de base permitido no significa base compartida. Contenedor/red/almacenamiento serán exclusivos de esa ejecución, etiquetados `hu062.aislado=true`. Verificar etiqueta, nombre, identidad de base y rutas efímeras antes de cargar o limpiar; rechazar recursos existentes que no haya creado el runner.

Proponer un solo runner de prueba con módulos estándar de Node que coordine los recursos usados en la aceptación HU-061: PostGIS `postgis/postgis:16-3.5`, backend del Dockerfile vigente, build Angular y proxy existente. Usar tmpfs o almacenamiento temporal exclusivo para DB/archivos, sin volúmenes de desarrollo, sin montar `.env`, con `spring.config.import=` y propiedades de prueba. Deshabilitar únicamente la siembra automática del formulario para cargar el fixture mínimo, como en la aceptación real. JWT y contraseña ficticios de prueba, cookie no segura solo en loopback, SMTP ficticio sin envío real; no modificar perfiles de producción.

Identificadores de inspección/fotos/claves: reutilizar `uuidV7()` y conservarlos en un manifiesto por ejecución; fixture de referencias fijo, datos constantes `vigas.existencia=PRESENTE`, `dano=MODERADO`, `cortado=NO`, fecha de inspección obtenida en **America/Guatemala**, no con recorte de fecha UTC. Registrar versión de código, navegador y perfil. No fijar el reloj de producción ni deshabilitar validaciones para acomodar los tests.

**Diferencia importante entre fixtures fotográficos:** `sgp-api/src/test/resources/archivo/imagen.webp` ya sirve para el contrato multipart real, pero la galería Angular exige EXIF GPS/fecha. Reutilizar los bytes JPEG válidos con EXIF de [metadatos-exif.integration.spec.ts](sgp-client/src/app/features/inspecciones/services/metadatos-exif.integration.spec.ts) como una pequeña fixture E2E compartida, sin copiar el parser ni simular el procesador de producción. Su fecha explícita `2026-10-14T16:20:30Z` es anterior a la fecha de esta prueba; documentarla como captura histórica. Preparar formulario/referencias online y adjuntar esa imagen por la galería en las cuatro categorías **offline**.

No hace falta crear una foto enorme para provocar U: el proxy de prueba puede dividir deliberadamente un multipart pequeño, identificar la parte `archivo`, transmitir hasta el punto elegido y cortar. Debe demostrar bytes binarios parciales efectivamente enviados al upstream, no simplemente contar bytes recibidos del navegador.

**Limpieza:** cerrar contextos/proxy, retirar exclusivamente la qdisc de P, detener/eliminar contenedores/red creados por el runner y descartar su almacenamiento temporal. Conservar fuera del contenedor únicamente evidencias. No borrar tablas ni reutilizar credenciales de desarrollo. El DELETE de borrador existente no limpia todos los recursos publicados: destruir la instancia efímera evita depender de endpoints de limpieza nuevos.

## 9. Implementación por fases pequeñas

La auditoría Fase 0 queda realizada por este documento. Las fases siguientes son trabajo futuro, sujeto a los bloqueos indicados.

| Fase | Objetivo y reutilización | Archivos previstos | Pruebas / criterio de finalización | Dependencias |
|---|---|---|---|---|
| 0 — Auditoría (esta entrega) | Identificar fuente de verdad, herramientas, mecanismos y brechas. | Solo `PLAN_IMPLEMENTACION_HU062.md`. | Rutas reales inspeccionadas; distinguir evidencia histórica, propuesta y no verificado; revisión documental y `git diff --check`. | Repositorio y solicitud adjunta. |
| 1 — Base real y offline | Reutilizar fixture SQL, login, proxy, worker y lecturas locales; hacer reproducible el entorno desechable. Crear por UI pendientes reales y línea base N/O. | Nuevos `e2e/hu062/run.mjs`, `hu062.spec.mjs`, `helpers.mjs`, fixture JPEG; modificar `playwright.config.mjs`. Extracción mínima de helpers del test HU-061 solo si evita duplicar login/lecturas/SQL. | N termina con 1 inspección, 4 fotos y cola global del escenario vacía; O conserva respuestas/fotos/cola tras recarga sin escrituras remotas. Verificación API/SQL básica desde esta fase; falta de backend falla, sin skip. Regresión dirigida de los 2 casos reales HU-061. | Node 24, Chromium compatible, Docker accesible, backend actual, fixture/bootstrap y origen real. Confirmar Decisión 6 o dejar el ADR pendiente de aprobación según autoridad aplicable. |
| 2 — Red degradada | Extender proxy para L/F y barreras observables; usar el backend aislado para P con netem. | `e2e/hu061/server.mjs`, `e2e/hu062/{run,helpers,hu062.spec}.mjs`. | L completa sin pérdida/duplicados; F demuestra reintento antes del procesamiento y replay de creación confirmada; P acredita descartes reales y recuperación. Ejecutar dos corridas independientes desde entorno limpio para demostrar repetibilidad, sin retries del runner que oculten fallos. | Fase 1. Para P: Linux, permisos de namespace/netem y filtro verificado. |
| 3 — Corte fotográfico y cierre | Añadir U/U-confirmación; completar integridad remota, evidencias y documentación. | Mismos archivos E2E/config; nuevo ADR-0008, nueva `docs/testing/HU-062.md`, enlace en `README.md` y corrección puntual/enlace de `docs/testing/HU-061.md`. | Corte con parte binaria incompleta demostrado; Blob pendiente hasta confirmación, retry con misma clave y exactamente 4 fotos finales completas. Pérdida de 201 con foto remota única. Fallo de aserción controlado deja screenshot/traza/adjuntos. Suite final sin fallos ni omitidas; documentación refleja resultados reales. | Fases 1/2, oráculos completos y autoridad documental resuelta para cerrar CA10. |

No crear una fase aparte para CA6/CA7: se comprueban desde la línea base y se reutilizan en cada perfil. Tampoco repetir la auditoría PWA, IC, autorización ni la prueba de carga inicial ya aceptadas salvo regresión causada por cambios del harness.

## 10. Archivos previstos y alcance concreto

| Archivo | Acción futura |
|---|---|
| `sgp-client/e2e/hu062/hu062.spec.mjs` | Nuevo conjunto N/O/L/F/P/U/U-confirmación; CA6/7 transversales. N/O pueden compartir preparación y observar ambos lados de la reconexión. |
| `sgp-client/e2e/hu062/helpers.mjs` | Helpers de escenario, instantáneas, oráculos y configuración de fallos. Extraer/reutilizar lógica de HU-061; evitar una capa genérica de automatización. |
| `sgp-client/e2e/hu062/run.mjs` | Preparación/health/fixture, ejecución seleccionada y limpieza por identidad; control netem acotado, logs y error de requisitos ausentes. No es un servicio de la aplicación. |
| `sgp-client/e2e/hu062/fixtures/foto-exif.jpg` | Fixture de galería derivada de bytes existentes; datos válidos y deterministas. |
| `sgp-client/e2e/hu061/server.mjs` | Mantener modo actual; añadir únicamente modo de fallos HU-062 explícito, contadores y barreras. Manejar desconexiones y destruir sockets en cortes programados; no convertirlos en el 502 normal del proxy. |
| `sgp-client/playwright.config.mjs` | Descubrir `e2e/hu062` además de HU-061 (p. ej. `testDir: './e2e'` manteniendo `**/*.spec.mjs`), capturas de fallo y evidencias. Usar fixtures `page/context` del runner en la suite nueva para que capturas/trazas se generen antes del cierre. |
| `sgp-client/e2e/hu061/hu061-real.spec.mjs` | Solo si se extraen helpers comunes: mantener comportamiento/expectativas; parametrizar nombre de contenedor SQL con default actual si es necesario. No duplicar sus casos. |
| `sgp-client/e2e/hu061/real-fixture.sql` | Reutilizar sin cambios previstos; preparar primero bootstrap y base correcta. |
| `docs/adr/0008-hu062-pruebas-e2e-red-degradada.md` | Nuevo ADR de extensión, pendiente de resolución documental y validación. |
| `docs/testing/HU-062.md` | Única guía operativa y resultados de HU-062; referenciar HU-061 y ADR. |
| `docs/testing/HU-061.md`, `README.md` | Enlace a la nueva guía y actualización mínima del límite histórico del modo real. |

No se prevén cambios en código Angular/Spring de producción, esquemas Dexie, migraciones, `package.json`, lockfile, Dockerfiles, Compose habitual ni CI. Si una prueba nueva demuestra un defecto, registrar reproducción y evidencia; su corrección se evalúa separadamente, sin presuponer refactorizaciones.

## 11. Ejecución local, CI y evidencias

### Comandos actualmente disponibles (referencia; no ejecutados aquí)

Con dependencias y navegador ya disponibles, desde `sgp-client`, bajo Node 24:

```bash
node --version
npm run build
npm run test:pwa -- --grep 'precarga, reabre offline|revalida referencias'
```

Con el entorno real **ya preparado** conforme a la auditoría HU-061 §3.R, incluida su base/contenedor y configuración de sesión del fixture:

```bash
HU061_API_ORIGIN=http://127.0.0.1:18090 npm run test:pwa -- --grep 'CA4/CA7 backend real'
```

Estos comandos existentes no preparan automáticamente PostGIS ni Spring Boot. La guía HU-061 y su auditoría contienen las piezas, pero falta un arranque automatizado completo y versionado; esa es una tarea concreta de Fase 1. No presentar un `test:pwa` sin backend, con los casos reales omitidos, como aceptación integral.

### Interfaz de ejecución propuesta (a implementar)

Desde la raíz Git, después de seleccionar Node 24 y disponer de las dependencias bloqueadas, Chromium e imagen/backend vigente:

```bash
node sgp-client/e2e/hu062/run.mjs --perfil normal-offline
node sgp-client/e2e/hu062/run.mjs --perfil latencia
node sgp-client/e2e/hu062/run.mjs --perfil fallos
node sgp-client/e2e/hu062/run.mjs --perfil paquetes
node sgp-client/e2e/hu062/run.mjs --perfil subida
node sgp-client/e2e/hu062/run.mjs --perfil todos
```

**Son comandos diseñados, no disponibles aún.** El runner deberá construir/preparar los recursos aislados si faltan, ejecutar el build/Playwright existente con selección de HU-062, propagar el código de salida y limpiar incluso en fallo. Las pruebas nuevas reciben obligatoriamente origen real y nombres creados por el runner. Cualquier invocación con backend ausente o datos fuera del entorno aislado falla de forma explícita. No usar la omisión condicional de HU-061 para la suite HU-062.

Durante la implementación, documentar la preparación de dependencias ya declaradas (`npm ci`/navegador compatible) y de imágenes del proyecto si la máquina no las tiene. Esta planificación **no instala ni actualiza nada**. Evitar depender de rutas `/tmp` históricas o binarios instalados manualmente en la sesión de HU-061.

**Evidencias:** mantener `trace: retain-on-failure`, añadir `screenshot: only-on-failure` y salida JSON con los reporteros Playwright existentes; adjuntar perfil/UUID/claves/etapas, snapshot local sin contenido sensible, conteos remotos, integridad y contadores de corte/drop. Guardar logs de Spring/proxy y datos de preparación para diagnosticar. No guardar JWT, cookies o credenciales en los adjuntos; tratar trazas con sesiones como artefactos privados. Usar `test-results/` ignorado para artefactos; la guía conserva resultados/resumen, no copias redundantes de todos los logs.

**Validación apropiada de los cambios futuros:** sintaxis de los `.mjs`, escenarios HU-062, regresión del proxy con casos HU-061 afectados y `git diff --check`. No volver a ejecutar toda la batería backend si no cambia producción; ejecutar integraciones puntuales de foto/idempotencia solo ante una discrepancia que lo requiera. Para esta entrega documental solo procede revisión de fuentes/diff.

**CI:** los jobs existentes pueden servir de antecedente para Java/Node/Docker, pero no hay job E2E que reúna todos los servicios. La ejecución sin interfaz es técnicamente planteable; P añade requisitos de kernel/privilegios que deben comprobarse en el runner elegido. Sin demostrar esa necesidad y disponibilidad, no modificar CI. La ejecución local manual mediante los comandos versionados anteriores satisface la alternativa de CA10 una vez validada. La guía debe registrar máquina, versiones, comando y resultados; un caso P omitido impide afirmar cumplimiento completo.

## 12. Riesgos, dependencias y bloqueos

| Riesgo / dependencia | Impacto y tratamiento mínimo |
|---|---|
| Historia HU-062 y Decisión 6 ausentes | No se puede certificar alineación con un mandato no disponible. Plan basado en CA1–11 adjuntos y ADR-0007; contrastar la autoridad antes de cerrar herramienta/ADR. Si exige Cypress, revisar selección y evitar dos frameworks activos sin justificación. |
| Node 18 activo | Bloquea ejecutor instalado y build Angular con ese PATH. Seleccionar Node 24; no cambiar dependencias para acomodar Node 18. |
| Entorno real no persistente y arranque no encapsulado | La aceptación previa eliminó sus contenedores. El nuevo runner debe recrear el entorno, validar health/base/bootstrap y no depender de un servicio local preexistente. |
| Backend habitual históricamente desalineado con Flyway | No usar ni reparar su base; construir código actual contra PostGIS vacío. No deshabilitar validación ni crear migraciones. |
| Confusión entre mock y servidor real | Fallar si falta origen real; ejecutar solo selección HU-062 en modo real. No correr la suite mock HU-061 con el origen real globalmente activado. |
| Permisos netem / daemon Docker no verificados | La pérdida real P puede bloquearse aunque existan binarios. Ejecutar en máquina aislada apta y conservar evidencia; no sustituirla silenciosamente por 500/abort. |
| Fotos pequeñas terminan antes del corte | Cortar por posición binaria en el proxy y demostrar multipart incompleto; no depender de tamaño/duración de máquina. |
| Galería exige EXIF | WebP backend por sí solo no prepara la UI. Reutilizar JPEG EXIF válido existente y probarlo primero en N/O. |
| Jitter, auth y timers | Esperar estados/eventos dentro del presupuesto. En HU-062 mantener TTL normal de prueba, sin copiar los 5 s exclusivos de expiración HU-061. No renovar la cookie desde dos actores concurrentes. |
| Agotamiento / ENVIANDO | Cortes programados menores a cinco intentos; si hay cierre inesperado, respetar recuperación >60 s + comprobación periódica. Un pendiente conservado en INTERVENCION no es pérdida, pero sin recuperación no completa el caso exigido. |
| Falso positivo de no duplicación o integridad | Contar conjunto del actor/puente, no solo PK; verificar fotos/objetos y checksum normalizado, no solo 201 ni listado. |
| Almacenamiento local vs S3 | Se prueba un proveedor real soportado y aislado. No afirmar certificación de S3/staging. MinIO ya está disponible si el requisito original exige ese proveedor; activarlo únicamente con esa evidencia, reutilizando su integración existente. |
| Documentación anterior al código | `docs/testing/HU-061.md` aún limita PWA a simulación y `DEVELOPMENT.md` dice que MinIO no se consume; el código ya tiene S3 y aceptación real. Basarse en implementación/auditoría actuales y corregir solo referencias relevantes a HU-062. |

**Dependencias adicionales:** ninguna librería npm/Maven ni framework E2E nuevo previsto. Se requieren las herramientas/versiones ya usadas por el proyecto, imágenes del backend/PostGIS y Chromium compatible. La comprobación literal P agrega como requisito operativo Linux/netem y permisos de namespace; `tc`/`nsenter` existen en esta máquina, su uso autorizado y efectivo no se ha probado. No se introducen servicios de producción ni infraestructura de observabilidad.

## 13. Criterios de finalización de HU-062

1. Fuente de HU-062/Decisión 6 contrastada si se obtiene, elección E2E documentada conforme a autoridad vigente y ADR trazable.
2. N/O/L/F/P/U y pérdida de confirmación fotográfica ejecutados con Spring Boot, PostGIS y almacenamiento reales aislados; ningún escenario requerido omitido ni sustituido por respuestas de negocio simuladas.
3. Inspección creada offline, respuestas y cuatro fotografías pendientes conservadas durante fallos; no hay escrituras de la aplicación recibidas durante O.
4. Cada operación conserva identidad, cuerpo efectivo y clave hasta confirmación; luego cola de inspección/fotos vacía e intención final CONFIRMADA. Reintentos observados donde se inyecta error, sin imponerlos a TCP cuando recupera transparentemente.
5. Una inspección esperada por escenario, cuatro fotografías DISPONIBLES únicas, datos/estado definitivo correctos y originales/miniaturas íntegros; ningún objeto parcial accesible ni pendiente abandonado tras recuperación.
6. P demuestra descarte de paquetes con contadores del filtro/netem. Si no se puede ejecutar, registrar bloqueo y mantener CA4 sin cierre literal.
7. Perfiles numéricos y fallos por evento reproducibles, dos corridas limpias de los perfiles degradados y sin retries del runner usados para ocultar inestabilidad; logs suficientes para comparar.
8. Un fallo controlado demuestra capturas/trazas/adjuntos útiles; regresiones HU-061 afectadas por el harness siguen pasando.
9. Guía única dentro de `docs/testing/`, enlazada desde documentación técnica, con requisitos, comandos comprobados, resultados por CA/perfil y límites CI/proveedor. Limpieza segura validada sin tocar bases compartidas.

La autonomía offline de **ocho horas (RNF-07)** queda fuera de la automatización HU-062. Su medición manual está prevista para el **domingo 25 de octubre de 2026**, según la solicitud; no se crea un test de ocho horas ni se supedita este plan a esa ejecución.

**Resultado de esta entrega:** únicamente este plan. No se ejecutaron E2E, pruebas destructivas, instalaciones, cambios CI, migraciones ni commits. La implementación comienza solo en una tarea posterior.
