# Verification record

Verified locally on 18 November 2026 with Java 21.0.13, Maven 3.9.11 and Spring Boot 3.5.16.

## Results

| Check | Result |
| --- | --- |
| Maven Wrapper download and pinned distribution checksum | Passed |
| Default isolated H2 suite: mvnw.cmd -B -ntp verify | 51 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| MySQL-configured suite against isolated MySQL Community Server 8.4.7 | 51 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| Executable Spring Boot JAR packaging | Passed |
| Real embedded-Tomcat HTTP workflow | Passed: demo startup, registration, session login, CSRF, actual multipart WebP upload, public media delivery, wishlist and search |
| Running local demo and linen search | HTTP 200; preserved fictional catalogue present |

The MySQL-configured run exercises the 33 transactional persistence/web tests against a dedicated MySQL schema. The 17 unit tests do not require a database, and the one real-HTTP regression test intentionally starts its own isolated H2 database and random-port server. This is not a claim that the real-HTTP test uses MySQL.

Both Flyway migrations were applied and Hibernate schema validation succeeded against real MySQL, not just H2 compatibility mode. Flyway currently logs a MySQL 8.4 version-support advisory; migrations and tests nevertheless passed. This advisory is not suppressed.

The temporary MySQL instance used a separate data directory, loopback-only port and generated credentials. It was shut down after verification and its plaintext credential file removed. The machine's existing MySQL80 service was not altered.

## Coverage

- Registration/password hashing, duplicate emails, invalid passwords, seller store creation, role-escalation rejection, profile-edit safety and account deactivation.
- Form login/logout, session authentication, CSRF enforcement, buyer/seller/admin route restrictions and active-account revalidation.
- Product validation, ownership, safe field binding, editing/stale versions, draft/archive states, availability, tag generation and five-image limit.
- Real JPEG/PNG/WebP decoding, bad/oversized input rejection, unique keys, path traversal rejection, image ownership/removal and moderated-image access.
- Multiword keyword relevance, seller search, combined category/price/colour/size/availability filters, sorting, stable pagination, query preservation and escaped markup.
- Persistent idempotent wishlists, ownership, unavailable listings, real review aggregates, no self/duplicate reviews, moderation, review removal and confirmation checks.
- Rendered public, buyer, seller and administrator pages, including populated reviews and empty states.

## Regressions found and fixed during the build

1. An empty relevance query initially generated ORDER BY 0, which H2 rejected. Empty queries now sort by newest timestamp and ID.
2. Real HTTP testing exposed direct reads of lazy Hibernate proxy fields that transaction-scoped fixture tests had masked. Entity reads now use proxy-aware getters; NativeHttpIntegrationTest protects the actual request/session/transaction boundaries with open-in-view disabled.

## Reproduce

Use the test and MySQL test commands in README.md. Surefire writes per-suite XML/text results to target/surefire-reports. Build output, database files, upload files and logs are intentionally ignored by Git.

## Limits

No production deployment, payment processing, real Instagram account integration, external seller verification, load testing or browser-based visual acceptance is claimed. Responsive pages have been rendered through MockMvc and actual HTTP, but they have not been visually inspected in a browser. Optional AI/visual search and structured order requests remain out of scope for this MVP.
