import { isOauthUiKnownProvider } from "../oauth-ui-callback";

describe("isOauthUiKnownProvider", () => {
  it("accepts known ids", () => {
    expect(isOauthUiKnownProvider("google")).toBe(true);
    expect(isOauthUiKnownProvider("azure")).toBe(true);
    expect(isOauthUiKnownProvider("dex")).toBe(true);
  });

  it("rejects unknown ids", () => {
    expect(isOauthUiKnownProvider("okta")).toBe(false);
    expect(isOauthUiKnownProvider("")).toBe(false);
  });
});
