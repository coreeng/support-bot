import { getToken } from "next-auth/jwt";
import { backendFetch, proxyFetch } from "../backend-fetch";

jest.mock("next-auth/jwt", () => ({
  getToken: jest.fn(),
}));

describe("proxyFetch", () => {
  const originalFetch = global.fetch;
  const mockGetToken = getToken as jest.MockedFunction<typeof getToken>;

  afterEach(() => {
    global.fetch = originalFetch;
    delete process.env.PROXY_LOGGING;
    jest.restoreAllMocks();
    mockGetToken.mockReset();
  });

  it("logs when PROXY_LOGGING=true", async () => {
    process.env.PROXY_LOGGING = "true";
    global.fetch = jest.fn(() => Promise.resolve({ status: 200, ok: true } as Response));
    const spy = jest.spyOn(console, "log").mockImplementation();

    const res = await proxyFetch("proxy", "/test", "http://localhost/test", { method: "GET" });

    expect(res.status).toBe(200);
    expect(spy).toHaveBeenCalledWith(expect.stringContaining("[proxy] GET /test 200"));
  });

  it("does not log when PROXY_LOGGING is not set", async () => {
    global.fetch = jest.fn(() => Promise.resolve({ status: 200, ok: true } as Response));
    const spy = jest.spyOn(console, "log").mockImplementation();

    await proxyFetch("proxy", "/test", "http://localhost/test", { method: "GET" });

    expect(spy).not.toHaveBeenCalled();
  });

  it("always logs non-ok responses", async () => {
    global.fetch = jest.fn(() => Promise.resolve({ status: 500, ok: false } as Response));
    const spy = jest.spyOn(console, "error").mockImplementation();

    const res = await proxyFetch("proxy", "/test", "http://localhost/test", { method: "GET" });

    expect(res.status).toBe(500);
    expect(spy).toHaveBeenCalledWith(expect.stringContaining("[proxy] GET /test 500"));
  });

  it("fetches the full backend URL while excluding query values from the default log path", async () => {
    process.env.PROXY_LOGGING = "true";
    mockGetToken.mockResolvedValue({ accessToken: "access-token" });
    global.fetch = jest.fn(() => Promise.resolve({ status: 200, ok: true } as Response));
    const spy = jest.spyOn(console, "log").mockImplementation();

    await backendFetch({} as Request, "/elevate/products?query=customer-secret");

    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining("/elevate/products?query=customer-secret"), expect.any(Object));
    expect(spy).toHaveBeenCalledWith(expect.stringContaining("[proxy] GET /elevate/products 200"));
    expect(spy.mock.calls.flat().join(" ")).not.toContain("customer-secret");
  });

  it("uses an explicit route template to keep dynamic IDs out of failure logs", async () => {
    mockGetToken.mockResolvedValue({ accessToken: "access-token" });
    global.fetch = jest.fn(() => Promise.resolve({ status: 500, ok: false } as Response));
    const spy = jest.spyOn(console, "error").mockImplementation();

    await backendFetch(
      {} as Request,
      "/elevate/products/product-secret/users?snapshotVersion=snapshot-secret",
      {},
      "/elevate/products/:id/users"
    );

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/elevate/products/product-secret/users?snapshotVersion=snapshot-secret"),
      expect.any(Object)
    );
    expect(spy).toHaveBeenCalledWith(expect.stringContaining("[proxy] GET /elevate/products/:id/users 500"));
    expect(spy.mock.calls.flat().join(" ")).not.toMatch(/product-secret|snapshot-secret/);
  });
});
