# InstaBazaar

A social-commerce catalogue with a React and TypeScript frontend, a Java 21 Spring Boot backend, PostgreSQL, and Flyway migrations. Buyers discover listings and contact sellers through WhatsApp. Sellers manage stores, listings, images, and tags; administrators moderate content and reports.

The application includes session authentication, CSRF protection, search, wishlists, and reviews. Payments, carts, and checkout are outside its current scope.

## Repository

- `frontend/`: React, Vite, API client, UI, and frontend tests.
- `backend/`: Spring Boot application, Maven wrapper, migrations, and Java tests.
- `docker/` and `docker-compose.yml`: database roles and the container stack.
- `scripts/`: Windows launchers and volume backup/restore utilities.
- `verification/`: SQL acceptance queries and schema checks.
- `.github/workflows/ci.yml`: backend, frontend, and Compose checks.

## Run with Docker Compose

Copy `.env.example` to `.env` at the repository root. Set three distinct passwords for `POSTGRES_SUPERUSER_PASSWORD`, `MIGRATOR_DB_PASSWORD`, and `APP_DB_PASSWORD`, then run:

```sh
docker compose up -d --build --wait
docker compose ps
```

Open https://localhost. The bundled proxy generates a self-signed development certificate, so the browser requires local certificate trust. A public deployment needs a certificate and domain configuration appropriate to that deployment.

```sh
docker compose down
```

Database and media data live in named volumes. The `-v` option deletes those volumes; reserve it for disposable environments.

## Run locally on Windows

Install JDK 21, Node.js 22.12 or later, and PostgreSQL. Set `JAVA_HOME` to your JDK directory. Provision the database and migration/runtime roles first, or start only the Compose database services with the configured root `.env`:

```powershell
docker compose up -d postgres db-role-reconciler
```

Export the settings listed in `backend/.env.example` into your PowerShell session. Use the same application and migration passwords as the database roles. Spring Boot does not automatically read `.env` files. For an interactive password prompt:

```powershell
$privatePassword = Read-Host 'Runtime database password' -AsSecureString
$env:SPRING_DATASOURCE_PASSWORD = [System.Net.NetworkCredential]::new('', $privatePassword).Password
$env:SPRING_FLYWAY_USER = 'instabazaar_migrator'
$privatePassword = Read-Host 'Migration database password' -AsSecureString
$env:SPRING_FLYWAY_PASSWORD = [System.Net.NetworkCredential]::new('', $privatePassword).Password
Remove-Variable privatePassword
.\scripts\Start-Backend.ps1
```

The launcher defaults to the `dev` profile for local HTTP cookies, the database URL `jdbc:postgresql://127.0.0.1:5432/instabazaar`, and runtime user `instabazaar_runtime`. Database bootstrap is disabled in this profile. Keep the terminal running.

In another terminal:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open http://localhost:5173. Vite proxies `/api` to the backend on port 8080. The health endpoint is http://localhost:8080/api/v1/health. Alternatively, after installing frontend dependencies and exporting database settings, `start-local.ps1` opens separate interactive terminals for both servers.

## Verify

```powershell
cd frontend
npm.cmd test
npm.cmd run build
```

```powershell
cd backend
.\mvnw.cmd verify
```

On Linux/macOS, use `sh ./mvnw verify` in `backend`. Database tests require `INSTA_INTEGRATION_TEST=true` and `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` pointing at a separate disposable PostgreSQL database. See [VERIFICATION.md](VERIFICATION.md) for details and verification limits.

## Configuration and data

Commit the example environment files with blank passwords. Keep real `.env` files, private keys, backups, media uploads, logs, dependency directories, and generated test/build output outside Git. Frontend `VITE_` variables are public browser configuration.

Flyway owns schema evolution. Keep applied migrations unchanged; add a migration for schema changes. Container startup uses a separate migration role and a restricted runtime role. See [backend/API-ROUTES.md](backend/API-ROUTES.md) for the API route inventory.

The frontend and API should share an HTTPS origin when deployed. Configure allowed origins and media URLs for the target deployment. This repository does not establish that a public deployment has passed operational or security acceptance.

## Sample workspace and Reel listings

Open `/sample` or run `npm run sample` from `frontend/` for an interactive example with six fictional products. The seller editor now accepts an Instagram Reel link and an optional private caption/transcript. It suggests editable search keywords from complete product details, even without a Reel. See [SAMPLE-GUIDE.md](SAMPLE-GUIDE.md) for the preview, local API seed script, sample accounts, and V5 migration.
