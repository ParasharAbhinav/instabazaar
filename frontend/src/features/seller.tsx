import { ReelKeywordExtractorCard } from '../components/ui';
import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Plus, ArrowUp, ArrowDown, Trash2, Upload } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { ApiError, request, write } from "../api/client";
import type {
  Asset,
  ManagedProduct,
  Page,
  ProductInput,
  Store,
  TagSet,
} from "../api/contracts";
import {
  ActionForm,
  ConfirmAction,
  Empty,
  Field,
  Notice,
  Pager,
  QueryState,
  useApi,
  usePage,
} from "../components/ui";
import {
  money,
  nullable,
  safeImage,
  safeSocial,
  uniqueList,
  validatePrice,
} from "../utils/validation";
import { useCategories } from "./catalog";
import { SAMPLE_CATEGORIES, SAMPLE_LISTING, safeReelUrl, type ListingFields } from '../utils/listing';
export function SellerDashboard() {
  const q = useApi<Page<ManagedProduct>>(
    "/seller/products?page=" + usePage() + "&size=20",
  );
  return (
    <section className="section">

      <div className="section-heading">
        <div>
          <span className="eyebrow">SELLER STUDIO</span>
          <h1>Your collection, your story.</h1>
          <p>Manage your listings and share something worth discovering.</p>
        </div>
        <Link className="button" to="/seller/products/new">
          <Plus size={18} /> Add a product
        </Link>
      </div>
      <div className="workspace-note">
        <strong>Your storefront is your first impression.</strong>
        <Link to="/seller/store">Edit your store →</Link>
      </div>
      <QueryState query={q}>
        {q.data?.items.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Price</th>
                  <th>Stock</th>
                  <th>Listing</th>
                  <th>Visibility</th>
                  <th>Manage</th>
                </tr>
              </thead>
              <tbody>
                {q.data.items.map((m) => (
                  <tr key={m.product.id}>
                    <td>
                      <div className="table-product">
                        <img src={safeImage(m.product.images[0]?.url)} alt="" />
                        <strong>{m.product.title}</strong>
                      </div>
                    </td>
                    <td>{money(m.product.price)}</td>
                    <td>{m.product.stockQuantity}</td>
                    <td>
                      <span className="badge">{m.listingStatus}</span>
                    </td>
                    <td>{m.moderationStatus}</td>
                    <td>
                      <Link to={"/seller/products/" + m.product.id + "/edit"}>
                        Edit →
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty
            title="Your next chapter starts here."
            detail="Set up your storefront, then add your first product."
            href="/seller/store"
            label="Set up my store"
          />
        )}
      </QueryState>
      <Pager data={q.data} />
    </section>
  );
}
export function StoreEditor() {
  const q = useQuery({
    queryKey: ["own-store"],
    queryFn: async () => {
      try {
        return await request<Store>("/seller/store");
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
  });
  const [logo, setLogo] = useState<Asset | null>(null);
  const [uploadError, setError] = useState<unknown>();
  const [uploading, setUploading] = useState(false);
  return (
    <section className="narrow">

      <span className="eyebrow">SELLER STUDIO</span>
      <h1>Make yourself at home.</h1>
      <p>
        Contact information saved here is publicly visible on your storefront.
      </p>
      <QueryState query={q}>
        <ActionForm
          key={q.data?.version ?? "new"}
          submit={async (f) => {
            if (uploading)
              throw new Error("Wait for the logo upload to finish.");
            for (const [key, platform] of [
              ["instagramUrl", "instagram"],
              ["facebookUrl", "facebook"],
            ] as const) {
              const value = nullable(f.get(key));
              if (value && !safeSocial(value, platform))
                throw new Error(
                  "Use an HTTPS " + platform + " URL on its official host.",
                );
            }
            for (const key of ["publicPhone", "whatsappNumber"]) {
              const value = nullable(f.get(key));
              if (value && !/^\+[1-9]\d{6,14}$/.test(value))
                throw new Error(
                  "Use international phone format, e.g. +919876543210.",
                );
            }
            if (q.data?.logoUrl && !logo)
              throw new Error(
                "Please upload your existing logo or a replacement before saving these storefront changes.",
              );
            return write("/seller/store", "PUT", {
              storeName: String(f.get("storeName")).trim(),
              description: String(f.get("description")).trim(),
              logoAssetId: logo?.id ?? null,
              publicEmail: nullable(f.get("publicEmail")),
              publicPhone: nullable(f.get("publicPhone")),
              whatsappNumber: nullable(f.get("whatsappNumber")),
              instagramUrl: nullable(f.get("instagramUrl")),
              facebookUrl: nullable(f.get("facebookUrl")),
              version: q.data?.version ?? null,
            });
          }}
        >
          <Field
            label="Store name"
            name="storeName"
            required
            maxLength={120}
            value={q.data?.storeName}
          />
          <Field
            label="Your story"
            name="description"
            type="textarea"
            maxLength={2000}
            value={q.data?.description}
          />
          <div className="field">
            <span>Store logo</span>
            {(logo?.previewUrl || q.data?.logoUrl) && (
              <img
                className="logo-preview"
                src={safeImage(logo?.previewUrl || q.data?.logoUrl)}
                alt="Store logo preview"
              />
            )}
            <input
              aria-label="Upload store logo"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={uploading}
              onChange={async (e) => {
                const file = e.target.files?.[0];
                if (!file) return;
                setUploading(true);
                setError(null);
                try {
                  await validateImage(file);
                  const body = new FormData();
                  body.set("file", file);
                  setLogo(await write<Asset>("/seller/media", "POST", body));
                } catch (err) {
                  setError(err);
                } finally {
                  setUploading(false);
                  e.target.value = "";
                }
              }}
            />
            {uploading && <p role="status">Uploading…</p>}
            <Notice error={uploadError} />
            <small>JPEG, PNG or WebP. Up to 5 MiB and 20 megapixels.</small>
          </div>
          <Field
            label="Public email (optional)"
            name="publicEmail"
            type="email"
            maxLength={254}
            value={q.data?.publicEmail || ""}
          />
          <Field
            label="Public phone (optional)"
            name="publicPhone"
            value={q.data?.publicPhone || ""}
            maxLength={20}
          />
          <Field
            label="WhatsApp number (optional)"
            name="whatsappNumber"
            value={q.data?.whatsappNumber || ""}
            maxLength={20}
          />
          <Field
            label="Instagram URL (optional)"
            name="instagramUrl"
            type="url"
            value={q.data?.instagramUrl || ""}
            maxLength={2048}
          />
          <Field
            label="Facebook URL (optional)"
            name="facebookUrl"
            type="url"
            value={q.data?.facebookUrl || ""}
            maxLength={2048}
          />
        </ActionForm>
      </QueryState>
    </section>
  );
}
export async function validateImage(file: File) {
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type))
    throw new Error("Choose a JPEG, PNG, or WebP image.");
  if (file.size === 0 || file.size > 5 * 1024 * 1024)
    throw new Error("Image must be nonempty and no larger than 5 MiB.");
  const image = await createImageBitmap(file);
  try {
    if (image.width * image.height > 20000000)
      throw new Error("Image must be at most 20 megapixels.");
  } finally {
    image.close();
  }
}
type EditableImage = { assetId: string; url: string; altText: string };
export function ProductEditor() {
  const { id } = useParams();
  const cat = useCategories();
  const q = useApi<ManagedProduct>("/seller/products/" + id, !!id);
  const tags = useApi<TagSet>("/seller/products/" + id + "/tags", !!id);
  if (id)
    return (
      <QueryState query={q}>

        <QueryState query={tags}>
          {q.data && tags.data && (
            <Editor
              key={
                q.data.product.id +
                ":" +
                q.data.product.version +
                ":" +
                tags.data.productVersion
              }
              managed={q.data}
              tagSet={tags.data}
            />
          )}
        </QueryState>
      </QueryState>
    );
  return (
    <QueryState query={cat}>
      <Editor />
    </QueryState>
  );
}
export function Editor({
  managed,
  tagSet,
  sampleMode = false,
}: {
  managed?: ManagedProduct;
  tagSet?: TagSet;
  sampleMode?: boolean;
}) {
  const p = managed?.product;
  const cat = useCategories(!sampleMode);
  const navigate = useNavigate();
  const categories = sampleMode ? SAMPLE_CATEGORIES : cat.data?.items ?? [];
  const [fields, setFields] = useState<ListingFields>({
    title: p?.title ?? '', description: p?.description ?? '', categoryId: p?.category.id ?? '',
    price: p?.price ?? '', stockQuantity: String(p?.stockQuantity ?? 0), colour: p?.colour ?? '',
    material: p?.material ?? '', occasion: p?.occasion ?? '', brand: p?.brand ?? '', sizes: p?.sizes.join(', ') ?? '',
    sellerTags: tagSet?.items.filter(t => t.source === 'SELLER').map(t => t.tag).join(', ') ?? '',
    sourceUrl: p?.sourceUrl ?? '', reelCaption: managed?.reelCaption ?? '',
  });
  const patchFields = (patch: Partial<ListingFields>) => setFields(previous => ({ ...previous, ...patch }));
  const editField = (name: keyof ListingFields) => ({ value: fields[name], onChange: (value: string) => patchFields({ [name]: value }) });
  const [sampleSaved, setSampleSaved] = useState<ProductInput | null>(null);
  const previewUrls = useRef<string[]>([]);
  useEffect(() => () => previewUrls.current.forEach(url => URL.revokeObjectURL(url)), []);
  const [images, setImages] = useState<EditableImage[]>(
    p?.images.map((i) => ({
      assetId: i.assetId,
      url: i.url,
      altText: i.altText,
    })) || [],
  );
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<unknown>();
  const version = tagSet?.productVersion ?? p?.version;
  const reorder = (i: number, delta: number) => {
    const next = [...images];
    [next[i], next[i + delta]] = [next[i + delta], next[i]];
    setImages(next);
  };
  return (
    <section className="editor">
      <div className="section-heading">
        <div>
          <span className="eyebrow">SELLER STUDIO / COLLECTION</span>
          <h1>{p ? "Refine your find." : "A new find starts here."}</h1>
          <p>{sampleMode ? 'Interactive sample: fill the form, add keywords, and preview your listing.' : 'Save a draft, then publish when everything feels right.'}</p>
          {!p && <button type="button" onClick={() => { setFields({ ...SAMPLE_LISTING }); setSampleSaved(null); }}>Load complete sample details</button>}
          {!p && <small className="sample-hint">Loading the sample replaces the text fields. Images stay as they are.</small>}
        </div>
        <Link to={sampleMode ? "/products" : "/seller/dashboard"}>{sampleMode ? "Explore the live catalogue →" : "Back to collection →"}</Link>
      </div>
      {managed && (
        <p className="notice">
          Listing: {managed.listingStatus} · Moderation:{" "}
          {managed.moderationStatus}
        </p>
      )}
      <ActionForm
        label={sampleMode ? "Preview sample listing" : p ? "Save product" : "Create draft"}
        success={sampleMode ? "Sample preview updated below. No listing was sent to the server." : "Changes saved."}
        submit={async (f) => {
          if (uploading)
            throw new Error("Wait for the image upload to finish.");
          const price = String(f.get("price"));
          if (!validatePrice(price))
            throw new Error(
              "Enter a valid price with at most two decimal places.",
            );
          const sizes = uniqueList(String(f.get("sizes")), true),
            sellerTags = uniqueList(String(f.get("sellerTags")));
          if (sizes.length > 20 || sizes.some((s) => s.length > 20))
            throw new Error("Use up to 20 sizes, each at most 20 characters.");
          if (sellerTags.length > 20 || sellerTags.some((s) => s.length > 60))
            throw new Error("Use up to 20 tags, each at most 60 characters.");
          const sourceUrl = nullable(f.get("sourceUrl"));
          if (sourceUrl && sourceUrl !== p?.sourceUrl && !safeReelUrl(sourceUrl)) throw new Error("Use a valid Instagram Reel HTTPS link.");
          if (sourceUrl && !safeSocial(sourceUrl))
            throw new Error(
              "Source URL must be an approved Instagram or Facebook HTTPS link.",
            );
          const title = String(f.get("title")).trim(),
            colour = String(f.get("colour")).trim().toLowerCase();
          if (!title || !colour)
            throw new Error("Title and colour cannot be blank.");
          const body: ProductInput = {
            title,
            description: String(f.get("description")).trim(),
            price: Number(price).toFixed(2),
            currency: "INR",
            categoryId: String(f.get("categoryId")),
            colour,
            sizes,
            material: nullable(f.get("material")),
            occasion: nullable(f.get("occasion")),
            brand: nullable(f.get("brand")),
            stockQuantity: Number(f.get("stockQuantity")),
            availabilityEnabled: f.get("availabilityEnabled") === "on",
            sourceUrl: sourceUrl ? safeReelUrl(sourceUrl) ?? sourceUrl : null,
            reelCaption: nullable(f.get("reelCaption")),
            sellerTags,
            images: images.map((im, displayOrder) => ({
              assetId: im.assetId,
              displayOrder,
              altText: im.altText,
            })),
            ...(p ? { version } : {}),
          };
          if (sampleMode) { setSampleSaved(body); return body; }
          const result = await write<ManagedProduct>(
            p ? "/seller/products/" + p.id : "/seller/products",
            p ? "PUT" : "POST",
            body,
          );
          if (!p) navigate("/seller/products/" + result.product.id + "/edit");
        }}
      >
        <div className="editor-grid">
          <div className="panel">
            <h2>The details</h2>
            <Field label="Product title" name="title" {...editField('title')} required maxLength={160} />
            <Field label="Description" name="description" type="textarea" {...editField('description')} maxLength={5000} />
            <Field label="Category" name="categoryId">
              <select name="categoryId" required value={fields.categoryId} onChange={e => patchFields({ categoryId: e.target.value })}>
                <option value="" disabled>Select category</option>
                {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <div className="two-col">
              <Field label="Price (₹)" name="price" {...editField('price')} type="number" min={0} max={9999999999.99} step="0.01" required />
              <Field label="Stock quantity" name="stockQuantity" {...editField('stockQuantity')} type="number" min={0} max={2147483647} step="1" required />
            </div>
            <label className="check"><input type="checkbox" name="availabilityEnabled" defaultChecked={managed?.availabilityEnabled ?? true} /> Enable availability</label>
            <div className="two-col">
              <Field label="Colour" name="colour" {...editField('colour')} required maxLength={40} />
              <Field label="Material (optional)" name="material" {...editField('material')} maxLength={100} />
              <Field label="Occasion (optional)" name="occasion" {...editField('occasion')} maxLength={100} />
              <Field label="Brand (optional)" name="brand" {...editField('brand')} maxLength={100} />
            </div>
            <Field label="Sizes, separated by commas" name="sizes" {...editField('sizes')} placeholder="S, M, L, XL" />
            <small>Sizes are informational; stock is shared across the listing.</small>
            <Field label="Your tags, separated by commas" name="sellerTags" {...editField('sellerTags')} />
            <small>Up to 20 tags. Selected suggestions are saved here and become searchable with your public listing.</small>
          </div>
          <div className="panel">
            <ReelKeywordExtractorCard fields={fields} onChange={patchFields}
              originalSourceUrl={p?.sourceUrl}
              category={categories.find(c => c.id === fields.categoryId)?.name}
              excluded={tagSet?.items.filter(t => t.source === 'SYSTEM').map(t => t.tag.toLocaleLowerCase())} />
              <h2>Tell the story in pictures</h2>

            <p>Up to 10 images. The first image becomes your cover.</p>
            <label className="upload-area">
              <Upload />
              <strong>{uploading ? "Uploading…" : "Choose an image"}</strong>
              <span>JPEG, PNG, WebP · 5 MiB maximum</span>
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                disabled={uploading || images.length >= 10}
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  setUploading(true);
                  setError(null);
                  try {
                    await validateImage(file);
                    if (sampleMode) {
                      const url = URL.createObjectURL(file);
                      previewUrls.current.push(url);
                      setImages(previous => [...previous, { assetId: crypto.randomUUID(), url, altText: file.name }]);
                      return;
                    }
                    const f = new FormData();
                    f.set("file", file);
                    const asset = await write<Asset>(
                      "/seller/media",
                      "POST",
                      f,
                    );
                    setImages((prev) => [
                      ...prev,
                      { assetId: asset.id, url: asset.previewUrl, altText: "" },
                    ]);
                  } catch (err) {
                    setError(err);
                  } finally {
                    setUploading(false);
                    e.target.value = "";
                  }
                }}
              />
            </label>
            <Notice error={error} />
            {images.map((im, i) => (
              <div className="image-editor" key={im.assetId}>
                <img
                  src={sampleMode ? im.url : safeImage(im.url)}
                  alt={im.altText || "Uploaded product image"}
                />
                <label>
                  Image {i + 1} description
                  <input
                    maxLength={160}
                    value={im.altText}
                    onChange={(e) =>
                      setImages(
                        images.map((v, j) =>
                          j === i ? { ...v, altText: e.target.value } : v,
                        ),
                      )
                    }
                  />
                </label>
                <div className="actions">
                  <button
                    type="button"
                    aria-label="Move image earlier"
                    disabled={i === 0}
                    onClick={() => reorder(i, -1)}
                  >
                    <ArrowUp size={14} />
                  </button>
                  <button
                    type="button"
                    aria-label="Move image later"
                    disabled={i === images.length - 1}
                    onClick={() => reorder(i, 1)}
                  >
                    <ArrowDown size={14} />
                  </button>
                  <button
                    type="button"
                    aria-label="Detach image"
                    onClick={() => setImages(images.filter((_, j) => j !== i))}
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            ))}
            <small>
              Removing an image detaches it when you save. Unreferenced uploads
              are cleaned up by the backend.
            </small>
          </div>
        </div>
      </ActionForm>
      {sampleSaved && <section className="panel sample-result" aria-label="Sample listing preview">
        <span className="eyebrow">SAMPLE BUYER VIEW</span>
        <div className="sample-product-preview">
          <img src={images[0]?.url || '/images/look-1.svg'} alt={images[0]?.altText || 'Illustrative sample garment'} />
          <div><h2>{sampleSaved.title}</h2><strong>{money(sampleSaved.price)}</strong><p>{sampleSaved.description}</p>
            <p>{sampleSaved.colour} · {sampleSaved.material} · {sampleSaved.sizes.join(', ')}</p>
            <p>{sampleSaved.stockQuantity} in stock · {sampleSaved.availabilityEnabled ? 'Availability enabled' : 'Unavailable'}</p>
            <div className="keyword-chips">{sampleSaved.sellerTags.map(tag => <span className="badge" key={tag}>#{tag}</span>)}</div>
            {sampleSaved.sourceUrl && <a className="reel-link" href={sampleSaved.sourceUrl} target="_blank" rel="noopener noreferrer">Watch the Reel on Instagram ↗</a>}
            <small>This preview uses fictional details. Your caption stays in the seller editor.</small>
          </div>
        </div>
      </section>}
      {p && (
        <>
          <section className="panel">
            <h2>Publication</h2>
            <p>
              Save changes above before changing publication status. Publishing
              requires a complete storefront and a description.
            </p>
            <div className="actions">
              {(["DRAFT", "ACTIVE", "ARCHIVED"] as const)
                .filter((s) => s !== managed?.listingStatus)
                .map((status) => (
                  <ConfirmAction
                    key={status}
                    label={
                      status === "ACTIVE"
                        ? "Publish listing"
                        : status === "DRAFT"
                          ? "Move to draft"
                          : "Archive listing"
                    }
                    danger={status === "ARCHIVED"}
                    run={() =>
                      write("/seller/products/" + p.id + "/status", "PATCH", {
                        listingStatus: status,
                        version,
                      })
                    }
                  />
                ))}
            </div>
          </section>
          <section className="panel">
            <h2>Suggested discovery tags</h2>
            <p>
              Only approved tags are searchable. Suggestions never publish
              automatically.
            </p>
            <ActionForm
              label="Generate suggestions"
              submit={() =>
                write("/seller/products/" + p.id + "/tag-suggestions", "POST", {
                  version,
                })
              }
            >
              <span>Find relevant tags from the controlled vocabulary.</span>
            </ActionForm>
            {tagSet?.items
              .filter((t) => t.source === "SYSTEM")
              .map((t) => (
                <div className="tag-row" key={t.id}>
                  <span>
                    {t.tag} <small>{t.status}</small>
                  </span>
                  {t.status === "SUGGESTED" && (
                    <div className="actions">
                      {(["APPROVED", "REJECTED"] as const).map((status) => (
                        <ConfirmAction
                          key={status}
                          label={status === "APPROVED" ? "Approve" : "Reject"}
                          run={() =>
                            write(
                              "/seller/products/" + p.id + "/tags/" + t.id,
                              "PATCH",
                              { status, productVersion: version },
                            )
                          }
                        />
                      ))}
                    </div>
                  )}
                </div>
              ))}
          </section>
        </>
      )}
    </section>
  );
}
