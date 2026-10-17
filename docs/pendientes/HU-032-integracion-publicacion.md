# HU-032: Confirmacion del estado e integracion con HU-026

## Implementado

- Consulta del calculo y confirmacion por el autor responsable autenticado.
- Ajuste del estado con justificacion obligatoria.
- Conservacion del IC numerico, estado automatico y condiciones criticas.
- Auditoria transaccional de la confirmacion y sus cambios.
- Rechazo de ajustes sobre inspecciones publicadas.
- Incorporacion de la decision al resultado definitivo y estado del puente.
- Rechazo de confirmaciones desactualizadas durante la publicacion.

## Contrato HTTP

GET /api/v1/inspecciones/{inspeccionId}/confirmacion-estado
PUT /api/v1/inspecciones/{inspeccionId}/confirmacion-estado

El PUT recibe estadoConfirmado, hashEntrada y justificacion.
La identidad del evaluador se obtiene de la sesion.

## Integracion pendiente con HU-026

El flujo real de publicacion debe:
- Autorizar al publicador y validar la transicion y revision correspondientes.
- Bloquear primero el puente y despues la inspeccion.
- Guardar PUBLICADA, publicado_en e historial dentro de una misma transaccion.
- Invocar PublicacionIndiceCondicionService.integrarPublicacion con el ID
  de la inspeccion y el usuario autenticado responsable de publicar.
- Propagar cualquier error para revertir toda la transaccion.
- No invocar la integracion de forma asincrona ni despues del commit.

La integracion verifica que la confirmacion corresponda al autor actual
y al calculo definitivo. Si cambio el calculo, responde con conflicto.

Si no existe confirmacion explicita, se conserva el comportamiento de
HU-020: el estado confirmado coincide con el calculado.

## Verificacion pendiente

Probar el flujo completo de HU-026 con confirmacion, ajuste justificado,
condicion critica y confirmacion desactualizada. Verificar tambien
publicacion concurrente y reversion del historial ante un fallo.

Las pruebas actuales simulan la transicion a PUBLICADA y el motor de
calculo. No sustituyen la prueba del flujo real completo de HU-026.

## Verificacion realizada en HU-026

Probado con el flujo real de publicacion y el calculador real en
`PublicacionInspeccionIntegrationTest`:

- Ajuste justificado (MALO a REGULAR) incorporado al resultado definitivo y al puente.
- Confirmacion desactualizada: la publicacion responde 409
  (`confirmacion_ic_desactualizada`) y no queda nada publicado ni en el historial.
