#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 \
     -v migrator_pw="$MIGRATOR_DB_PASSWORD" \
     -v app_pw="$APP_DB_PASSWORD" \
     --host="${PGHOST:-localhost}" \
     --username="$POSTGRES_USER" \
     --dbname="$POSTGRES_DB" <<'EOSQL'
SELECT set_config('instabazaar.migrator_pw', :'migrator_pw', false);
SELECT set_config('instabazaar.app_pw', :'app_pw', false);

DO $$
DECLARE
    v_migrator_pw text := current_setting('instabazaar.migrator_pw');
    v_app_pw text := current_setting('instabazaar.app_pw');
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'instabazaar_migrator') THEN
        EXECUTE format('CREATE ROLE instabazaar_migrator WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', v_migrator_pw);
    ELSE
        EXECUTE format('ALTER ROLE instabazaar_migrator WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', v_migrator_pw);
    END IF;

    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'instabazaar_runtime') THEN
        EXECUTE format('CREATE ROLE instabazaar_runtime WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', v_app_pw);
    ELSE
        EXECUTE format('ALTER ROLE instabazaar_runtime WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', v_app_pw);
    END IF;
END
$$;

GRANT CONNECT ON DATABASE :"POSTGRES_DB" TO instabazaar_migrator, instabazaar_runtime;
GRANT USAGE, CREATE ON SCHEMA public TO instabazaar_migrator;
GRANT USAGE ON SCHEMA public TO instabazaar_runtime;
REVOKE CREATE ON SCHEMA public FROM PUBLIC, instabazaar_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO instabazaar_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO instabazaar_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE instabazaar_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO instabazaar_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE instabazaar_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO instabazaar_runtime;
EOSQL