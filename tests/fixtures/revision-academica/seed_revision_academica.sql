-- Uso con psql -X -v ON_ERROR_STOP=1. No es una migración Flyway.
\set ON_ERROR_STOP on
\if :{?fixture_db}
\else
  \echo 'Falta -v fixture_db=nombre_exacto_de_la_base_de_pruebas'
  \quit 3
\endif
\if :{?fixture_password_hash}
\else
  \echo 'Falta -v fixture_password_hash=hash_BCrypt_generado_fuera_del_repositorio'
  \quit 3
\endif
BEGIN;
SET LOCAL search_path = pg_temp, public;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SELECT set_config('sgp.fixture_db', :'fixture_db', true) AS fixture_target,
       set_config('sgp.fixture_password_hash', :'fixture_password_hash', true) AS fixture_hash
\gset
SELECT pg_advisory_xact_lock(736724, 35039);
DO $$
DECLARE t record;
BEGIN
 IF current_database() <> current_setting('sgp.fixture_db') THEN
  RAISE EXCEPTION 'SGP-TEST-REV: la base conectada no coincide con fixture_db';
 END IF;
 IF current_setting('sgp.fixture_password_hash') !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$' THEN
  RAISE EXCEPTION 'SGP-TEST-REV: proporcione un hash BCrypt válido, nunca una contraseña';
 END IF;
 IF EXISTS (SELECT n::text FROM generate_series(1,25) n
            EXCEPT SELECT version FROM flyway_schema_history WHERE success)
    OR EXISTS (SELECT 1 FROM flyway_schema_history WHERE NOT success) THEN
  RAISE EXCEPTION 'SGP-TEST-REV: se requieren migraciones oficiales V1 a V25 sin fallos';
 END IF;
 IF (SELECT count(*) FROM rol WHERE nombre IN
     ('ADMINISTRADOR','CATEDRATICO','ESTUDIANTE','PROFESIONAL_EXTERNO')) <> 4 THEN
  RAISE EXCEPTION 'SGP-TEST-REV: faltan roles oficiales';
 END IF;
 -- La comprobación de identidad y el INSERT deben ver el mismo conjunto de filas.
 FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public'
  AND tablename=ANY(ARRAY['usuario','usuario_profesional','departamento','municipio',
   'asignatura','curso','curso_estudiante','version_formulario','configuracion_ic_elemento',
   'puente','asignacion_puente','inspeccion','historial_estado_inspeccion','revision'])
  ORDER BY tablename LOOP
  EXECUTE format('LOCK TABLE public.%I IN SHARE ROW EXCLUSIVE MODE',t.tablename);
 END LOOP;
END $$;
\ir fixture_revision_academica.sql
DO $$
DECLARE r record; columnas text; valores text;
BEGIN
 FOR r IN SELECT * FROM rev_fixture ORDER BY orden LOOP
  -- Idempotencia semántica: una fila reconocida se conserva íntegra, incluso su contraseña.
  SELECT string_agg(format('%I',key),',' ORDER BY key),
         string_agg(format('x.%I',key),',' ORDER BY key)
    INTO columnas,valores FROM jsonb_object_keys(r.datos) key;
  EXECUTE format('INSERT INTO %I (%s) SELECT %s FROM jsonb_populate_record(NULL::%I,$1) x
    WHERE NOT EXISTS (SELECT 1 FROM %I WHERE %I=$2::uuid)',
    r.tabla,columnas,valores,r.tabla,r.tabla,r.pk)
    USING r.datos,r.datos->>r.pk;
 END LOOP;
END $$;
SELECT tabla,count(*) AS registros_definidos FROM rev_fixture GROUP BY tabla ORDER BY tabla;
COMMIT;
\echo 'Fixture preparado. Reejecutarlo conserva todo progreso; no crea fotos ni resultados funcionales.'
