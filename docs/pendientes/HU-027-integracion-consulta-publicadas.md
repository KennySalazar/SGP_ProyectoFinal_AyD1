# HU-027: Integracion pendiente con la consulta de publicadas (HU-030)

## Estado

Correccion de inspecciones publicadas implementada y probada con el flujo real de
publicacion e IC (`POST /api/v1/inspecciones/{id}/correcciones`).

## Contrato para HU-030

`InspeccionResumenResponse` incluye:

- `tipo`: `ORIGINAL` o `CORRECCION`.
- `supersedeAId` y `motivoCorreccion`: en una correccion, la inspeccion que corrige y el motivo.
- `superadaPorId`: en una inspeccion publicada, la correccion publicada que la supera,
  o nulo si sigue vigente.

`GET /api/v1/inspecciones/{id}` y el listado de mis inspecciones ya lo informan. La
consulta de inspecciones publicadas de HU-030 debe mostrar la original como "Superada"
cuando `superadaPorId` no sea nulo y enlazar hacia esa correccion. Para listados, usar
`InspeccionRepository.findCorreccionesPublicadas` en una sola consulta (sin N+1).

## Reglas implementadas

- Solo el autor de la original o un Catedratico crean la correccion; motivo obligatorio.
- Solo se corrigen inspecciones PUBLICADAS y no superadas.
- Una sola correccion publicada por inspeccion: 409 al publicar otra
  (tambien garantizado por el indice `uq_inspeccion_sucesora_publicada` de V8).
