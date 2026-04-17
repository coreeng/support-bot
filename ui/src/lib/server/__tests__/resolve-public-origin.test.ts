import { resolvePublicOrigin } from "../resolve-public-origin";

describe("resolvePublicOrigin", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = { ...originalEnv };
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
