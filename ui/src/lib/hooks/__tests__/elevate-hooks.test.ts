import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import {
  ApiError,
  shouldRetryElevateQuery,
  useElevateJourneyUsers,
  useElevateProducts,
  useElevateProductUsers,
  useElevateUserJourneys,
} from "../index";

const originalFetch = global.fetch;

afterEach(() => {
  global.fetch = originalFetch;
});

describe("shouldRetryElevateQuery", () => {
  it.each([400, 401, 403, 404, 409, 422])("fails fast for non-transient HTTP %s responses", (status) => {
    expect(shouldRetryElevateQuery(0, new ApiError(status))).toBe(false);
  });

  it.each([429, 500, 502, 503])("retries transient HTTP %s responses within the retry limit", (status) => {
    expect(shouldRetryElevateQuery(0, new ApiError(status))).toBe(true);
    expect(shouldRetryElevateQuery(2, new ApiError(status))).toBe(false);
  });

  it("retries transport failures within the retry limit", () => {
    expect(shouldRetryElevateQuery(1, new Error("network failure"))).toBe(true);
    expect(shouldRetryElevateQuery(2, new Error("network failure"))).toBe(false);
  });
});

describe("Elevate query cancellation", () => {
  it("aborts the browser request when the query is cancelled", async () => {
    let requestSignal: AbortSignal | undefined;
    global.fetch = jest.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      requestSignal = init?.signal as AbortSignal;
      return new Promise<Response>((_resolve, reject) => {
        requestSignal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
      });
    }) as jest.MockedFunction<typeof fetch>;
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children);

    renderHook(
      () =>
        useElevateProducts({
          snapshotVersion: "11111111-1111-1111-1111-111111111111",
          page: 0,
        }),
      { wrapper }
    );

    await waitFor(() => expect(global.fetch).toHaveBeenCalled());
    expect(requestSignal).toBeInstanceOf(AbortSignal);
    expect(requestSignal?.aborted).toBe(false);

    await act(async () => {
      await queryClient.cancelQueries({ queryKey: ["elevate"] });
    });

    expect(requestSignal?.aborted).toBe(true);
  });
});

describe("Elevate relationship query keys", () => {
  it("separates direct-relationship caches by relationship filter", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children);
    const request = {
      snapshotVersion: "11111111-1111-1111-1111-111111111111",
      page: 0,
    } as const;

    renderHook(() => useElevateJourneyUsers("journey-1", { ...request, relationship: "linked" }, false), { wrapper });
    renderHook(() => useElevateJourneyUsers("journey-1", { ...request, relationship: "unassigned" }, false), { wrapper });
    renderHook(() => useElevateUserJourneys("user-1", { ...request, relationship: "linked" }, false), { wrapper });
    renderHook(() => useElevateUserJourneys("user-1", { ...request, relationship: "unassigned" }, false), { wrapper });

    const keys = queryClient
      .getQueryCache()
      .getAll()
      .map(({ queryKey }) => queryKey);
    expect(keys).toEqual(
      expect.arrayContaining([
        ["elevate", "journey-users", request.snapshotVersion, "journey-1", 0, 20, "", "", "linked", "name", "asc"],
        ["elevate", "journey-users", request.snapshotVersion, "journey-1", 0, 20, "", "", "unassigned", "name", "asc"],
        ["elevate", "user-journeys", request.snapshotVersion, "user-1", 0, 20, "", "", "linked", "name", "asc"],
        ["elevate", "user-journeys", request.snapshotVersion, "user-1", 0, 20, "", "", "unassigned", "name", "asc"],
      ])
    );
  });

  it("separates exact-ID lookups from free-text searches", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children);
    const request = {
      snapshotVersion: "11111111-1111-1111-1111-111111111111",
      page: 0,
    } as const;

    renderHook(() => useElevateProductUsers("product-1", { ...request, exactId: "user-21" }, false), { wrapper });
    renderHook(() => useElevateProductUsers("product-1", { ...request, query: "user-21" }, false), { wrapper });

    const keys = queryClient
      .getQueryCache()
      .getAll()
      .map(({ queryKey }) => queryKey);
    expect(keys).toEqual(
      expect.arrayContaining([
        ["elevate", "product-users", request.snapshotVersion, "product-1", 0, 20, "", "user-21", "all", "name", "asc"],
        ["elevate", "product-users", request.snapshotVersion, "product-1", 0, 20, "user-21", "", "all", "name", "asc"],
      ])
    );
  });
});
