import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { useAnalysisPrompt } from "../index";

const originalFetch = global.fetch;

afterEach(() => {
  global.fetch = originalFetch;
});

const createWrapper = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

  it("fetches the prompt from the BFF route when enabled", async () => {
    global.fetch = jest.fn(async () => ({
      ok: true,
      json: async () => ({ prompt: "prompt text" }),
    })) as unknown as jest.MockedFunction<typeof fetch>;

    const { result } = renderHook(() => useAnalysisPrompt(true), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.data).toEqual({ prompt: "prompt text" }));
    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect((global.fetch as jest.Mock).mock.calls[0][0]).toBe("/api/analysis/prompt");
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
});
