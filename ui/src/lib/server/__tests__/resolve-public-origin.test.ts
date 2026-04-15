import {
  resolvePublicOrigin,
  tryResolvePublicOrigin,
} from "../resolve-public-origin";

describe("resolvePublicOrigin", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = {...originalEnv};
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  it("returns origin from NEXTAUTH_URL", () => {
    process.env.NEXTAUTH_URL = "https://app.example.com/support-bot/";
    expect(resolvePublicOrigin()).toBe("https://app.example.com");
  });

  it("strips path and trailing slash from NEXTAUTH_URL", () => {
    process.env.NEXTAUTH_URL = "https://app.example.com:8443/path/";
    expect(resolvePublicOrigin()).toBe("https://app.example.com:8443");
  });

  it("throws when NEXTAUTH_URL is not set", () => {
    delete process.env.NEXTAUTH_URL;
    expect(() => resolvePublicOrigin()).toThrow("NEXTAUTH_URL is not set or invalid");
  });

  it("throws when NEXTAUTH_URL is empty", () => {
    process.env.NEXTAUTH_URL = "  ";
    expect(() => resolvePublicOrigin()).toThrow("NEXTAUTH_URL is not set or invalid");
  });

  it("throws when NEXTAUTH_URL is malformed", () => {
    process.env.NEXTAUTH_URL = "not-a-url";
    expect(() => resolvePublicOrigin()).toThrow("NEXTAUTH_URL is not set or invalid");
  });
});

describe("tryResolvePublicOrigin", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = {...originalEnv};
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
    const r = tryResolvePublicOrigin();
    expect(r).toEqual({ok: true, origin: "https://app.example.com"});
  });

  it("returns 500 response when NEXTAUTH_URL is unset", async () => {
    delete process.env.NEXTAUTH_URL;
    const r = tryResolvePublicOrigin();
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.response.status).toBe(500);
      const body = await r.response.json();
      expect(body.error).toContain("NEXTAUTH_URL");
    }
  });
});
