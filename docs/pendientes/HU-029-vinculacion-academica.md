# HU-029: vínculo académico de las fotografías privadas

`POST /api/v1/inspecciones/iniciar` acepta `asignacionPuenteId` opcional.
Cuando se indica, debe identificar una asignación no revocada del autor para el
puente solicitado. No se elige una asignación implícitamente, aunque exista una
sola candidata: omitirla conserva la creación existente sin vínculo académico,
sin deducir si la intención era independiente o académica. HU-024 no cambia.

El permiso adicional de lectura de BORRADOR requiere el rol CATEDRATICO de la
sesión y el vínculo exacto inspección-asignación-estudiante-puente-curso-catedrático.
Se usa la definición existente de asignación vigente (`revocado_en IS NULL`).
La revocación retira ese permiso; el autor conserva su acceso. No se amplía la
lectura de otros estados ni los permisos de carga o retirada.

## Bloqueo específico: inspecciones históricas sin vínculo

El código y la documentación no identifican qué asignación debe corresponder a
una inspección ya creada con `asignacion_puente_id` nulo, ni permiten distinguir
una inspección independiente de una académica basándose solo en autor y puente.
Hace falta indicar expresamente la correspondencia para esos registros; no se
realiza una asignación retroactiva automática ni se concede acceso por coincidencias.
