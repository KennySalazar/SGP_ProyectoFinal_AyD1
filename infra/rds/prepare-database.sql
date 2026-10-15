\set ON_ERROR_STOP on

-- Ejecutar una sola vez como el usuario administrador de RDS. Los nombres y
-- contrasenas se leen del entorno de psql para no versionar secretos.
\getenv database_name SGP_DATABASE_NAME
\getenv migrator_user SGP_MIGRATOR_USER
\getenv migrator_password SGP_MIGRATOR_PASSWORD
\getenv app_user SGP_APP_USER
\getenv app_password SGP_APP_PASSWORD

-- Los identificadores se restringen al formato usado por la infraestructura.
-- Esto permite interpolarlos de forma segura en \connect y evita reutilizar el
-- usuario administrador como cuenta de aplicacion o migracion.
SELECT
  :'database_name' ~ '^[a-z][a-z0-9_]{0,62}$'
  AND :'migrator_user' ~ '^[a-z][a-z0-9_]{0,62}$'
  AND :'app_user' ~ '^[a-z][a-z0-9_]{0,62}$' AS identifiers_valid,
  length(:'migrator_password') > 0
  AND length(:'app_password') > 0 AS passwords_present,
  :'migrator_user' <> :'app_user'
  AND :'migrator_user' <> current_user
  AND :'app_user' <> current_user AS roles_are_distinct
\gset

\if :identifiers_valid
\else
  \echo 'Los nombres de base y roles deben usar letras minusculas, numeros o guion bajo.'
  \quit 3
\endif

\if :passwords_present
\else
  \echo 'Las contrasenas del migrador y de la aplicacion son obligatorias.'
  \quit 3
\endif

\if :roles_are_distinct
\else
  \echo 'Los roles migrador, aplicacion y administrador deben ser diferentes.'
  \quit 3
\endif

-- CREATE ROLE y CREATE DATABASE requieren atributos diferentes. Amazon RDS no
-- concede SUPERUSER al usuario maestro, por lo que se validan explicitamente.
SELECT
  rolcreaterole AS admin_can_create_roles,
  rolcreatedb AS admin_can_create_databases
FROM pg_roles
WHERE rolname = current_user
\gset

\if :admin_can_create_roles
\else
  \echo 'El usuario administrador necesita CREATEROLE.'
  \quit 3
\endif

\if :admin_can_create_databases
\else
  \echo 'El usuario administrador necesita CREATEDB.'
  \quit 3
\endif

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

-- Si los roles ya existian, se rechazan privilegios superiores a los previstos.
SELECT
  migrator.rolcanlogin
  AND NOT migrator.rolsuper
  AND NOT migrator.rolcreatedb
  AND NOT migrator.rolcreaterole
  AND NOT migrator.rolinherit AS migrator_is_restricted,
  application.rolcanlogin
  AND NOT application.rolsuper
  AND NOT application.rolcreatedb
  AND NOT application.rolcreaterole
  AND NOT application.rolinherit AS app_is_restricted
FROM pg_roles AS migrator
CROSS JOIN pg_roles AS application
WHERE migrator.rolname = :'migrator_user'
  AND application.rolname = :'app_user'
\gset

\if :migrator_is_restricted
\else
  \echo 'El rol migrador existente posee atributos incompatibles.'
  \quit 3
\endif

\if :app_is_restricted
\else
  \echo 'El rol de aplicacion existente posee atributos incompatibles.'
  \quit 3
\endif

SELECT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = :'database_name'
) AS database_exists
\gset

\if :database_exists
  SELECT database_owner.rolname = :'migrator_user' AS database_owner_matches
  FROM pg_database AS database
  JOIN pg_roles AS database_owner ON database_owner.oid = database.datdba
  WHERE database.datname = :'database_name'
  \gset

  \if :database_owner_matches
  \else
    \echo 'La base existente no pertenece al rol migrador solicitado.'
    \quit 3
  \endif
\else
  -- PostgreSQL 16 exige que quien asigna OWNER pueda hacer SET ROLE al nuevo
  -- propietario. La membresia se concede solo durante CREATE DATABASE.
  SELECT format('GRANT %I TO %I', :'migrator_user', current_user)
  \gexec

  SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'migrator_user')
  \gexec

  SELECT format('REVOKE %I FROM %I', :'migrator_user', current_user)
  \gexec
\endif

\connect :database_name

BEGIN;

-- La membresia temporal habilita SET ROLE durante la preparacion. El REVOKE
-- final elimina SET e INHERIT; PostgreSQL 16 puede conservar el ADMIN OPTION que
-- concede automaticamente a quien crea un rol. Un error revierte esta segunda
-- concesion porque se encuentra dentro de la transaccion.
SELECT format('GRANT %I TO %I', :'migrator_user', current_user)
\gexec

-- PostGIS requiere privilegios rds_superuser en RDS. Las extensiones se crean
-- con el administrador para que Flyway pueda ejecutar V1 sin elevar al migrador.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Las concesiones y privilegios predeterminados se ejecutan como el propietario.
-- De esta forma no dependen de privilegios implícitos del administrador de RDS.
SET ROLE :"migrator_user";

GRANT CONNECT ON DATABASE :"database_name" TO :"migrator_user";
GRANT CONNECT ON DATABASE :"database_name" TO :"app_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"migrator_user";
GRANT USAGE ON SCHEMA public TO :"app_user";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";

-- Permite volver a ejecutar el archivo si Flyway ya creo objetos.
-- Solo modifica objetos propiedad del migrador y evita conceder permisos sobre
-- tablas internas de PostGIS creadas por el administrador.
SELECT format(
  'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %I.%I TO %I',
  schemaname,
  tablename,
  :'app_user'
)
FROM pg_tables
WHERE schemaname = 'public'
  AND tableowner = current_user
\gexec
SELECT format(
  'GRANT USAGE, SELECT ON SEQUENCE %I.%I TO %I',
  schemaname,
  sequencename,
  :'app_user'
)
FROM pg_sequences
WHERE schemaname = 'public'
  AND sequenceowner = current_user
\gexec

RESET ROLE;

SELECT format('REVOKE %I FROM %I', :'migrator_user', current_user)
\gexec

COMMIT;
