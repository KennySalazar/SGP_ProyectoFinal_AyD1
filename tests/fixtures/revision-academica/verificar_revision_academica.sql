-- Consultas de persistencia; NO sustituyen las pruebas por interfaz/API.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT current_database(),current_user,version();
SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT u.id,u.email,r.nombre AS rol,u.activo,u.activado,u.verificado
FROM usuario u JOIN rol r ON r.id=u.rol_id
WHERE u.nombre_usuario LIKE 'SGP-TEST-REV-%' ORDER BY u.email;
SELECT p.nombre AS escenario,i.id AS inspeccion_id,i.estado,i.autor_id,
 i.rol_autor_creacion,i.dispositivo_id,i.fecha_inspeccion,i.publicado_en,
 r.id AS revision_id,r.numero_ciclo,r.estado AS estado_revision,r.veredicto,
 r.enviada_en,r.iniciada_en,r.resuelta_en,r.revisor_id,r.calificacion
FROM inspeccion i JOIN puente p ON p.id=i.puente_id
LEFT JOIN revision r ON r.inspeccion_id=i.id
WHERE i.dispositivo_id LIKE 'SGP-TEST-REV-%' ORDER BY p.nombre,r.numero_ciclo;
SELECT p.nombre,h.secuencia,h.estado_anterior,h.estado_nuevo,h.evento,
 h.revision_id,h.realizado_por_id,h.motivo,h.creado_en
FROM historial_estado_inspeccion h JOIN inspeccion i ON i.id=h.inspeccion_id
JOIN puente p ON p.id=i.puente_id WHERE i.dispositivo_id LIKE 'SGP-TEST-REV-%'
ORDER BY p.nombre,h.secuencia;
SELECT p.nombre,r.numero_ciclo,o.id,o.elemento_ref,o.instancia_elemento_id,
 o.contenido,o.estado,o.respuesta_autor,o.resuelto_por_id,o.resuelto_en
FROM observacion_revision o JOIN revision r ON r.id=o.revision_id
JOIN inspeccion i ON i.id=r.inspeccion_id JOIN puente p ON p.id=i.puente_id
WHERE i.dispositivo_id LIKE 'SGP-TEST-REV-%' ORDER BY p.nombre,r.numero_ciclo,o.creado_en;
SELECT p.nombre,r.id,r.estado,r.enviada_en,
 CURRENT_TIMESTAMP-r.enviada_en AS espera,
 r.enviada_en <= CURRENT_TIMESTAMP-interval '15 days' AS supera_umbral,
 r.revisor_id,n.usuario_id AS destinatario,n.tipo,n.clave_evento,n.creado_en
FROM revision r JOIN inspeccion i ON i.id=r.inspeccion_id JOIN puente p ON p.id=i.puente_id
LEFT JOIN notificacion n ON n.entidad='revision' AND n.entidad_id=r.id
 AND n.tipo='REVISION_SIN_REVISAR_15_DIAS'
WHERE i.dispositivo_id IN ('SGP-TEST-REV-antigua','SGP-TEST-REV-control-b')
ORDER BY p.nombre;
SELECT n.usuario_id,n.clave_evento,count(*) AS duplicados
FROM notificacion n JOIN revision r ON n.entidad='revision' AND r.id=n.entidad_id
JOIN inspeccion i ON i.id=r.inspeccion_id
WHERE i.dispositivo_id LIKE 'SGP-TEST-REV-%'
GROUP BY n.usuario_id,n.clave_evento HAVING count(*)>1;
SELECT p.nombre,a.categoria_foto,a.estado,count(*)
FROM archivo a JOIN archivo_inspeccion ai ON ai.archivo_id=a.id
JOIN inspeccion i ON i.id=ai.inspeccion_id JOIN puente p ON p.id=i.puente_id
WHERE i.dispositivo_id LIKE 'SGP-TEST-REV-%'
GROUP BY p.nombre,a.categoria_foto,a.estado ORDER BY p.nombre,a.categoria_foto;
-- Inventario exacto de restricciones y valores por defecto del esquema conectado.
SELECT c.relname AS tabla,co.conname,pg_get_constraintdef(co.oid) AS definicion
FROM pg_constraint co JOIN pg_class c ON c.oid=co.conrelid
WHERE c.relname IN ('usuario','usuario_profesional','curso','curso_estudiante',
 'asignacion_puente','puente','version_formulario','configuracion_ic_elemento',
 'inspeccion','revision','observacion_revision','historial_estado_inspeccion','notificacion')
 AND c.relnamespace='public'::regnamespace ORDER BY c.relname,co.conname;
SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public'
 AND tablename IN ('usuario','curso','curso_estudiante','asignacion_puente','puente',
 'version_formulario','inspeccion','revision','observacion_revision','notificacion')
ORDER BY tablename,indexname;
SELECT table_name,column_name,data_type,is_nullable,column_default
FROM information_schema.columns WHERE table_schema='public'
 AND table_name IN ('usuario','curso','curso_estudiante','asignacion_puente','puente',
 'version_formulario','inspeccion','revision','observacion_revision','notificacion')
ORDER BY table_name,ordinal_position;
COMMIT;
