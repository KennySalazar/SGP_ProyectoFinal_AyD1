\set ON_ERROR_STOP on

-- Ejecutar una sola vez como el usuario administrador de RDS. Los nombres y
-- contrasenas se leen del entorno de psql para no versionar secretos.
\getenv database_name SGP_DATABASE_NAME
\getenv migrator_user SGP_MIGRATOR_USER
\getenv migrator_password SGP_MIGRATOR_PASSWORD
\getenv app_user SGP_APP_USER
\getenv app_password SGP_APP_PASSWORD

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
  :'migrator_user',
  :'migrator_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migrator_user')
\gexec

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
  :'app_user',
  :'app_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'migrator_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'database_name')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'migrator_user')
\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'app_user')
\gexec

\connect :database_name

-- PostGIS requiere privilegios rds_superuser. Se crea con el administrador para
-- que Flyway pueda ejecutar V1 sin elevar al usuario migrador.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'migrator_user')
\gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app_user')
\gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
  :'migrator_user',
  :'app_user'
)
\gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I',
  :'migrator_user',
  :'app_user'
)
\gexec

-- Permite volver a ejecutar el archivo si Flyway ya creo objetos.
SELECT format(
  'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I',
  :'app_user'
)
\gexec
SELECT format(
  'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I',
  :'app_user'
)
\gexec
