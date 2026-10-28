-- Limpieza explícita: nunca invocada por seed, Flyway ni Spring Boot.
\set ON_ERROR_STOP on
\if :{?fixture_db}
\else
  \echo 'Falta -v fixture_db=nombre_exacto_de_la_base_de_pruebas'
  \quit 3
\endif
\if :{?cleanup_fixture}
\else
  \echo 'Falta -v cleanup_fixture=SGP-TEST-REV'
  \quit 3
\endif
BEGIN;
SET LOCAL search_path = pg_temp, public;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SELECT set_config('sgp.fixture_db', :'fixture_db', true),
       set_config('sgp.cleanup_fixture', :'cleanup_fixture', true),
       set_config('sgp.fixture_password_hash', 'unused-for-cleanup', true);
SELECT pg_advisory_xact_lock(736724, 35039);
DO $$
DECLARE t record;
BEGIN
 IF current_database() <> current_setting('sgp.fixture_db')
    OR current_setting('sgp.cleanup_fixture') <> 'SGP-TEST-REV' THEN
  RAISE EXCEPTION 'SGP-TEST-REV: base o confirmación de limpieza incorrecta';
 END IF;
 -- Evita carreras con nuevas relaciones, incluidas las de ON DELETE CASCADE.
 -- Detener previamente la aplicación en esta base de pruebas.
 FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename LOOP
  EXECUTE format('LOCK TABLE %I IN SHARE ROW EXCLUSIVE MODE',t.tablename);
 END LOOP;
END $$;
\ir fixture_revision_academica.sql
CREATE TEMP TABLE rev_owned(tabla text, pk text, id uuid, fila jsonb,
  PRIMARY KEY(tabla,id)) ON COMMIT DROP;
DO $$
DECLARE r record; j jsonb;
BEGIN
 FOR r IN SELECT * FROM rev_fixture LOOP
  EXECUTE format('SELECT to_jsonb(t) FROM %I t WHERE %I=$1::uuid',r.tabla,r.pk)
   INTO j USING r.datos->>r.pk;
  IF j IS NOT NULL THEN
   INSERT INTO rev_owned VALUES(r.tabla,r.pk,(r.datos->>r.pk)::uuid,j);
  END IF;
 END LOOP;
END $$;
-- Los hijos generados por la aplicación solo se aceptan con padre Y actor exclusivos.
INSERT INTO rev_owned
SELECT 'revision','id',r.id,to_jsonb(r) FROM revision r
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='inspeccion' AND o.id=r.inspeccion_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.revisor_id)
ON CONFLICT DO NOTHING;
INSERT INTO rev_owned
SELECT 'observacion_revision','id',r.id,to_jsonb(r) FROM observacion_revision r
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='revision' AND o.id=r.revision_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.creado_por_id)
  AND (r.resuelto_por_id IS NULL OR EXISTS
       (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.resuelto_por_id));
INSERT INTO rev_owned
SELECT 'historial_estado_inspeccion','id',r.id,to_jsonb(r) FROM historial_estado_inspeccion r
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='inspeccion' AND o.id=r.inspeccion_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.realizado_por_id)
ON CONFLICT DO NOTHING;
INSERT INTO rev_owned
SELECT 'resultado_ic','id',r.id,to_jsonb(r) FROM resultado_ic r
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='inspeccion' AND o.id=r.inspeccion_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='version_formulario' AND o.id=r.version_configuracion_id)
  AND (r.confirmado_por_id IS NULL OR EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.confirmado_por_id))
  AND (r.solicitado_por_id IS NULL OR EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.solicitado_por_id));
INSERT INTO rev_owned
SELECT 'confirmacion_estado_inspeccion','inspeccion_id',r.inspeccion_id,to_jsonb(r)
FROM confirmacion_estado_inspeccion r
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='inspeccion' AND o.id=r.inspeccion_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.realizado_por_id);
INSERT INTO rev_owned
SELECT 'archivo','id',a.id,to_jsonb(a) FROM archivo a JOIN archivo_inspeccion ai ON ai.archivo_id=a.id
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='inspeccion' AND o.id=ai.inspeccion_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=a.subido_por_id);
INSERT INTO rev_owned
SELECT 'archivo_inspeccion','archivo_id',a.archivo_id,to_jsonb(a) FROM archivo_inspeccion a
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='archivo' AND o.id=a.archivo_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='inspeccion' AND o.id=a.inspeccion_id);
INSERT INTO rev_owned
SELECT 'operacion_idempotente','id',op.id,to_jsonb(op) FROM operacion_idempotente op
JOIN archivo_inspeccion ai ON op.entidad='archivo' AND op.entidad_id=ai.archivo_id
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='archivo' AND o.id=ai.archivo_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=op.usuario_id)
  AND op.metodo='POST' AND op.estado='COMPLETADA'
  AND op.ruta='/api/v1/inspecciones/'||ai.inspeccion_id||'/fotografias';
INSERT INTO rev_owned
SELECT 'foro_hilo','id',r.id,to_jsonb(r) FROM foro_hilo r
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='inspeccion' AND o.id=r.inspeccion_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='puente' AND o.id=r.puente_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=r.autor_id)
  AND r.cerrado_por_id IS NULL;
INSERT INTO rev_owned
SELECT 'notificacion','id',n.id,to_jsonb(n) FROM notificacion n
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=n.usuario_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla=n.entidad AND o.id=n.entidad_id);
INSERT INTO rev_owned
SELECT 'auditoria','id',a.id,to_jsonb(a) FROM auditoria a
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=a.usuario_id)
  AND EXISTS (SELECT FROM rev_owned o WHERE o.tabla=a.entidad AND o.id=a.entidad_id);
INSERT INTO rev_owned
SELECT 'token_refresco','id',t.id,to_jsonb(t) FROM token_refresco t
WHERE EXISTS (SELECT FROM rev_owned o WHERE o.tabla='usuario' AND o.id=t.usuario_id);
-- No se adoptan OTP, comentarios, mantenimiento, invitaciones u otras relaciones.
-- Cualquiera de ellas obliga a revisar manualmente el escenario, sin borrado parcial.
DO $$
DECLARE fk record; condicion text; hay boolean;
BEGIN
 FOR fk IN
  SELECT c.*,s.relname origen,ns.nspname esquema_origen,t.relname destino FROM pg_constraint c
  JOIN pg_class s ON s.oid=c.conrelid JOIN pg_class t ON t.oid=c.confrelid
  JOIN pg_namespace ns ON ns.oid=s.relnamespace
  WHERE c.contype='f' AND c.confrelid IN
   (SELECT to_regclass('public.'||tabla) FROM rev_owned)
 LOOP
  SELECT string_agg(format('to_jsonb(s)->%L = o.fila->%L',a.attname,b.attname),' AND ')
   INTO condicion FROM unnest(fk.conkey,fk.confkey) AS k(src,dst)
   JOIN pg_attribute a ON a.attrelid=fk.conrelid AND a.attnum=k.src
   JOIN pg_attribute b ON b.attrelid=fk.confrelid AND b.attnum=k.dst;
  EXECUTE format('SELECT EXISTS(SELECT FROM %I.%I s JOIN rev_owned o ON o.tabla=%L AND %s
    WHERE NOT EXISTS(SELECT FROM rev_owned z WHERE z.tabla=%L AND z.fila=to_jsonb(s)))',
    fk.esquema_origen,fk.origen,fk.destino,condicion,fk.origen) INTO hay;
  IF hay THEN RAISE EXCEPTION 'SGP-TEST-REV: relación ajena/no demostrable en % (FK %). Limpieza cancelada.',fk.origen,fk.conname; END IF;
 END LOOP;
 -- Relaciones polimórficas no protegidas por FK: no se dejan huérfanas ni se adoptan de terceros.
 IF EXISTS (SELECT FROM notificacion n JOIN rev_owned o ON o.tabla=n.entidad AND o.id=n.entidad_id
            WHERE NOT EXISTS (SELECT FROM rev_owned z WHERE z.tabla='notificacion' AND z.id=n.id))
 OR EXISTS (SELECT FROM auditoria a JOIN rev_owned o ON o.tabla=a.entidad AND o.id=a.entidad_id
            WHERE NOT EXISTS (SELECT FROM rev_owned z WHERE z.tabla='auditoria' AND z.id=a.id))
 OR EXISTS (SELECT FROM operacion_idempotente a JOIN rev_owned o ON o.tabla=a.entidad AND o.id=a.entidad_id
            WHERE NOT EXISTS (SELECT FROM rev_owned z WHERE z.tabla='operacion_idempotente' AND z.id=a.id))
 THEN RAISE EXCEPTION 'SGP-TEST-REV: relación polimórfica no demostrable; limpieza cancelada'; END IF;
END $$;
SELECT tabla,count(*) AS a_eliminar FROM rev_owned GROUP BY tabla ORDER BY tabla;
-- Rompe solo el ciclo de referencias del puente ya demostrado como exclusivo.
UPDATE puente SET estado_actual='Sin evaluar',indice_condicion_actual=NULL,
 fecha_ultima_inspeccion=NULL,inspeccion_vigente_id=NULL,resultado_ic_vigente_id=NULL,
 inspeccion_vigente_publicada_en=NULL
WHERE id IN (SELECT id FROM rev_owned WHERE tabla='puente');
DO $$
DECLARE t text; r record;
BEGIN
 FOREACH t IN ARRAY ARRAY['token_refresco','notificacion','auditoria','operacion_idempotente','foro_hilo',
  'archivo_inspeccion','archivo','confirmacion_estado_inspeccion','resultado_ic',
  'observacion_revision','historial_estado_inspeccion','revision','inspeccion',
  'asignacion_puente','puente','configuracion_ic_elemento','version_formulario',
  'curso_estudiante','curso','asignatura','municipio','departamento','usuario_profesional','usuario'] LOOP
  FOR r IN SELECT * FROM rev_owned WHERE tabla=t LOOP
   EXECUTE format('DELETE FROM %I WHERE %I=$1',r.tabla,r.pk) USING r.id;
  END LOOP;
 END LOOP;
END $$;
COMMIT;
\echo 'Limpieza terminada. Los objetos físicos de fotografías no se eliminan con SQL.'
