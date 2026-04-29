import { isOauthUiKnownProvider } from "../oauth-ui-callback";

describe("isOauthUiKnownProvider", () => {
  it("accepts known ids", () => {
    expect(isOauthUiKnownProvider("dex")).toBe(true);
  });

  it("rejects unknown ids", () => {
    expect(isOauthUiKnownProvider("google")).toBe(false);
    expect(isOauthUiKnownProvider("azure")).toBe(false);
    expect(isOauthUiKnownProvider("okta")).toBe(false);
    expect(isOauthUiKnownProvider("")).toBe(false);
  });
});
