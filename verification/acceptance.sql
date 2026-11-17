-- Read-only checks; run with psql -v ON_ERROR_STOP=1 against the intended database.
SELECT current_database(), current_user, version();
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls
FROM pg_roles WHERE rolname = current_user;
SELECT version, description, script, checksum, success
FROM flyway_schema_history ORDER BY installed_rank;
SELECT count(*) AS foundation_rows FROM foundation_metadata;
SELECT count(*) AS categories FROM category;
SELECT count(*) AS domain_tables FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
AND table_name NOT IN ('foundation_metadata', 'flyway_schema_history');
SELECT table_name, column_name, data_type, is_generated, generation_expression
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'product_tag' AND column_name = 'normalized_tag';
