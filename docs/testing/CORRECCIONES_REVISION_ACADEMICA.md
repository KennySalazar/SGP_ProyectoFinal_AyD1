# Correcciones de inspecciones estudiantiles

RN-INS-08 exige crear una nueva inspección ligada mediante `supersede_a_id`.
RN-REV-01 exige revisar también las correcciones cuyo autor es estudiante.
La original publicada conserva su contenido y su historial. Se considera
superada cuando se publica la corrección, no cuando se crea el borrador.

## Flujo normal

1. Como estudiante autor, abrir **Mis inspecciones** y pulsar **Corregir** sobre
   una inspección publicada. Indicar el motivo.
2. Completar la corrección y sus evidencias obligatorias; enviar a revisión.
3. Como catedrático asignado, abrir la bandeja sin filtros. Debe aparecer la
   nueva inspección con su propio ciclo 1. Revisarla y emitir el veredicto.
4. Al aprobar, ambas inspecciones permanecen publicadas y consultables; la
   original se muestra como superada por la corrección.

La corrección conserva la asignación original del estudiante. Al enviar se
comprueba que corresponde a ese estudiante y puente, y que la inscripción y
el curso siguen vigentes. En el primer envío, si ya no es válida, se utiliza
una única asignación vigente del mismo estudiante y puente. Esto permite continuar con otro curso
o docente sin cambiar la cuenta ni reasignar la inspección publicada.

Si no existe ninguna, el estudiante necesita que un catedrático lo vincule a
un curso vigente y le asigne ese puente. Si hay varias y la original ya no es
válida, el envío responde `409 asignacion_ambigua`: los docentes deben aclarar
las asignaciones vigentes (por ejemplo, revocar la asignación que no corresponda).
En reenvíos posteriores a cambios solicitados se conserva la asignación y el
revisor del ciclo previo; no se cambia de docente a mitad de la revisión. Si
esa asignación pierde vigencia, el reenvío se bloquea.
No se selecciona arbitrariamente un docente. Los fallos conservan el borrador,
sin ciclo, notificación ni transición parcial.

Las correcciones creadas por un catedrático o profesional externo verificado
conservan la publicación directa. No heredan la asignación del estudiante autor
de la original.

## Recuperar una corrección enviada antes de la solución

No se modifican registros automáticamente al desplegar y no se requiere migración.
La recuperación se solicita por cada corrección, como su estudiante autor, mediante
el endpoint existente `POST /api/v1/inspecciones/{id}/enviar` (también `/reenviar`).
La interfaz actual no ofrece el botón de envío cuando ya está `ENVIADA`: para
estos registros se utiliza la API.

Antes de actuar se puede identificar el caso con esta consulta de solo lectura:

```sql
SELECT i.id, i.autor_id, i.puente_id, i.supersede_a_id,
       i.asignacion_puente_id, i.estado
FROM inspeccion i
WHERE i.tipo = 'CORRECCION'
  AND i.rol_autor_creacion = 'ESTUDIANTE'
  AND i.estado = 'ENVIADA'
  AND i.eliminado_en IS NULL
  AND NOT EXISTS (SELECT 1 FROM revision r WHERE r.inspeccion_id = i.id);
```

Con el backend actualizado, usar la URL y el ID del entorno correspondiente.
El token debe pertenecer al estudiante autor y no debe guardarse en archivos
versionados. Utilizar una clave de idempotencia nueva: reutilizar la del envío
antiguo podría devolver su respuesta almacenada sin ejecutar la recuperación.

```bash
SGP_API_BASE_URL='https://tu-servidor/api/v1'
SGP_CORRECCION_ID='UUID-DE-LA-CORRECCION'
read -r -s -p 'Token del estudiante autor: ' SGP_ACCESS_TOKEN
printf '\n'
curl --fail-with-body --request POST \
  "$SGP_API_BASE_URL/inspecciones/$SGP_CORRECCION_ID/enviar" \
  --header "Authorization: Bearer $SGP_ACCESS_TOKEN" \
  --header "Idempotency-Key: recuperar-correccion-$SGP_CORRECCION_ID"
unset SGP_ACCESS_TOKEN
```

El backend bloquea la inspección durante la operación. Si falta el ciclo,
valida los datos y la evidencia, resuelve la asignación y crea el ciclo y su
notificación en una transacción. Recupera la fecha de envío del historial del
servidor, por lo que conserva la antigüedad para la alerta de 15 días. Registra
la reparación en auditoría sin reescribir el historial original. Si el ciclo
ya existe, el reintento no crea otro ni repite el aviso.

Si falta el historial de envío, responde `409 envio_sin_historial` sin inventar
una fecha. Si no hay asignación válida o la inspección está incompleta, tampoco
hace cambios parciales: el registro previamente enviado permanece en ese estado.
Estos casos requieren resolver la precondición antes de repetir la solicitud;
no deben repararse simulando una aprobación en SQL.

Verificar con la cuenta del docente que aparece en la bandeja y comprobar:

```sql
-- En psql: \set correccion_id 'UUID-DE-LA-CORRECCION'
SELECT r.id, r.numero_ciclo, r.revisor_id, r.estado, r.enviada_en
FROM revision r WHERE r.inspeccion_id = :'correccion_id'::uuid;

SELECT n.usuario_id, n.clave_evento, count(*)
FROM notificacion n
JOIN revision r ON n.clave_evento = 'revision-enviada:' || r.id::text
WHERE r.inspeccion_id = :'correccion_id'::uuid
GROUP BY n.usuario_id, n.clave_evento;
```

Para el caso recuperado debe existir un ciclo 1 y una notificación de envío para
su revisor. Repetir la solicitud no debe incrementar esas cantidades. La
recuperación no aprueba ni publica la corrección: el docente debe revisarla.
