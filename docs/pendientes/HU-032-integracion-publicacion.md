# HU-032: Confirmacion del estado e integracion con HU-026

## Estado

Integracion backend con HU-026 implementada y verificada.
La interfaz de usuario corresponde al trabajo de frontend.

## Funcionalidad implementada

- Consulta del calculo por el autor responsable autenticado.
- Confirmacion o ajuste del estado con justificacion obligatoria
  cuando difiere del estado calculado.
- Conservacion del IC numerico, estado automatico y condiciones criticas.
- Registro del autor, fecha y justificacion del ajuste.
- Auditoria transaccional de la confirmacion y sus cambios.
- Rechazo de ajustes sobre inspecciones publicadas.
- Incorporacion de la decision al resultado definitivo y al puente.
- Rechazo de confirmaciones desactualizadas durante la publicacion.

## Contrato HTTP

GET /api/v1/inspecciones/{inspeccionId}/confirmacion-estado
PUT /api/v1/inspecciones/{inspeccionId}/confirmacion-estado

El PUT recibe estadoConfirmado, hashEntrada y justificacion.
La identidad del evaluador se obtiene de la sesion.

## Integracion con HU-026

La publicacion autoriza al responsable, valida la transicion,
bloquea primero el puente y despues la inspeccion y guarda
PUBLICADA, publicado_en e historial en una misma transaccion.

PublicacionIndiceCondicionService.integrarPublicacion comprueba
que la confirmacion corresponda al autor y al calculo definitivo.
Una confirmacion desactualizada produce 409 y revierte la publicacion.

El autor de la confirmacion puede ser distinto del revisor que publica.
Ambas responsabilidades se conservan por separado.

Si no existe confirmacion explicita, el estado confirmado coincide
con el calculado, conforme al comportamiento de HU-020.

## Verificacion

PublicacionInspeccionIntegrationTest y PublicacionRealIcIntegrationTest
utilizan el flujo real de publicacion y el calculador real.

Cubren ajustes justificados, condiciones criticas, conservacion del
IC numerico, confirmaciones desactualizadas, revision por otro usuario
y reversion del historial ante fallos.

La concurrencia se verifica con dos solicitudes sobre la misma
inspeccion: una publica y la otra recibe 409.

## Validacion registrada

mvn verify: 908 pruebas, sin fallos, errores ni pruebas omitidas.
Verificacion de cobertura exigida por HU-018 aprobada.
