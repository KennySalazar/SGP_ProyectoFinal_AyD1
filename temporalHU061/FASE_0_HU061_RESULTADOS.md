# HU-061 — Resultados de la fase 0

Fecha: 24 de octubre de 2026. Base del repositorio: `SGP_ProyectoFinal_AyD1`, commit `72a198c`. Referencias: [HU-061](HUS/HU-061.md) y [auditoría inicial](AUDITORIA_HU_OFFLINE.md).

**Resultado: 65 pruebas seleccionadas aprobadas: 41 frontend y 24 backend.** De ellas, 21 son nuevas: 13 frontend y 8 backend. Las pruebas nuevas caracterizan el comportamiento actual; que pasen no significa que los defectos reproducidos estén corregidos ni que HU-061 esté implementada.

**E10 no se reproduce y corrige una conclusión de la auditoría inicial. E12 y E13 sí se reproducen.** También se confirmaron discrepancias de identificadores y versiones en el recorrido offline. No se modificó código de producción, configuración, manifiestos de dependencias ni migraciones. No se avanzó a la fase 1.

Rutas de código relativas a `SGP_ProyectoFinal_AyD1/`. En referencias Java, **J** = `sgp-api/src/main/java/gt/usac/cunoc/sgp/`; **JT** = `sgp-api/src/test/java/gt/usac/cunoc/sgp/`.

## 1. Problemas confirmados mediante pruebas

### E12: el contexto fotográfico sustituye el borrador local

Pruebas: `sgp-client/src/app/offline/hu061-fase0.integration.spec.ts`, casos `E12: reproduce que guardarContexto...` y `E12: montar el reporte real...`.

Se creó una inspección con el servicio real y se guardó una edición en una transacción Dexie real sobre IndexedDB en memoria. Después se guardó un contexto anterior mediante `FotografiasLocalesService.guardarContexto()`. Otra prueba montó `ReporteFotograficoComponent` y dejó ejecutar su efecto de carga, que invoca ese mismo servicio.

Resultados observados:

- El registro deja de tener el payload envuelto `{ borrador, versionFormularioId, ... }` y pasa a contener directamente el contexto anterior.
- Se sobrescriben los datos del registro y desaparece `actualizacionLocalPendiente`; `sincronizado` queda en `true`.
- Cuando el contexto anterior tiene datos no vacíos, `obtenerInspeccionLocal()` devuelve esos datos anteriores en lugar de incorporar la edición posterior que sigue en cola.
- **La operación encolada conserva sus respuestas.** En ese escenario no se demostró pérdida total del contenido: permanece recuperable desde la cola y `liberarInspeccionesSincronizadas()` no elimina la inspección porque detecta operaciones pendientes.

Código responsable: `sgp-client/src/app/offline/services/fotografias-locales.service.ts`, `guardarContexto()`; `sgp-client/src/app/features/inspecciones/components/reporte-fotografico/reporte-fotografico.component.ts`, `recargar()`; `sgp-client/src/app/offline/services/inspecciones-locales.service.ts`, `obtenerInspeccionLocal()`.

### E12: una edición solo en caché puede eliminarse como si estuviera sincronizada

Pruebas del mismo archivo: `E12: reproduce pérdida de edición solo en caché...` y `E12: reproduce que liberar espacio ignora actualizacionLocalPendiente...`.

Se guardó una edición local con `actualizacionLocalPendiente: true`, sin operación todavía en la cola, reproduciendo la ventana del guardado inmediato online anterior al encolado/autoguardado. Se comprobaron dos variantes: con escritura posterior del contexto fotográfico y sin ella.

En ambas, la limpieza devuelve una inspección eliminada y la lectura posterior devuelve `undefined`. En la segunda variante se verificó antes de limpiar que coexistían `sincronizado: true` y `actualizacionLocalPendiente: true`.

**Aquí sí se confirmó pérdida del registro y de la edición local almacenada en este escenario controlado.** No se simuló una carrera temporizada de usuario: se construyó explícitamente el estado que admite la API real de persistencia y se ejecutó la limpieza real.

Código: `InspeccionesLocalesService.guardarBorradorEnCache()` y `liberarInspeccionesSincronizadas()`; el contexto fotográfico agrava el problema, pero no es condición necesaria.

### E13: otra cuenta puede leer un borrador local mediante su ID

Pruebas: `E13: listado aísla B, pero lectura directa por ID...` y `E13: página real muestra autor de A...`.

Se persistió el borrador de A, se cambió el `AuthStore` real a B y se comprobó que:

- `listarPorUsuario(B)` devuelve una lista vacía: ese filtro funciona.
- `obtenerInspeccionLocal(idDeA)` devuelve el registro de A.
- La página real `CompletarInspeccionPage`, inicializada con ese ID y sin red bajo sesión B, carga el borrador de A y permite presentar su información. La prueba renderiza el ID del autor del borrador en una plantilla mínima; confirma que `soloLectura()` es `true`. Esa restricción impide editar, pero no evita la lectura.

No se hizo una petición al backend para obtener el borrador. El problema se confirma en la capa local y en la carga de estado de la página, sin atribuirle una brecha equivalente a la API remota.

Código: `InspeccionesLocalesService.obtenerInspeccionLocal()` y `CompletarInspeccionPage.cargarBorradorDesdeLocal()`, en las rutas indicadas en la auditoría.

### E13: las lecturas locales de referencias no filtran al propietario

Prueba: `E13: reproduce referencias de A devueltas por lecturas locales bajo sesión de B`.

La preparación de A se guardó con `SalidaLocalService` real. Bajo sesión B, `ultima(B)` no entrega la preparación de A, pero `obtenerPuentesPreparados()` y `obtenerEsquemaActivoLocal()` sí devuelven los registros de A. La separación al guardar y la separación al consultar son responsabilidades distintas: solo la primera está cubierta en ese recorrido.

Código: `sgp-client/src/app/features/salida/services/salida-local.service.ts`; `sgp-client/src/app/offline/services/inspecciones-locales.service.ts`.

### Preparación: se propagan claves locales donde el contrato espera UUID

Prueba frontend: `preparación: conserva origenId, pero diálogo offline usa la clave local como versión y puente`.

Se ejecutó la persistencia de preparación real, la búsqueda/selección de `MisInspeccionesPage` y el inicio offline de `IniciarInspeccionDialogComponent`. Se comprobó que:

1. La preparación conserva el UUID real en `origenId` y utiliza `salida:<usuario>:<uuid>` como clave primaria local.
2. La selección pasa esa clave local como `puente.id`.
3. El diálogo utiliza también la clave local del esquema como `versionFormularioId` del borrador.
4. La petición de creación en la cola contiene el puente con clave compuesta y no contiene `versionFormularioId`.

Prueba backend: `identificadorCompuestoLocalNoEsUnUuidAceptadoPorElContratoHttp()`, en **JT**`integration/Hu061Fase0IntegrationTest.java`. El controlador real, detrás del filtro real, rechaza ese `puenteId` compuesto con **400 `validation_error`**, porque el DTO exige UUID.

Los servicios de preparación guardan correctamente `origenId`; el fallo está en el consumo del dato local al seleccionar/iniciar. No se corrigió ese traspaso.

### Formulario: creación y sincronización pueden dejar versiones distintas

Dos comprobaciones independientes delimitan el problema:

- **Backend real:** `preparacionConservaVersionPeroCrearUsaLaActivaPosterior()`. Se prepara con versión A, se publica/activa B en el fixture y se crea una inspección usando el contrato actual. La preparación sigue apuntando a A; la inspección creada apunta a B. El control se ejecuta sin filtro/clave para aislar el contrato de versión, y conserva el UUID de inspección proporcionado por el cliente.
- **Cliente con HTTP controlado:** `sincronización: conserva UUID y orden, pero descarta la versión canónica devuelta al crear`. La API simulada confirma una versión diferente de la local. `SincronizacionService` vacía la operación y marca la inspección sincronizada, pero deja la versión anterior en su payload.

Código: **J**`inspeccion/dto/IniciarInspeccionRequest.java` no contiene versión; **J**`inspeccion/service/InspeccionService.java`, `crearNuevaInspeccion()`, elige la activa; `sgp-client/src/app/offline/services/sincronizacion.service.ts`, rama `CREAR_INSPECCION`, no aplica la respuesta canónica.

La prueba frontend no usa la API real: demuestra el manejo de una respuesta diferente, no un recorrido E2E conectado entre ambos procesos. La prueba backend confirma por separado que el cambio de versión activa puede producir esa diferencia.

## 2. Problemas no reproducidos o pendientes de verificación

### E10 no reproducido: existe coordinación entre filtro y servicio

La primera versión de la prueba contrastó la hipótesis de la auditoría esperando 409. Obtuvo **201 en creación y 422 de validación en envío/reenvío**, por lo que tres aserciones de aquella hipótesis fallaron. Esto no se reporta como un error de producción.

La explicación está en **J**`sync/http/SolicitudIdempotente.java:23`: `getHeader()`, `getHeaders()` y `getHeaderNames()` ocultan `Idempotency-Key` en la solicitud que el filtro entrega al controlador. El filtro reserva la operación HTTP; el controlador recibe la clave como ausente y no vuelve a reservarla mediante el servicio. La auditoría inicial no había incorporado este adaptador a su deducción.

Las pruebas definitivas verificaron:

| Escenario real con filtro, controlador, servicio y PostgreSQL | Resultado |
|---|---|
| Crear con clave nueva | 201, un registro de inspección y una reserva `http_hu043` completada. |
| Repetir creación con la misma clave | 201, mismo cuerpo y `Idempotent-Replay: true`; sin nueva inspección. |
| Enviar y reenviar un borrador incompleto | 422 `inspeccion_incompleta`; no aparece `idempotencia_solicitud_distinta`. |
| Enviar y reenviar un borrador con evidencia mínima disponible | 200; el replay conserva cuerpo y status; una sola transición en historial. |
| Crear con idempotencia de servicio, sin filtro | 201 y replay sin duplicar inspección. |

Los escenarios de envío/reenvío usan esquema mínimo y fotografías disponibles sembradas en la base de pruebas. No verifican el ciclo académico completo ni calculan IC. El servicio de publicación IC es el único mock backend de la suite nueva y se verifica que no se invoque. Las identidades se establecen explícitamente en pruebas; no se valida aquí el filtro JWT ni el orden de registro de filtros del servidor desplegado.

**Consecuencia para la fase 1:** retirar como requisito la supuesta corrección de doble reserva E10. Reutilizar el circuito filtro → `SolicitudIdempotente` → controlador. No eliminarlo, reescribirlo ni introducir otra coordinación. El informe actual prevalece sobre las recomendaciones E10 contradictorias del documento inicial, que no se modificó.

### Controles que sí funcionan

- Los borradores, respuestas y claves sobreviven al cierre/apertura de IndexedDB en memoria.
- El sincronizador crea antes de guardar y conserva las respuestas tras las confirmaciones HTTP controladas.
- Con sesión B validada, la cola de A no se envía ni se elimina.
- B no lista ni confirma las fotos pendientes de A.
- La cola preserva los datos de la edición encolada incluso tras la sobrescritura del contexto fotográfico; no afirmar pérdida total en ese caso.
- Las pruebas existentes de preparación comprueban transacción/rollback, separación de cuentas al guardar, rechazo de paquetes ajenos y protección de pendientes al limpiar.

### Verificaciones pendientes

No se ejecutaron instalación PWA, service worker real, reinicio completo de navegador/dispositivo, 3G, medición del paquete comprimido ni concurrencia entre pestañas. Corresponden a fases posteriores. Tampoco se certificó un recorrido navegador → API real completo: se verificaron los límites frontend/backend por separado.

La publicación sigue fuera de la protección del filtro según la inspección de `IdempotenciaHttpFilter.protegida()`. No se añadió una prueba de publicación con IC completo a esta fase, por lo que no se declara un fallo de replay de publicación confirmado mediante prueba nueva. La recuperación automática tras login/reconexión y los resultados inciertos de larga duración tampoco se verificaron en esta selección.

## 3. Pruebas ejecutadas y resultados reales

### Entorno y aislamiento

- Java y Maven: OpenJDK **21.0.12.1**, Maven **3.8.7**; se comprobaron con `java -version` y `mvn -version`.
- Frontend: Node **24.19.0** ya disponible; Angular builder `@angular/build:unit-test`, Vitest **4.1.11** y jsdom existentes.
- Dexie usa exclusivamente `fake-indexeddb` en memoria. El singleton captura el adaptador al importarse; la prueba configura ese adaptador de instancia mediante reflexión y lo restaura. No se sustituyen las tablas, transacciones ni servicios locales por mocks. Las plantillas de las páginas/componentes se reducen para ejercitar su lógica, no su presentación completa.
- Backend: Testcontainers/PostGIS **16-3.5** con base nueva `hu061_fase0_test`. La suite existente de idempotencia crea otro contenedor/base `test`. `DynamicPropertySource` sustituye tanto datasource como Flyway por los datos del contenedor. Se aplicaron las 22 migraciones existentes **solo en esas bases efímeras**; ninguna base de desarrollo o producción fue utilizada.
- `HttpTestingController` controla la red frontend; MockMvc integra filtro/controladores/servicios backend reales con PostgreSQL. No son pruebas E2E de navegador.

### Ejecuciones finales

| Comprobación | Resultado final |
|---|---|
| Nueva suite frontend `offline/hu061-fase0.integration.spec.ts` | 13 aprobadas. |
| Suite existente `offline/inspecciones-locales.spec.ts` | Incluida en la selección final aprobada. |
| Suites existentes `offline/services/fotografias-locales.service.spec.ts` y `sincronizacion.service.spec.ts` | Incluidas en la selección final aprobada. |
| Suite existente `features/salida/services/salida-local.service.spec.ts` | Incluida en la selección final aprobada. |
| Total frontend: cinco archivos | **41 aprobadas, 0 fallidas**. |
| Nueva suite **JT**`integration/Hu061Fase0IntegrationTest.java` | **8 aprobadas**, 0 fallos, 0 errores, 0 omitidas. |
| Existente **JT**`integration/IdempotenciaHttpIntegrationTest.java` | **6 aprobadas**, 0 fallos, 0 errores, 0 omitidas. |
| Existente **JT**`sync/http/IdempotenciaHttpFilterTest.java` | **10 aprobadas**, 0 fallos, 0 errores, 0 omitidas. |
| Total backend seleccionado | **24 aprobadas; BUILD SUCCESS**. |
| ESLint sobre el archivo TypeScript nuevo | Código de salida 0. |
| Prettier sobre el archivo TypeScript nuevo | Aprobado. |
| Spotless sobre el archivo Java nuevo | BUILD SUCCESS. |
| `git diff --check` | Aprobado. |

Los casos que contienen «reproduce» afirman deliberadamente el comportamiento defectuoso observado para documentar fase 0. En fase 1 deberán convertirse en pruebas de protección que exijan preservación/aislamiento correctos; no son certificaciones de cumplimiento.

### Comandos reproducibles

Desde `sgp-client/`, con Node 24 en PATH:

```bash
npm test -- --include='src/app/offline/hu061-fase0.integration.spec.ts' \
  --include='src/app/offline/inspecciones-locales.spec.ts' \
  --include='src/app/offline/services/fotografias-locales.service.spec.ts' \
  --include='src/app/offline/services/sincronizacion.service.spec.ts' \
  --include='src/app/features/salida/services/salida-local.service.spec.ts'

node node_modules/eslint/bin/eslint.js src/app/offline/hu061-fase0.integration.spec.ts
node node_modules/prettier/bin/prettier.cjs --check src/app/offline/hu061-fase0.integration.spec.ts
```

La ejecución real invocó directamente `node_modules/@angular/cli/bin/ng.js test --watch=false` con los mismos cinco `--include`, mediante el binario existente de Node 24, pues el `node` predeterminado del shell era 18.20.8. El binario utilizado está en `/home/krm/.local/share/pnpm/store/v11/links/@/node/24.19.0/8f8adc8a32f090f436e7c4a07b225f546aa14c5663b0ee2a8684c78c4dcf7f35/node_modules/node/bin/node`.

Desde `sgp-api/`, con Java 21 y acceso a Docker:

```bash
mvn -o -Dtest=Hu061Fase0IntegrationTest,IdempotenciaHttpIntegrationTest,IdempotenciaHttpFilterTest test
mvn -o '-DspotlessFiles=.*Hu061Fase0IntegrationTest.java' spotless:check
```

`-o` requiere las dependencias ya resueltas. La primera ejecución offline falló porque faltaba PDFBox 3.0.8, dependencia existente; se resolvió ejecutando Maven con red. También faltaba `fake-indexeddb` en `node_modules`, aunque ya estaba declarado. Se descargó y extrajo la versión declarada **6.2.6**, sin cambiar `package.json` ni `package-lock.json`. No se añadieron dependencias nuevas.

### Intentos iniciales y límites

Hubo fallos de preparación de las pruebas nuevas: un import duplicado de repositorio Spring Data, un código de puente del fixture demasiado largo, el uso de `vi.mock` relativo no admitido por el builder Angular y la inicialización tardía del adaptador del singleton Dexie. Se corrigieron únicamente en las pruebas. Esos errores no se contabilizan como defectos del producto. La selección final no contiene pruebas omitidas o bloqueadas.

La ejecución que intentaba confirmar E10 falló en tres expectativas de 409; sus resultados 201/422 motivaron la revisión y descarte de esa hipótesis. Las pruebas finales comprueban el contrato correcto, sin cambios de producción.

Logs de ejecución disponibles en `/tmp/hu061-fase0/`: `frontend-final.log`, `backend-final.log`, `java-formato-check.log`, y registros de intentos anteriores. Los XML/textos backend finales están en `sgp-api/target/surefire-reports/`. Estos son artefactos locales temporales, no archivos versionados. No se ejecutó toda la suite del repositorio, el build de producción ni `mvn verify`; las verificaciones fueron las aplicables a los tests de fase 0.

## 4. Contratos técnicos necesarios

| Contrato | Evidencia y decisión técnica necesaria |
|---|---|
| Idempotencia JSON | El filtro es dueño de la reserva HTTP y oculta la cabecera al controlador. Conservar `SolicitudIdempotente`; no volver a reservar la misma operación en dominio. Este contrato ya funciona en creación/envío probados. |
| Idempotencia de archivos | Mantener separado el circuito multipart de reservas/checksum/compensación existente. La prueba nueva no lo reemplaza ni certifica toda su recuperación. |
| Identidad local y remota | `id` local preparado sirve para Dexie; `origenId` identifica al recurso remoto. Toda petición que exige UUID debe usar el segundo. El UUID de la inspección creada offline se conserva durante reintentos. |
| Versión del formulario | La versión que valida/captura offline debe coincidir con la que recibe el servidor. El contrato actual no transporta esa versión. Definir una extensión compatible del inicio actual, o una adaptación explícita del inicio versionado que conserve UUID; no intercambiar endpoints sin ese acuerdo. |
| Propiedad de registros | Toda lectura local por ID/referencia debe comprobar propietario, no solo el listado. Un registro sin propietario no se asigna automáticamente al usuario visible. Confirmaciones tardías mantienen el propietario original. |
| Payload de inspección | Contexto fotográfico e inventario deben combinarse con el borrador existente sin sustituir respuestas, versión, envoltura ni marcas pendientes. El resumen/contexto no es una copia canónica completa. |
| Confirmación y limpieza | Solo una respuesta confirmada del servidor puede habilitar la eliminación local. `sincronizado` no basta si hay edición local pendiente, operaciones o fotos sin confirmar. |
| Orden de sincronización | Crear → guardar datos → fotos → futura intención final. Los tests verifican crear/guardar y aislamiento de la cola; la barrera para acciones finales aún no existe. |
| Pendiente y rechazo final | La intención será local; ENVIADA/PUBLICADA requiere confirmación remota. Rechazo definitivo debe conservar datos y permitir corrección; un resultado incierto no autoriza a crear otra clave ni forzar BORRADOR. Contrato futuro, no implementado. |

Permanecen pendientes de definición funcional: significado de «index sin caché» frente al app shell offline; aislamiento de caché de puentes asignados; continuidad del perfil después de cerrar totalmente la PWA; edición/cancelación mientras hay intención pendiente; comportamiento del IC previamente confirmado; sincronización con aplicación cerrada; retención superior a siete días; perfiles exactos de rendimiento y navegadores objetivo. No se tomaron esas decisiones unilateralmente ni se implementaron sus mecanismos en fase 0.

## 5. Componentes reutilizables y dependencias disponibles

### Reutilización demostrada en las pruebas

- `sgp-client/src/app/offline/db/sgp-db.ts`: tablas y transacciones existentes; no hizo falta otro esquema para caracterizar los casos.
- `sgp-client/src/app/offline/services/inspecciones-locales.service.ts`: creación/guardado transaccional, claves estables, confirmación que preserva respuestas. Su API de lectura, contexto asociado y limpieza requieren los ajustes delimitados; no reemplazar el servicio.
- `sgp-client/src/app/features/salida/services/salida-local.service.ts`: almacenamiento transaccional, propietario, `origenId` y resumen por cuenta. Reutilizar el mecanismo y corregir su consumo.
- `sgp-client/src/app/offline/services/fotografias-locales.service.ts`: aislamiento de pendientes y confirmación de propietario comprobados; ampliar/corregir `guardarContexto()`.
- `sgp-client/src/app/offline/services/sincronizacion.service.ts`, `core/services/token-refresh.service.ts`, `core/services/auth.store.ts` e `inspeccion-api.service.ts` de la feature: renovación, orden crear/guardar, contratos HTTP y filtrado de dueño ejercitados con backend controlado.
- **J**`sync/http/{IdempotenciaHttpFilter,SolicitudIdempotente}.java`, **J**`sync/service/{IdempotenciaHttpService,OperacionIdempotenteService}.java`: autoridad HTTP y servicio funcionando en los escenarios probados.
- **J**`inspeccion/service/{TransicionInspeccionService,ReglasEvidenciaFotografica,MaquinaEstadosInspeccion}.java` y validador JSON Schema: envío/reenvío con validación y una sola transición probados. No duplicar su lógica para offline.
- **J**`sync/service/PreparacionSalidaService.java`: paquete con UUID/versiones reales comprobado contra PostgreSQL.

### Reutilización localizada, no certificada por esta selección

`FormularioDinamicoComponent.revisar()` y el motor en `sgp-client/src/app/features/formulario/motor/` siguen siendo la base para prevalidar en fase 2. `StorageService`, `uuid-v7.ts`, diagnóstico, `ApiErrorService` y `PublicacionIndiceCondicionService` también se mantienen como puntos de ampliación. Esta fase no ejecutó todos sus escenarios, por lo que localizar código no equivale a demostrar su cumplimiento completo.

### HU-057 a HU-060

Se buscaron archivos, referencias textuales, ramas disponibles e historial Git local, sin consultar Jira ni servicios externos.

| Dependencia | Evidencia disponible | Conclusión de fase 0 |
|---|---|---|
| HU-057 | No se localizó su historia ni una identificación explícita en archivos, ramas o mensajes del historial disponible. | Especificación y alcance pendientes; no afirmar implementada o ausente solo por esa búsqueda. |
| HU-058 | Merge `2da45ad`, PR #94, rama `feature/hu-058-059-fotos-conflictos`; commit de implementación `ac0ebca`, «sincronizar fotos y resolver conflictos offline». Añade fotos secuenciales a `SincronizacionService`. | Hay implementación atribuible en historial y código actual. Falta el texto de sus criterios para declarar cumplimiento. |
| HU-059 | Mismo merge/commit. Cambios en `InspeccionController`, `InspeccionService`, `ConflictoSincronizacion`, repositorio y cabecera `X-Sync-Conflict-Policy: client-wins`. El servicio etiqueta la auditoría como `HU-059`. | Reutilizar la política existente y su registro de conflictos. No cambiarla silenciosamente. Esta selección comprueba el envío de la cabecera, no toda la resolución de conflictos. |
| HU-060 | No se localizó historia ni identificación explícita equivalente en las fuentes disponibles. | Especificación y alcance pendientes. |

Esta revisión amplía la auditoría inicial: faltan las historias completas, pero **sí existe evidencia histórica de integración de HU-058/HU-059**. El cambio no implementa una segunda cola; amplía la cola actual.

## 6. Cambios mínimos recomendados para la fase 1

Recomendaciones únicamente; no se ejecutó ninguna corrección.

1. **No corregir E10 como doble reserva.** Conservar el adaptador que oculta la cabecera y las nuevas pruebas de replay. Al proteger publicación, ampliar el filtro/contrato cliente existentes; no hace falta añadir otro servicio de idempotencia ni modificar el controlador solo para hacer una segunda reserva.
2. En `FotografiasLocalesService.guardarContexto()`, combinar contexto/inventario con el registro existente de forma transaccional, conservando payload del borrador y marcas de pendiente. Reutilizar el almacenamiento de inspecciones.
3. En `InspeccionesLocalesService`, hacer que la limpieza excluya `actualizacionLocalPendiente` y que ninguna escritura de contexto convierta una edición pendiente en confirmada.
4. Comprobar propietario en lecturas por ID y referencias. Ajustar los consumidores de completar, seleccionar e iniciar; preservar los filtros de cola/fotos que ya pasan los controles.
5. Resolver `id` local frente a `origenId` remoto en selección e inicio offline. Transportar/reconciliar la versión conforme al contrato acordado y aplicar la respuesta canónica al confirmar creación, sin sustituir respuestas pendientes por un DTO incompleto.
6. Reutilizar el mecanismo idempotente JSON para `/publicar` cuando corresponda al alcance de fase 1. Mantener la transacción de negocio actual y el circuito multipart separado. No implementar todavía la intención final, su validación ni el despacho de fases 2 y 3.
7. Transformar las pruebas de caracterización de defectos en pruebas del comportamiento corregido al abordar cada cambio; mantener los controles de comportamiento correcto y las pruebas E10 que descartan el falso positivo.

No se justifica una arquitectura nueva, otra cola, otro motor de formularios ni una migración SQL para estas correcciones acotadas. Un posible cambio del DTO de inicio debe ser compatible con consumidores existentes y probarse explícitamente.

## 7. Riesgos y bloqueos pendientes

- **Bloqueos técnicos demostrados:** sobrescritura del borrador/marcas, eliminación de edición solo en caché, lectura de datos ajenos y traspaso incorrecto de IDs/versiones. Deben atenderse antes de ampliar las acciones finales.
- **No es un bloqueo demostrado:** la superposición de reserva E10. La coordinación ya existe; modificarla por la hipótesis inicial agregaría riesgo de regresión.
- **Acuerdos funcionales pendientes:** los contratos PWA/sesión/caché/edición de sección 4 y los criterios completos de HU-057 a HU-060. No bloquean la entrega de fase 0, pero condicionan decisiones posteriores.
- **Límites de evidencia:** IndexedDB en memoria no certifica durabilidad física tras apagado; HTTP controlado no certifica conectividad real; MockMvc no certifica el despliegue ni la cadena JWT; no se ensayaron carreras entre pestañas ni caída del proceso del servidor.
- **Dependencias de ejecución:** acceso a Docker para Testcontainers, Java 21, Node compatible y dependencias declaradas resueltas. Ninguna prueba seleccionada quedó sin ejecutar en la verificación final.
- **Alcance del diff:** solo se añadieron `sgp-client/src/app/offline/hu061-fase0.integration.spec.ts`, **JT**`integration/Hu061Fase0IntegrationTest.java` y este informe en la raíz del workspace. Las pruebas previas, código de producción, migraciones, `package.json`, lockfile y auditoría original quedaron intactos. No se hicieron commits ni operaciones de despliegue.

La fase 0 finaliza con estos resultados. Las correcciones quedan pendientes para una fase posterior autorizada.
