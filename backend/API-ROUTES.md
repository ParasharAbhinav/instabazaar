# Tier 3 API reference

All paths below start with /api/v1. GET also supports HEAD. Mutations require the XSRF-TOKEN cookie and matching raw X-CSRF-TOKEN header. All JSON bodies are strict: unknown fields are rejected.

| Method | Path | Access | Response |
|---|---|---|---|
| GET | /auth/csrf | Public | 200 {headerName, token} |
| POST | /auth/register | Public + CSRF | 201 User |
| POST | /auth/login | Public + CSRF | 200 User; session established |
| POST | /auth/logout | Public + CSRF; idempotent | 204 |
| GET | /me | Any account role | 200 User |
| PUT | /me | Any account role | 200 User |
| GET | /categories | Public | 200 Items<Category> |
| GET | /products | Public | 200 Page<ProductSummary> |
| GET | /products/{id} | Public | 200 ProductDetail |
| GET | /products/{id}/reviews | Public | 200 Page<Review> |
| GET | /sellers/{id} | Public | 200 Store |
| GET | /health | Public | 200 or 503 HealthResponse |
| GET | /seller/products | SELLER | 200 Page<ManagedProduct> |
| GET | /seller/products/{id} | SELLER owner | 200 ManagedProduct |
| POST | /seller/products | SELLER | 201 ManagedProduct |
| PUT | /seller/products/{id} | SELLER owner | 200 ManagedProduct |
| PATCH | /seller/products/{id}/status | SELLER owner | 200 ManagedProduct |
| GET | /seller/products/{id}/tags | SELLER owner | 200 TagSet |
| POST | /seller/products/{id}/tag-suggestions | SELLER owner | 200 TagSet |
| PATCH | /seller/products/{id}/tags/{tagId} | SELLER owner | 200 TagSet |
| GET | /seller/store | SELLER | 200 Store |
| PUT | /seller/store | SELLER | 201 Store on creation; 200 on update |
| POST | /seller/media | SELLER | 201 Asset |
| DELETE | /seller/media/{id} | SELLER owner | 204 |
| GET | /media/{id} | Public attachment or authenticated owner | 200 image bytes |
| GET | /me/wishlist | Any account role | 200 Page<SavedProduct> |
| PUT | /me/wishlist/{productId} | Any account role | 204, idempotent |
| DELETE | /me/wishlist/{productId} | Any account role | 204, idempotent |
| GET | /me/reviews | Any account role | 200 Page<Review> |
| POST | /products/{id}/reviews | Any account role | 201 Review |
| PUT | /reviews/{id} | Review owner | 200 Review |
| POST | /reports | Any account role | 201 Report |
| GET | /admin/statistics | ADMIN | 200 Statistics |
| GET | /admin/users | ADMIN | 200 Page<User> |
| PATCH | /admin/users/{id}/status | ADMIN | 200 User |
| GET | /admin/products | ADMIN | 200 Page<ManagedProduct> |
| PATCH | /admin/products/{id}/moderation | ADMIN | 200 ManagedProduct |
| GET | /admin/reviews | ADMIN | 200 Page<Review> |
| PATCH | /admin/reviews/{id}/moderation | ADMIN | 200 Review |
| GET | /admin/reports | ADMIN | 200 Page<Report> |
| PATCH | /admin/reports/{id} | ADMIN | 200 Report |

Responses use the existing Contracts records. Page contains items, page, size, totalElements, and totalPages. Pagination defaults to page=0, size=20; existing services enforce size=1..100 and an offset no greater than 10,000.

## Query parameters

GET /products accepts q, categoryId, sellerId, minPrice, maxPrice, colour, sizeCode, available, sort, page, and size. Unknown or repeated parameters are rejected. Sort accepts newest, relevance, price_asc, or price_desc. The existing search service validates lengths, prices, offset, and minimum/maximum ordering.

Admin lists accept page and size, plus:

- /admin/users: role, enabled.
- /admin/products: sellerId, listingStatus, moderationStatus.
- /admin/reviews: productId, status.
- /admin/reports: targetType, status.

Public review lists, own review lists, seller product lists, and wishlist lists accept page and size.

## JSON request bodies

All IDs are UUIDs. Versions are nonnegative integer values from the last corresponding response. Invalid or stale versions produce 400 or 409 respectively.

| Operation | Fields |
|---|---|
| Register | name, email, password, role=BUYER or SELLER |
| Login | email, password |
| Update profile | name, version |
| Product create/replace | categoryId, title, description, price, currency=INR, colour, sizes, material, occasion, brand, stockQuantity, availabilityEnabled, sourceUrl, sellerTags, images; replace additionally requires version |
| Product status | listingStatus=DRAFT/ACTIVE/ARCHIVED, version |
| Tag suggestions | version |
| Tag decision | status=APPROVED/REJECTED, productVersion |
| Store create/replace | storeName, description, logoAssetId, publicEmail, publicPhone, whatsappNumber, instagramUrl, facebookUrl, version |
| Review create | rating=1..5, text |
| Review update | rating=1..5, text, version |
| Report create | targetType=PRODUCT/REVIEW, targetId, reason=MISLEADING/INAPPROPRIATE/SPAM/OTHER, details |
| Account status | enabled, reason, version |
| Product moderation | moderationStatus=VISIBLE/HIDDEN, reason, version |
| Review moderation | status=APPROVED/REJECTED, reason, version |
| Report resolution | status=RESOLVED/DISMISSED, resolutionNote, version |

Product images contain assetId, displayOrder, and altText. Sizes, sellerTags, and images must be supplied as arrays, including empty arrays where appropriate. Nullable optional strings may be omitted or supplied as null. Version must be omitted/null for store creation and present for updates; product creation rejects a supplied version.

Product price supports the existing decimal string representation, such as "1499.00". Product creation always produces a draft; activation is a separate versioned status request. Seller tags are replaced with the product body; system tag approval/rejection uses the tag decision route.

Upload uses multipart/form-data with exactly one file part named file. There is no JSON upload body. JPEG, PNG, and WebP are decoded by the existing media validation service; caller-provided filenames and MIME values do not authorize storage access.

Errors are the flat Contracts.ApiError shape documented in README.md. Foreign seller-owned resources and private media are concealed with RESOURCE_NOT_FOUND. Domain conflicts and transitions retain existing BusinessException codes and statuses.

## Reel metadata

Product create/replace requests accept an optional `reelCaption` (maximum 2,000 characters). Flyway V5 adds the nullable `product.reel_caption` column. Authorized managed-product responses return this caption at the top level; public product responses exclude it. The social source URL remains in `product.sourceUrl`. Saved tag generation includes caption text; only approved tags enter catalogue search.
