import { buildHref } from "../buildHref";

describe("buildHref", () => {
  describe("no params / empty params", () => {
    it("returns the path unchanged when params object is empty", () => {
      expect(buildHref("/tickets", {})).toBe("/tickets");
    });

    it("returns the path unchanged when all values are empty strings", () => {
      expect(buildHref("/tickets", { team: "", page: "" })).toBe("/tickets");
    });

    it("returns the path unchanged when all values are null", () => {
      expect(buildHref("/tickets", { team: null, page: null })).toBe("/tickets");
    });

    it("returns the path unchanged when all values are undefined", () => {
      expect(buildHref("/tickets", { team: undefined })).toBe("/tickets");
    });

    it("works correctly with the root path", () => {
      expect(buildHref("/", {})).toBe("/");
    });
  });

  describe("valid params", () => {
    it("appends a single param as a query string", () => {
      expect(buildHref("/tickets", { team: "platform" })).toBe("/tickets?team=platform");
    });

    it("appends multiple params", () => {
      const result = buildHref("/tickets", { team: "platform", page: "2" });
      // URLSearchParams order is insertion order — deterministic.
      expect(result).toBe("/tickets?team=platform&page=2");
    });

    it("URL-encodes values with spaces", () => {
      expect(buildHref("/escalations", { team: "Core Platform" })).toBe("/escalations?team=Core+Platform");
    });

    it("URL-encodes values with special characters", () => {
      expect(buildHref("/tickets", { filter: "a&b=c" })).toBe("/tickets?filter=a%26b%3Dc");
    });
  });

  describe("mixed valid and empty/null/undefined values", () => {
    it("omits null values but keeps non-null ones", () => {
      expect(buildHref("/tickets", { team: "platform", page: null })).toBe("/tickets?team=platform");
    });

    it("omits undefined values but keeps non-undefined ones", () => {
      expect(buildHref("/tickets", { team: undefined, page: "3" })).toBe("/tickets?page=3");
    });

    it("omits empty-string values but keeps non-empty ones", () => {
      expect(buildHref("/tickets", { team: "platform", page: "" })).toBe("/tickets?team=platform");
    });

    it("omits all three absent-value forms simultaneously", () => {
      expect(buildHref("/tickets", { team: "eng", a: null, b: undefined, c: "" })).toBe("/tickets?team=eng");
    });
  });

  describe("sidebar navigation use-case", () => {
    it("preserves the team param when navigating between pages", () => {
      expect(buildHref("/escalations", { team: "platform" })).toBe("/escalations?team=platform");
    });

    it("produces a clean URL with no query string when selectedTeam is empty", () => {
      expect(buildHref("/escalations", { team: "" })).toBe("/escalations");
    });

    it("produces a clean URL with no query string when selectedTeam is null", () => {
      expect(buildHref("/escalations", { team: null })).toBe("/escalations");
    });
  });
});
