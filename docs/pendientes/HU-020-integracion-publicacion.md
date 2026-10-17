# HU-020: Integracion con HU-026

## Estado

Integracion backend implementada y verificada con el flujo real de HU-026,
el calculador real del IC y PostgreSQL.

## Contrato implementado

La publicacion ejecuta en una misma transaccion:

1. Bloquear primero el puente y despues la inspeccion.
2. Validar autorizacion, revision y transicion de estado.
3. Establecer PUBLICADA y publicado_en, y guardar el historial.
4. Invocar PublicacionIndiceCondicionService.integrarPublicacion
   con la inspeccion y el usuario responsable de publicar.

La integracion exige una transaccion existente mediante MANDATORY.
Cualquier error se propaga y revierte la operacion.
No se ejecuta despues del commit ni de forma asincrona.

La publicacion vigente se ordena por publicado_en DESC, id DESC.
La fecha mostrada corresponde a fecha_inspeccion.

## Verificacion

PublicacionInspeccionIntegrationTest y PublicacionRealIcIntegrationTest
verifican:

- Publicacion directa autorizada y publicacion mediante revision.
- Calculo definitivo con el motor real y conservacion de su desglose.
- Actualizacion del estado, IC y fecha del puente.
- Conservacion del IC numerico ante una condicion critica.
- Incorporacion del estado confirmado por el evaluador.
- Reversion de la publicacion, historial, resultado y auditoria
  cuando falla el calculo o la confirmacion queda desactualizada.
- Dos solicitudes concurrentes sobre la misma inspeccion:
  una publica, la otra recibe 409 y no se duplican los resultados.

La prueba de concurrencia cubre la misma inspeccion; no representa
una prueba de carga ni de publicaciones de inspecciones distintas.

## Validacion registrada

mvn verify: 908 pruebas, sin fallos, errores ni pruebas omitidas.
Verificacion de cobertura exigida por HU-018 aprobada.
