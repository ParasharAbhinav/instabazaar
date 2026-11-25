# InstaBazaar

**Saw it. Loved it. Find it again.** A Java full-stack application that helps buyers rediscover independent fashion from social media and connect directly with small sellers.

One Spring Boot application, one Maven build, one database. No React, Node server, Docker, paid AI API or Instagram scraping is required.

## What works

- Buyer/seller registration, BCrypt passwords, form login/logout, profile editing, duplicate email checks and server-enforced BUYER / SELLER / ADMIN permissions.
- Seller storefronts, optional logo and validated contact/social links, product creation/editing, draft/active/archive states, availability, database categories and tags.
- Real JPEG/PNG/WebP uploads: decoded content and dimensions checked, 5 MB / 20 MP limits, generated storage keys, up to five images, ownership checks and protected media delivery.
- Weighted database search across title, descriptions, tags, attributes, category and store name; combined price/category/colour/size/stock filters, relevance/price/newest sorting and 12-item pagination.
- Persistent private wishlists, one review per user/product, self-review prevention, real ratings and administrator moderation.
- Free offline MP4-to-keywords: local visual inference, private bounded background processing, seller-approved tags, editable product prefill and searchable saved attributes. See [docs/VIDEO-TAGGING.md](docs/VIDEO-TAGGING.md).
- Administration with catalogue statistics, paginated account/product/review lists, confirmed deactivation/visibility changes and subsequent-request session invalidation for inactive accounts.
- Responsive, accessible Thymeleaf pages with a locally served Bootstrap 5 stylesheet and boutique illustrations. No external fonts, scripts or image services are needed.

Structured order requests, payments, semantic buyer search, screenshot search and automatic social-account import are **not implemented**. Offline visual tagging is available for uploaded videos; this is a discovery/contact MVP, not a checkout or seller-verification service.

## Requirements

- A **Java 21 JDK**; set JAVA_HOME to its installation folder.
- **MySQL 8.x** for the normal runtime. Use a fresh dedicated database.
- Maven 3.9+ or the included Maven Wrapper (downloads pinned Maven 3.9.11 with SHA-256 verification on first use).
- Internet access for the first Maven dependency download. There is no build-time Node dependency.

## Normal startup: Java + MySQL

Create a fresh database/account in MySQL, replacing the example password with your own:

```sql
CREATE DATABASE instabazaar CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'instabazaar'@'localhost' IDENTIFIED BY 'choose-a-strong-local-password';
GRANT ALL PRIVILEGES ON instabazaar.* TO 'instabazaar'@'localhost';
```

The local account needs schema privileges for Flyway. For deployment, use a separate migration account and restrict the runtime account to the operations it needs.

PowerShell, from this repository:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:DB_URL = 'jdbc:mysql://localhost:3306/instabazaar?connectionTimeZone=UTC'
$env:DB_USERNAME = 'instabazaar'
$env:DB_PASSWORD = 'your-local-password'
.\mvnw.cmd spring-boot:run
```

With Maven already installed, the primary command is simply **mvn spring-boot:run**. On macOS/Linux use `sh ./mvnw spring-boot:run` after exporting the same environment variables.

Open **http://localhost:8080**. Flyway creates the schema and six clothing categories; Hibernate validates the schema rather than silently updating it. Stop the foreground app with Ctrl+C. Create a buyer or seller account at `/register`; sellers also retain buyer features.

Alternatively, copy `.env.example` to `.env`, fill in its values, then run `powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1`. The script loads only documented keys as data, not executable PowerShell. Direct Maven commands do **not** automatically load `.env`.

## Quick demonstration without MySQL

For an explicitly opt-in local demonstration:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1 -Demo
```

Or `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'`. This profile uses a persistent local **H2** database at `data/instabazaar`, not MySQL. A fresh database imports six original illustrated listings plus ten photo-backed listings across three fictional stores. Existing demo databases receive the ten additional listings without replacing their products or accounts; repeated startup does not duplicate them. It is not the production database configuration.

The launch script can discover the portable Java 21 installation in `%LOCALAPPDATA%\InstaBazaar\tools` prepared during this build; otherwise install Java 21 normally.

For the same sample catalogue on MySQL, use `scripts/run.ps1 -Dev` or `spring-boot:run '-Dspring-boot.run.profiles=dev'` with MySQL settings. Demo accounts are `seller@example.test`, `edit@example.test`, `trail@example.test` and `buyer@example.test`. Set **DEMO_PASSWORD** to your own password of at least 12 characters **before the account's first seeded startup** to enable its login. Without it new accounts receive an unknown random password; register your own account instead. Changing DEMO_PASSWORD later does not reset existing users.

We do not seed fake reviews or fabricated social-post URLs. Sample illustration files and the original catalogue were preserved from the existing repository; see [docs/MIGRATION.md](docs/MIGRATION.md). The ten additional listings use distinct locally bundled stock photographs, with sources in [docs/DEMO-PHOTOS.md](docs/DEMO-PHOTOS.md). They include clothing, footwear and accessories, meaningful search tags and sample prices/stock, not real offers for sale. The demo seeder adds Accessories and Footwear categories if absent; production seeding remains disabled.

## Create an administrator explicitly

Public registration cannot create or promote an administrator. For **local dev/demo only**, set ADMIN_EMAIL and ADMIN_PASSWORD (12+ characters, at most 72 UTF-8 bytes), then start with dev or demo profile. The account is created only if the email does not already exist; existing accounts are never silently promoted or password-reset. Unset these variables after creation. There are no built-in administrator credentials. Production provisioning requires your own reviewed account-management procedure.

## Tests and packaging

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
java -jar target\instabazaar-1.0.0-SNAPSHOT.jar
```

The default isolated test profile uses in-memory H2 in MySQL compatibility mode, real Flyway migrations, JPA and Spring Security. Tests cover rendered public/seller/admin pages, auth and CSRF, safe registration, product validation/ownership/images, relevance/filters/sorting/pagination, wishlists, reviews/ratings/moderation, URL validation and traversal rejection.

The suite also includes real embedded-server HTTP tests for committed data, detached entities, sessions, CSRF and multipart uploads. Offline video inference and its real upload/approval/search workflow can be tested after asset setup with `verify '-Dvideo.model.tests=true'`; those external-asset tests are explicitly opt-in. See [docs/VERIFICATION.md](docs/VERIFICATION.md) and [docs/VIDEO-TAGGING.md](docs/VIDEO-TAGGING.md) for coverage and limitations.

To run the **same integration tests against an actual disposable MySQL 8 database**, create a separate empty test schema/account and set:

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/instabazaar_test?connectionTimeZone=UTC'
$env:TEST_DB_USERNAME = 'instabazaar_test'
$env:TEST_DB_PASSWORD = 'your-test-password'
$env:TEST_DB_DRIVER = 'com.mysql.cj.jdbc.Driver'
.\mvnw.cmd verify
```

Never use a production or shared database for tests. Test data is transactionally rolled back; Flyway's schema/categories persist in that dedicated database. Unset TEST_DB_* to return to isolated H2 tests. GitHub Actions runs the default suite with Java 21.

## Configuration

| Variable | Default / purpose |
| --- | --- |
| DB_URL | MySQL JDBC URL, localhost:3306/instabazaar, UTC connection time zone |
| DB_USERNAME / DB_PASSWORD | instabazaar / blank; supply your database credentials |
| PORT | 8080 |
| BIND_ADDRESS | 127.0.0.1; set an appropriate interface explicitly for deployment |
| UPLOAD_DIR | ./uploads; private app-managed storage, not a static web directory |
| SESSION_COOKIE_SECURE | false for local HTTP; **true when deployed behind HTTPS** |
| DEMO_PASSWORD | No known default; dev/demo seed accounts only |
| ADMIN_EMAIL / ADMIN_PASSWORD | Blank; explicit dev/demo administrator bootstrap only |

No secrets, local databases, uploaded media, logs or build output are committed. Do not reuse demo databases/accounts in production. Database migration and file backups must be coordinated for a deployment.

## Routes and architecture

- Public: `/`, `/products`, `/products/{id}`, `/categories/{id}`, `/sellers`, `/sellers/{id}`, `/login`, `/register`.
- Buyer features: `/wishlist`, `/profile`, product review submission/removal.
- Seller: `/seller/dashboard`, `/seller/store`, `/seller/products`, `/seller/products/new`, `/seller/products/{id}/edit`.
- Video tagging: `/seller/videos/new`, `/seller/videos/{id}`; seller-only upload, processing and review.
- Admin: `/admin`, `/admin/users`, `/admin/products`, `/admin/reviews`.
- Media: `/media/{generated-key}`; visibility is checked against the owning product/account, not just the URL.

Source is organized under `com.instabazaar` into config, controller, dto, entity, enums, exception, repository, security, service, service.impl and util. Controllers bind explicit validated form records rather than entities. Transactions and business rules live in services. JPA repositories handle persistence; templates consume read-only view records with open-in-view disabled. Flyway owns schema changes.

Search removes common stop words and handles up to 16 distinct query tokens; all remaining tokens must match. Each token scores title 10, tags 7, attributes 5, store 4, description 1 (highest applicable match per token). Ties use newest timestamp then ID. Queries without tokens use newest first. TagSuggestionService provides an isolated extension seam; the supplied implementation is deterministic Java keyword matching/aliases, not an AI model.

## Troubleshooting and deployment limits

- **JAVA_HOME / release 21 errors:** point JAVA_HOME at a JDK 21 directory, not its bin folder; restart your terminal after changing system environment settings.
- **Connection refused / access denied:** start MySQL, check DB_URL, create the schema and user, and verify the user's host/grants. The normal profile intentionally does not fall back to H2.
- **Port already used:** stop the previous app or set PORT to another port. Only one demo process can open the same H2 file at a time.
- **Sign-in or upload fails:** obtain a fresh form/session; CSRF protection is enabled. Uploads must be actual supported images, not renamed files; up to 5 MB each. The storage directory must be writable.
- **Video tools missing:** run `scripts/setup-video.ps1`, restart the app, and use a seller account. MP4 limits are 30 MB / 60 seconds / 1080p; manual entry still works without the optional local model installation.
- **Flyway/schema mismatch:** use a new schema for this implementation. Never edit an already-applied migration or point it at the upstream PostgreSQL database. See the migration audit before moving real data.
- **No sample login:** register your own account or set DEMO_PASSWORD before first seed. Sample products are not real inventory or offers for sale.
- **Production:** configure HTTPS/secure cookies, a production MySQL account, backup/restore, monitoring and operational rate limiting before public deployment. This local MVP is not a claim of production hardening or external seller verification.

Bootstrap 5.3.8 is vendored locally with its original license in `static/vendor`.
