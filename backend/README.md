# Backend

Java 21, Spring Boot, Maven wrapper, PostgreSQL, and Flyway. Run commands from this directory.

## Configuration

Export the variables in `.env.example` into your terminal. Spring Boot does not load that file automatically. Database credentials have no committed defaults.

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`: runtime database connection.
- `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD`: migration connection. If omitted, Flyway falls back to runtime credentials, which must then have migration privileges.
- `INSTABAZAAR_DATABASE_BOOTSTRAP_ENABLED=false`: use an already provisioned database.
- `SPA_ALLOWED_ORIGINS`: comma-separated exact frontend origins; defaults to `http://localhost:5173`.
- `SESSION_COOKIE_SECURE`: defaults to true. The `dev` profile uses false for local HTTP development.

The root Docker Compose stack provisions separate migration/runtime roles. See the root README for setup.

## Run

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

On Linux/macOS, use `sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. The service listens on port 8080; health is `/api/v1/health`.

The optional `LocalDatabaseBootstrap` can create a missing local database when explicitly enabled and given sufficient privileges. Normal development setup uses a provisioned database with bootstrap disabled.

## Verify

```powershell
.\mvnw.cmd verify
```

Database integration tests require `INSTA_INTEGRATION_TEST=true` and `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` for a disposable database. Generated reports are in `target/surefire-reports/` and are ignored by Git. See the root `VERIFICATION.md` and `API-ROUTES.md` for more detail.
