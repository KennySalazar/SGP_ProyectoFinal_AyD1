# Auditoría técnica de HU-061: PWA y envío/publicación sin conexión

Fecha: 24 de octubre de 2026. Fuente funcional principal: [HUS/HU-061.md](HUS/HU-061.md). Código auditado: repositorio `SGP_ProyectoFinal_AyD1`, revisión `72a198c`, sin cambios previos visibles en `git status`.

Alcance: inspección estática de código, configuración, migraciones existentes y pruebas. No se implementaron funcionalidades, no se ejecutaron compilaciones, pruebas ni migraciones, y no se instalaron dependencias. El único archivo creado es este informe. «Satisfecho en código» no equivale a certificación en navegador o producción.

Convención de rutas: salvo `HUS/HU-061.md`, las rutas entre comillas invertidas son relativas a `SGP_ProyectoFinal_AyD1/`. Para abreviar referencias Java, **J** significa `sgp-api/src/main/java/gt/usac/cunoc/sgp/`; **JT**, `sgp-api/src/test/java/gt/usac/cunoc/sgp/`. Las referencias E01–E15 de las secciones 2 y 6 identifican evidencia concreta reutilizada en la matriz y el plan.

## 1. Resumen ejecutivo

**La arquitectura actual permite implementar HU-061 ampliando los mecanismos existentes. La historia todavía no está completa.** Ya existen Angular Service Worker, manifiesto e iconos, persistencia Dexie, cola con propietarios y claves UUID v7, captura local de borradores y fotos, diagnóstico, renovación de sesión y transiciones de negocio en Spring Boot.

Las principales brechas son: validar localmente antes de confirmar; persistir y ejecutar las intenciones de enviar/publicar después de los datos y fotografías; hacer idempotente la publicación; conservar evidencia fotográfica confirmada para validar sin red; recuperar la cola al reconectar o volver a iniciar sesión; aislar todas las lecturas locales; avisar sobre actualizaciones; y demostrar arranque offline, tamaño y rendimiento.

**Antes de ampliar la cola deben corregirse dos problemas de integración:** la superposición de la idempotencia HTTP y de servicio sobre la misma clave para creación/envío (E10), y la escritura del contexto fotográfico sobre el registro del borrador local (E12). Ambos se deducen de rutas de ejecución reales; no se reprodujeron durante esta auditoría.

La solución mínima conserva la máquina de estados y las validaciones del servidor. «Pendiente» debe describir una intención local; `ENVIADA` o `PUBLICADA` solo se mostrarán tras confirmación del backend. No se necesitan otro motor de sincronización, un service worker propio, una nueva base de datos ni una biblioteca de validación adicional.

HU-061 declara expresamente que amplía la restricción de conexión del Sprint 3. Por ello, el requisito anterior de HU-042 no impide esta ampliación. Sí deben aclararse la caché de `index.html`, los datos privados en caché y el alcance de las dependencias HU-057 a HU-060.

## 2. Arquitectura existente relacionada con la historia

### E01. Organización y tecnología verificadas

- Frontend: Angular 21, componentes standalone, Signals, Reactive Forms, RxJS, PrimeNG, Transloco, `@angular/service-worker` y Dexie 4 declarados en `sgp-client/package.json`. Rutas de features con carga diferida en `sgp-client/src/app/app.routes.ts` y `features/inspecciones/inspecciones.routes.ts` dentro de `sgp-client/src/app/`.
- Backend: Java 21 y Spring Boot 3.3.13, Spring Security, JPA, PostgreSQL/PostGIS, Flyway, MapStruct y NetworkNT JSON Schema en `sgp-api/pom.xml`. Arquitectura por dominios `inspeccion`, `archivo`, `revision`, `sync`, `usuario`, etc.; flujo controller → service → repository.
- `AGENTS.md` y `docs/ARCHITECTURE.md` exigen utilizar `offline/` para persistencia de features, mantener HTTP en servicios y evitar nuevas capas y duplicaciones. Existe una excepción actual: `features/salida/services/salida-local.service.ts` accede a Dexie directamente; conviene reutilizar su lógica acotada, sin reorganizar la feature por esta historia.

Flujo existente y punto de ampliación:

```text
CompletarInspeccionPage / ReporteFotograficoComponent
  ├─ InspeccionesLocalesService / FotografiasLocalesService → Dexie
  └─ InspeccionApiService → interceptores → /api/v1

AppShellComponent → SincronizacionService
  → renovar sesión y /auth/me
  → CREAR_INSPECCION → GUARDAR_BORRADOR → FOTO
  → [faltan ENVIAR_INSPECCION y PUBLICAR_INSPECCION]

API → servicios de inspección / transición / fotografía
  → PostgreSQL + almacenamiento local o S3/MinIO
  → publicación: historial + IC + puente en una transacción
```

### E02. PWA, arranque y actualizaciones

Evidencia: `sgp-client/angular.json`, `sgp-client/src/app/app.config.ts`, `sgp-client/ngsw-config.json`, `sgp-client/public/manifest.webmanifest`, `sgp-client/src/index.html`, `sgp-client/nginx.conf`.

- El build declara `serviceWorker: "ngsw-config.json"`. `provideServiceWorker('ngsw-worker.js')` se activa fuera de modo desarrollo, con `registerWhenStable:30000`.
- El manifiesto declara `display: standalone`, `scope` y `start_url` en `/`, idioma y dos iconos. Se localizaron `public/icons/icon-192.png` y `icon-512.png`. La configuración permite intentar instalación; no demuestra por sí sola que un navegador haya instalado y controlado la aplicación.
- El grupo `app` precarga `index.html`, manifiesto, CSS y JS de raíz. Esto alcanza también chunks de features emitidos en raíz. Los recursos `/i18n/**` e iconos son `lazy`: pueden faltar en una apertura offline si no se descargaron antes. El patrón `/favicon.ico` no corresponde al favicon PNG usado por `src/index.html`.
- Nginx sirve `index.html`, `ngsw.json`, workers y manifiesto con `Cache-Control: no-store, no-cache, must-revalidate`; los assets reciben caché duradera. El worker incluye `index.html` en su caché propia: la cabecera HTTP no convierte esa caché en inexistente.
- La búsqueda en `sgp-client/src` no localizó `SwUpdate`, `versionUpdates`, `beforeinstallprompt` ni `appinstalled`. Falta el aviso de nueva versión. Un botón propio de instalación no es un criterio explícito; no debe añadirse como obligación si basta la instalación del navegador.
- `infra/production/https/nginx-https.conf.template` configura TLS y redirección HTTPS. Se verificó la plantilla, no el despliegue real.

### E03. Estrategia de caché y datos de referencia

Evidencia: `sgp-client/ngsw-config.json`; `sgp-client/src/app/features/formulario/services/formulario-api.service.ts`; controladores **J**`formulario/controller/VersionFormularioController.java`, **J**`puente/controller/CatalogoTerritorialController.java`, **J**`puente/controller/AsignacionPuenteController.java`; `sgp-client/src/app/features/salida/services/salida-api.service.ts`.

- El único `dataGroup`, `reference-data`, usa `freshness`, espera `4s`, conserva hasta 50 entradas durante 6 horas. Es preferencia por red con respaldo de caché; no entrega normalmente el dato anterior de inmediato mientras revalida, como exige HU-061.
- Sus patrones son `/api/v1/puentes/publico/**` y `/api/v1/formularios/activo/**`. No se localizó un controlador `/puentes/publico`; los controladores reales usan `/api/v1/puentes`. Debe probarse también la coincidencia del patrón de formulario con la URL exacta `/api/v1/formularios/activo`, sin asumirla.
- No están cubiertos los catálogos `/api/v1/catalogos/departamentos` y `/{...}/municipios`, ni `/api/v1/asignaciones-puentes/mis-puentes`. La preparación utiliza `/api/v1/preparaciones-salida` y descarga otro paquete a Dexie.
- No hay patrones de caché de datos para `/api/v1/inspecciones/**`: se conserva la separación correcta entre worker y sincronización. No ampliar el caché a todo `/api/v1/**`.
- La versión instalada de Angular admite `freshness` con timeout y conserva la actualización de red después de recurrir a caché. Esto se comprobó leyendo `sgp-client/node_modules/@angular/service-worker/config/schema.json` y `ngsw-worker.js`, método `handleFetchWithFreshness`. Una configuración `freshness` + `timeout: "0u"` es una candidata mínima para el comportamiento requerido, que debe verificarse en navegador; no exige otro worker.

**Límite de seguridad:** `mis-puentes` depende del usuario autenticado; una caché común por URL no garantiza aislamiento entre cuentas. No basta con agregar esa URL al `dataGroup`. Debe acordarse si su estrategia puede ejecutarse en la capa existente de preparación por propietario, o definir un mecanismo verificable que separe la caché del worker por cuenta antes de incluirla. Véase sección 10.

### E04. Dexie y almacenamiento persistente

Evidencia: `sgp-client/src/app/offline/db/sgp-db.ts`; servicios `inspecciones-locales.service.ts`, `fotografias-locales.service.ts`, `storage.service.ts` y `uuid-v7.ts` en `sgp-client/src/app/offline/services/`.

- Base `sgp_offline`, versiones Dexie 1–3. Tablas: puentes, inspecciones, fotos con `Blob`, esquemas, `cola_sync`, notificaciones y preparación de salida. Los campos de propietario de inspecciones, fotos y esquemas son opcionales; la cola tiene índice por propietario y estado.
- `SyncQueueItem.operacion` solo admite `CREAR_INSPECCION` y `GUARDAR_BORRADOR`. Las fotos se distinguen por `tipo: FOTO`. Los estados operativos existentes son `PENDIENTE`, `ENVIANDO`, `INTERVENCION`.
- `guardarBorradorLocal()` persiste borrador y operación dentro de una transacción Dexie. `iniciarInspeccionLocal()` hace lo mismo para el inicio. Se generan claves UUID v7 y marcas de creación ordenadas.
- `confirmarGuardado()` conserva los datos de una edición posterior encolada mientras incorpora la respuesta del guardado anterior. Esta lógica merece conservarse y probarse, no reemplazarse por un `put` genérico.
- `FotografiasLocalesService.guardar()` persiste blob, metadatos, propietario y clave junto con la operación. `confirmar()` elimina blob y operación después de éxito, comprobando propietario.
- `StorageService` ya ofrece `navigator.storage.persist()` y `estimate()`. La preparación solicita persistencia y expone advertencias; no puede garantizar que todo navegador la conceda.
- `liberarInspeccionesSincronizadas()` excluye inspecciones no confirmadas, con operaciones o con fotos pendientes. No interpreta todavía futuras intenciones finales ni sus errores.

No hace falta una migración SQL para agregar tipos y campos locales. Dexie es flexible: un campo no indexado no exige por sí solo aumentar su versión. Solo agregar una versión Dexie si realmente se necesita cambiar índices, conservando las versiones existentes.

### E05. Sincronización y observabilidad actuales

Evidencia: `sgp-client/src/app/offline/services/sincronizacion.service.ts`; `sgp-client/src/app/layouts/app-shell/app-shell.component.{ts,html}`; `sgp-client/src/app/offline/pages/diagnostics/diagnostics.page.{ts,html}`; `sgp-client/src/app/core/interceptors/offline.interceptor.ts`.

- `AppShellComponent` llama a `iniciar()`. Se dispara sincronización al abrir con red y en el evento `online`. Cada corrida renueva credenciales y carga `/auth/me`, luego filtra por propietario.
- Procesa primero operaciones de inspección ordenadas por `creadoEn`, después fotos secuenciales. Si sigue existiendo una operación de inspección tras procesarla, interrumpe ese lote. Las fotos esperan si hay cualquier operación de tipo `INSPECCION` sobre su inspección.
- Reintenta errores de red y 5xx hasta cinco intentos, con espera creciente y jitter entre intentos. Los demás errores quedan en `INTERVENCION`, con `code` y texto. `409 operacion_en_proceso` se trata actualmente como intervención, aunque otra capa ya lo reconoce como resultado incierto.
- El bloqueo `ejecutando` solo vive en memoria de una instancia. No coordina pestañas, el guardado HTTP de la página ni la subida directa de fotos del componente.
- `recuperarInterrumpidas()` solo se llama desde el inicio con red y utiliza el propietario conocido en ese momento. No se invoca al reconectar después de iniciar offline ni al cambiar de sesión. El singleton conserva `iniciado = true`; un nuevo login puede no provocar corrida ni recuperar `ENVIANDO`.
- La cola no se drena por el simple hecho de recibir un nuevo registro después del arranque; requiere evento de red o sincronización manual. Esto importa cuando una operación falla con el navegador todavía marcado online.
- El shell muestra pendientes y enlaza `/offline`. Cuenta operaciones `INSPECCION`, no inspecciones distintas: crear más guardar puede contar dos veces una sola inspección. Con cero pendientes dice «Todo sincronizado» incluso sin red.
- Diagnóstico tiene reintento y liberación de espacio, pero no describe la acción específica ni mantiene un resultado final de envío/publicación. `offlineInterceptor` es un pasamanos: `next(req)`. La funcionalidad offline real reside en páginas/servicios; no es correcto atribuírsela al interceptor.

### E06. Preparación, edición y validación de campos

Evidencia: `sgp-client/src/app/features/salida/services/{preparacion-salida,salida-local}.service.ts`; `sgp-client/src/app/features/inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component.ts`; `sgp-client/src/app/features/inspecciones/pages/completar-inspeccion/completar-inspeccion.page.ts`; `sgp-client/src/app/features/formulario/components/formulario-dinamico/formulario-dinamico.component.ts`; `sgp-client/src/app/features/formulario/motor/formulario/{constructor,validadores,avance,valor}.ts`.

- La preparación valida el propietario del paquete, guarda formulario, puentes y referencias en una transacción y preserva trabajo pendiente al quitar datos. Sus claves son `salida:<usuario>:<id>` y conservan `origenId` con el ID real del servidor.
- La página de completar guarda cambios localmente y tiene autoguardado con debounce de 1,2 s. Para operaciones online persiste una operación antes de llamar a la API. El guardado inmediato en caché online usa además `actualizacionLocalPendiente`.
- `FormularioDinamicoComponent.revisar()` ya muestra errores, revisa los controles y navega al primer pendiente. `avanceTotal` y los validadores contemplan obligatoriedad, visibilidad, condiciones, grupos opcionales y arreglos. No se debe construir otro validador de formularios para HU-061.
- `abrirConfirmacion()` y `confirmarAccion()` no llaman a `revisar()`, no consultan mínimos fotográficos y no comprueban una escritura Dexie pendiente de finalizar. La validación completa se delega actualmente al backend.
- El progreso inicial local tiene valores fijos; no debe usarse como evidencia de completitud. La validación debe salir del motor real y del inventario de evidencia, con el esquema de la inspección.

**Integración de preparación por verificar:** `obtenerEsquemaActivoLocal()` y `obtenerPuentesPreparados()` no filtran propietario. El diálogo usa `esquemaActivo.id`, que puede ser la clave compuesta de preparación, como `versionFormularioId`; la selección offline de puentes también usa `p.id`. Además, `IniciarInspeccionRequest` no transporta la versión preparada y `InspeccionService.crearNuevaInspeccion()` utiliza la activa al sincronizar. Existe `/inspecciones/iniciar` con versión explícita, pero su DTO no acepta el UUID de inspección del cliente. No son sustitutos intercambiables sin resolver identidad/versionado. Son riesgos de dependencias de HU-061, no justificación para rehacer toda la preparación.

### E07. Fotografías y evidencia mínima

Evidencia frontend: `sgp-client/src/app/features/inspecciones/components/reporte-fotografico/reporte-fotografico.component.{ts,html}`; `sgp-client/src/app/features/inspecciones/models/fotografia.models.ts`; `sgp-client/src/app/features/inspecciones/services/procesador-fotografias.service.ts`; E04.

- Se reutilizan captura de cámara, galería, GPS/EXIF, compresión y categorías `ACCESO`, `SUPERESTRUCTURA`, `SUBESTRUCTURA`, `CAUCE`, `OTRA`. La interfaz todavía anuncia que captura/subida requieren conexión hasta Sprint 4, aunque el código persiste fotos antes de subirlas.
- `faltantes` y `conteos` solo consideran `confirmadas()` en memoria. Las fotos pendientes en Dexie no cuentan. Al reabrir, las confirmadas se consultan por HTTP; no se localizó un inventario persistente de fotos confirmadas para validación offline.
- Adjuntar llama directamente a HTTP incluso offline y para una inspección cuya creación todavía puede estar pendiente. También puede competir con el sincronizador. Debe conservarse la captura/procesamiento y coordinar el envío con la cola existente.
- El límite visual de 60 considera solo confirmadas, no la suma deduplicada de confirmadas y pendientes.

Evidencia backend: **J**`inspeccion/service/ReglasEvidenciaFotografica.java`, **J**`inspeccion/service/FotografiaService.java`, **J**`inspeccion/controller/FotografiaController.java`, **J**`inspeccion/repository/InspeccionRepository.java`, **J**`archivo/service/{InspectorImagenesImageIo,ProcesadorImagenes,RecuperacionCargasService}.java`.

- El servidor exige al menos una foto disponible de cada una de las cuatro categorías obligatorias. `OTRA` no sustituye ninguna. Los conteos excluyen archivos no `DISPONIBLE`; el máximo es 60.
- Valida propietario, estado BORRADOR, anclaje, UUID v7, GPS, fecha, tamaño y contenido real JPEG/WebP. Normaliza a JPEG sin EXIF, con miniatura. No confiar solo en extensión, MIME o validación del cliente.
- Las cargas tienen idempotencia de servicio, checksum, reserva duradera y compensación de objetos; `RecuperacionCargasService` recupera intentos vencidos. Este mecanismo no debe reemplazarse por el filtro HTTP JSON.
- Lectura mediante API autorizada, respuestas de imagen `private, no-store` y `nosniff`. La publicación permite consulta a cuentas autenticadas según las reglas actuales. No se necesita abrir el bucket ni cachear fotografías privadas en el worker.

### E08. Envío, publicación e IC

Evidencia: `sgp-client/src/app/features/inspecciones/services/inspeccion-api.service.ts`; página de completar y su HTML; **J**`inspeccion/controller/TransicionInspeccionController.java`; **J**`inspeccion/service/{TransicionInspeccionService,MaquinaEstadosInspeccion,PublicacionIndiceCondicionService,ConfirmacionEstadoIcService}.java`.

- El botón final está deshabilitado con `esOffline()`. `abrirConfirmacion()` también exige red y no ser solo local. `confirmarAccion()` hace HTTP directamente. No existe encolado de estas acciones.
- El rol capturado al crear determina la acción: estudiante envía, catedrático/profesional externo publica. El backend verifica también rol actual, autoría y colegiado cuando aplica.
- `POST /{id}/enviar` y `/reenviar` exigen estudiante. `POST /{id}/publicar` exige catedrático o profesional externo; el estudiante no publica directamente. No ampliar HU-061 al veredicto académico de revisiones.
- `exigirCompleta()` valida el esquema de la versión y las fotos; devuelve 422 detallado con campos y categorías. La transición registra historial y auditoría. Publicar bloquea puente e inspección, pasa por ENVIADA y PUBLICADA y calcula IC/actualiza puente dentro de la misma transacción.
- El evento `InspeccionPublicadaEvent` se consume por **J**`mantenimiento/service/PropuestasAlPublicarListener.java` después del commit. Un reintento no debe duplicar historial, resultados, avisos ni eventos.
- El envío usa `OperacionIdempotenteService` en frontend; publicación usa `http.post` sin clave. El filtro backend tampoco protege `/publicar`. Un reintento tras respuesta perdida puede encontrar estado terminal y devolver 409, aunque la publicación original haya funcionado.
- `ConfirmacionEstadoIcComponent` ya muestra `icPreliminarOffline` y evita consultar offline. La fórmula está en Java. Sin embargo, el HTML de completar puede mostrar un IC preliminar cacheado y el componente de confirmación conserva su cálculo al perder red; su `guardar()` no comprueba `esOffline()`. El criterio de mensaje offline necesita un comportamiento consistente.
- Si no existe confirmación explícita del IC, `PublicacionIndiceCondicionService.resolverDecision()` usa el estado calculado. Si existe una confirmación obsoleta, rechaza con `confirmacion_ic_desactualizada`. No es correcto afirmar que siempre se exige confirmación manual; sí hay que resolver este rechazo en la cola.

### E09. Sesiones, autorización y errores

Evidencia: `sgp-client/src/app/core/services/auth.store.ts` y `sgp-client/src/app/core/services/{session-bootstrap,token-refresh,api-error}.service.ts`; `sgp-client/src/app/core/interceptors/{auth,problem}.interceptor.ts`; `sgp-client/src/app/core/guards/auth.guard.ts`; shell; **J**`common/security/JwtAuthenticationFilter.java`; **J**`common/config/SecurityConfiguration.java`; **J**`usuario/controller/AuthController.java`; **J**`common/exception/ApiExceptionHandler.java`.

- El access token vive en memoria. El perfil offline se guarda en `sessionStorage`, bajo `sgp.offline.user`; incluye ID, email, rol y estados. No contiene el access token. El refresh token se entrega mediante cookie HttpOnly, con Secure/SameSite configurables.
- `SessionBootstrapService` preserva el perfil ante errores de red/5xx; lo borra ante rechazos definitivos. `authGuard` acepta el perfil conocido para navegación offline. Esto no autoriza una escritura remota: el backend valida JWT, versión de token, cuenta activa/verificada/activada y rol en cada solicitud.
- `authInterceptor` renueva una vez ante 401 si había token. El sincronizador también renueva y consulta `/me` antes de procesar la cola. Deben reutilizarse ambos, sin almacenar tokens en Dexie/localStorage ni en los elementos de cola.
- Logout limpia sesión y navega a login, sin borrar Dexie. Los datos se conservan, pero la reanudación automática al nuevo login tiene el hueco descrito en E05. `sessionStorage` no garantiza continuidad del perfil tras cerrar completamente el navegador/PWA o reiniciar el dispositivo.
- Backend y frontend ya manejan `application/problem+json`, `code`, `detail` y errores de campos. El diagnóstico conserva texto de error, pero una futura acción final necesita además resultado persistente por inspección y reapertura segura tras rechazo.
- CORS permite `Authorization`, `Content-Type`, `Idempotency-Key`, `If-Match`; no permite `X-Sync-Conflict-Policy`, que el sincronizador usa. El proxy del mismo origen evita ese preflight en producción prevista; un despliegue separado puede fallar. No cambiar CORS salvo que ese despliegue forme parte del alcance acordado.

### Dependencias reales frontend/backend

| Operación | Consumidor existente | Contrato y condición del backend | Impacto mínimo |
|---|---|---|---|
| Preparar salida | `features/salida/services/salida-api.service.ts` | `POST /api/v1/preparaciones-salida`, propietario y asignaciones vigentes | Reutilizar paquete y persistencia; verificar IDs reales/versiones. |
| Formulario | `features/formulario/services/formulario-api.service.ts` | `GET /api/v1/formularios/activo`, `/{id}/esquema`, autenticados | Reutilizar esquema congelado para validar; ajustar referencias de caché. |
| Crear inspección | `InspeccionApiService.iniciarInspeccion()` | `POST /api/v1/inspecciones`, UUID de cliente; versión activa | Corregir superposición idempotente y acordar conservación de versión preparada. |
| Guardar borrador | `InspeccionApiService.guardarBorrador()` | `PUT /{id}/borrador`, versión optimista; admite `client-wins` con operación HTTP | Conservar confirmación/ediciones posteriores y fijar la petición de cada reintento. |
| Cargar foto | `adjuntarFotografia()` | `POST /{id}/fotografias`, multipart, clave obligatoria, BORRADOR propio | Reutilizar carga y confirmación; esperar creación/datos antes de intentarla. |
| Enviar/reenviar | `enviar()` / `reenviar()` | `POST /{id}/enviar` o `/reenviar`, clave opcional, estudiante autor, requisitos | Incorporar intención persistente, manteniendo validaciones y ciclo de revisión. |
| Publicar | `publicar()` | `POST /{id}/publicar`, sin idempotencia actual, autor autorizado | Añadir clave al contrato HTTP existente y proteger la respuesta con el mecanismo acordado. |
| IC/confirmación | `obtenerConfirmacionEstado()` / `confirmarEstado()` | `GET/PUT /{id}/confirmacion-estado`, hash y justificación | No calcular offline; rechazos por confirmación obsoleta requieren intervención. |
| Recuperar sesión | `TokenRefreshService`, `AuthStore.loadMe()` | `POST /auth/refresh`, `GET /auth/me` | Reutilizar y disparar reanudación del propietario que acaba de autenticarse. |

Rutas de inspección de la tabla bajo `/api/v1/inspecciones`. Los nombres frontend sin prefijo corresponden a `sgp-client/src/app/features/inspecciones/services/inspeccion-api.service.ts`; las rutas de preparación/formulario se ubican también dentro de `sgp-client/src/app/`. No se requieren endpoints `/sync/enviar` ni `/sync/publicar`.

## 3. Matriz de cumplimiento de criterios de aceptación

Estados: **Satisfecho en código** = implementación localizada para esa parte; **Parcial** = base existente con brechas; **Pendiente** = no se localizó el mecanismo requerido. Las mediciones sin ejecución se señalan explícitamente.

| Criterio de HU-061 | Estado | Evidencia y brecha |
|---|---|---|
| Instalable y arranque sin red | Parcial | Manifiesto, iconos y worker configurados (E02). Falta demostrar instalación/control, traducciones disponibles y navegación útil tras reinicio sin sesión en memoria (E09). |
| App shell precargado | Satisfecho en código | `ngsw-config.json`, grupo `app`, `installMode: prefetch` (E02). Confirmar el manifiesto generado y primera visita offline como prueba de aceptación. |
| Referencias con stale-while-revalidate | Pendiente | `freshness` con espera de 4 s y URLs incompletas; sin catálogos/asignaciones. Aislamiento de referencias privadas aún por decidir (E03). |
| Datos de inspección fuera del worker | Satisfecho en código | No hay `dataGroup` de inspecciones; Dexie/sincronización realizan la persistencia (E03–E05). |
| Campos y fotos se validan antes de encolar | Parcial | Motor `revisar()` y reglas/categorías existen, pero no están conectados a la acción final; faltan conteo local combinado e inventario confirmado persistente (E06–E08). |
| Enviar/publicar offline con clave y estado pendiente | Pendiente | Acciones deshabilitadas offline; tipos de operación ausentes; publicar sin clave (E04, E08). |
| Ejecutar la acción al reconectar | Pendiente para acciones finales | La infraestructura sincroniza borradores/fotos; faltan operaciones finales, barrera de dependencias y recuperación completa (E05). |
| Rechazo posterior: motivo y edición sin pérdida | Pendiente para acciones finales | Hay `INTERVENCION` y errores 422, pero no intención final persistente, desbloqueo ni reconciliación con el estado canónico (E05, E08, E12). |
| IC «disponible al sincronizar», fórmula solo backend | Parcial | Texto y restricción de consulta existen; cálculo Java reutilizable. Vistas pueden seguir mostrando cálculo previo al quedar offline (E08). |
| Logout/expiración conserva y reanuda al login | Parcial | Dexie no se borra y hay refresh. Reanudación por cambio de sesión y apertura tras cierre completo no garantizadas (E05, E09). |
| Otro usuario no ve ni sincroniza pendientes ajenos | Parcial, con riesgo alto | Cola/listados filtran propietario; lectura local por ID y lecturas de referencias no siempre lo hacen (E09, E13). |
| `index.html` sin caché | Parcial / requiere aclaración | Nginx evita caché HTTP, pero NGSW lo precarga para arranque offline. Cumplimiento depende de qué caché prohíbe el criterio (E02). |
| Aviso de nueva versión | Pendiente | No se localizó integración `SwUpdate` (E02). |
| Paquete inicial < 500 KB comprimidos | Parcial, medición pendiente | `angular.json` define máximo inicial de 500 kB y advertencia de 450 kB, pero ese presupuesto no demuestra tamaño comprimido. No se ejecutó build ni medición de transferencia. |
| Carga inicial 3G < 5 s | Pendiente de verificación | No se localizaron pruebas o resultados reproducibles de esta métrica. Deben precisarse dispositivo, perfil y punto de carga. |
| Pruebas SW, validación, cola final, rechazo y arranque offline | Parcial | Hay pruebas de Dexie, sesión, fotografías, formularios e idempotencia; faltan escenarios específicos de HU-061 y ejecución con worker real (sección 9). |

## 4. Componentes y servicios reutilizables

La reutilización sin modificaciones se refiere a su responsabilidad actual; no certifica el flujo completo donde se integran.

| Implementación y ruta | Reutilización propuesta | Tratamiento |
|---|---|---|
| `sgp-client/src/app/features/formulario/components/formulario-dinamico/formulario-dinamico.component.ts`, `revisar()` | Mostrar/focalizar campos pendientes antes de confirmar | Usar método existente sin cambiar el motor. |
| `sgp-client/src/app/features/formulario/motor/formulario/{constructor,validadores,avance,valor}.ts` | Obligatorios, condicionales, arreglos y valor normalizado | Sin nueva biblioteca ni reglas paralelas. |
| `sgp-client/src/app/offline/services/uuid-v7.ts` | IDs locales y claves de operación | Sin cambios previstos. |
| `sgp-client/src/app/offline/services/storage.service.ts` | Persistencia y cuota | Sin cambios previstos. |
| `sgp-client/src/app/features/inspecciones/services/procesador-fotografias.service.ts` | Captura, GPS/EXIF, JPEG y compresión | Reutilizar; no cambiar formato ni tratamiento por esta historia. |
| `sgp-client/src/app/core/services/token-refresh.service.ts` y `core/interceptors/auth.interceptor.ts` | Renovación compartida de token y un reintento 401 | Reutilizar; conectar reanudación fuera de esta lógica. |
| `sgp-client/src/app/core/services/api-error.service.ts`, `core/interceptors/problem.interceptor.ts` | Normalizar RFC 7807 y mostrar motivo | Reutilizar contrato; conservar también error por intención final. |
| **J**`inspeccion/service/MaquinaEstadosInspeccion.java` | Transiciones autorizadas | Sin nuevos estados de servidor para «pendiente». |
| **J**`inspeccion/service/ReglasEvidenciaFotografica.java` y **J**`formulario/validation/JsonSchemaValidatorService.java` | Validación definitiva al ejecutar enviar/publicar | Mantener reglas; el cliente hace prevalidación del mismo contrato. |
| **J**`inspeccion/service/PublicacionIndiceCondicionService.java` y `CalculoIndiceCondicionService.java` | IC, confirmaciones, puente y publicación atómica | Sin portar fórmula a Angular. |
| **J**`inspeccion/service/FotografiaService.java`, **J**`archivo/service/{CargasImagenService,ProcesadorImagenes,RecuperacionCargasService}.java` | Cargas seguras, idempotencia de archivos y recuperación | Mantener el circuito existente. |
| **J**`common/security/JwtAuthenticationFilter.java` y **J**`usuario/controller/AuthController.java` | Sesión remota y validación de cuenta vigente | No sustituir por autorización basada en perfil cacheado. |

**Mecanismos que se amplían:** `SgpDatabase`, `InspeccionesLocalesService`, `FotografiasLocalesService`, `SincronizacionService`, `InspeccionApiService`, `OperacionIdempotenteService` del cliente, página de completar, reporte de fotos, shell y diagnóstico. En backend se armoniza la integración de idempotencia existente y se agrega `/publicar` a la protección acordada. La preparación necesita ajustes puntuales solo donde su contrato impida el flujo auditado.

## 5. Funcionalidades faltantes

1. Prevalidación final mediante `FormularioDinamicoComponent.revisar()` y evidencia por categorías, usando la última versión de datos realmente persistida. No usar porcentaje de avance ni solo `form.valid` para reemplazar toda la revisión existente (E06).
2. Inventario local por propietario e inspección de fotos ya confirmadas, con ID/categoría y actualización tras carga, listado y retirada. Puede conservarse como metadatos en el registro local existente; no es necesario descargar otra copia de cada blob confirmado (E07).
3. Intenciones persistentes `ENVIAR_INSPECCION` y `PUBLICAR_INSPECCION`, clave estable, estado pendiente visible y prevención de doble confirmación. El dato de negocio sigue siendo BORRADOR hasta respuesta del servidor (E04, E08).
4. Orden por inspección: crear → guardar datos → confirmar fotos → acción final. La condición actual que bloquea fotos ante cualquier operación `INSPECCION` debe distinguir creación/guardado de una intención final; de lo contrario esta última bloquearía las fotos que necesita (E05).
5. Idempotencia de publicación y armonización de creación/envío, con replay de status/cuerpo y sin repetir efectos de negocio (E08, E10).
6. Rechazo final persistente con motivo, recuperación de edición y datos conservados; distinguir rechazo definitivo de red/5xx/401 y de operaciones aún en proceso. Tras corregir un rechazo definitivo, nueva intención con nueva clave (E09–E11).
7. Recuperación de operaciones interrumpidas y disparo de sincronización al restablecer red, validar login del propietario y agregar una operación con red disponible; coordinación entre ejecutores (E05).
8. Aislamiento obligatorio de lecturas, escrituras y actualizaciones asíncronas locales por propietario, incluidos accesos por URL/ID y referencias (E13).
9. Caché de referencias acorde a HU-061, aviso `SwUpdate` y recursos imprescindibles precargados; tratamiento seguro del perfil offline tras reinicio (E02, E03, E09).
10. Pruebas específicas con worker de producción, presupuesto comprimido y métrica 3G reproducible (sección 9).

## 6. Riesgos técnicos y de seguridad

### E10. Superposición de idempotencia HTTP y de dominio — prioridad crítica

Rutas: **J**`sync/http/IdempotenciaHttpFilter.java`; **J**`sync/service/{IdempotenciaHttpService,OperacionIdempotenteService}.java`; **J**`sync/repository/OperacionHttpRepository.java`; **J**`sync/config/IdempotenciaHttpConfiguration.java`; **J**`inspeccion/controller/{InspeccionController,TransicionInspeccionController}.java`; **J**`inspeccion/service/{InspeccionService,TransicionInspeccionService}.java`; `sgp-api/src/main/resources/db/migration/V11__foro_notificaciones_y_sincronizacion.sql`.

El filtro reserva `(usuario, clave)` como entidad `http_hu043` antes de llamar al controlador. Creación y envío vuelven a pasar esa misma clave al servicio de dominio, que busca en la misma tabla y exige otra entidad y otra huella. La restricción única es por usuario/clave, no por mecanismo. Para esas rutas, la reserva HTTP puede provocar `idempotencia_solicitud_distinta` al entrar al servicio. En `/reenviar`, además, la solicitud interna usa la ruta `/enviar`.

**Deducción estática de integración, no fallo reproducido.** La prueba **JT**`integration/IdempotenciaHttpIntegrationTest.java` ejercita el filtro contra una cadena simulada con un efecto SQL auxiliar; no prueba esa cadena contra los controladores/servicios reales. Las pruebas de servicio aisladas tampoco descartan el conflicto.

Corrección mínima propuesta: conservar la idempotencia HTTP como autoridad para los endpoints JSON cubiertos y evitar que se vuelva a reservar la misma operación desde el servicio cuando ya está protegida por ese filtro. Hacer explícita esa coordinación, sin debilitar los consumidores que invocan el servicio fuera de HTTP. Mantener idempotencia de servicio para cargas externas multipart. Incorporar publicación al mismo circuito JSON, sin crear un tercer mecanismo ni una tabla nueva. Una prueba integrada debe demostrar la decisión antes de ampliar el cliente.

### E11. Clave estable con petición mutable, errores memorizados y reservas abandonadas — prioridad alta

Rutas: sincronizador, método `procesar()`; **J**`sync/http/IdempotenciaHttpFilter.java`; **J**`sync/service/IdempotenciaHttpService.java`; **J**`sync/service/LimpiezaIdempotenciaService.java`; **J**`sync/repository/OperacionIdempotenteRepository.java`.

- Al guardar desde la cola, la versión de la petición se reemplaza por la del borrador local en cada intento, conservando clave. Si esa versión cambia entre un envío y su repetición, cambia la huella y el servidor puede rechazar el replay. Debe fijarse y persistirse la petición efectiva antes del primer intento; nunca mutarla mientras el resultado sea incierto. La operación siguiente puede adquirir la nueva versión una vez confirmada la anterior.
- El filtro recuerda respuestas 4xx. Corregir datos/fotos y pulsar reintento con la misma clave reproduce el rechazo; no vuelve a validar. Reintentar el mismo intento incierto conserva clave, pero una nueva acción corregida necesita otra. El botón genérico actual no hace esta distinción.
- `operacion_en_proceso` devuelve 409 y `Retry-After`; la cola lo manda a intervención. Debe tratarse como resultado pendiente, con espera acotada, sin nueva clave ni duplicación de intención.
- Una caída de proceso tras reservar HTTP puede dejar `EN_PROCESO`. La reserva no se recupera al detectar su expiración desde `reservar()`; la limpieza diaria elimina reservas HTTP vencidas. Si se exige recuperación inmediata tras caída del servidor, hace falta una recuperación acotada compatible con las transacciones existentes. El recuperador de imágenes no administra manifiestos HTTP.
- Las claves completadas caducan a los siete días y hay limpieza. Definir qué sucede con dispositivos desconectados más tiempo: no prometer idempotencia ilimitada. Creación cuenta además con UUID/huella; una transición terminal requiere reconciliación, no crear otra publicación.

### E12. Sobrescritura del borrador desde el reporte fotográfico — prioridad crítica

Rutas: `sgp-client/src/app/offline/services/fotografias-locales.service.ts`, `guardarContexto()`; `sgp-client/src/app/features/inspecciones/components/reporte-fotografico/reporte-fotografico.component.ts`, `recargar()`; `sgp-client/src/app/offline/services/inspecciones-locales.service.ts`, `guardarBorradorEnCache()` y `liberarInspeccionesSincronizadas()`.

Al cargar el reporte se guarda el contexto con un `put` completo sobre `inspecciones`, `sincronizado: true` y un payload directo. Puede reemplazar el payload envuelto del borrador, su versión o datos pendientes y omitir `actualizacionLocalPendiente`, sin coordinarse con la cola. Ese contexto puede proceder de una señal anterior al autoguardado.

También `guardarBorradorEnCache()` calcula `sincronizado` solo por existencia de cola; puede resultar true con `actualizacionLocalPendiente: true`. La liberación de espacio no consulta ese último campo. Una edición aún no encolada podría quedar elegible para borrar.

Corrección acotada: integrar contexto/inventario con el registro existente mediante transacción y combinación de campos, preservando datos, versión, propietario y marcas pendientes; nunca convertir una edición pendiente en confirmada por guardar contexto. `liberar espacio` debe excluir escrituras locales pendientes e intenciones finales no resueltas. No introducir otro repositorio de borradores.

### E13. Aislamiento local incompleto — prioridad alta

Rutas: `InspeccionesLocalesService.obtenerInspeccionLocal()`, `obtenerEsquemaActivoLocal()`, `obtenerPuentesPreparados()`, `obtenerDatosPermanentesLocal()`; `CompletarInspeccionPage.cargarBorradorDesdeLocal()`; `MisInspeccionesPage.buscarPuentes()`; `SalidaLocalService.guardar()`.

La cola y el listado de inspecciones filtran propietario, pero `obtenerInspeccionLocal(id)` devuelve cualquier registro con ese ID. La página lo muestra y calcula `soloLectura` por autor; solo lectura no impide verlo. Un usuario distinto con una URL conocida podría ver un borrador ajeno offline. Las referencias tampoco se filtran siempre y se mezclan registros preparados con claves compuestas y caches sin propietario.

Exigir propietario desde el servicio local antes de devolver datos. Los registros antiguos sin propietario no deben atribuirse automáticamente al usuario actual. Filtrar referencias privadas, usar `origenId` al comunicar con API y descartar resultados asíncronos si cambió la cuenta. No borrar datos ajenos al cerrar sesión. El perfil offline es información no confiable para autorización remota.

El almacenamiento local accesible al mismo origen no equivale a cifrado frente a quien controla el dispositivo. La HU exige aislamiento de la aplicación entre cuentas; no define cifrado en reposo. No añadir criptografía o dependencias sin una necesidad y un modelo de acceso definidos.

### E14. Concurrencia, conflictos y estado pendiente — prioridad alta

Rutas: E04–E08; **J**`inspeccion/service/InspeccionService.java`, `guardarBorrador()`; **J**`inspeccion/entity/Inspeccion.java`, `@Version`; tabla `conflicto_sincronizacion` en V11.

- Dos pestañas o el componente de fotos y el sincronizador pueden enviar la misma operación. La idempotencia limita efectos remotos, pero no evita carreras locales: borrados duplicados, marcado confirmado tardío, recuperación de un envío aún activo o consumo de intentos.
- La sincronización envía `X-Sync-Conflict-Policy: client-wins`. El backend conserva una copia del servidor y audita el conflicto, pero puede sobrescribirlo. No afirmar que toda diferencia produce un 409 ni cambiar esta política silenciosamente. Mostrar/reconciliar conflictos antes de publicar datos que el usuario no confirmó.
- Mantener BORRADOR con una intención pendiente y permitir nuevas ediciones sin regla explícita puede publicar una revisión distinta de la confirmada. Opción mínima: bloquear edición y segunda acción mientras la intención esté pendiente; desbloquear tras rechazo definitivo y confirmar de nuevo los cambios corregidos. Aplicar esa restricción en servicios además de la vista.
- La nueva operación final no debe entrar en el bucle que actualmente elimina una operación `INSPECCION` aun si su `operacion` no coincide con crear/guardar. Agregar casos explícitos y rechazar tipos desconocidos sin borrarlos.
- Tras un 409 de estado o resultado incierto, consultar/reproducir el resultado canónico antes de decidir que el borrador es editable. Nunca forzar localmente BORRADOR si el servidor ya lo publicó. Una confirmación tardía debe afectar al propietario original, no a la cuenta que esté visible.

Coordinación mínima: un único ejecutor por origen para la cola, aprovechando Web Locks si forma parte de los navegadores objetivo; si no, reclamación transaccional y vencimiento de ejecución en la misma Dexie. Elegir una alternativa y probar recuperación, no añadir dos coordinadores. Evitar que páginas hagan una segunda subida de fotos pendientes por fuera de ese circuito.

### E15. Riesgos de PWA, pérdida física y regresión — prioridad media/alta

Evidencia: E02, E03, E06–E09; `sgp-client/angular.json`; `sgp-client/src/app/features/salida/services/preparacion-salida.service.ts`; **J**`mantenimiento/service/PropuestasAlPublicarListener.java`.

- Recargar automáticamente al recibir una nueva versión puede interrumpir una escritura local, una cámara o una operación incierta. Mostrar aviso, permitir posponer y persistir antes de recargar. No borrar Dexie ni limpiar colas al actualizar el worker.
- Quitar `index.html` de la precarga para cumplir literalmente «sin caché» puede romper arranque offline. Cachear endpoints privados para cumplir SWR puede exponer datos entre cuentas. Resolver ambas interpretaciones antes del cambio.
- El cierre completo de la PWA puede dejar Dexie intacta pero sin perfil accesible para pasar guards offline. Definir continuidad de sesión local, sin persistir tokens ni reutilizar automáticamente el perfil de otra cuenta.
- Una cuota insuficiente, borrado del almacenamiento por el navegador o limpieza manual del dispositivo puede impedir guardar. Conservar el tratamiento de persistencia/cuota; confirmar «pendiente» solo después de commit local. La durabilidad del navegador no es un respaldo remoto.
- El formulario preparado puede diferir de la versión activa al reconectar (E06). No validar localmente contra una versión y enviar silenciosamente bajo otra.
- Añadir imports de formularios/fotografías al shell para prevalidar todas las operaciones aumentaría el paquete inicial. La prevalidación se realiza en la feature ya cargada; el sincronizador solo despacha contratos y verifica dependencias.
- Repetir publicación puede afectar IC, puente, historial, revisión, notificaciones y mantenimiento. Las pruebas deben verificar esos efectos además del estado final.

## 7. Plan mínimo de implementación por fases

Plan propuesto, no ejecutado. No se requieren dependencias de producción nuevas ni migraciones SQL para el núcleo de HU-061. No ampliar el alcance a sincronizar PDFs, aprobar revisiones offline, gestionar todo HTTP en el interceptor o rehacer funcionalidades anteriores.

### Fase 0. Cerrar contratos y reproducir los bloqueos

Dependencias: ninguna implementación previa; decisiones de sección 10.

- Acordar significado de caché de index, sesiones offline, referencias privadas y contenido que representa una acción pendiente.
- Crear pruebas de integración de creación/envío con filtro y servicios reales para E10; pruebas de preservación del borrador al montar el reporte y liberar espacio para E12.
- Verificar el recorrido preparar → seleccionar puente → crear offline → reconectar con los IDs/versiones reales (E06).
- Confirmar contratos o escenarios de HU-057 a HU-060, que no se localizaron como historias completas en las fuentes inspeccionadas. La etiqueta `HU-059` aparece en la auditoría de conflictos de `InspeccionService`, pero no sustituye su especificación.

Archivos de pruebas previstos: **JT**`integration/IdempotenciaHttpIntegrationTest.java` o prueba hermana que monte el flujo HTTP real; `sgp-client/src/app/offline/inspecciones-locales.spec.ts`; specs de reporte/fotos/preparación existentes.

Salida verificable: casos que delimitan el fallo y contratos escritos; no medir cumplimiento solo con mocks de la API.

### Fase 1. Integridad, aislamiento e idempotencia reutilizada

Dependencia: fase 0 para las decisiones que cambian contratos.

- Resolver E10 con una autoridad por operación. Ampliar la protección HTTP JSON a `/publicar`; permitir clave opcional para compatibilidad con consumidores actuales y usarla siempre en el cliente de HU-061.
- Reutilizar íntegramente la transacción de `TransicionInspeccionService.publicar()`. No duplicar la lógica de publicación para la cola.
- Corregir combinación de contexto fotográfico, confirmación local y liberación de espacio (E12). Añadir comprobación de propietario a lecturas locales e integración de referencias preparada/canónica (E13).
- Preservar metadatos de fotos confirmadas por usuario/inspección tras listar/cargar; retirar esos metadatos solo tras retirada confirmada. Mantener blob pendiente hasta confirmación, como hoy.
- Resolver la discrepancia de identidad/versión de preparación únicamente si el caso de fase 0 la confirma. Mantener el UUID del cliente y no cambiar de endpoint sin mapear su respuesta/identidad.

Archivos principales: servicios locales, reporte fotográfico, servicio/API de inspecciones del cliente, filtro/configuración de idempotencia y controladores/servicios Java afectados. Esquema SQL existente reutilizado.

Pruebas: integración de replay real en crear/enviar/publicar, una sola publicación/historial/IC, acceso cruzado por ID, contexto que no destruye ediciones y limpieza que conserva pendientes. Cada corrección se puede revisar por separado.

### Fase 2. Prevalidación e intención final persistente

Dependencia: fase 1 para no encolar sobre datos potencialmente sobrescritos o ajenos.

- En `CompletarInspeccionPage`, reutilizar `formulario().revisar()` para campos y errores. Mantener el mismo motor y esquema congelado; no crear otra interpretación JSON Schema en Angular.
- Ampliar el resumen de evidencia existente para contar, por ID único, confirmadas conocidas más pendientes propias con blob/metadatos válidos. Solo las cuatro categorías obligatorias satisfacen mínimos. Información confirmada desconocida no se inventa: se informa que falta preparación/evidencia y se evita confirmar una inspección que no se puede validar localmente.
- Esperar las escrituras locales en curso y persistir la última revisión antes de la intención final. Guardado más intención se coordinan en una transacción o secuencia durable que nunca deje una intención referida a datos sin guardar.
- Ampliar `SyncQueueItem.operacion` con enviar/publicar y conservar clave, propietario, inspección y referencia de revisión. Prevenir duplicado de intención activa dentro de una transacción por inspección.
- Conservar estado de negocio BORRADOR; mostrar «Envío pendiente» / «Publicación pendiente». Bloquear cambios mientras esté pendiente, con el mismo criterio para datos, fotos y documentos. El simple cierre del diálogo no cancela una operación ya encolada.
- Usar la cola también cuando la red desaparece entre abrir el diálogo y confirmar. El guardado confirmado en Dexie es el límite para anunciar éxito local.

Archivos: página/HTML de completar, reporte de fotos, modelos locales, servicios locales y traducciones. El motor de formulario y reglas Java se reutilizan.

Pruebas: obligatorios/condicionales/arreglos, cuatro mínimos y `OTRA`, mezcla de fotos confirmadas y pendientes, blob ausente, doble clic, cuota, cierre durante guardado, reapertura con intención y usuario ajeno. Verificar que una inspección inválida no deja una operación final en la cola.

### Fase 3. Despacho, recuperación y rechazo

Dependencias: fases 1 y 2.

- Ampliar `SincronizacionService`, sin crear otro sincronizador. Despachar explícitamente crear/guardar, fotos y luego acciones finales. La intención final espera todas las operaciones previas de esa inspección, incluidas las que requieren intervención. Excluir la propia intención de la condición que habilita fotos.
- Fijar en Dexie el request efectivo al primer intento y repetir exactamente clave/cuerpo/ruta hasta conocer el resultado. Una operación posterior usa la versión confirmada anterior; un replay anterior no se modifica (E11).
- Coordinar ejecutores y recuperar operaciones abandonadas al arrancar, reconectar o validar login, sin reclamar una operación que otro ejecutor mantiene activa. Disparar también ante trabajo nuevo con sesión validada/red disponible, evitando bucles de reintento.
- Mantener reintentos de red/5xx y refresh existente; respetar `Retry-After` para operación en proceso. Tras límite, conservar datos e intención para intervención.
- Ante rechazo de validación/autorización: persistir el problema y finalizar la intención activa sin borrar contenido/fotos. Habilitar corrección si el borrador sigue siendo editable para su dueño. Tras corregir y confirmar otra vez, generar otra clave; conservar el motivo anterior hasta que el usuario lo conozca.
- Ante éxito: actualizar solo el estado canónico confirmado y sus metadatos locales dentro de una transacción, completar la intención y reflejarlo en la página/listado/diagnóstico. No sustituir el borrador completo por un DTO resumen que carece de respuestas.
- Ante 409 de estado/confirmación IC o resultado incierto: reconciliar con el servidor. Una publicación ya realizada no vuelve a BORRADOR; una confirmación IC obsoleta requiere nueva intervención online. No inventar cálculo offline para resolverla.
- Mostrar acción específica, error y navegación a la inspección en diagnóstico. Contar inspecciones distintas; mostrar conectividad incluso si el contador es cero.

Archivos: sincronizador, servicios de sesión/store donde corresponda al disparo, servicios locales, API, shell, diagnóstico y listado. No convertir `offlineInterceptor` en encolador universal; los endpoints de HU-061 usan operaciones explícitas tipadas con propietario.

Pruebas: respuesta perdida/replay, servidor caído sin evento `online`, cierre/reinicio, login A → B → A, cinco fallos, 401 renovado, 403/422, rechazo memorizado con corrección nueva, cola bloqueada por fotos, dos pestañas y cambios de cuenta durante HTTP. Probar también las garantías de datos de las fases anteriores.

### Fase 4. PWA y experiencia offline

Dependencia de contratos: fase 0. Puede prepararse por separado del trabajo de cola; se valida integralmente después de fase 3.

- Ajustar `ngsw-config.json` sobre URLs reales: app shell precargado, traducciones/iconos imprescindibles precargados y SWR de referencias permitidas. Inspecciones, sesiones y contenido privado de archivos siguen fuera del caché del worker.
- Resolver el aislamiento de puentes asignados conforme a la decisión acordada; aprovechar la preparación por propietario y sus claves, sin duplicar el paquete offline.
- Integrar `SwUpdate.versionUpdates` en un servicio pequeño de `core/services/`, consistente con los servicios transversales actuales, y mostrar el aviso en shell/app. Comprobar soporte/habilitación del worker; posponer recarga hasta que las escrituras locales estén confirmadas y sea seguro interrumpir la interfaz.
- Conservar cabeceras Nginx de no caché HTTP del index/worker. No retirar el index del app shell sin resolver primero la contradicción del criterio.
- Unificar el mensaje de IC offline y evitar presentar como vigente o editable un cálculo anterior al corte. Conservar la fórmula solo en backend.
- Implementar únicamente la continuidad de perfil offline acordada, con identidad local separada de la credencial remota. No añadir login offline con contraseñas ni guardar tokens para resolver el arranque.

Archivos: `ngsw-config.json`, shell, nuevo servicio transversal de actualización si se necesita, configuración/app raíz para inicializarlo, componente IC, página de completar, traducciones y, según decisión, store/bootstrap. Nginx solo si una prueba demuestra un cambio necesario.

Pruebas: build de producción servido por HTTPS/localhost, instalación y control del worker, primera preparación, reinicio completo sin red, rutas lazy, traducciones, actualización entre dos builds, cola durante cambio de versión y separación de cuentas en caché.

### Fase 5. Aceptación integral y presupuesto

Dependencias: fases anteriores; entorno de pruebas aislado y perfiles de medición acordados.

- Ampliar pruebas existentes y agregar recorridos de HU-061 en navegador real con worker activo y API real. Reutilizar el enfoque E2E disponible si se puede ejecutar de forma reproducible; ver justificación de herramientas en sección 9.
- Medir bytes comprimidos del conjunto inicial real, además del presupuesto Angular; medir 3G fría con definición acordada de carga. Conservar lazy loading; no añadir optimizaciones ajenas a las mediciones que fallen.
- Ejecutar lint, formato, unitarias pertinentes y build. Backend con Java 21, unitarias e integración en entorno aislado según las verificaciones del repositorio. Documentar comandos y resultados reales.

Salida verificable: evidencia de cada fila de la matriz y regresión online de creación, guardado, envío académico, publicación directa, fotos e IC. No declarar la HU completa si solo pasan pruebas con APIs simuladas o `ng serve` de desarrollo.

## 8. Archivos que probablemente deberán modificarse

Los siguientes son candidatos, no cambios realizados. Los grupos con llaves representan archivos concretos existentes, no archivos con ese nombre literal.

| Ruta | Motivo y fase |
|---|---|
| `sgp-client/src/app/offline/db/sgp-db.ts` | Tipos de intención final y metadatos/estado local, fases 1–2. Nuevos índices solo si se necesitan. |
| `sgp-client/src/app/offline/services/inspecciones-locales.service.ts` | Propietario obligatorio, transacciones de intención, payload estable, confirmación/rechazo, limpieza segura, fases 1–3. |
| `sgp-client/src/app/offline/services/fotografias-locales.service.ts` | Evitar sobrescritura de borrador y conservar inventario confirmado, fase 1. |
| `sgp-client/src/app/offline/services/sincronizacion.service.ts` | Dependencias por inspección, acciones finales, recuperación, coordinación y errores, fase 3. |
| `sgp-client/src/app/features/inspecciones/services/inspeccion-api.service.ts` | Clave de publicación y contratos de acción, fases 1–3. |
| `sgp-client/src/app/features/inspecciones/services/operacion-idempotente.service.ts` | Solo si exige coordinación de requests/reintentos; ampliar patrón existente, no duplicarlo. |
| `sgp-client/src/app/features/inspecciones/pages/completar-inspeccion/completar-inspeccion.page.{ts,html}` | Prevalidación, guardado durable, pendiente/rechazo e IC offline, fases 2–4. SCSS únicamente si el estado necesita estilos. |
| `sgp-client/src/app/features/inspecciones/components/reporte-fotografico/reporte-fotografico.component.{ts,html}` | Inventario combinado, coordinación de subida y bloqueo por intención, fases 1–3. |
| `sgp-client/src/app/features/inspecciones/pages/mis-inspecciones/mis-inspecciones.page.{ts,html}` | Mostrar intención/error y resolver selección local con propietario/ID canónico, fases 1–3. |
| `sgp-client/src/app/features/inspecciones/components/confirmacion-estado-ic/confirmacion-estado-ic.component.{ts,html}` | Mensaje y controles al perder conexión, fase 4. |
| `sgp-client/src/app/features/inspecciones/components/documentos-inspeccion/documentos-inspeccion.component.ts` | Condicional: solo si necesita consumir el mismo bloqueo de intención; no agregar PDFs a la cola. |
| `sgp-client/src/app/layouts/app-shell/app-shell.component.{ts,html}` | Reanudación, contador por inspección y aviso de versión, fases 3–4. |
| `sgp-client/src/app/offline/pages/diagnostics/diagnostics.page.{ts,html}` | Acción, motivo y corrección segura, fase 3. |
| `sgp-client/src/app/core/services/auth.store.ts`, `session-bootstrap.service.ts` | Según contrato: identidad offline/limpieza visible y reanudación al validar sesión, fases 3–4. |
| `sgp-client/src/app/core/services/pwa-update.service.ts` | Archivo nuevo propuesto y acotado para `SwUpdate`; evitarlo si el ciclo completo cabe limpiamente en un componente global existente, fase 4. |
| `sgp-client/src/app/app.config.ts` o `app.component.ts` | Solo si hace falta inicialización global de actualización/reanudación, fase 4. |
| `sgp-client/src/app/features/salida/services/salida-local.service.ts`, `preparacion-salida.service.ts`; diálogo `features/inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component.ts` | Ajustes de integración de propietario, ID/versiones según prueba de fase 0; no rehacer preparación. Todas estas rutas frontend bajo `sgp-client/src/app/`. |
| `sgp-client/ngsw-config.json`, `sgp-client/public/i18n/es.json` | Caché efectiva y textos de pendiente/rechazo/versión, fases 2–4. |
| **J**`sync/http/IdempotenciaHttpFilter.java`, **J**`sync/config/IdempotenciaHttpConfiguration.java` | Cobertura `/publicar` y coordinación de idempotencia, fase 1. |
| **J**`inspeccion/controller/{InspeccionController,TransicionInspeccionController}.java`, **J**`inspeccion/service/{InspeccionService,TransicionInspeccionService}.java` | Evitar reserva duplicada y proteger publicación sin duplicar negocio, fase 1. Contrato de versión preparada si se confirma imprescindible. |
| **J**`sync/service/IdempotenciaHttpService.java` | Condicional: recuperar reserva HTTP abandonada según garantía acordada, fases 1/3. |
| **J**`common/config/SecurityConfiguration.java` | Condicional al despliegue con orígenes separados: cabecera de política de sincronización, E09. |
| Specs citados en sección 9 y nueva suite E2E HU-061 | Verificaciones por fase. Configuración de E2E/scripts solo para hacerlas reproducibles. |

No se prevén cambios de fórmula IC, reglas de evidencia, esquema SQL ni migraciones Flyway. `angular.json`, `nginx.conf`, `package.json`, `docs/DEVELOPMENT.md` y `.github/workflows/ci.yml` se modifican únicamente si las mediciones o la incorporación acordada de pruebas lo requieren, con justificación específica.

## 9. Estrategia de pruebas

### Base existente localizada

- Dexie y conservación de ediciones: `sgp-client/src/app/offline/inspecciones-locales.spec.ts`. Incluye recuperación tras recarga, versiones confirmadas y edición posterior a otra operación.
- Fotografías locales: `sgp-client/src/app/offline/services/fotografias-locales.service.spec.ts`. Cubre blob/clave/metadatos, propietario y borrado tras confirmación.
- Sincronización: `sgp-client/src/app/offline/services/sincronizacion.service.spec.ts`. Su cobertura actual se limita a autenticación previa: no tocar cola sin credenciales y validar `/me` antes de leerla. No demuestra FIFO, fotos, reintentos o cierre a mitad de operación.
- Preparación: `sgp-client/src/app/features/salida/services/{salida-local,preparacion-salida,salida-api}.service.spec.ts`.
- Motor de formulario: `sgp-client/src/app/features/formulario/motor/formulario/constructor.spec.ts`, `danos.spec.ts` y pruebas del resolutor/condiciones en `motor/esquema/`.
- UI/API/idempotencia: specs de completar inspección, reporte fotográfico, confirmación IC, `inspeccion-api.service.spec.ts`, `operacion-idempotente.service.spec.ts` y bootstrap de sesión en sus carpetas correspondientes.
- Backend: **JT**`sync/http/IdempotenciaHttpFilterTest.java`; **JT**`integration/{IdempotenciaHttpIntegrationTest,IdempotenciaArchivosIntegrationTest,FotografiaFlujoIntegrationTest,FotografiaHttpLimitesIntegrationTest,PublicacionInspeccionIntegrationTest,PublicacionRealIcIntegrationTest,PublicacionIndiceCondicionIntegrationTest,PreparacionSalidaIntegrationTest}.java`; pruebas de seguridad de controladores de transición/fotos y de inicio versionado/HU-024.

Estas pruebas fueron leídas, no ejecutadas. La documentación `docs/DEVELOPMENT.md` afirma que faltan dependencias de pruebas frontend, pero `sgp-client/package.json` ya declara Vitest, jsdom y fake-indexeddb. Debe comprobarse el entorno real al implementar; no instalar o cambiar versiones basándose solo en esa nota histórica.

### Casos mínimos a incorporar

| Nivel | Casos que aportan evidencia de HU-061 |
|---|---|
| Unitarias del flujo final | `revisar()` impide encolar campos pendientes/condicionales/arreglos; categorías mínimas combinadas; error de almacenamiento no anuncia pendiente; roles correctos; dos confirmaciones generan una intención. |
| Persistencia Dexie | Reabrir con intención/clave intactas; no sobrescribir datos al cargar fotos; preservar ediciones posteriores; filtrar URL/ID de otro dueño; conservar registros ajenos y sin propietario; liberar espacio solo tras confirmación segura. |
| Cola | Crear/guardar/fotos/final en orden; foto en intervención impide final; tipo desconocido no se borra; request estable en replay; error red/5xx, 401 y 409 en proceso; cierre durante envío; recuperación al login/reconexión; coordinación entre pestañas. |
| Rechazo posterior | 422 por dato/foto y motivo visible tras reinicio; edición vuelve a habilitarse sin perder datos; corrección crea nueva clave; 403 conserva datos; 409 terminal consulta servidor y no convierte PUBLICADA en BORRADOR; IC obsoleto exige confirmación online. |
| Integración backend real | Filtro HTTP más controladores más servicios sin conflicto de claves; replay idéntico crear/enviar/publicar; mismo usuario/clave con cuerpo o ruta distintos rechazado; transacción revierte si falla IC; un solo resultado/historial/evento y ningún cambio autorizado por perfil cacheado. |
| Seguridad de caché/sesión | A prepara y captura, logout, B no ve ni envía datos de A; URL directa y caché de asignaciones no filtran información; A vuelve y reanuda; sin tokens en almacenamiento persistente o cola; cuenta revocada no publica. |
| Worker real | Instalar/controlar tras primera visita, luego cerrar/abrir offline; traducciones y feature lazy disponibles; referencias entregan caché y se actualizan en red; inspecciones no entran en caché SW; aviso entre builds y recarga sin pérdida de pendientes. |
| Rendimiento | Build de producción, suma del conjunto inicial comprimido y transferencia fría; perfil 3G acordado, marcador de carga útil y tiempo < 5 s. Registrar condiciones y repeticiones. |

### E2E, herramientas y comandos

Se localizaron `sgp-client/e2e/cypress.config.cjs` y `revision-detalle.cy.cjs`. Esa configuración apunta solo a la revisión y al puerto de desarrollo 4200; no certifica PWA. `package.json` no declara Cypress ni Playwright ni script E2E. La CI frontend, `.github/workflows/ci.yml`, ejecuta lint, formato y build, pero no unitarias/E2E.

Primero verificar si el ejecutor Cypress de esa suite está realmente disponible y puede reutilizarse de forma reproducible contra un build de producción. Si no existe ejecutor disponible, se justifica **una sola dependencia de desarrollo de navegador E2E**, elegida al cerrar HU-057–HU-060: aporta control de red, worker, reinicio y dos pestañas, que Vitest/jsdom no demuestran. No instalar Playwright y Cypress simultáneamente ni introducir dependencias de producción. La preferencia histórica de HU-044 por Playwright debe conciliarse con la suite Cypress localizada.

Comandos existentes para la implementación posterior, desde sus carpetas respectivas: `npm test`, `npm run lint`, `npm run format:check` y `npm run build`. Seleccionar las pruebas por fase utilizando los filtros que admita el builder instalado y documentar el comando definitivo. Para backend, verificar `java -version` y `mvn -version` con Java 21, después pruebas seleccionadas y `mvn verify` según alcance. Las integraciones usan Testcontainers/PostGIS y Flyway en la base aislada de pruebas; nunca la base de desarrollo/producción.

No se propone un comando E2E inexistente como si ya funcionara. La fase 5 debe agregar y documentar el comando real y sus requisitos. Esta auditoría no ejecutó estos comandos para evitar generar artefactos o iniciar bases/migraciones; solo se verificará la integridad del documento y que el código permanezca sin cambios.

## 10. Aspectos que deben aclararse antes de comenzar

1. **Caché del index.** HU-061 pide app shell precargado y arranque sin red, pero también que `index.html` no se almacene en caché. Interpretación compatible propuesta: no caché HTTP/intermediarios, sí copia versionada administrada por NGSW. Si se prohíbe también esa copia, los criterios se contradicen y debe corregirse la historia antes de cambiar configuración (E02).
2. **Asignaciones privadas con SWR.** Acordar si SWR puede implementarse mediante los datos preparados por propietario de la capa actual, o se exige literalmente en el worker para `mis-puentes`. Incluir la URL autenticada en una caché compartida sin aislamiento contradice «otro usuario no ve sus datos» (E03, E13).
3. **Alcance de apertura offline.** Definir si basta recargar una pestaña ya autenticada o debe cerrar PWA/navegador, reiniciar dispositivo y seguir capturando. `sessionStorage` no cubre con garantía todos esos casos. Definir cómo reconocer al último usuario sin convertir el perfil persistido en credencial remota (E09).
4. **Edición mientras está pendiente y alcance de «rechazo».** Propuesta mínima: congelar contenido mientras hay intención activa; tras rechazo definitivo permitir corregir y confirmar otra intención. Un resultado incierto o un estado terminal canónico no permite desbloquear ciegamente. Aclarar cancelación, cambios desde otro dispositivo y si se exige que el servidor publique exactamente una revisión identificada; en este último caso conviene una precondición atómica de versión/hash en la acción existente (E14).
5. **Fotografías válidas offline.** Confirmar que cuentan pendientes propias con blob/metadatos y confirmadas conocidas, pero solo `DISPONIBLE` en el servidor permite la transición definitiva. Definir manejo de fotos confirmadas nunca descargadas, retiradas desde otra sesión, máximos y cargas rechazadas. Los mínimos actuales son las cuatro categorías de E07; no crear otros mínimos por interpretación.
6. **IC y confirmación previa.** La fórmula solo vive en backend; confirmar si el usuario acepta el estado calculado automáticamente al reconectar o debe revisar cualquier cambio del IC. Actualmente la ausencia de confirmación usa cálculo; una confirmación previa desactualizada bloquea publicación. Ese rechazo necesita interacción online, no portado de fórmula (E08).
7. **Versión del formulario y preparación.** Establecer la garantía de versión congelada cuando la activa cambia antes de sincronizar. Identificar si HU-057–HU-060 ya corrigen ese contrato y las claves preparadas. No sustituir el endpoint actual por `/iniciar` sin resolver UUID/respuestas (E06).
8. **Reanudación garantizada.** Definir si «al recuperar conexión» supone aplicación abierta o exige procesamiento con la PWA cerrada. El código actual trabaja en la ventana; no hay Background Sync. La opción mínima cumple con la app abierta y reanuda al abrirla. El envío con app cerrada requeriría otro alcance explícito.
9. **Tiempo máximo offline e intentos abandonados.** Acordar retención más allá de siete días y recuperación tras caída del servidor entre reserva y commit. No borrar la copia local ni generar claves nuevas para ocultar un resultado incierto (E11).
10. **Rendimiento y plataformas.** Precisar KB decimal o KiB, gzip/Brotli, qué archivos forman el paquete inicial, perfil 3G, latencia, CPU/dispositivo, caché fría y marcador de «carga» para los 5 s. Fijar navegadores de instalación, almacenamiento y coordinación entre pestañas. Los presupuestos Angular no sustituyen esas mediciones.
11. **Dependencias HU-057–HU-060 y pruebas.** Facilitar sus criterios o identificar sus implementaciones verificables; no se localizaron historias completas en `HUS/` ni en `docs/Jira_HUs_completas.md`. Acordar ejecutor E2E y comandos reproducibles antes de justificar una dependencia de desarrollo.
12. **Excepción documental a «no modificar archivos».** Se ha interpretado la petición de generar `AUDITORIA_HU_OFFLINE.md` como autorización exclusivamente para crear este entregable. Ningún archivo de aplicación, configuración o migración se modificó.
