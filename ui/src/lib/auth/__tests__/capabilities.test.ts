import { hasUiCapability, UI_CAPABILITIES } from "../capabilities";

describe("hasUiCapability", () => {
  const capability = UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS;

  it.each(["LEADERSHIP", "SUPPORT_ENGINEER"])("grants restricted dashboards to the %s role", (role) => {
    expect(hasUiCapability(["USER", role], capability)).toBe(true);
  });

  it.each([["USER"], ["ESCALATION"], ["USER", "ESCALATION"], [], undefined, null])(
    "denies restricted dashboards without a permitted backend role",
    (roles) => {
      expect(hasUiCapability(roles, capability)).toBe(false);
    }
  );

  it("fails closed for unknown or differently-cased roles", () => {
    expect(hasUiCapability(["leadership", "supportEngineer", "ADMIN"], capability)).toBe(false);
  });
});
