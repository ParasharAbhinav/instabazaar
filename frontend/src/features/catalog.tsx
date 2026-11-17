import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import {
  ArrowRight,
  Heart,
  Search,
  SlidersHorizontal,
  Star,
  ExternalLink,
} from "lucide-react";
import { request, write } from "../api/client";
import type {
  Category,
  Page,
  ProductSummary,
  ProductDetail,
  Store,
  SavedProduct,
  Review,
} from "../api/contracts";
import {
  ActionForm,
  Empty,
  Field,
  Modal,
  Notice,
  Pager,
  QueryState,
  useApi,
  usePage,
} from "../components/ui";
import { money, nullable, safeImage, safeSocial } from "../utils/validation";
import { demo, useUser } from "./auth";
import { categories, demoSearch, products, stores } from "../api/demo";
export function useCategories(enabled = true) {
  return useQuery({
    queryKey: ["categories"],
    enabled,
    queryFn: () =>
      demo
        ? Promise.resolve({ items: categories })
        : request<{ items: Category[] }>("/categories"),
  });
}
function useProducts(params: URLSearchParams) {
  return useQuery({
    queryKey: ["products", params.toString()],
    queryFn: ({ signal }) =>
      demo
        ? Promise.resolve(demoSearch(params))
        : request<Page<ProductSummary>>("/products?" + params, { signal }),
  });
}
export function ProductCard({
  product,
  hideSave = false,
}: {
  product: ProductSummary;
  hideSave?: boolean;
}) {
  return (
    <article className="product-card">
      <Link className="product-photo" to={"/products/" + product.id}>
        <img
          loading="lazy"
          src={safeImage(product.thumbnailUrl)}
          onError={(e) => {
            e.currentTarget.src = "/images/product-placeholder.svg";
          }}
          alt={product.title}
        />
        {!product.available && (
          <span className="photo-badge">Currently unavailable</span>
        )}
      </Link>
      {!hideSave && <SaveButton id={product.id} />}
      <div className="card-meta">
        <Link to={"/sellers/" + product.seller.id}>
          {product.seller.storeName}
        </Link>
        <span>
          {product.rating.average !== null ? (
            <>
              <Star size={12} /> {product.rating.average.toFixed(1)}
            </>
          ) : (
            "New find"
          )}
        </span>
      </div>
      <Link className="product-title" to={"/products/" + product.id}>
        {product.title}
      </Link>
      <p className="price">
        {money(product.price)} <span>{product.category.name}</span>
      </p>
    </article>
  );
}
export function SaveButton({
  id,
  remove = false,
}: {
  id: string;
  remove?: boolean;
}) {
  const user = useUser();
  const nav = useNavigate();
  const client = useQueryClient();
  const [saved, set] = useState(remove);
  const m = useMutation({
    mutationFn: () => write("/me/wishlist/" + id, saved ? "DELETE" : "PUT"),
    onSuccess: () => {
      set(!saved);
      client.invalidateQueries({
        predicate: (q) => String(q.queryKey[0]).startsWith("/me/wishlist"),
      });
    },
  });
  return (
    <div className="save-control">
      <button
        className={"heart " + (saved ? "saved" : "")}
        aria-label={saved ? "Remove from wishlist" : "Save to wishlist"}
        aria-pressed={saved}
        disabled={m.isPending}
        onClick={() =>
          user.data
            ? m.mutate()
            : nav("/login?next=" + encodeURIComponent(location.pathname))
        }
      >
        <Heart size={18} fill={saved ? "currentColor" : "none"} />
      </button>
      {m.error && (
        <span role="alert" className="save-error">
          Could not save. Try again.
        </span>
      )}
    </div>
  );
}
export function Home() {
  const cat = useCategories();
  const q = useProducts(new URLSearchParams({ size: "8", sort: "newest" }));
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">
            <span className="tiny-line" /> INDEPENDENT FINDS. INDIVIDUAL STYLE.
          </span>
          <h1>
            Not everywhere.
            <br />
            <em>Just your kind.</em>
          </h1>
          <p>
            Discover fashion from independent stores.
            <br />
            Find a little unexpected. Make it your own.
          </p>
          <Link className="button" to="/products">
            Explore the collection <ArrowRight size={18} />
          </Link>
          <div className="hero-note">
            <span>✦</span> Small stores. Big personality.
          </div>
        </div>
        <div className="hero-image">
          <img
            src="/images/hero.svg"
            alt="Illustrated independent fashion collection in warm earth tones"
          />
          <span className="vertical-note">THE INDEPENDENT EDIT — 2026</span>
          <div className="hero-caption">
            <span>GOOD FINDS, GREAT STORIES</span>
            <strong>A wardrobe less ordinary.</strong>
            <Link to="/products" aria-label="Discover the edit">
              <ArrowRight />
            </Link>
          </div>
        </div>
      </section>
      <div className="value-strip">
        <span>Thoughtfully discovered</span>
        <span>✦</span>
        <span>Connect directly with sellers</span>
        <span>✦</span>
        <span>Find your own kind of style</span>
      </div>
      <section className="section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">FOLLOW YOUR MOOD</span>
            <h2>Where shall we start?</h2>
          </div>
          <Link to="/products">
            Explore all <ArrowRight size={16} />
          </Link>
        </div>
        <QueryState query={cat}>
          <div className="category-grid">
            {cat.data?.items.map((c, i) => (
              <Link key={c.id} to={"/categories/" + c.id}>
                <div className={"category-art tone-" + i}>
                  <img alt="" src={"/images/look-" + (i % 4) + ".svg"} />
                </div>
                <span>
                  {c.name} <ArrowRight size={15} />
                </span>
              </Link>
            ))}
          </div>
        </QueryState>
      </section>
      <section className="section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">FRESH FROM THE STOREFRONTS</span>
            <h2>New finds to fall for.</h2>
          </div>
          <Link to="/products?sort=newest">
            View all finds <ArrowRight size={16} />
          </Link>
        </div>
        <QueryState query={q}>
          {q.data?.items.length ? (
            <div className="product-grid">
              {q.data.items.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          ) : (
            <Empty
              title="Something good is on its way."
              detail="Independent sellers will appear here when they publish their collections."
            />
          )}
        </QueryState>
      </section>
      <section className="seller-banner">
        <div>
          <span className="eyebrow">YOUR STORE. A BIGGER WORLD.</span>
          <h2>
            Small business.
            <br />
            Endless possibilities.
          </h2>
          <p>Give your collection a home. Help your next customer find you.</p>
        </div>
        <Link className="button light" to="/register">
          Open your storefront <ArrowRight size={18} />
        </Link>
        <span className="banner-star" aria-hidden>
          ✦
        </span>
      </section>
    </>
  );
}
export function Catalog({ sellerId }: { sellerId?: string }) {
  const [params, setParams] = useSearchParams();
  const route = useParams();
  const cat = useCategories();
  const [filters, setFilters] = useState(false);
  const [filterError, setFilterError] = useState<string | null>(null);
  const query = new URLSearchParams();
  for (const key of [
    "q",
    "categoryId",
    "sellerId",
    "minPrice",
    "maxPrice",
    "colour",
    "sizeCode",
    "available",
    "sort",
    "page",
  ]) {
    const v = params.get(key);
    if (v) query.set(key, v);
  }
  query.set("size", "20");
  query.set("page", String(usePage()));
  if (route.id && !sellerId) query.set("categoryId", route.id);
  if (sellerId) query.set("sellerId", sellerId);
  const q = useProducts(query);
  const selected = cat.data?.items.find(
    (c) => c.id === query.get("categoryId"),
  );
  return (
    <section className="section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">THE DISCOVERY EDIT</span>
          <h1>{selected?.name || "Find your next favourite."}</h1>
          <p>Independent style, all in one place.</p>
        </div>
        <button onClick={() => setFilters(!filters)} aria-expanded={filters}>
          <SlidersHorizontal size={16} /> Filters
        </button>
      </div>
      <form
        className="search-large"
        onSubmit={(e) => {
          e.preventDefault();
          const next = new URLSearchParams(params);
          const term = String(
            new FormData(e.currentTarget).get("q") || "",
          ).trim();
          term ? next.set("q", term) : next.delete("q");
          next.delete("page");
          setParams(next);
        }}
      >
        <Search size={20} />
        <input
          aria-label="Search products"
          name="q"
          maxLength={200}
          defaultValue={params.get("q") || ""}
          key={params.get("q")}
          placeholder="Try linen, festive, everyday…"
        />
        <button className="button">Find my style</button>
      </form>
      <div className="catalog-layout">
        <aside className={"filters " + (filters ? "open" : "")}>
          <form
            key={params.toString() + route.id}
            onSubmit={(e) => {
              e.preventDefault();
              setFilterError(null);
              const f = new FormData(e.currentTarget);
              if (
                f.get("minPrice") &&
                f.get("maxPrice") &&
                Number(f.get("minPrice")) > Number(f.get("maxPrice"))
              ) {
                setFilterError("Minimum price must not exceed maximum price.");
                return;
              }
              const next = new URLSearchParams(params);
              for (const key of [
                "categoryId",
                "minPrice",
                "maxPrice",
                "colour",
                "sizeCode",
                "available",
              ]) {
                let v = String(f.get(key) || "").trim();
                if (key === "colour") v = v.toLowerCase();
                if (key === "sizeCode") v = v.toUpperCase();
                if ((key === "minPrice" || key === "maxPrice") && v)
                  v = Number(v).toFixed(2);
                v ? next.set(key, v) : next.delete(key);
              }
              next.delete("page");
              setParams(next);
              setFilters(false);
            }}
          >
            <h3>Make it yours</h3>
            {filterError && (
              <p className="notice error" role="alert">
                {filterError}
              </p>
            )}
            {!route.id && (
              <Field label="Category" name="categoryId">
                <select
                  name="categoryId"
                  defaultValue={params.get("categoryId") || ""}
                >
                  <option value="">All categories</option>
                  {cat.data?.items.map((c) => (
                    <option value={c.id} key={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </Field>
            )}
            <Field
              label="Minimum price (₹)"
              name="minPrice"
              type="number"
              min={0}
              step="0.01"
              value={params.get("minPrice") || ""}
            />
            <Field
              label="Maximum price (₹)"
              name="maxPrice"
              type="number"
              min={0}
              step="0.01"
              value={params.get("maxPrice") || ""}
            />
            <Field
              label="Colour"
              name="colour"
              maxLength={40}
              value={params.get("colour") || ""}
            />
            <Field
              label="Size"
              name="sizeCode"
              maxLength={20}
              value={params.get("sizeCode") || ""}
            />
            <label className="check">
              <input
                type="checkbox"
                name="available"
                value="true"
                defaultChecked={params.get("available") === "true"}
              />{" "}
              Available now
            </label>
            <button className="button">Apply filters</button>
            <button
              type="button"
              className="text-button"
              onClick={() => setParams({})}
            >
              Clear all
            </button>
          </form>
        </aside>
        <div className="results">
          <div className="results-bar">
            <span>
              {q.data ? q.data.totalElements + " finds" : "Discovering…"}
            </span>
            <label>
              Sort by{" "}
              <select
                value={params.get("sort") || "newest"}
                onChange={(e) => {
                  const n = new URLSearchParams(params);
                  n.set("sort", e.target.value);
                  n.delete("page");
                  setParams(n);
                }}
              >
                <option value="newest">Newest first</option>
                <option value="relevance">Relevance</option>
                <option value="price_asc">Price: low to high</option>
                <option value="price_desc">Price: high to low</option>
              </select>
            </label>
          </div>
          <div className="chips">
            {[
              "q",
              "categoryId",
              "colour",
              "sizeCode",
              "minPrice",
              "maxPrice",
              "available",
            ]
              .filter((k) => params.has(k))
              .map((k) => (
                <button
                  key={k}
                  onClick={() => {
                    const n = new URLSearchParams(params);
                    n.delete(k);
                    n.delete("page");
                    setParams(n);
                  }}
                >
                  {k === "categoryId"
                    ? cat.data?.items.find((c) => c.id === params.get(k))
                        ?.name || "Category"
                    : k + ": " + params.get(k)}{" "}
                  ×
                </button>
              ))}
          </div>
          <QueryState query={q}>
            {q.data?.items.length ? (
              <div className="product-grid">
                {q.data.items.map((p) => (
                  <ProductCard key={p.id} product={p} />
                ))}
              </div>
            ) : (
              <Empty
                title="No finds just yet."
                detail="Try another search or clear a filter to discover more."
              />
            )}
          </QueryState>
          <Pager data={q.data} />
        </div>
      </div>
    </section>
  );
}
export function ContactLinks({ store }: { store: Store }) {
  const links: { label: string; href: string }[] = [];
  if (store.whatsappNumber && /^\+[1-9]\d{6,14}$/.test(store.whatsappNumber))
    links.push({
      label: "WhatsApp",
      href: "https://wa.me/" + store.whatsappNumber.slice(1),
    });
  if (store.publicEmail && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(store.publicEmail))
    links.push({ label: "Email seller", href: "mailto:" + store.publicEmail });
  if (store.publicPhone && /^\+[1-9]\d{6,14}$/.test(store.publicPhone))
    links.push({ label: "Call seller", href: "tel:" + store.publicPhone });
  for (const [label, url, platform] of [
    ["Instagram", store.instagramUrl, "instagram"],
    ["Facebook", store.facebookUrl, "facebook"],
  ] as const) {
    const href = safeSocial(url, platform);
    if (href) links.push({ label, href });
  }
  return (
    <div className="contact">
      <p>
        Contact this seller directly. Links open an external app or website.
      </p>
      <div className="actions">
        {links.map((l) => (
          <a
            className="button secondary"
            target="_blank"
            rel="noopener noreferrer"
            href={l.href}
            key={l.label}
          >
            {l.label}
            <ExternalLink size={14} />
          </a>
        ))}
      </div>
      {!links.length && (
        <small>No public contact details have been provided.</small>
      )}
    </div>
  );
}
export function ProductPage() {
  const { id } = useParams();
  const [index, setIndex] = useState(0);
  const q = useQuery({
    queryKey: ["product", id],
    queryFn: () =>
      demo
        ? Promise.resolve(products.find((p) => p.id === id))
        : request<ProductDetail>("/products/" + id),
  });
  const p = q.data;
  return (
    <QueryState query={q}>
      {p ? (
        <section className="section">
          <div className="breadcrumbs">
            <Link to="/products">All finds</Link> /{" "}
            <Link to={"/categories/" + p.category.id}>{p.category.name}</Link> /{" "}
            {p.title}
          </div>
          <div className="product-detail">
            <div>
              <div className="detail-image">
                <img
                  src={safeImage(p.images[index]?.url)}
                  alt={p.images[index]?.altText || p.title}
                />
              </div>
              <div className="thumbnails">
                {p.images.map((im, i) => (
                  <button
                    key={im.id}
                    aria-label={"View " + im.altText}
                    aria-pressed={i === index}
                    onClick={() => setIndex(i)}
                  >
                    <img src={safeImage(im.url)} alt="" />
                  </button>
                ))}
              </div>
            </div>
            <div className="detail-copy">
              <Link className="eyebrow" to={"/sellers/" + p.seller.id}>
                {p.seller.storeName} ↗
              </Link>
              <h1>{p.title}</h1>
              <p className="detail-price">{money(p.price)}</p>
              <p className="muted">
                {p.rating.average === null
                  ? "No approved reviews yet"
                  : p.rating.average + " / 5 · " + p.rating.count + " reviews"}
              </p>
              <hr />
              <p className="preserve">{p.description}</p>
              <dl>
                <dt>Colour</dt>
                <dd>{p.colour}</dd>
                <dt>Material</dt>
                <dd>{p.material || "Not specified"}</dd>
                <dt>Occasion</dt>
                <dd>{p.occasion || "Not specified"}</dd>
                {p.brand && (
                  <>
                    <dt>Brand</dt>
                    <dd>{p.brand}</dd>
                  </>
                )}
              </dl>
              <p>
                Available sizes <small>Confirm your size with the seller</small>
              </p>
              <div className="chips">
                {p.sizes.map((s) => (
                  <span key={s}>{s}</span>
                ))}
              </div>
              <p className={p.available ? "stock" : "muted"}>
                {p.available
                  ? "● Available from this seller"
                  : "Currently unavailable"}
              </p>
              <div className="detail-save">
                <SaveButton id={p.id} />
                <span>Keep this find in your wishlist</span>
              </div>
              <ContactLinks store={p.seller} />
              {safeSocial(p.sourceUrl) && (
                <a
                  href={safeSocial(p.sourceUrl)!}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  View original social listing ↗
                </a>
              )}
              <ReportButton targetId={p.id} targetType="PRODUCT" />
            </div>
          </div>
          <section className="section">
            <h2>Community notes</h2>
            <p>Reviews are moderated and do not imply a verified purchase.</p>
            <Reviews productId={p.id} />
          </section>
        </section>
      ) : (
        <Empty
          title="This find is unavailable."
          detail="It may have been removed or is not currently public."
          href="/products"
        />
      )}
    </QueryState>
  );
}
export function Storefront() {
  const { id } = useParams();
  const q = useQuery({
    queryKey: ["store", id],
    queryFn: () =>
      demo
        ? Promise.resolve(stores.find((s) => s.id === id))
        : request<Store>("/sellers/" + id),
  });
  return (
    <QueryState query={q}>
      {q.data ? (
        <>
          <section className="store-hero">
            <div className="store-avatar">
              {q.data.logoUrl ? (
                <img src={safeImage(q.data.logoUrl)} alt="Store logo" />
              ) : (
                q.data.storeName.slice(0, 1)
              )}
            </div>
            <span className="eyebrow">INDEPENDENT STOREFRONT</span>
            <h1>{q.data.storeName}</h1>
            <p className="preserve">{q.data.description}</p>
            <ContactLinks store={q.data} />
          </section>
          <Catalog sellerId={id} />
        </>
      ) : (
        <Empty
          title="Store not found"
          detail="This storefront is not available."
          href="/products"
        />
      )}
    </QueryState>
  );
}
export function Wishlist() {
  const page = usePage();
  const q = useApi<Page<SavedProduct>>(
    "/me/wishlist?page=" + page + "&size=20",
  );
  return (
    <section className="section">
      <span className="eyebrow">THE ONES YOU LOVE</span>
      <h1>Your little collection.</h1>
      <QueryState query={q}>
        {q.data?.items.length ? (
          <div className="product-grid">
            {q.data.items.map((s) => (
              <div key={s.productId}>
                {s.product ? (
                  <ProductCard product={s.product} hideSave />
                ) : (
                  <div className="empty">
                    <h3>Find unavailable</h3>
                    <p>This listing is no longer public.</p>
                  </div>
                )}
                <div className="wishlist-remove">
                  <SaveButton id={s.productId} remove />
                  <span>Remove from wishlist</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Empty
            title="Make room for your favourites."
            detail="Tap the heart on a find to save it for later."
            href="/products"
          />
        )}
      </QueryState>
      <Pager data={q.data} />
    </section>
  );
}
export function ReportButton({
  targetId,
  targetType,
}: {
  targetId: string;
  targetType: "PRODUCT" | "REVIEW";
}) {
  const [open, set] = useState(false);
  const user = useUser();
  const nav = useNavigate();
  return (
    <>
      <button
        className="text-button muted"
        onClick={() =>
          user.data
            ? set(true)
            : nav("/login?next=" + encodeURIComponent(location.pathname))
        }
      >
        Report {targetType.toLowerCase()}
      </button>
      {open && (
        <Modal
          title="Help keep InstaBazaar thoughtful"
          onClose={() => set(false)}
        >
          <ActionForm
            label="Submit report"
            success="Your report has been submitted."
            submit={(f) =>
              write("/reports", "POST", {
                targetId,
                targetType,
                reason: f.get("reason"),
                details: nullable(f.get("details")),
              })
            }
          >
            <Field label="Reason" name="reason">
              <select name="reason">
                <option value="MISLEADING">Misleading information</option>
                <option value="INAPPROPRIATE">Inappropriate content</option>
                <option value="SPAM">Spam</option>
                <option value="OTHER">Something else</option>
              </select>
            </Field>
            <Field
              label="Details (optional)"
              name="details"
              type="textarea"
              maxLength={1000}
            />
          </ActionForm>
        </Modal>
      )}
    </>
  );
}
export function ReviewForm({
  productId,
  review,
}: {
  productId: string;
  review?: Review;
}) {
  return (
    <ActionForm
      label={review ? "Update review" : "Submit for moderation"}
      success="Your review is pending moderation. It will appear publicly after approval."
      submit={(f) =>
        write(
          review
            ? "/reviews/" + review.id
            : "/products/" + productId + "/reviews",
          review ? "PUT" : "POST",
          {
            rating: Number(f.get("rating")),
            text: nullable(f.get("text")),
            ...(review ? { version: review.version } : {}),
          },
        )
      }
    >
      <Field label="Rating" name="rating">
        <select name="rating" defaultValue={review?.rating || 5}>
          {[5, 4, 3, 2, 1].map((n) => (
            <option key={n} value={n}>
              {n} stars
            </option>
          ))}
        </select>
      </Field>
      <Field
        label="Your experience (optional)"
        name="text"
        type="textarea"
        maxLength={2000}
        value={review?.text || ""}
      />
    </ActionForm>
  );
}
function Reviews({ productId }: { productId: string }) {
  const page = usePage();
  const user = useUser();
  const q = useQuery({
    queryKey: ["reviews", productId, page],
    queryFn: () =>
      demo
        ? Promise.resolve({
            items: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
          } as Page<Review>)
        : request<Page<Review>>(
            "/products/" + productId + "/reviews?page=" + page + "&size=20",
          ),
  });
  return (
    <>
      <QueryState query={q}>
        {q.data?.items.map((r) => (
          <article className="review" key={r.id}>
            <strong>{r.reviewer.name}</strong>
            <span>
              {"★".repeat(r.rating)} ·{" "}
              {new Date(r.createdAt).toLocaleDateString()}
            </span>
            <p className="preserve">{r.text}</p>
            <ReportButton targetId={r.id} targetType="REVIEW" />
          </article>
        ))}
        {!q.data?.items.length && (
          <p className="muted">Be the first to share a thoughtful review.</p>
        )}
      </QueryState>
      <Pager data={q.data} />
      {user.data ? (
        <div className="panel">
          <h3>Share your experience</h3>
          <ReviewForm productId={productId} />
          <Link to="/profile/reviews">
            Already reviewed? Edit your review here.
          </Link>
        </div>
      ) : (
        <Link
          to={"/login?next=" + encodeURIComponent("/products/" + productId)}
        >
          Sign in to write a review →
        </Link>
      )}
    </>
  );
}
export function MyReviews() {
  const q = useApi<Page<Review>>("/me/reviews?page=" + usePage() + "&size=20");
  return (
    <section className="narrow">
      <h1>Your reviews</h1>
      <QueryState query={q}>
        {q.data?.items.map((r) => (
          <article className="panel" key={r.id + ":" + r.version}>
            <Link to={"/products/" + r.productId}>View product →</Link>
            <p className="badge">{r.status}</p>
            <ReviewForm productId={r.productId} review={r} />
          </article>
        ))}
        {!q.data?.items.length && (
          <Empty
            title="No reviews yet"
            detail="Your product reviews will appear here."
            href="/products"
          />
        )}
      </QueryState>
      <Pager data={q.data} />
    </section>
  );
}
