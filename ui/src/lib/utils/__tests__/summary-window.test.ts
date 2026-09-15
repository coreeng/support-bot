// src/lib/utils/__tests__/summary-window.test.ts
import { presetWindow, toUtcDateString } from "../summary-window";

describe("presetWindow", () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe("lastWeek", () => {
    it("is Monday to Friday of the previous week on a weekday", () => {
      jest.setSystemTime(new Date("2026-09-16T12:00:00Z")); // Wednesday
      expect(presetWindow("lastWeek")).toEqual({ from: "2026-09-07", to: "2026-09-11" });
    });

    it("stays on the previous week all through the current one, including the weekend", () => {
      jest.setSystemTime(new Date("2026-09-14T00:10:00Z")); // Monday
      expect(presetWindow("lastWeek")).toEqual({ from: "2026-09-07", to: "2026-09-11" });
      jest.setSystemTime(new Date("2026-09-20T23:50:00Z")); // Sunday
      expect(presetWindow("lastWeek")).toEqual({ from: "2026-09-07", to: "2026-09-11" });
    });

    it("crosses month and year boundaries in UTC", () => {
      jest.setSystemTime(new Date("2026-01-01T00:10:00Z")); // Thursday
      expect(presetWindow("lastWeek")).toEqual({ from: "2025-12-22", to: "2025-12-26" });
    });

    it("uses the UTC weekday, not the local one", () => {
      // 23:30Z on Sunday 2026-09-13 is already Monday in zones east of UTC; UTC still says Sunday.
      jest.setSystemTime(new Date("2026-09-13T23:30:00Z"));
      expect(presetWindow("lastWeek")).toEqual({ from: "2026-08-31", to: "2026-09-04" });
    });
  });

  describe("last2Weeks", () => {
    it("is the Monday two weeks back to the Friday of the previous week", () => {
      jest.setSystemTime(new Date("2026-09-16T12:00:00Z")); // Wednesday
      expect(presetWindow("last2Weeks")).toEqual({ from: "2026-08-31", to: "2026-09-11" });
    });

    it("stays on the UTC calendar across the local DST spring-forward day", () => {
      // Europe/London springs forward on 2026-03-29 (a Sunday).
      jest.setSystemTime(new Date("2026-03-29T23:30:00Z"));
      expect(presetWindow("last2Weeks")).toEqual({ from: "2026-03-09", to: "2026-03-20" });
    });
  });

  describe("lastMonth", () => {
    it("is the first to the last day of the previous month", () => {
      jest.setSystemTime(new Date("2026-09-15T12:00:00Z"));
      expect(presetWindow("lastMonth")).toEqual({ from: "2026-08-01", to: "2026-08-31" });
    });

    it("handles short months, leap years and the year boundary", () => {
      jest.setSystemTime(new Date("2026-03-01T00:10:00Z"));
      expect(presetWindow("lastMonth")).toEqual({ from: "2026-02-01", to: "2026-02-28" });
      jest.setSystemTime(new Date("2028-03-01T00:10:00Z"));
      expect(presetWindow("lastMonth")).toEqual({ from: "2028-02-01", to: "2028-02-29" });
      jest.setSystemTime(new Date("2026-01-31T23:50:00Z"));
      expect(presetWindow("lastMonth")).toEqual({ from: "2025-12-01", to: "2025-12-31" });
    });
  });

  it("accepts an explicit clock", () => {
    expect(presetWindow("lastWeek", new Date("2026-06-15T00:00:00Z"))).toEqual({ from: "2026-06-08", to: "2026-06-12" });
  });
});

describe("toUtcDateString", () => {
  it("formats the UTC calendar day", () => {
    expect(toUtcDateString(new Date("2026-03-28T23:59:59Z"))).toBe("2026-03-28");
  });
});
