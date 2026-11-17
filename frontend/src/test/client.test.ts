import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, request, resetCsrf } from "../api/client";
const fetchMock = vi.fn();
beforeEach(() => {
  resetCsrf();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("window", { dispatchEvent: vi.fn() });
});
afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
describe("session transport", () => {
  it("adds CSRF and same-origin credentials to writes", async () => {
    fetchMock
      .mockResolvedValueOnce(
        json({ headerName: "X-CSRF-TOKEN", token: "secure" }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    await request("/me/wishlist/123", { method: "PUT" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [url, options] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/v1/me/wishlist/123");
    expect(options.credentials).toBe("same-origin");
    expect(options.headers["X-CSRF-TOKEN"]).toBe("secure");
  });
  it("does not replay a rejected mutation", async () => {
    fetchMock
      .mockResolvedValueOnce(
        json({ headerName: "X-CSRF-TOKEN", token: "secure" }),
      )
      .mockResolvedValueOnce(
        json(
          {
            code: "CSRF_INVALID",
            message: "Expired",
            fieldErrors: [],
            traceId: "t",
          },
          403,
        ),
      );
    await expect(
      request("/seller/products", { method: "POST", body: {} }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
  it("keeps field errors and concurrency conflicts", async () => {
    fetchMock.mockResolvedValueOnce(
      json(
        {
          code: "VERSION_CONFLICT",
          message: "Changed",
          fieldErrors: [{ field: "title", message: "Required" }],
          traceId: "trace",
        },
        409,
      ),
    );
    await expect(request("/example")).rejects.toMatchObject({
      status: 409,
      body: { code: "VERSION_CONFLICT", traceId: "trace" },
    });
  });
  it("leaves multipart boundary to the browser", async () => {
    fetchMock
      .mockResolvedValueOnce(
        json({ headerName: "X-CSRF-TOKEN", token: "secure" }),
      )
      .mockResolvedValueOnce(json({ id: "asset" }));
    const form = new FormData();
    form.set("file", new Blob(["image"]));
    await request("/seller/media", { method: "POST", body: form });
    expect(fetchMock.mock.calls[1][1].headers["Content-Type"]).toBeUndefined();
  });
  it("does not expire session on normal anonymous /me", async () => {
    fetchMock.mockResolvedValueOnce(
      json({ code: "AUTHENTICATION_REQUIRED", message: "Login required" }, 401),
    );
    await expect(request("/me")).rejects.toBeInstanceOf(ApiError);
    expect(window.dispatchEvent).not.toHaveBeenCalled();
  });
});
