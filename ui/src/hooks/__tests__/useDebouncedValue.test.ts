import { act, renderHook } from "@testing-library/react";
import { useDebouncedValue } from "../useDebouncedValue";

describe("useDebouncedValue", () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it("coalesces rapid value changes and clears an empty search immediately", () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 250), {
      initialProps: { value: "platform" },
    });

    rerender({ value: "runtime" });
    expect(result.current).toBe("platform");

    act(() => jest.advanceTimersByTime(249));
    expect(result.current).toBe("platform");

    act(() => jest.advanceTimersByTime(1));
    expect(result.current).toBe("runtime");

    rerender({ value: "" });
    expect(result.current).toBe("");

    rerender({ value: "journeys" });
    expect(result.current).toBe("");

    act(() => jest.advanceTimersByTime(249));
    expect(result.current).toBe("");

    act(() => jest.advanceTimersByTime(1));
    expect(result.current).toBe("journeys");
  });
});
