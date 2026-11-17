-- Run with psql -v ON_ERROR_STOP=1 -f verification/schema-check.sql
-- Select a dedicated EMPTY test database. Every change is rolled back on success.
BEGIN;
\ir ../backend/src/main/resources/db/migration/V1__foundation.sql
\ir ../backend/src/main/resources/db/migration/V2__domain_models.sql
\ir ../backend/src/main/resources/db/migration/V3__domain_indexes_and_categories.sql
\ir ../backend/src/main/resources/db/migration/V4__database_owned_tag_keys.sql
\ir ../backend/src/main/resources/db/migration/V5__seller_reel_caption.sql
\ir schema-contract.sql
