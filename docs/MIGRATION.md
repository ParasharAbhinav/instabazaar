# Repository inspection and continuity

## Inspected sources

The local T3 workspace initially contained only README.md, assets/icon.svg and Git history at 96c7330. It had no configured remote, Java code, database or frontend to overwrite.

The public GitHub repository ParasharAbhinav/instabazaar was accessible without a connector. Its HEAD was fetched and inspected at **438926a23706da0abd0bc4f2a735b6b64ca3bfb7**, not the earlier ee24d9d referenced in the conversation. No upstream files, branches or commits were changed or pushed.

The upstream snapshot was inventoried across backend, frontend, migrations, verification, scripts, workflows and documentation. Detailed inspection focused on the application dependencies, domain model, security configuration, catalogue controllers/repositories, PostgreSQL search, migrations, enrichment services, tests and sample catalogue.

## What existed and how it maps

| Upstream | This application |
| --- | --- |
| Java 21 / Spring Boot 3.5.16 backend | Retained stack and domain concepts; one Maven application at the project root |
| React / TypeScript / Vite frontend and separate API | Server-rendered Spring MVC / Thymeleaf pages, Bootstrap 5 and vanilla JavaScript; no Node runtime |
| PostgreSQL Flyway migrations and tsvector / ts_rank queries | New MySQL 8-compatible Flyway schema and parameterized JPA Criteria search |
| Session API / SPA CSRF conventions | Spring Security form login, CSRF-protected forms, service-level role/ownership checks, active-account revalidation |
| ControlledVocabularyTagProvider / ReelTranscriptEnrichmentService | Adapted fashion vocabulary and deterministic aliases behind TagSuggestionService; no AI or scraping dependency |
| Six fictional catalogue entries | Preserved verbatim in src/main/resources/demo/catalogue.json; loaded only in dev/demo |
| Boutique SVG illustrations | Preserved hero.svg, look-0.svg through look-3.svg and product-placeholder.svg |
| Raw SQL / API tests tied to old architecture | New JUnit / MockMvc tests exercise this application's actual services, persistence, security and rendered pages |
| Docker / PostgreSQL / frontend launch scripts | Not brought into the new primary runtime; run one application via Maven and MySQL |

This is a new implementation in the initially empty local workspace, **not** an in-place PostgreSQL data migration or a claim that the upstream API remains compatible. The original upstream implementation remains intact on GitHub. Existing production data would need an explicit export/mapping/import plan before switching applications; do not point this schema at an old database.

## Phase status

1. Inspection and plan: completed; rationale recorded here.
2. Foundation: Java 21, Maven Wrapper, Boot, MySQL configuration, Flyway and JPA.
3. Data model/authentication: accounts, roles, safe registration, profiles and sessions.
4. Seller catalogue: storefronts, products, image storage, soft deletion by archiving and tags.
5. Discovery: weighted keyword search, combined filters, sorting and database pagination.
6. Buyer features: persistent wishlists, real reviews/ratings and direct seller contact.
7. Administration: paginated account/product/review management with confirmation.
8. Polish/verification: responsive templates, regression tests and local setup documentation.
9. Optional extensions: intentionally deferred. No semantic/image search, Instagram synchronization, order requests, payments or cart is represented as implemented.

There is no silent account verification, fabricated purchase verification, fake review generation or hardcoded production password. SVGs are labelled illustrations and sample products/stores are explicitly labelled demo content.

## Follow-up: offline video rediscovery

The original migration's optional AI boundary has now been extended with local MP4 visual tagging. CLIP/ONNX Runtime and FFmpeg suggest a fixed vocabulary without a paid API, and seller approval/product save are required before indexing. Migration V3 adds private analysis job metadata without altering existing products. Automatic Instagram import, audio transcription, semantic query search, buyer screenshot search and multi-product video segmentation remain deferred. See VIDEO-TAGGING.md for setup, permissions, limits and model provenance.
