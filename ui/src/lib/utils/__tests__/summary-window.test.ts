// src/lib/utils/__tests__/summary-window.test.ts
import { toUtcDateString, windowEndingYesterday } from "../summary-window";

describe("windowEndingYesterday", () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("returns 14 inclusive UTC days ending yesterday on a plain date", () => {
    jest.setSystemTime(new Date("2026-06-15T12:00:00Z"));
    expect(windowEndingYesterday(14)).toEqual({ from: "2026-06-01", to: "2026-06-14" });
  });

  it("stays on the UTC calendar across the local DST spring-forward day", () => {
    // Europe/London springs forward on 2026-03-29; local-calendar arithmetic would yield to=2026-03-29.
    jest.setSystemTime(new Date("2026-03-29T23:30:00Z"));
    expect(windowEndingYesterday(14)).toEqual({ from: "2026-03-15", to: "2026-03-28" });
  });

  it("crosses month and year boundaries in UTC", () => {
    jest.setSystemTime(new Date("2026-01-01T00:10:00Z"));
    expect(windowEndingYesterday(7)).toEqual({ from: "2025-12-25", to: "2025-12-31" });
  });

  it("accepts an explicit clock", () => {
    expect(windowEndingYesterday(1, new Date("2026-06-15T00:00:00Z"))).toEqual({
      from: "2026-06-14",
      to: "2026-06-14",
    });
  });
});

describe("toUtcDateString", () => {
  it("formats the UTC calendar day", () => {
    expect(toUtcDateString(new Date("2026-03-28T23:59:59Z"))).toBe("2026-03-28");
  });
});
