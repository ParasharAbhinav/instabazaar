import {
  Component,
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type FormEvent,
} from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { X, ArrowRight } from "lucide-react";
import { ApiError, request } from "../api/client";
import type { Page } from "../api/contracts";
export function useApi<T>(path: string, enabled = true) {
  return useQuery<T>({
    queryKey: [path],
    queryFn: ({ signal }) => request<T>(path, { signal }),
    enabled,
  });
}
export function Notice({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <div className="notice error" role="alert">
      <strong>
        {error instanceof ApiError && error.body.code === "VERSION_CONFLICT"
          ? "This record has changed. Reload the latest version before saving again."
          : error instanceof Error
            ? error.message
            : "Something went wrong."}
      </strong>
      {error instanceof ApiError && (
        <>
          <ul>
            {error.body.fieldErrors.map((e, i) => (
              <li key={i}>
                {e.field}: {e.message}
              </li>
            ))}
          </ul>
          {error.body.traceId && (
            <small>Support reference: {error.body.traceId}</small>
          )}
          {error.body.code === "VERSION_CONFLICT" && (
            <button type="button" onClick={() => location.reload()}>
              Reload latest version
            </button>
          )}
        </>
      )}
    </div>
  );
}
export function QueryState({
  query,
  children,
}: {
  query: { isPending: boolean; error: unknown; refetch: () => unknown };
  children: ReactNode;
}) {
  if (query.isPending)
    return (
      <div className="skeletons" role="status" aria-label="Loading">
        <div />
        <div />
        <div />
      </div>
    );
  if (query.error)
    return (
      <>
        <Notice error={query.error} />
        <button onClick={() => query.refetch()}>Try again</button>
      </>
    );
  return <>{children}</>;
}
export function Empty({
  title,
  detail,
  href,
  label,
}: {
  title: string;
  detail: string;
  href?: string;
  label?: string;
}) {
  return (
    <div className="empty">
      <span className="empty-mark">✦</span>
      <h2>{title}</h2>
      <p>{detail}</p>
      {href && (
        <Link className="button" to={href}>
          {label || "Explore the collection"} <ArrowRight size={16} />
        </Link>
      )}
    </div>
  );
}
export function Pager({
  data,
}: {
  data?: Pick<Page<unknown>, "page" | "totalPages">;
}) {
  const [params, set] = useSearchParams();
  if (!data || data.totalPages < 2) return null;
  const go = (page: number) => {
    const next = new URLSearchParams(params);
    next.set("page", String(page));
    set(next);
  };
  return (
    <nav className="pager" aria-label="Pagination">
      <button disabled={data.page === 0} onClick={() => go(data.page - 1)}>
        Previous
      </button>
      <span>
        Page {data.page + 1} of {data.totalPages}
      </span>
      <button
        disabled={
          data.page + 1 >= data.totalPages || (data.page + 1) * 20 > 10000
        }
        onClick={() => go(data.page + 1)}
      >
        Next
      </button>
    </nav>
  );
}
export function usePage() {
  const [p] = useSearchParams();
  const n = Number(p.get("page") || 0);
  return Number.isInteger(n) && n >= 0 && n <= 500 ? n : 0;
}
export function Field({
  label,
  name,
  type = "text",
  value,
  required = false,
  maxLength,
  min,
  max,
  step,
  children,
  placeholder,
  onChange,
}: {
  label: string;
  name: string;
  type?: string;
  value?: string | number;
  required?: boolean;
  maxLength?: number;
  min?: number;
  max?: number;
  step?: string;
  children?: ReactNode;
  placeholder?: string;
  onChange?: (value: string) => void;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children ||
        (type === "textarea" ? (
          <textarea
            name={name}
            defaultValue={onChange ? undefined : value}
            value={onChange ? value : undefined}
            onChange={onChange ? e => onChange(e.target.value) : undefined}
            required={required}
            maxLength={maxLength}
            placeholder={placeholder}
          />
        ) : (
          <input
            name={name}
            type={type}
            defaultValue={onChange ? undefined : value}
            value={onChange ? value : undefined}
            onChange={onChange ? e => onChange(e.target.value) : undefined}
            required={required}
            maxLength={maxLength}
            min={min}
            max={max}
            step={step}
            placeholder={placeholder}
          />
        ))}
    </label>
  );
}
export function ActionForm({
  submit,
  children,
  label = "Save changes",
  onSuccess,
  success = "Changes saved.",
  className = "",
}: {
  submit: (data: FormData) => Promise<unknown>;
  children: ReactNode;
  label?: string;
  onSuccess?: () => void;
  success?: string;
  className?: string;
}) {
  const client = useQueryClient();
  const m = useMutation({
    mutationFn: submit,
    onSuccess: async () => {
      await client.invalidateQueries();
      onSuccess?.();
    },
  });
  return (
    <form
      className={"form " + className}
      onSubmit={(e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        if (!m.isPending) m.mutate(new FormData(e.currentTarget));
      }}
    >
      <fieldset disabled={m.isPending}>{children}</fieldset>
      <Notice error={m.error} />
      {m.isSuccess && (
        <p className="notice success" role="status">
          {success}
        </p>
      )}
      <button className="button" disabled={m.isPending}>
        {m.isPending ? "Saving…" : label}
      </button>
    </form>
  );
}
export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    d?.showModal();
    return () => d?.close();
  }, []);
  return (
    <dialog ref={ref} onCancel={onClose}>
      <div className="modal-head">
        <h2>{title}</h2>
        <button aria-label="Close dialog" onClick={onClose}>
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function ConfirmAction({
  label,
  run,
  danger = false,
}: {
  label: string;
  run: () => Promise<unknown>;
  danger?: boolean;
}) {
  const [open, set] = useState(false);
  return (
    <>
      <button className={danger ? "danger" : ""} onClick={() => set(true)}>
        {label}
      </button>
      {open && (
        <Modal title={label} onClose={() => set(false)}>
          <p>This changes the record immediately.</p>
          <ActionForm submit={run} label="Confirm" onSuccess={() => set(false)}>
            <p>Continue with this action?</p>
          </ActionForm>
        </Modal>
      )}
    </>
  );
}
export class ErrorBoundary extends Component<
  { children: ReactNode },
  { error: boolean }
> {
  state = { error: false };
  static getDerivedStateFromError() {
    return { error: true };
  }
  render() {
    return this.state.error ? (
      <main className="container">
        <Empty
          title="This page could not load"
          detail="Refresh the page to try again."
        />
        <button onClick={() => location.reload()}>Reload page</button>
      </main>
    ) : (
      this.props.children
    );
  }
}

export function SkeletonCard({ count = 4 }: { count?: number }) {
  return (
    <div className="skeleton-grid" role="status" aria-busy="true" aria-label="Loading catalogue items">
      {Array.from({ length: count }).map((_, idx) => (
        <div key={idx} className="editorial-card" style={{ padding: '0.75rem' }}>
          <div className="skeleton-box" style={{ width: '100%', height: '260px', marginBottom: '0.75rem' }} />
          <div className="skeleton-box" style={{ width: '70%', height: '16px', marginBottom: '0.5rem' }} />
          <div className="skeleton-box" style={{ width: '40%', height: '14px' }} />
        </div>
      ))}
    </div>
  );
}

export function LegalComplianceFooter() {
  return (
    <footer className="legal-footer" aria-label="Legal and privacy disclosures">
      <div>
        <strong>INSTABAZAAR ARCHIVE</strong> | Independent Indian Thrift &amp; Handloom Directory
      </div>
      <div className="legal-links">
        <a href="#terms-of-service" className="legal-link-btn" aria-label="Terms of Service">
          Terms of Service (Direct Seller Dispatch)
        </a>
        <a href="#privacy-policy" className="legal-link-btn" aria-label="Privacy Policy">
          Privacy Policy (Session &amp; E.164 WhatsApp Handoff)
        </a>
      </div>
    </footer>
  );
}

export { extractListingKeywords as extractReelKeywordsFromTranscript } from '../utils/listing';
export { ReelKeywordExtractorCard } from './listing-assistant';
