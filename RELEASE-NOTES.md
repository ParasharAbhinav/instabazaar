# Sample and Reel release notes

Prepared 5 November 2026.

## Added

- Public `/sample` workspace with a working seller editor, complete example details, six fictional catalogue products, local search, image preview and buyer-facing listing preview.
- Instagram Reel link and optional caption/transcript in the real seller editor. Tracking parameters are removed from new Reel links; existing unchanged social listing URLs are preserved.
- Live keyword suggestions from product details and caption, individually selectable chips, editable manual tags and duplicate/limit handling. Complete product information works without a Reel.
- Private caption persistence through product create/edit/read, with V5 migration. V1-V4 remain byte-for-byte unchanged. Public product responses exclude the working caption.
- Local sample loader that uses normal authentication, CSRF, uploads, product, publication, wishlist and review APIs. It creates no administrator account and uses no committed password.
- Corrected Flyway fallback to the resolved datasource properties, including integration-test overrides. CI supplies explicit test migration credentials.
- `SAMPLE-GUIDE.md` with quick start, seed command, sample accounts, behavior and migration details.

## Verified

| Check | Result |
| --- | --- |
| Frontend tests | 23 passed in 4 files |
| Frontend production build | Passed TypeScript, Vite and obsolete-fixture guard |
| Backend Maven verify with JDK 21 and isolated PostgreSQL 18 | 99 tests passed; no failures, errors or skipped cases; executable JAR built |
| Caption regression | Create/edit persistence and public-response exclusion passed |
| Real sample API loader | Passed first run and repeat run |
| Seeded database after repeat | 2 users, 1 store, 6 public products, 6 images, 6 captions, 1 wishlist item and 1 pending review |
| Real catalogue search | `chanderi` returned the seeded kurta |
| Browser checks | Sample fill, keyword selection, preview creation, Reel-link validation and six-product catalogue search passed |
| Git hygiene | Source-only packaging, whitespace/ignore checks and original migration hashes checked |

The runtime test database, dependencies, generated build/test files and test credentials are excluded from this source package. Docker Compose and a hosted GitHub Actions run were not executed during this feature delivery.

The Reel workflow uses seller-provided text. It does not download Instagram content or transcribe video/audio. The sample workspace is separate from the live catalogue. The complete application source is included, with its existing buyer/seller/admin role boundaries.

## Commit

From your repository root, review and commit the extracted source files:

```sh
git add .
git diff --cached --check
git commit -m "Add sample catalogue and Reel keyword workflow"
```

No GitHub repository, commit or push was created during this task. The earlier cleaned release remains available separately.
