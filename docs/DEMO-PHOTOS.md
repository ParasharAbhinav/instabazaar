# Photo-backed demonstration catalogue

`src/main/resources/demo/photo-catalogue.json` adds ten fictional listings across
Sample Loom Studio (3), The Everyday Edit (3), and Urban Trail Outfitters (4).
The original six illustrated listings and any user-created records remain intact.

The photographs are stock/reference imagery, not photographs of actual seller
inventory. Prices, materials, sizes and quantities are fictional demonstration
data. Visible trademarks are not claims of brand affiliation, authenticity or
availability. No reviews or source social posts are fabricated.

## Photograph sources

Source: Unsplash image CDN. Downloaded on 18 November 2026 as JPEGs, resized to
800 pixels wide with quality 85. The application serves the bundled copies from
`/images/demo/`, so catalogue rendering requires no third-party image requests,
API keys, image-generation service or internet connection at runtime.

| Local asset | Listing | Original source |
| --- | --- | --- |
| `denim-jacket.jpg` | Light Blue Denim Jacket | https://images.unsplash.com/photo-1543076447-215ad9ba6923 |
| `printed-tshirt.jpg` | Cream Graphic Cotton T-Shirt | https://images.unsplash.com/photo-1576566588028-4147f3842f27 |
| `knit-sweater.jpg` | Ivory Fringe Knit Poncho | https://images.unsplash.com/photo-1434389677669-e08b4cac3105 |
| `black-handbag.jpg` | Black Quilted Chain Shoulder Bag | https://images.unsplash.com/photo-1548036328-c9fa89d128fa |
| `everyday-bag.jpg` | Coral Structured Top-Handle Bag | https://images.unsplash.com/photo-1584917865442-de89df76afd3 |
| `sunglasses.jpg` | Round Green-Lens Sunglasses | https://images.unsplash.com/photo-1511499767150-a48a237f0083 |
| `red-trainers.jpg` | Red Mesh Lace-Up Trainers | https://images.unsplash.com/photo-1542291026-7eec264c27ff |
| `canvas-sneakers.jpg` | Burgundy Canvas Low-Top Sneakers | https://images.unsplash.com/photo-1525966222134-fcfa99b8ae77 |
| `backpack.jpg` | Navy Everyday Zip Backpack | https://images.unsplash.com/photo-1553062407-98eeb64c6a62 |
| `minimal-watch.jpg` | Minimal White-Strap Watch Duo | https://images.unsplash.com/photo-1523275335684-37898b6baf30 |

Unsplash licence and terms: https://unsplash.com/license and
https://unsplash.com/terms. Photo copyright remains with the original creators;
these photographs are not relicensed under the application's source-code licence.
Do not use the demonstration descriptions as verified manufacturing or safety
specifications, or imply endorsement by anyone pictured or any visible brand.
Replace stock references with authorised seller product photos before real sales.

## Import behaviour

Only the opt-in `dev`/`demo` seeder imports this catalogue. It reuses existing
demo seller profiles, never resets their passwords, and refuses to attach demo
products to a non-demo account occupying a reserved email address. Each new
listing is recognised by its seller and stable bundled photo path; restarting
does not recreate it after a title, price, stock, archive or moderation edit.
Uploaded seller photos take precedence over the bundled reference image.

Accessories and Footwear categories are added by name if missing. Search uses
the existing product rediscovery pipeline: titles, descriptive attributes,
captions and tags. These static photos do not pretend to be video-derived tags;
the existing MP4 tagging workflow is unchanged.
