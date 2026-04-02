import {computePublicOrigin} from "../resolve-public-origin";

describe("computePublicOrigin", () => {
  it("uses NEXTAUTH_URL origin when set", () => {
    expect(
      computePublicOrigin({
        nextAuthUrl: "https://app.example.com/support-bot/",
        forwardedHost: "wrong.example",
        forwardedProto: "http",
        fallbackOrigin: "http://0.0.0.0:3000",
      })
    ).toBe("https://app.example.com");
  });

  it("uses first X-Forwarded-Host and X-Forwarded-Proto when NEXTAUTH_URL unset", () => {
    expect(
      computePublicOrigin({
        nextAuthUrl: undefined,
        forwardedHost: "ingress.example.com, internal",
        forwardedProto: "https, http",
        fallbackOrigin: "http://0.0.0.0:3000",
      })
    ).toBe("https://ingress.example.com");
  });

  it("defaults proto to https when only forwarded host is present", () => {
    expect(
      computePublicOrigin({
        nextAuthUrl: undefined,
        forwardedHost: "only-host.example",
        forwardedProto: null,
        fallbackOrigin: "http://0.0.0.0:3000",
      })
    ).toBe("https://only-host.example");
  });

  it("falls back to fallbackOrigin when no env or forwarded host", () => {
    expect(
      computePublicOrigin({
        nextAuthUrl: undefined,
        forwardedHost: null,
        forwardedProto: null,
        fallbackOrigin: "http://127.0.0.1:3000",
      })
    ).toBe("http://127.0.0.1:3000");
  });
});
