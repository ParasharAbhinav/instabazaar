# Sample project and Reel workflow

## Try it without a database

From `frontend/`:

```powershell
npm.cmd ci
npm.cmd run sample
```

Open the `/sample` page if the browser does not open automatically. The sample workspace is available independently of backend authentication.

1. Choose **Load complete sample details** to fill the seller form.
2. Optionally paste your own Instagram Reel URL, such as a real `https://www.instagram.com/reel/<shortcode>/` URL.
3. Paste the Reel caption or transcript, or leave it blank and use the full product details.
4. Choose individual keyword chips or **Add suggested keywords**. Edit the comma-separated tags as needed.
5. Optionally upload an image for local preview.
6. Choose **Preview sample listing** to see the buyer-facing result. This page does not send product data or uploads to the server.
7. Switch to **Sample storefront** to explore six fictional products. Try searching `denim`, `chanderi`, `linen`, or `workwear`.

This sample is intentionally separate from the live catalogue. Reloading or leaving the seller tab clears its unsaved preview state. The illustrations are placeholders, not product photographs. No fake external Reel is supplied; paste a Reel you own or have permission to reference.

## Load the sample into the real local backend

Start PostgreSQL and the backend using the root README. Use a disposable development database: this command creates real local application records. Keep the `dev` profile active for HTTP cookies, and apply migrations V1-V5 through normal backend startup.

From the repository root, with Node.js 22.12 or later:

```powershell
$privatePassword = Read-Host 'Choose a sample account password (12-128 characters)' -AsSecureString
$env:INSTABAZAAR_SAMPLE_PASSWORD = [System.Net.NetworkCredential]::new('', $privatePassword).Password
try {
    node .\scripts\seed-sample.mjs --base-url=http://localhost:8080/api/v1
} finally {
    Remove-Item Env:INSTABAZAAR_SAMPLE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable privatePassword -ErrorAction SilentlyContinue
}
```

If the password environment variable is omitted, the script generates and prints a random password before creating accounts. Save it locally for login and reruns. No password is written into the project files.

Created records:

| Sample | Details |
| --- | --- |
| Seller | `seller@example.test` |
| Buyer | `buyer@example.test` |
| Store | Sample Loom Studio |
| Catalogue | Six public listings covering the six seeded categories |
| Images | Generated colour swatches, explicitly labelled as sample placeholders |
| Buyer activity | First product saved to the wishlist and a pending sample review |

Sign in using the sample email and the password you chose. The seller can edit listings, replace colour swatches with real images, attach Reel links, and review keywords. The buyer can browse, search, save, and review listings. The review stays pending until an existing authorized administrator moderates it. The seed script does not create administrator privileges or bypass the application's role checks.

Rerun with the same password to reuse accounts, product titles, the wishlist item, and the review. Existing matching listings are left as they are. The script accepts only loopback API hosts; it cannot target a remote deployment.

## Reel and keyword behavior

- The Reel option accepts HTTPS `instagram.com/reel/...` or `instagram.com/reels/...` links and strips tracking parameters when saved through the editor. It opens Instagram in a new tab; no embed or scraping is required.
- Captions/transcripts are optional, editable, limited to 2,000 characters, and stored as seller metadata in the new `product.reel_caption` column. Public product DTOs exclude this text.
- Keyword suggestions use the title, category, colour, material, occasion, brand, description, and caption. They work when complete product details are written without a Reel.
- Suggestions are local, deterministic text extraction. They do not claim to understand video or transcribe audio. English fashion terms, hashtags and Unicode words are supported; this is not semantic translation or AI inference.
- Selecting a suggestion adds it to the editable seller tags. Up to 20 seller tags are saved. Approved tags feed the existing catalogue search index; suggestions alone do not publish anything.
- The existing saved-listing **Generate suggestions** workflow also reads the caption when generating controlled-vocabulary suggestions. System suggestions still require approval.
- Product saving retains normal ownership, session/CSRF, validation and optimistic-version checks.

## Migration

`V5__seller_reel_caption.sql` adds one nullable column. V1-V4 are unchanged. Existing listings remain valid with an empty caption. Run normal backend startup to apply V5; keep applied migration files intact.
