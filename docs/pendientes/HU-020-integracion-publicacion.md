# HU-020: Integracion con HU-026

## Estado

Servicio de integracion implementado y probado con PostgreSQL.
Pendiente conectar y verificar el flujo real de publicacion de HU-026.

## Contrato

HU-026 debe ejecutar en una misma transaccion:

1. Bloquear primero el puente y despues la inspeccion.
2. Validar autorizacion, revision y transicion de estado.
3. Establecer PUBLICADA y publicado_en, y guardar el historial.
4. Invocar PublicacionIndiceCondicionService.integrarPublicacion(
   inspeccionId, usuarioId).

El usuarioId debe obtenerse de la sesion autenticada.
Las excepciones deben propagarse para revertir toda la operacion.

El servicio exige una transaccion existente mediante MANDATORY.
No debe invocarse despues del commit ni de forma asincrona.

La publicacion vigente se ordena por publicado_en DESC, id DESC.
La fecha mostrada corresponde a fecha_inspeccion.

## Verificacion pendiente

- Flujo real autorizado de HU-026 utilizando el calculador real.
- Reversion conjunta de estado, historial, IC, puente y auditoria.
- Publicaciones concurrentes respetando el orden de bloqueos.

HU-020 no debe marcarse como completada hasta verificar esta integracion.
