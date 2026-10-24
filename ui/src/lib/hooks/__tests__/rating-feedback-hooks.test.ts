import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { isApiError, useRatingFeedback } from "../index";

const originalFetch = global.fetch;

afterEach(() => {
  global.fetch = originalFetch;
});

function mockFetch(status: number, body: unknown) {
  global.fetch = jest.fn(() =>
    Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) } as Response)
  ) as unknown as typeof fetch;
}

function wrapper({ children }: { children: ReactNode }) {
  // No client-level retry override, so the hook's own retry policy is what runs
  return createElement(QueryClientProvider, { client: new QueryClient() }, children);
}

describe("useRatingFeedback", () => {
  it("asks the proxy route for the range as from/to", async () => {
    mockFetch(200, []);

    const { result } = renderHook(() => useRatingFeedback("2025-10-01", "2025-10-31"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(global.fetch).toHaveBeenCalledWith("/api/rating/feedback?from=2025-10-01&to=2025-10-31", expect.anything());
  });

  it("surfaces a 403 without retrying", async () => {
    mockFetch(403, {});

    const { result } = renderHook(() => useRatingFeedback(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(isApiError(result.current.error, 403)).toBe(true);
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });
});
