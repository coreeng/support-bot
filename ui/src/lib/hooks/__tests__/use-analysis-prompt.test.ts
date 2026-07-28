import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { ApiError, useAnalysisPrompt } from "../index";

jest.mock("next-auth/react", () => ({
  getCsrfToken: jest.fn(() => Promise.resolve("mock-csrf-token")),
  signOut: jest.fn(() => Promise.resolve()),
}));

const originalFetch = global.fetch;

afterEach(() => {
  global.fetch = originalFetch;
});

const createWrapper = (defaults: { retry?: boolean; staleTime: number } = { retry: false, staleTime: 1000 * 60 * 5 }) => {
  // Mirror GlobalProviders' 5-minute default so the refetch test guards against the global cache
  const queryClient = new QueryClient({ defaultOptions: { queries: defaults } });
  const Wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children);
  Wrapper.displayName = "QueryClientTestWrapper";
  return Wrapper;
};

describe("useAnalysisPrompt", () => {
  it("does not fetch while disabled", async () => {
    global.fetch = jest.fn() as jest.MockedFunction<typeof fetch>;

    const { result } = renderHook(() => useAnalysisPrompt(false), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isFetching).toBe(false));
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("fetches the prompt from the BFF route with the CSRF token attached", async () => {
    global.fetch = jest.fn(async () => ({
      ok: true,
      json: async () => ({ prompt: "prompt text" }),
    })) as unknown as jest.MockedFunction<typeof fetch>;

    const { result } = renderHook(() => useAnalysisPrompt(true), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.data).toEqual({ prompt: "prompt text" }));
    expect(global.fetch).toHaveBeenCalledTimes(1);
    const [url, options] = (global.fetch as jest.Mock).mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/analysis/prompt");
    expect(options.headers).toMatchObject({ "X-CSRF-Token": "mock-csrf-token" });
  });

  it("refetches instead of serving cached data when re-enabled", async () => {
    let call = 0;
    global.fetch = jest.fn(async () => {
      call += 1;
      return { ok: true, json: async () => ({ prompt: `prompt v${call}` }) };
    }) as unknown as jest.MockedFunction<typeof fetch>;

    const { result, rerender } = renderHook(({ enabled }) => useAnalysisPrompt(enabled), {
      wrapper: createWrapper(),
      initialProps: { enabled: true },
    });
    await waitFor(() => expect(result.current.data).toEqual({ prompt: "prompt v1" }));

    rerender({ enabled: false });
    rerender({ enabled: true });

    await waitFor(() => expect(result.current.data).toEqual({ prompt: "prompt v2" }));
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it("surfaces an error when the fetch fails", async () => {
    global.fetch = jest.fn(async () => ({
      ok: false,
      status: 500,
      json: async () => ({}),
    })) as unknown as jest.MockedFunction<typeof fetch>;

    const { result } = renderHook(() => useAnalysisPrompt(true), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.data).toBeUndefined();
  });

  it("parses the backend error code into the ApiError", async () => {
    global.fetch = jest.fn(async () => ({
      ok: false,
      status: 500,
      json: async () => ({ code: "ANALYSIS_PROMPT_LOAD_FAILED" }),
    })) as unknown as jest.MockedFunction<typeof fetch>;

    const { result } = renderHook(() => useAnalysisPrompt(true), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect((result.current.error as ApiError).status).toBe(500);
    expect((result.current.error as ApiError).reason).toBe("ANALYSIS_PROMPT_LOAD_FAILED");
  });

  it("does not retry a failed fetch", async () => {
    global.fetch = jest.fn(async () => ({
      ok: false,
      status: 500,
      json: async () => ({}),
    })) as unknown as jest.MockedFunction<typeof fetch>;

    // No retry override in the wrapper: like GlobalProviders, only staleTime is set,
    // so the hook's own retry: false is what's under test.
    const { result } = renderHook(() => useAnalysisPrompt(true), {
      wrapper: createWrapper({ staleTime: 1000 * 60 * 5 }),
    });

    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });
});
