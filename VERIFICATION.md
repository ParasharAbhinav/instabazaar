# Verification

Run these checks from a fresh checkout. Generated reports and build products are excluded from version control. Historical reports from the input release ZIP are not included as evidence of a fresh run.

## Frontend

```sh
cd frontend
npm ci
npm test
npm run build
```

The build runs TypeScript, Vite, and the production bundle fixture guard. To audit installed production dependencies, run `npm audit --omit=dev --audit-level=high` with registry access.

## Backend

Use JDK 21. From `backend/`:

```powershell
.\mvnw.cmd verify
```

On Linux/macOS, use `sh ./mvnw verify`. Inspect `target/surefire-reports/` for actual test totals and skipped cases.

Database suites are opt-in. Provision a separate disposable PostgreSQL database with a login that can apply migrations. Export:

- `INSTA_INTEGRATION_TEST=true`
- `TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/instabazaar_test`
- `TEST_DB_USERNAME`: disposable test database login.
- `TEST_DB_PASSWORD`: that login's password.

Then rerun Maven verification. These suites modify fixtures and the foundation marker; use a disposable database. CI supplies one in a service container.

## SQL checks

`verification/acceptance.sql` contains read-only application database checks. `verification/schema-check.sql` loads the schema contract harness and requires a separate empty scratch database. The harness applies its DDL and fixtures in a transaction and rolls them back. Run with `psql -v ON_ERROR_STOP=1 -f <script>` and appropriate connection parameters.

## Container checks

Copy the root `.env.example` to `.env`, populate the three passwords, then run:

```sh
docker compose up -d --build --wait
docker compose ps
```

Verify `https://localhost/api/v1/health` using the proxy certificate as a trusted local CA. The proxy generates a self-signed certificate for local use. CI exercises this workflow and removes its disposable volumes afterward.

## Scope

A successful build does not establish browser end-to-end correctness, operational readiness, or production security acceptance. No new hosted GitHub Actions result is bundled with this source package. Consult the separate cleanup report supplied with the cleaned release for checks actually run during preparation.

## Reel and sample regression checks

Frontend `listing.test.ts` covers detail-only keyword extraction, hashtags/Unicode, tag merging and bounds, and Reel URL validation. Backend tests cover HTTP caption validation and database create/edit/read persistence with public-response exclusion. The sample seed script exercises the normal registration, login, CSRF, media, product publication, wishlist, and pending-review routes. See the accompanying sample delivery report for checks actually performed.
