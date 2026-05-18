jest.mock("next/server", () => ({
  NextResponse: {
    json: (body: unknown, init?: { status?: number }) => ({
      status: init?.status ?? 200,
      headers: new Headers([["content-type", "application/json"]]),
      json: async () => body,
    }),
    redirect: (url: string | URL, init?: number | { status?: number }) => {
      const h = new Headers();
      h.set("Location", url instanceof URL ? url.toString() : String(url));
      const status = typeof init === "number" ? init : ((init && "status" in init ? init.status : undefined) ?? 307);
      return { status, headers: h };
    },
  },
}));

import { resolvePublicOriginOrConfigurationLoginRedirect, tryResolvePublicOrigin } from "../resolve-public-origin-response";

describe("tryResolvePublicOrigin", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = { ...originalEnv };
    jest.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  it("returns origin when NEXTAUTH_URL is valid", () => {
    process.env.NEXTAUTH_URL = "https://app.example.com/path";
    const r = tryResolvePublicOrigin("https://request.example", "/");
    expect(r).toEqual({ ok: true, origin: "https://app.example.com" });
  });

  it("returns 302 redirect to login when NEXTAUTH_URL is unset", () => {
    delete process.env.NEXTAUTH_URL;
    const r = tryResolvePublicOrigin("https://request.example", "/dashboard");
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.response.status).toBe(302);
      const loc = r.response.headers.get("Location");
      expect(loc).toBeTruthy();
      const u = new URL(loc!);
      expect(u.origin).toBe("https://request.example");
      expect(u.pathname).toBe("/login");
      expect(u.searchParams.get("error")).toBe("configuration");
      expect(u.searchParams.get("callbackUrl")).toBe("/dashboard");
    }
  });
});

describe("resolvePublicOriginOrConfigurationLoginRedirect", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = { ...originalEnv };
    jest.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  it("returns origin when NEXTAUTH_URL is valid", () => {
    process.env.NEXTAUTH_URL = "https://configured.example/path";
    const r = resolvePublicOriginOrConfigurationLoginRedirect("https://request.example", "/dashboard");
    expect(r).toEqual({ ok: true, origin: "https://configured.example" });
  });

  it("returns redirect to login on request origin when NEXTAUTH_URL is unset", () => {
    delete process.env.NEXTAUTH_URL;
    const r = resolvePublicOriginOrConfigurationLoginRedirect("https://request.example", "/dashboard");
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.response.status).toBe(302);
      const loc = r.response.headers.get("Location");
      expect(loc).toBeTruthy();
      const u = new URL(loc!);
      expect(u.origin).toBe("https://request.example");
      expect(u.pathname).toBe("/login");
      expect(u.searchParams.get("error")).toBe("configuration");
      expect(u.searchParams.get("callbackUrl")).toBe("/dashboard");
    }
  });
});
