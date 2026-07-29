import type { NextRequest } from "next/server";
import { validateCsrfToken } from "../csrf";

jest.mock("../backend-fetch", () => ({
  errorResponse: (message: string, status = 500) => ({ status, json: async () => ({ error: message }) }),
}));

function makeRequest({
  csrfHeader = "csrf-token",
  csrfCookie = "csrf-token|hash",
  cookieName = "authjs.csrf-token",
}: { csrfHeader?: string | null; csrfCookie?: string | null; cookieName?: string } = {}): NextRequest {
  return {
    headers: { get: (name: string) => (name === "X-CSRF-Token" ? csrfHeader : null) },
    cookies: {
      get: (name: string) => (name === cookieName && csrfCookie !== null ? { value: csrfCookie } : undefined),
    },
  } as unknown as NextRequest;
}

describe("validateCsrfToken", () => {
  it("returns null when the header matches the token part of the cookie", () => {
    expect(validateCsrfToken(makeRequest())).toBeNull();
  });

  it("compares against only the token part of the token|hash cookie format", () => {
    expect(validateCsrfToken(makeRequest({ csrfHeader: "tok", csrfCookie: "tok|somehash" }))).toBeNull();
  });

  it("returns 403 when the CSRF header is missing", async () => {
    const response = validateCsrfToken(makeRequest({ csrfHeader: null }));

    expect(response?.status).toBe(403);
    await expect(response?.json()).resolves.toEqual({ error: "Missing CSRF token" });
  });

  it("returns 403 when the CSRF cookie is missing", async () => {
    const response = validateCsrfToken(makeRequest({ csrfCookie: null }));

    expect(response?.status).toBe(403);
    await expect(response?.json()).resolves.toEqual({ error: "Missing CSRF token" });
  });

  it("returns 403 when the header does not match the cookie token", async () => {
    const response = validateCsrfToken(makeRequest({ csrfHeader: "other-token" }));

    expect(response?.status).toBe(403);
    await expect(response?.json()).resolves.toEqual({ error: "Invalid CSRF token" });
  });

  it("reads the __Host- prefixed cookie in production", () => {
    const originalNodeEnv = process.env.NODE_ENV;
    (process.env as { NODE_ENV?: string }).NODE_ENV = "production";
    try {
      expect(validateCsrfToken(makeRequest({ cookieName: "__Host-authjs.csrf-token" }))).toBeNull();
      expect(validateCsrfToken(makeRequest())?.status).toBe(403);
    } finally {
      (process.env as { NODE_ENV?: string }).NODE_ENV = originalNodeEnv;
    }
  });
});
