import type { ApiErrorBody } from "./contracts";
const base = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(
  /\/$/,
  "",
);
if (!base.startsWith("/") || base.startsWith("//"))
  throw new Error("API base must be a same-origin path.");
export class ApiError extends Error {
  constructor(
    public status: number,
    public body: ApiErrorBody,
  ) {
    super(body.message);
    this.name = "ApiError";
  }
}
let csrf: Promise<{ headerName: string; token: string }> | undefined;
export function resetCsrf() {
  csrf = undefined;
}
export async function getCsrf() {
  csrf ??= fetch(base + "/auth/csrf", {
    signal: AbortSignal.timeout(30000),
    credentials: "same-origin",
    cache: "no-store",
  })
    .then(async (r) => {
      if (!r.ok)
        throw new Error(
          "Could not initialize a secure session. Please try again.",
        );
      const token = await r.json();
      if (
        token.headerName !== "X-CSRF-TOKEN" ||
        typeof token.token !== "string"
      )
        throw new Error("Invalid security token response.");
      return token;
    })
    .catch((e) => {
      csrf = undefined;
      throw e;
    });
  return csrf;
}
export async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  const { method = "GET", body, signal } = options;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined && !(body instanceof FormData))
    headers["Content-Type"] = "application/json";
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = await getCsrf();
    headers[token.headerName] = token.token;
  }
  const controller = new AbortController();
  const timeout = setTimeout(
    () =>
      controller.abort(
        new Error("Request timed out. Check your connection and retry."),
      ),
    30000,
  );
  try {
    const response = await fetch(base + path, {
      method,
      headers,
      credentials: "same-origin",
      cache: "no-store",
      signal: signal
        ? AbortSignal.any([signal, controller.signal])
        : controller.signal,
      body:
        body === undefined
          ? undefined
          : body instanceof FormData
            ? body
            : JSON.stringify(body),
    });
    if (!response.ok) {
      const fallback: ApiErrorBody = {
        code: "REQUEST_FAILED",
        message: "Request failed (" + response.status + "). Please try again.",
        fieldErrors: [],
        traceId: "",
        timestamp: new Date().toISOString(),
      };
      const data = await response.json().catch(() => fallback);
      const error = new ApiError(response.status, { ...fallback, ...data });
      if (
        (response.status === 401 &&
          path !== "/me" &&
          !path.startsWith("/auth/")) ||
        data.code === "ACCOUNT_DISABLED"
      )
        window.dispatchEvent(new Event("session-expired"));
      if (data.code === "CSRF_INVALID") resetCsrf();
      throw error;
    }
    return response.status === 204 ? (undefined as T) : await response.json();
  } finally {
    clearTimeout(timeout);
  }
}
export const write = <T>(path: string, method: string, body?: unknown) =>
  request<T>(path, { method, body });
