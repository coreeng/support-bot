import { sanitizeCallbackUrl } from "../url";

describe("sanitizeCallbackUrl", () => {
  it("should sanitize external URL", () => {
    expect(sanitizeCallbackUrl("https://evil.com")).toBe("/");
  });

  it("should sanitize protocol-relative URL", () => {
    expect(sanitizeCallbackUrl("//evil.com")).toBe("/");
  });

  it("should sanitize javascript: URL", () => {
    expect(sanitizeCallbackUrl("javascript:alert(1)")).toBe("/");
  });

  it("should sanitize data: URL", () => {
    expect(sanitizeCallbackUrl("data:text/html,<script>alert(1)</script>")).toBe("/");
  });

  it("should sanitize http URL", () => {
    expect(sanitizeCallbackUrl("http://evil.com")).toBe("/");
  });

  it("should sanitize https URL", () => {
    expect(sanitizeCallbackUrl("https://evil.com")).toBe("/");
  });

  it("should allow valid relative URL", () => {
    expect(sanitizeCallbackUrl("/knowledge-gaps")).toBe("/knowledge-gaps");
  });

  it("should allow valid relative URL with query params", () => {
    expect(sanitizeCallbackUrl("/tickets?status=open")).toBe("/tickets?status=open");
  });

  it("should allow valid relative URL with hash", () => {
    expect(sanitizeCallbackUrl("/dashboard#metrics")).toBe("/dashboard#metrics");
  });

  it("should default to / when null", () => {
    expect(sanitizeCallbackUrl(null)).toBe("/");
  });

  it("should default to / when undefined", () => {
    expect(sanitizeCallbackUrl(undefined)).toBe("/");
  });

  it("should default to / when empty string", () => {
    expect(sanitizeCallbackUrl("")).toBe("/");
  });

  it("should sanitize URL that looks like relative but is not", () => {
    expect(sanitizeCallbackUrl("//example.com/path")).toBe("/");
  });
});
