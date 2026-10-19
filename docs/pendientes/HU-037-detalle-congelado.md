# Alcance histórico del detalle de revisión (HU-037)

`GET /api/v1/revisiones/{revisionId}` utiliza los identificadores, fecha de inspección,
identificación capturada y datos de `revision.contenido_enviado`. La estructura se obtiene
por `versionFormularioId`; no se reconstruye con los datos actuales de la inspección ni
con datos técnicos actuales del puente. Autor y puente se identifican mediante los UUID
capturados, sin inventar nombres históricos que el snapshot no conserva.
Los campos técnicos o metadatos que el snapshot no haya capturado no se completan con
valores vivos: recuperar información histórica omitida pertenece al contrato de captura,
no a este endpoint de lectura.

HU-035 no guarda un resultado de IC en el ciclo. El detalle calcula un resultado marcado
`PRELIMINAR` con la configuración histórica y los datos congelados. No persiste resultados
ni reutiliza un IC de publicación posterior. Si esa configuración es inválida, los servicios
existentes devuelven su error de dominio; no se sustituye por cero ni por datos actuales.

Las cargas y retiradas de fotografías requieren un borrador propio, por lo que no son
operaciones permitidas durante `EN_REVISION`. Sin embargo, el ciclo no conserva un manifiesto
de fotografías. Una devolución a borrador puede permitir retiradas o cargas posteriores,
y la disponibilidad depende del estado actual de cada archivo. El streaming y listado
existentes consultan esas vinculaciones actuales: **no son una instantánea fotográfica
histórica del ciclo**. El detalle lo expresa con `fotografiasCongeladas=false`.

El permiso adicional solo habilita lectura al revisor de un ciclo `EN_CURSO` cuando la
inspección está `EN_REVISION`. No habilita carga ni retirada, ni publica URLs. Se mantienen
los permisos del autor, de inspecciones publicadas y de catedráticos autorizados en borradores.

## Bloqueo de observaciones al terminar la edición (FASE 5)

`ObservacionService.crear/editar/eliminar/guardarGeneral` verifican al revisor asignado,
`Revision.estado=EN_CURSO` e `Inspeccion.estado=EN_REVISION` dentro de la misma transacción
que persiste la observación y usa `AuditoriaWriter.registrarEnTransaccion()`.
Las lecturas autorizadas permanecen disponibles después del cambio de estado.

El orden de llamadas es `RevisionRepository.findByIdForUpdate()` y después
`InspeccionRepository.findByIdAndEliminadoEnIsNullForUpdate()`. La primera consulta usa
`join fetch` de inspección y bloqueo pesimista NOWAIT: PostgreSQL bloquea las filas del
ciclo y de la inspección en esa consulta. No debe suponerse un orden interno de filas
que dependa del plan del JOIN. Una contención se traduce en 409 y revierte la transacción.
Las transiciones ordinarias bloquean inspección y su versión mediante
`findActivaByIdForUpdate()`; aprobar/publicar bloquean primero puente y después inspección.
HU-037 no necesita el bloqueo de puente. La espera entre una escritura y una transición
se verifica con dos conexiones reales y `pg_blocking_pids`, sin sustituir repositorios,
transiciones ni auditoría por mocks. El cálculo de publicación queda fuera de esa prueba.

Actualmente `Revision` solo permite iniciar un ciclo. Los métodos existentes
`TransicionInspeccionService.aprobar/solicitarCambios/rechazar` cambian la inspección,
pero no establecen `Revision.estado=RESUELTA` ni guardan un veredicto en el ciclo.
HU-037 bloquea las escrituras mediante el estado de inspección aunque el ciclo conserve
EN_CURSO. Esto no equivale a un cierre definitivo del ciclo: la HU de veredicto deberá
resolverlo en la misma transacción que la transición, coordinar los bloqueos y evitar
que un ciclo anterior vuelva a resultar editable en posteriores revisiones.
No se añade un cierre artificial ni se modifica esa máquina de estados.

Angular vuelve a consultar detalle y observaciones tras un 409, conserva su presentación
en lectura y bloquea las acciones. `bloqueada` es una restricción local de escritura;
no sustituye ni inventa `estadoRevision`. El estado mostrado proviene del nuevo GET.
