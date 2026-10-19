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
