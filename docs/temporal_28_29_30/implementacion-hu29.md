# Plan de implementación de HU-029 sobre HU-028 integrada

Fecha de revisión: 16 de octubre de 2026.
Base examinada: `c62fe92e377310063ff99890122b9dfeec148fc5`.
Rama observada: `feature/hu-028-fotografias-inspeccion`.

Este documento sustituye la planificación que suponía ausentes los contratos de archivos. Describe trabajo futuro; esta revisión modifica únicamente el plan. No implica autorización para implementar, cambiar ramas o integrar cambios de otros responsables.

La preparación técnica de fase 1 se desarrolló en `feature/hu-029-impl-contratos-asignacion` y se concreta en [contratos-hu29.md](contratos-hu29.md). Allí se fijan las firmas de trabajo, la fuente de mínimos y el error estructurado; las ratificaciones académicas y acuerdos externos pendientes permanecen identificados. La autorización de esta entrega se limita a fase 1.

Actualización de implementación: el usuario autorizó posteriormente la fase 2, desarrollada sobre `022d2e9`. Se implementaron las políticas, la lectura mediante relación académica, las propiedades de mínimos y el control concurrente de capacidad en la carga. Las fases 3–5 continúan pendientes. La sección 9 registra la validación ejecutada.

## 1. Fuentes y alcance

- Historias originales: `../HUS/hu29.md` y `../HUS/hu28.md`, relativas a la raíz del repositorio; están en el directorio padre y ambas fueron localizadas.
- [Plan anterior de HU-028](implementacion-hu28.md): referencia de intención, con apartados desactualizados respecto del código.
- [Arquitectura](../ARCHITECTURE.md), [instrucciones](../../AGENTS.md), README, esquema V10/V11 y código backend/frontend actual.
- [Publicación HU-020/HU-026](../pendientes/HU-020-integracion-publicacion.md), [integración HU-032](../pendientes/HU-032-integracion-publicacion.md) y [actas HU-019](../pendientes/HU-019-acta-calibracion.md).

Las rutas Java de las tablas parten de `sgp-api/src/main/java/gt/usac/cunoc/sgp/`; las de pruebas usan el mismo paquete bajo `src/test/java/`.

Objetivo: integrar mínimos, máximo 60, permisos y retirada con la infraestructura existente. Se conserva la compresión, almacenamiento, idempotencia, recuperación y captura de HU-028. No crear otro sistema de archivos ni un editor paralelo. Las actas PDF de HU-019 mantienen su política propia y quedan fuera de las reglas fotográficas.

## 2. Estado real y diferencias frente al plan anterior

| Área | Implementación localizada | Pendiente para HU-029 |
|---|---|---|
| Tipos compartidos | `CategoriaFoto`, `EstadoArchivo`, `FotografiaRegistrada`, `ContextoArchivosInspeccion`, `ImagenInspeccionada` | Consumirlos sin duplicación; el contexto no contiene asignación académica |
| Registro | `RegistroFotografiasInspeccion` y `RegistroFotografiasInspeccionJpa` | Conectar diagnóstico y retirada autorizada |
| Persistencia | `Archivo`, `ArchivoInspeccion`, sus repositorios y `FotografiaMapper` | Reutilizar; no se identifica una migración necesaria para HU-029 |
| Bloqueo | `InspeccionRepository.findByIdAndEliminadoEnIsNullForUpdate` con `@Query` y `PESSIMISTIC_WRITE` | Aplicar el mismo bloqueo al conteo final, retirada y transiciones |
| Carga | `FotografiaService.adjuntar`: autor, borrador, metadatos, anclaje, procesamiento, reserva, conteo final bajo bloqueo y confirmación | Capacidad integrada en fase 2; conservar regresión en siguientes fases |
| Lectura | Listado, original y miniatura; autor o Catedrático relacionado para BORRADOR y archivo DISPONIBLE | Permiso backend integrado en fase 2; el flujo académico todavía debe fijar la asignación |
| Contenido real | `InspectorImagenesImageIo` decodifica JPEG/WebP con límites; `ProcesadorImagenes` normaliza a JPEG y genera miniatura | Verificar y ampliar cobertura, sin repetir el decodificador |
| Almacenamiento | `AlmacenamientoArchivos`, adaptadores local/S3, configuración MinIO/S3, cargas y recuperación | Pruebas conjuntas; no crear otro cliente ni cambiar infraestructura para implementar reglas |
| Idempotencia | `OperacionIdempotenteService`, reservas y compensación ya consumidas por carga | Conservar reintentos confirmados al añadir capacidad/estado |
| Reglas HU-029 | Evaluador, DTO, política de permisos, resolución académica y propiedades del servidor | Capacidad conectada; diagnóstico y transiciones pendientes de fases 3/5 |
| Retirada | `Archivo.retirar` y registro JPA implementados | Faltan caso de uso autorizado y endpoint DELETE |
| Frontend | `ReporteFotograficoComponent`, API de fotos, procesamiento y persistencia local | Diagnóstico, acción de retirada, error de capacidad y lectura autorizada del Catedrático |
| Publicación | `PublicacionIndiceCondicionService` exige transacción existente e inspección ya PUBLICADA | Falta conectar evidencia antes de enviar/publicar en los flujos correspondientes |
| Actas | Documento de pendientes vigente; no se localizaron servicios de actas en lo revisado | No declarar HU-019 terminada por existir almacenamiento fotográfico |

La frase anterior «hay que esperar los siete tipos de HU-028» ya no aplica. También dejó de ser necesario introducir un `ValidadorContenidoFotografico` que únicamente repita las comprobaciones actuales: el procesador ya utiliza el decodificador real y este rechaza otros formatos. Solo considerar extracción de una política si aparece una necesidad concreta, conservando una sola decodificación por carga.

El plan HU-028 conserva afirmaciones históricas sobre ausencia de infraestructura y pruebas anteriores. El código actual es la fuente de verdad. No se acredita la finalización completa de todos los criterios HU-028/HU-019 por el nombre de la rama o de los commits.

## 3. Trazabilidad de criterios HU-029

| Criterio | Estado actual | Trabajo de cierre |
|---|---|---|
| Mínimo en ACCESO/SUPERESTRUCTURA/SUBESTRUCTURA/CAUCE | Evaluador puro parametrizado | Filtrar inventario real, agrupar, fijar política de servidor e invocar antes de enviar/publicar |
| Evidencia completa | Se evalúan conteos, no inventario persistente | Demostrar cuatro categorías con fotos DISPONIBLE reales |
| Foto 61 rechazada con 422 | Integrada en POST y probada con PostGIS en fase 2 | Mantener garantía al incorporar retirada/transiciones |
| Tipo real JPEG/WebP | Implementado en decoder y utilizado por carga | Regresión con bytes reales válidos, falsos, PNG y truncados |
| Retirar foto del borrador | Mutación persistente disponible | Autorizar, bloquear, ejecutar retirada y actualizar diagnóstico/panel |
| Publicada rechaza retirada con 409 | No existe endpoint | Implementar política y probar estado vigente en transacción |
| Acceso protegido | Endpoints autenticados y cabeceras privadas | Completar matriz de acceso por entidad y verificar proveedor privado |
| Borrador visible al autor/Catedrático asignado | Política y consulta real integradas; prueba con vínculo persistido y revocación | Completar creación del vínculo y navegación académica |
| Visitante recibe 401 | Seguridad configurada; existen pruebas de lectura anónima | Cubrir también nuevos endpoints y POST |
| Prueba por rol en cada endpoint | Cobertura parcial con servicios simulados | Matriz completa por rol, relación, estado y variante de contenido |

La interpretación de una foto por cada categoría conserva la confirmación académica pendiente que señala la historia. Se utiliza la propuesta autorizada para desarrollo, sin presentarla como decisión definitiva. OTRA es válida y cuenta para el máximo, pero no tiene mínimo propuesto.

## 4. Contratos existentes que deben conservarse

### Inventario y retirada

`archivo/service/RegistroFotografiasInspeccion.java`:

```java
List<FotografiaRegistrada> consultar(UUID inspeccionId);
Optional<FotografiaRegistrada> buscar(UUID inspeccionId, UUID fotografiaId);
void retirar(UUID inspeccionId, UUID fotografiaId, Instant retiradoEn);
```

La implementación consulta únicamente FOTO_INSPECCION del destino e incluye distintos estados. HU-029 filtra explícitamente `EstadoArchivo.DISPONIBLE` antes de contar. Los documentos no participan. No contar blobs locales ni cargas sin confirmar.

`retirar` exige `Propagation.MANDATORY`, rechaza PURGADO o vínculo inexistente y conserva la fecha al repetir RETIRADO. El llamador debe autorizar y bloquear; el registro por sí solo no es una API segura. Se conservan objetos y asociación, excluyéndolos de lectura y conteos. No añadir purga física al DELETE.

### Evaluador y DTO ya implementados

```java
ValidacionEvidenciaResponse evaluar(
    Map<String, Long> disponiblesPorCategoria,
    Map<String, Integer> minimosPorCategoria);
void validarCapacidadParaNuevaFotografia(long fotografiasDisponibles);
```

Conservar `FaltanteEvidenciaResponse(String categoria, int requeridas, long disponibles)` y `ValidacionEvidenciaResponse`, con lista inmutable. Convertir el enum mediante `categoria().name()` en la orquestación, sin reemplazar el evaluador ni crear un catálogo alternativo.

El inventario debe pertenecer a una inspección y contener IDs únicos. Verificar ese contrato mediante pruebas de la implementación real. Un fallo de consulta no equivale a inventario vacío ni a resultado válido.

### Contenido y almacenamiento

`InspectorImagenes.inspeccionar(Path)` devuelve `ImagenInspeccionada`. El archivo temporal lo controla el servidor. `InspectorImagenesImageIo` limita bytes/píxeles y permite JPEG/WebP; `ProcesadorImagenes` utiliza su decodificación y produce JPEG sin EXIF, con original reducido y miniatura.

Conservar la cabecera JPEG del controlador mientras el formato de salida siga siendo JPEG. No confundir el MIME de entrada WebP con el del objeto normalizado. La lectura mantiene `private, no-store`, `nosniff` y nombres generados.

## 5. Diseño de autorización y evidencia

### Política única de fotografías

`inspeccion/service/PoliticaArchivosInspeccion.java` se implementó en fase 2, sin acceso a almacenamiento. Firmas disponibles:

```java
void validarCarga(ContextoArchivosInspeccion contexto, UUID actorId, long disponibles);
void validarLectura(ContextoArchivosInspeccion contexto, UUID actorId,
                    boolean esCatedraticoAsignado);
void validarRetirada(ContextoArchivosInspeccion contexto, UUID actorId);
```

Se reutiliza el contexto existente sin cambiar su record. La identidad procede de `JwtData`. El booleano se calcula en el servidor, nunca llega del cliente. `AccesoFotografiasInspeccionService` centraliza la resolución académica y su llamada a la política; listado, contenido y miniatura ya lo consumen. El diagnóstico de fase 3 reutilizará ese control.

Existe `Inspeccion.getAsignacionPuenteId()` y la consulta `AsignacionPuenteRepository.findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull`. Sin embargo, el constructor/flujo de inicio revisado no fija dicha relación. No inferirla por coincidencia de puente ni conceder acceso a cualquier Catedrático. Acordar la fuente de asignación y su vigencia con el responsable académico. Un vínculo nulo no concede permiso adicional. Si el dominio exige comprobar curso activo o inscripción vigente, la consulta actual no basta y ese requisito debe confirmarse antes de ampliarla.

| Operación | Regla conocida | Resultado ante rechazo |
|---|---|---|
| Carga nueva | Autor y BORRADOR, menos de 60 disponibles | 404 por autor ajeno; 409 por estado; 422 por capacidad |
| Lectura de borrador | Autor o Catedrático asignado | 404 para otro usuario autenticado |
| Retirada en BORRADOR | Autor | 404 por actor ajeno |
| Retirada en PUBLICADA | Prohibida para quien supera el control de acceso | 409 |
| Cualquier endpoint sin sesión | Requiere sesión | 401 |

Para no revelar existencia, comprobar autorización antes de informar estado. El permiso de lectura del Catedrático no concede carga o retirada. El rol Administrador no otorga automáticamente acceso fotográfico; el permiso administrativo de actas es otra política.

La lectura del autor fuera de BORRADOR existe hoy; conservarla salvo decisión expresa. La matriz de otros lectores y la retirada en ENVIADA/EN_REVISION/CAMBIOS_SOLICITADOS/RECHAZADA necesita ratificación. Se propone retirar solo en BORRADOR y rechazar los demás estados con 409; no declararlo requisito académico cerrado.

### Orquestación de evidencia

Crear `EvidenciaFotograficaService`: cargar inspección no eliminada, autorizar lectura para diagnóstico, consultar registro, filtrar disponibles, agrupar por código y llamar al evaluador existente.

Proponer `consultar(UUID inspeccionId, UUID actorId)` y una entrada interna `exigirParaTransicion(UUID inspeccionId)` con `Propagation.MANDATORY`. Esta última exige inspección bloqueada por el llamador y mínimos definidos; la autorización para enviar/publicar pertenece al flujo correspondiente. MANDATORY verifica transacción, no comprueba por sí mismo que exista un bloqueo: ese requisito se demuestra con integración concurrente.

La fase 2 implementa la fuente parametrizada `app.evidencia-fotografica.minimos`, consumida por `EvidenciaFotograficaProperties`. Se mantiene la propuesta 1 por cada categoría obligatoria y no se aceptan parámetros HTTP que reduzcan mínimos. Política vacía/inválida o ausencia de categorías obligatorias impide inicializar el bean; las pruebas de binding lo verifican. El binding, validación y conversión al mapa existente se precisan en [el contrato](contratos-hu29.md#5-fuente-única-de-mínimos). El consumidor de diagnóstico se incorporará en fase 3.

## 6. Fases de implementación y dependencias

### Fase 1. Actualizar acuerdos sobre código entregado

- [x] Contratos y bloqueo existentes comprobados y firmas registradas en [contratos-hu29.md](contratos-hu29.md).
- [x] Fuente técnica de asignación definida: vínculo persistido de inspección y consulta no revocada; vínculo nulo no concede permiso. El inicio actual no fija ese vínculo.
- [x] Retirada únicamente en BORRADOR como decisión conservadora de trabajo, conservando ratificación de los estados no cubiertos por la historia.
- [x] Error 422 estructurado definido con faltantes, total y máximo.
- [x] Fuente parametrizada de mínimos definida, con la confirmación académica pendiente explícita.
- [x] Responsables y límites de edición de `FotografiaService`, handler y panel delimitados.
- [ ] Coordinación externa de edición y entrega del vínculo académico; no se acredita aprobación de otros responsables.

**Salida técnica entregada:** documento de contratos y decisiones. La fase no añade servicios ejecutables: estos pertenecen a fases 2/3. Las dependencias externas no se presentan como acuerdos completados. No se requiere esperar más infraestructura HU-028 para iniciar la fase 2.

### Fase 2. Completar las políticas y conectar el máximo

**Estado: implementada y verificada en su alcance backend.** Se crearon política, resolución de asignación y propiedades; se integró el control final de capacidad y lectura en `FotografiaService`. La prueba de asignación utiliza un vínculo real persistido como fixture, no demuestra que HU-024 ya lo genere. La política de retirada está disponible, pero su caso de uso y endpoint siguen en fase 3.

- Conservar los dos DTO y `ReglasEvidenciaFotografica`, ya integrados.
- Implementar política y resolución de acceso académico; probar autor, no autor, asignado/no asignado, eliminado y estados.
- En `FotografiaService`, separar autorización de acceso de validación de una carga nueva. La reproducción idempotente confirmada debe mantener el comportamiento existente incluso si la inspección pasó a ENVIADA; existe una prueba de regresión explícita.
- Mantener autorización temprana. Después de resolver reintentos y antes de confirmar, obtener inspección con bloqueo, revalidar autor/BORRADOR/anclaje, contar DISPONIBLE y llamar al control de capacidad.
- La confirmación actual utiliza `TransactionTemplate`; conservar esa transacción y el guardado conjunto archivo/vínculo/resultado idempotente. No abrir otra transacción para validar capacidad.
- No mantener bloqueo durante procesamiento ni transferencia de objetos. Si el rechazo final ocurre después de escribir bytes, reutilizar la compensación y recuperación actuales.
- Conectar lectura de listado/original/miniatura a la autorización compartida sin exigir BORRADOR para todo GET.

**Salida comprobada:** POST impide la foto 61; dos cargas distintas desde 59 producen una confirmación 201 y un rechazo 422. El rechazo compensa sus objetos. RETIRADO/PURGADO no cuentan. El Catedrático relacionado lee listado, original y miniatura del borrador; la revocación impide lecturas posteriores. Un reintento confirmado sigue funcionando con 60 fotos y después de pasar a ENVIADA.

### Fase 3. Diagnóstico y retirada persistente

- Implementar `EvidenciaFotograficaService` y `EvidenciaFotograficaController`.
- Implementar `RetiradaFotografiaService` con `@Transactional`: bloquear inspección, autorizar, buscar por ambos IDs y retirar mediante registro con `Clock.instant()`.
- Implementar `RetiradaFotografiaController` como único propietario del DELETE.
- Repetición autorizada de una retirada devuelve 204 y mantiene su fecha. PURGADO o vínculo ajeno/inexistente devuelve 404. Si cambió el estado o permiso, revalidarlo antes de repetir.
- Un reintento del POST después de retirar no debe reactivar el archivo ni crear asociación nueva. Preservar el resultado idempotente acordado y hacer que listado/diagnóstico reflejen su estado actual.

**Salida:** ciclo carga → consulta → retirada → consulta coherente, reversión transaccional y ausencia de lectura de retiradas.

### Fase 4. Integración con panel existente y pruebas reales

- Ampliar `InspeccionApiService` con diagnóstico y DELETE.
- En `ReporteFotograficoComponent`, mostrar faltantes/total confirmados y la retirada para el autor de BORRADOR; después de cargar/retirar refrescar el diagnóstico y liberar las URL de objetos retirados.
- Añadir traducción del error `limite_fotografias_excedido` al mapa existente: actualmente termina en el error genérico de carga.
- Mantener fotografías locales pendientes separadas del total confirmado. Un 422 no confirma ni elimina silenciosamente el pendiente; no reintentar indefinidamente un rechazo semántico.
- La carga del panel está actualmente condicionada a `usuario.id === inspeccion.autorId`. Para lectura del Catedrático, separar consulta de fotos remotas de lectura/escritura de cola local y controles de captura. No guardar el contexto ajeno en la cola del lector.
- El panel recibe `IniciarInspeccionResponse`; coordinar su montaje desde una futura vista académica/reanudación autorizada. No crear un segundo editor para resolver esa navegación ni afirmar que el recorrido del Catedrático está disponible solo por permitir el GET.
- Ejecutar integración PostGIS y almacenamiento aislado, seguridad de todos los endpoints y regresión HU-028.

**Salida:** interfaz coherente para el flujo disponible; declarar cualquier dependencia pendiente de navegación académica. El servidor mantiene autoridad sobre permisos y capacidad.

### Fase 5. Integración con envío/publicación y cierre

- Conectar `exigirParaTransicion` en los flujos reales de envío y publicación antes de modificar estado.
- En publicación: autorizar, bloquear puente → inspección, validar evidencia, guardar PUBLICADA/fecha/historial e invocar `PublicacionIndiceCondicionService.integrarPublicacion` en la misma transacción.
- Propagar excepciones para revertir estado, historial, IC, puente y auditoría. No validar después del commit ni modificar el cálculo IC para añadir fotografías.
- La retirada/carga y las transiciones deben respetar el bloqueo de la misma fila. El GET diagnóstico puede quedar desactualizado y nunca sustituye la validación final.
- Si HU-026 o el flujo de envío no están disponibles, entregar el contrato y sus pruebas transaccionales, registrando la integración real como pendiente. No declarar HU-029 completamente terminada.

**Salida:** envío/publicación responden 422 con categorías faltantes; evidencia válida permite continuar; concurrencia no abre una ventana entre comprobar y cambiar el estado.

Orden recomendado: **1 → 2 → 3 → 4 → 5**. Tras acordar firmas, las pruebas/reglas de fase 2 y la orquestación de fase 3 pueden prepararse en paralelo; el cierre de integración requiere ambas. Las pruebas de regresión se ejecutan en cada entrega, no solo al final.

## 7. API y errores

| Ruta bajo `/api/v1` | Estado | Contrato |
|---|---|---|
| POST `/inspecciones/{id}/fotografias` | Existente, ampliar integración | 201; mantener UUID v7, multipart e Idempotency-Key; máximo 422 |
| GET `/inspecciones/{id}/fotografias` | Existente, ampliar lectura | 200, únicamente DISPONIBLE |
| GET `/inspecciones/{id}/fotografias/{fotoId}/contenido` | Existente | Bytes JPEG normalizados y autorización vigente |
| GET `/inspecciones/{id}/fotografias/{fotoId}/miniatura` | Existente | Misma autorización y disponibilidad |
| GET `/inspecciones/{id}/evidencia-fotografica` | Nuevo HU-029 | 200 con `ValidacionEvidenciaResponse`, aunque falten fotos |
| DELETE `/inspecciones/{id}/fotografias/{fotoId}` | Nuevo HU-029 | 204 tras retirada lógica autorizada |

Conservar `inspeccion_no_encontrada`/`fotografia_no_encontrada` (404), `estado_inspeccion_no_permite_archivos` (409), `limite_fotografias_excedido` y `contenido_fotografico_invalido` (422), autenticación 401 y límites multipart 413 existentes.

Para transición incompleta: 422 `evidencia_fotografica_incompleta` con categorías faltantes en la respuesta. El handler actual de `ApiException` no transporta el DTO de faltantes. La decisión de fase 1 es implementar en fase 3 una excepción específica y una ampliación acotada de ProblemDetail para añadir `faltantes`, `totalDisponibles` y `maximoPermitido`, conservando el comportamiento de los demás errores. El ejemplo JSON figura en [el contrato](contratos-hu29.md#6-contrato-http-y-error-informativo). El GET por separado no cumple el rechazo informativo de la transición. Si el inventario supera 60, usar `limite_fotografias_excedido`, sin inventar categorías faltantes.

## 8. Archivos previstos y responsabilidad

| Acción | Archivos | Responsable |
|---|---|---|
| Reutilizar | `archivo/model/*`, `Archivo`, `ArchivoInspeccion`, registro JPA, mapper, almacenamiento, procesador y recuperación | Entregados por HU-028 |
| Conservar | `ReglasEvidenciaFotografica`, `FaltanteEvidenciaResponse`, `ValidacionEvidenciaResponse` y pruebas | HU-029 ya implementado |
| Implementado en fase 2 | `inspeccion/service/PoliticaArchivosInspeccion.java`, `AccesoFotografiasInspeccionService.java` | HU-029 |
| Crear | `inspeccion/service/EvidenciaFotograficaService.java`, `RetiradaFotografiaService.java` | HU-029 |
| Crear | `inspeccion/controller/EvidenciaFotograficaController.java`, `RetiradaFotografiaController.java` | HU-029 |
| Propiedades implementadas; excepción pendiente | `inspeccion/config/EvidenciaFotograficaProperties` y excepción de evidencia con faltantes | HU-029; fases 2/3 respectivamente |
| Integrado en fase 2 | `inspeccion/service/FotografiaService.java` y pruebas existentes | Integración HU-029 sobre HU-028 |
| Ampliar para error estructurado específico | `common/exception/ApiExceptionHandler.java` | Un único responsable; coordinación pendiente |
| Ampliar | API/modelos de inspecciones, componente `reporte-fotografico`, traducciones y pruebas frontend | Un único responsable coordinado con HU-028 |
| Integrar | Servicio real de envío/publicación | Responsable del flujo/HU-026 |

No duplicar ni editar V10/V11. No se propone nueva migración para mínimos, máximo o retirada: los campos y asociaciones necesarios existen. No implementar actas PDF en esta historia ni aplicar el rechazo JPEG/WebP a sus documentos.

## 9. Pruebas necesarias y evidencia actual

### Existentes para conservar y ampliar

- `inspeccion/service/ReglasEvidenciaFotograficaTest` y `inspeccion/dto/ValidacionEvidenciaResponseTest`: 29 pruebas aprobadas en la ejecución histórica de fase 2; no repetidas en esta revisión.
- `archivo/service/ImagenesYAlmacenamientoTest`: casos JPEG/WebP, falsos/PNG/truncados, límites y compensación.
- `archivo/service/AlmacenamientoS3Test` y `archivo/config/AlmacenamientoConfigurationTest`.
- `inspeccion/controller/FotografiaControllerSecurityTest`: incluye lectura anónima y cabeceras, pero simula `FotografiaService`; no prueba la autorización real por asignación.
- `integration/FotografiaFlujoIntegrationTest`: flujo real con PostGIS y almacenamiento local de prueba, reintento tras ENVIADA, estados, metadatos, WebP, anclajes, compensación y concurrencia de identidad. Esa concurrencia no demuestra el límite 60.
- `integration/IdempotenciaArchivosIntegrationTest` y `FotografiaHttpLimitesIntegrationTest`.
- Pruebas del reporte fotográfico, servicio API, procesamiento/EXIF y `offline/services/fotografias-locales.service.spec.ts`.

### Nuevas pruebas por requisito

| Área | Casos mínimos |
|---|---|
| Inventario | Solo DISPONIBLE; excluir RETIRADO/PURGADO/documentos/otra inspección; mínimos parametrizados; OTRA no sustituye categoría; error técnico no produce aprobación |
| Capacidad | 59→60, 60→422; dos IDs distintos desde 59; reintento con 60 no consume plaza; retirada libera una plaza según interpretación propuesta |
| Seguridad | Administrador, Catedrático autor/asignado/no asignado, profesional autor/ajeno, estudiante y anónimo; cada endpoint y original/miniatura; sin permisos implícitos por rol |
| Retirada | Autor en borrador; publicada 409; actor ajeno 404; repetición mantiene fecha; par de IDs ajeno 404; rollback; acceso posterior denegado |
| Idempotencia | Pérdida de respuesta, solicitud distinta, reintento tras transición y tras retirada; ningún archivo reactivado ni duplicado |
| Contenido | Bytes válidos JPEG/WebP y falsificados; PNG renombrado; corrupción/truncado; límites; original normalizado y miniatura protegidos |
| Integridad | Rechazo final de capacidad compensa exclusivamente el intento; fallo de limpieza queda recuperable; cero asociaciones DISPONIBLE incompletas |
| Transiciones | Falta por categoría bloquea envío/publicación; reversión de estado/historial/IC; carrera con retirada/carga; orden puente→inspección |
| Frontend | Refresco de mínimos tras carga/retirada; errores 401/404/409/422; pendiente local no cuenta; lectura académica no expone cola del autor |

No convertir pruebas simuladas de controlador en evidencia de permisos del servicio. La consulta real de asignación y los bloqueos necesitan integración. Probar MinIO privado en entorno aislado cuando corresponda; la existencia de configuración o un commit de imagen local no acredita funcionamiento en todas las máquinas.

Validación al implementar: Java/Maven 21, pruebas focalizadas de cada fase, suites relacionadas, `mvn verify` con su cobertura configurada y frontend `npm test`, `npm run lint`, `npm run build`, `npm run format:check`. Leer `docs/UI_GUIDELINES.md` antes de cambios de interfaz. Los fallos preexistentes y las limitaciones de Docker deben documentarse por separado, sin cambiar configuración para ocultarlos.

**El análisis previo fue estático:** se leyeron código, pruebas y configuración. La validación posterior de implementación se registra a continuación; la existencia de una prueba se distingue de su resultado actual.

### Validación posterior: implementación de fase 2

El 16 de octubre de 2026 se compiló el backend y sus fuentes de prueba con Java 21.0.12. La primera ejecución offline se detuvo porque faltaban dependencias ya declaradas por HU-028 (S3 y WebP); Maven las resolvió sin modificar `pom.xml`. Docker funcionó con permisos adicionales y se utilizaron contenedores temporales PostGIS, sin migraciones contra bases existentes.

```bash
mvn -B -Dstyle.color=never \
  -Dtest=PoliticaArchivosInspeccionTest,AccesoFotografiasInspeccionServiceTest,EvidenciaFotograficaPropertiesTest,ReglasEvidenciaFotograficaTest,ValidacionEvidenciaResponseTest,FotografiaFlujoIntegrationTest,FotografiaControllerSecurityTest,ImagenesYAlmacenamientoTest,IdempotenciaArchivosIntegrationTest test
```

Resultado: **87 pruebas aprobadas en 9 clases, sin fallos, errores ni omitidas**. Incluye 11 pruebas del flujo fotográfico real, con las cuatro nuevas de fase 2: límite/compensación, exclusión de históricas/reintento, concurrencia de IDs distintos y acceso académico/revocación. Las pruebas del flujo usan almacenamiento local aislado; no acreditan MinIO real ni frontend.

Spotless se aplicó y verificó exclusivamente sobre los ocho archivos Java nuevos/modificados. No se ejecutó la suite completa ni `mvn verify`. Diagnóstico, DELETE y validación de envío/publicación permanecen sin implementar.

## 10. Decisiones pendientes y criterios de cierre

| Decisión | Propuesta y alcance del bloqueo |
|---|---|
| Mínimos académicos | 1 en cuatro categorías, parametrizado; ratificación pendiente para cierre definitivo, no impide integrar técnicamente |
| Conteo de 60 | Solo DISPONIBLE, retirada libera plaza; interpretación de trabajo solicitada previamente, conservarla documentada |
| Catedrático asignado | Fuente técnica definida en fase 1: ID de asignación de inspección y relación no revocada. Pendientes el flujo que fija el vínculo y ratificación de curso/inscripción/rol. Bloquea cierre de ese recorrido, no capacidad ni retirada del autor |
| Otros estados/roles | Preservar lectura del autor existente; retirar solo BORRADOR como propuesta. No ampliar acceso sin requisito |
| Error 422 de transición | Código y estructura definidos en fase 1; implementar y coordinar consumo con frontend/HU-026 |
| Reintento después de retirada | No reactivar; preservar semántica idempotente y refrescar inventario actual |
| Retirada offline | Proponer DELETE online para fotos confirmadas en esta entrega; no inventar un protocolo de eliminación offline sin requisito |
| Navegación académica | Coordinar la vista que suministra una inspección autorizada al lector asignado; evitar duplicar editor |

HU-029 se considera completa cuando sus criterios se demuestran en endpoints y transiciones reales: mínimo requerido, máximo concurrente, contenido real, retirada según permisos/estado, lectura privada, 401/404/409/422 correctos y pruebas por rol. La regresión HU-028 debe conservar captura, compresión, miniaturas, anclajes, idempotencia y recuperación.

**Se puede continuar ahora con las fases 2 y 3.** Los contratos compartidos que antes bloqueaban la implementación ya existen. Las dependencias restantes son decisiones concretas de permisos y la conexión final con envío/publicación; no es necesario esperar a una nueva infraestructura de HU-028.
