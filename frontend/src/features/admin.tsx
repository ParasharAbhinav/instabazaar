import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import type {
  ManagedProduct,
  Page,
  Report,
  Review,
  Statistics,
  User,
} from "../api/contracts";
import { write } from "../api/client";
import {
  ActionForm,
  Empty,
  Field,
  Modal,
  Pager,
  QueryState,
  useApi,
  usePage,
} from "../components/ui";
export function AdminDashboard() {
  const q = useApi<Statistics>("/admin/statistics");
  const labels: Record<keyof Statistics, string> = {
    enabledUsers: "Enabled accounts",
    sellerStores: "Seller stores",
    publicProducts: "Public products",
    pendingReviews: "Reviews to review",
    openReports: "Open reports",
  };
  return (
    <section className="section">
      <span className="eyebrow">TRUST & COMMUNITY</span>
      <h1>Keep good things growing.</h1>
      <p>
        Review the community, resolve reports, and maintain a thoughtful
        marketplace.
      </p>
      <QueryState query={q}>
        <div className="stats-grid">
          {q.data &&
            Object.entries(q.data).map(([k, v]) => (
              <div className="panel" key={k}>
                <span>{labels[k as keyof Statistics]}</span>
                <strong>{v.toLocaleString()}</strong>
              </div>
            ))}
        </div>
      </QueryState>
      <div className="admin-links">
        {["users", "products", "reviews", "reports"].map((t) => (
          <Link className="panel" key={t} to={"/admin/" + t}>
            <h2>{t[0].toUpperCase() + t.slice(1)}</h2>
            <p>Manage {t} →</p>
          </Link>
        ))}
      </div>
    </section>
  );
}
type Kind = "users" | "products" | "reviews" | "reports";
type RecordType = User | ManagedProduct | Review | Report;
export function AdminList({ kind }: { kind: Kind }) {
  const [params, setParams] = useSearchParams();
  const page = usePage();
  const filterSpec: Record<Kind, Record<string, string[] | null>> = {
    users: { role: ["BUYER", "SELLER", "ADMIN"], enabled: ["true", "false"] },
    products: {
      sellerId: null,
      listingStatus: ["DRAFT", "ACTIVE", "ARCHIVED"],
      moderationStatus: ["VISIBLE", "HIDDEN"],
    },
    reviews: { productId: null, status: ["PENDING", "APPROVED", "REJECTED"] },
    reports: {
      targetType: ["PRODUCT", "REVIEW"],
      status: ["OPEN", "RESOLVED", "DISMISSED"],
    },
  };
  const query = new URLSearchParams({ page: String(page), size: "20" });
  for (const name of Object.keys(filterSpec[kind])) {
    const value = params.get(name);
    if (value) query.set(name, value);
  }
  const q = useApi<Page<RecordType>>("/admin/" + kind + "?" + query);
  const [selected, setSelected] = useState<RecordType | null>(null);
  return (
    <section className="section">
      <span className="eyebrow">ADMINISTRATION</span>
      <h1>{kind[0].toUpperCase() + kind.slice(1)}</h1>
      <p>Changes require a reason and are recorded in the backend audit log.</p>
      <form
        className="admin-filters panel"
        key={kind + params.toString()}
        onSubmit={(e) => {
          e.preventDefault();
          const data = new FormData(e.currentTarget);
          const next = new URLSearchParams();
          for (const name of Object.keys(filterSpec[kind])) {
            const value = String(data.get(name) || "").trim();
            if (value) next.set(name, value);
          }
          setParams(next);
        }}
      >
        <div className="two-col">
          {Object.entries(filterSpec[kind]).map(([name, options]) => (
            <Field
              key={name}
              label={name.replace(/([A-Z])/g, " $1")}
              name={name}
            >
              {options ? (
                <select name={name} defaultValue={params.get(name) || ""}>
                  <option value="">All</option>
                  {options.map((v) => (
                    <option key={v} value={v}>
                      {v}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  name={name}
                  aria-label={name}
                  defaultValue={params.get(name) || ""}
                  placeholder="UUID"
                  pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                />
              )}
            </Field>
          ))}
        </div>
        <div className="actions">
          <button className="button">Apply filters</button>
          <button type="button" onClick={() => setParams({})}>
            Clear filters
          </button>
        </div>
      </form>
      <QueryState query={q}>
        {q.data?.items.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Record</th>
                  <th>Details</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {q.data.items.map((item) => {
                  if (kind === "users") {
                    const u = item as User;
                    return (
                      <tr key={u.id}>
                        <td>
                          <strong>{u.name}</strong>
                          <small>{u.email}</small>
                        </td>
                        <td>{u.role}</td>
                        <td>{u.enabled ? "Enabled" : "Disabled"}</td>
                        <td>
                          <button onClick={() => setSelected(u)}>Manage</button>
                        </td>
                      </tr>
                    );
                  }
                  if (kind === "products") {
                    const m = item as ManagedProduct;
                    return (
                      <tr key={m.product.id}>
                        <td>
                          {m.product.title}
                          <small>{m.product.id}</small>
                        </td>
                        <td>
                          {m.product.seller.storeName} · {m.listingStatus}
                        </td>
                        <td>{m.moderationStatus}</td>
                        <td>
                          <button onClick={() => setSelected(m)}>
                            Moderate
                          </button>
                        </td>
                      </tr>
                    );
                  }
                  if (kind === "reviews") {
                    const r = item as Review;
                    return (
                      <tr key={r.id}>
                        <td>
                          {r.reviewer.name}
                          <small>{r.rating} / 5</small>
                        </td>
                        <td className="preserve">{r.text || "No text"}</td>
                        <td>{r.status}</td>
                        <td>
                          <button onClick={() => setSelected(r)}>Review</button>
                        </td>
                      </tr>
                    );
                  }
                  const r = item as Report;
                  return (
                    <tr key={r.id}>
                      <td>
                        {r.reason}
                        <small>
                          {r.targetType}: {r.targetId}
                        </small>
                      </td>
                      <td className="preserve">
                        {r.details || "No additional details"}
                        {r.resolutionNote && (
                          <small>Resolution: {r.resolutionNote}</small>
                        )}
                      </td>
                      <td>{r.status}</td>
                      <td>
                        <button
                          disabled={r.status !== "OPEN"}
                          onClick={() => setSelected(r)}
                        >
                          Resolve
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty
            title="All clear here."
            detail="There are no records on this page."
          />
        )}
      </QueryState>
      <Pager data={q.data} />
      {selected && (
        <Modal
          title={"Manage " + kind.slice(0, -1)}
          onClose={() => setSelected(null)}
        >
          <ModerationForm
            kind={kind}
            item={selected}
            onDone={() => setSelected(null)}
          />
        </Modal>
      )}
    </section>
  );
}
function ModerationForm({
  kind,
  item,
  onDone,
}: {
  kind: Kind;
  item: RecordType;
  onDone: () => void;
}) {
  return (
    <ActionForm
      label="Apply decision"
      onSuccess={onDone}
      submit={(f) => {
        const reason = String(f.get("reason")).trim();
        if (!reason) throw new Error("Explain the reason for this change.");
        const decision = String(f.get("decision"));
        if (kind === "users") {
          const u = item as User;
          return write("/admin/users/" + u.id + "/status", "PATCH", {
            enabled: decision === "true",
            reason,
            version: u.version,
          });
        }
        if (kind === "products") {
          const m = item as ManagedProduct;
          return write(
            "/admin/products/" + m.product.id + "/moderation",
            "PATCH",
            { moderationStatus: decision, reason, version: m.product.version },
          );
        }
        if (kind === "reviews") {
          const r = item as Review;
          return write("/admin/reviews/" + r.id + "/moderation", "PATCH", {
            status: decision,
            reason,
            version: r.version,
          });
        }
        const r = item as Report;
        return write("/admin/reports/" + r.id, "PATCH", {
          status: decision,
          resolutionNote: reason,
          version: r.version,
        });
      }}
    >
      <Field label="Decision" name="decision">
        <select name="decision">
          {(kind === "users"
            ? [
                ["true", "Enable account"],
                ["false", "Disable account"],
              ]
            : kind === "products"
              ? [
                  ["VISIBLE", "Make visible"],
                  ["HIDDEN", "Hide listing"],
                ]
              : kind === "reviews"
                ? [
                    ["APPROVED", "Approve review"],
                    ["REJECTED", "Reject review"],
                  ]
                : [
                    ["RESOLVED", "Resolve report"],
                    ["DISMISSED", "Dismiss report"],
                  ]
          ).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </Field>
      <Field
        label={kind === "reports" ? "Resolution note" : "Reason"}
        name="reason"
        type="textarea"
        required
        maxLength={1000}
      />
      {kind === "reports" && (
        <p>
          Closing a report does not moderate its target. Use the products or
          reviews page for a separate moderation action.
        </p>
      )}
    </ActionForm>
  );
}
