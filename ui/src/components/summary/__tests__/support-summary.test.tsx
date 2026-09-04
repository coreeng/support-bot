/**
 * SupportSummaryPage Unit Tests
 *
 * The page is rendered against the real `useSummary` / `useRegistry` hooks and a real
 * QueryClient; only `fetch` is mocked, at the API-route level, so the tests pin the exact
 * requests the page makes (`/api/summary?from=..&to=..`) and how it handles each reply
 * shape defined by the backend's `SummaryUI` contract.
 */

import { clearMockUrlParamsInitial, useMockUrlParams as mockUseUrlParams, setMockUrlParamsInitial } from "@/test-utils/mock-url-params";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PRESET_DAYS } from "../../../lib/dateRange";
import { ApiError, MAX_SUMMARY_POLL_FAILURES } from "../../../lib/hooks";
import type { SummaryData, SummarySection, SummaryTicket } from "../../../lib/types/summary";
import { MAX_SUMMARY_WINDOW_DAYS, windowEndingYesterday } from "../../../lib/utils/summary-window";
import SupportSummaryPage, { SUMMARY_NOT_ENABLED, summaryErrorMessage } from "../support-summary";

// useSummary sends the CSRF token with the summary request; the global next-auth mock has no getCsrfToken.
jest.mock("next-auth/react", () => ({
  getCsrfToken: jest.fn(() => Promise.resolve("mock-csrf-token")),
  signOut: jest.fn(() => Promise.resolve()),
}));

// A useState-backed useUrlParams so preset and date changes re-render, and so tests can seed
// deep-link params (`?dateFilter=custom&dateFrom=..`) via setMockUrlParamsInitial.
jest.mock("../../../lib/hooks/useUrlParams", () => ({
  ...jest.requireActual("../../../lib/hooks/useUrlParams"),
  useUrlParams: mockUseUrlParams,
}));

jest.mock("../../tickets/EditTicketModal", () => ({
  __esModule: true,
  default: ({
    ticketId,
    open,
    onOpenChange,
    onSuccess,
  }: {
    ticketId: string | null;
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSuccess?: () => void;
  }) =>
    open ? (
      <div data-testid="edit-ticket-modal">
        <span>Ticket modal for {ticketId}</span>
        <button type="button" onClick={onSuccess}>
          Trigger modal success
        </button>
        <button type="button" onClick={() => onOpenChange(false)}>
          Close modal
        </button>
      </div>
    ) : null,
}));

jest.mock("../prompt-dialog", () => ({
  __esModule: true,
  default: ({ open }: { open: boolean }) => (open ? <div data-testid="prompt-dialog" /> : null),
}));

// ===== Fixtures (shaped like SummaryMapper's output) =====

const ticket = (ticketId: string, text: string, timestamp: string): SummaryTicket => ({ ticketId, text, timestamp });

const readySection: SummarySection = {
  state: "ready",
  content: "Tenants mostly asked how to rotate Kafka credentials; the rest were transient CI outages.",
  model: "gemini-2.5-pro",
  generatedAt: "2026-09-02T06:00:00Z",
};

const readySummary: SummaryData = {
  from: "2026-08-19",
  to: "2026-08-31",
  totalTickets: 42,
  classifiedTickets: 40,
  unclassifiedTickets: 2,
  drivers: [
    {
      label: "Knowledge Gap",
      count: 24,
      recent: [
        ticket("101", "How do I rotate the Kafka credentials?", "2026-08-31T09:15:00Z"),
        ticket("102", "Where are the DNS rules documented?", "2026-08-30T14:20:00Z"),
      ],
    },
    { label: "Product Temporary Issue", count: 10, recent: [ticket("103", "CI runners are timing out", "2026-08-29T10:00:00Z")] },
    { label: "Task Request", count: 6, recent: [] },
  ],
  categories: [
    {
      label: "Connectivity and Networking",
      count: 12,
      recent: [ticket("102", "Where are the DNS rules documented?", "2026-08-30T14:20:00Z")],
    },
  ],
  knowledgeGaps: [{ label: "Connectivity and Networking", count: 9, recent: [] }],
  features: [{ label: "Kafka", count: 8, recent: [ticket("101", "How do I rotate the Kafka credentials?", "2026-08-31T09:15:00Z")] }],
  teams: [{ label: "payments", count: 15, recent: [], topProduct: "Checkout" }],
  products: [{ label: "Checkout", count: 11, recent: [] }],
  summary: readySection,
};

const withSummary = (summary: SummarySection): SummaryData => ({ ...readySummary, summary });

const emptyRegistry = { impacts: [], tags: [] };

// ===== fetch mocking at the API-route level =====

type MockReply = { status: number; body?: unknown } | Promise<never>;
type RouteHandler = (url: string) => MockReply;

const originalFetch = global.fetch;

const enabled =
  (body: { enabled: boolean }): RouteHandler =>
  () => ({ status: 200, body });
const pending: RouteHandler = () => new Promise<never>(() => {});

/** Routes every `/api/*` call the page makes; unrouted paths fail loudly. */
function mockApi(summaryReply: RouteHandler, registry: unknown = emptyRegistry, enabledReply: RouteHandler = enabled({ enabled: true })) {
  global.fetch = jest.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    let reply: MockReply;
    if (url === "/api/registry") reply = { status: 200, body: registry };
    else if (url === "/api/summary/enabled") reply = enabledReply(url);
    else if (url.startsWith("/api/summary")) reply = summaryReply(url);
    else throw new Error(`Unexpected fetch: ${url}`);
    const { status, body } = await reply;
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
  }) as jest.MockedFunction<typeof fetch>;
}

/** A successful summary reply; like the backend, it echoes the requested window. */
const ok =
  (body: SummaryData): RouteHandler =>
  (url) => {
    const requested = new URLSearchParams(url.split("?")[1] ?? "");
    const from = requested.get("from");
    const to = requested.get("to");
    return { status: 200, body: from && to ? { ...body, from, to } : body };
  };

const failing =
  (status: number): RouteHandler =>
  () => ({ status, body: { error: `Backend error: ${status}` } });

/** Answers each summary request with the next handler; the last one repeats. */
function sequence(...replies: (SummaryData | RouteHandler)[]): RouteHandler {
  let index = 0;
  return (url) => {
    const reply = replies[Math.min(index++, replies.length - 1)];
    return typeof reply === "function" ? reply(url) : ok(reply)(url);
  };
}

const summaryRequests = () =>
  (global.fetch as jest.MockedFunction<typeof fetch>).mock.calls
    .map(([input]) => String(input))
    .filter((url) => url === "/api/summary" || url.startsWith("/api/summary?"));

const enabledRequests = () =>
  (global.fetch as jest.MockedFunction<typeof fetch>).mock.calls
    .map(([input]) => String(input))
    .filter((url) => url === "/api/summary/enabled");

const summaryUrl = (from: string, to: string) => `/api/summary?from=${from}&to=${to}`;
const defaultWindow = () => windowEndingYesterday(PRESET_DAYS.last2Weeks);

// ===== Rendering =====

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <SupportSummaryPage />
    </QueryClientProvider>
  );
  return { ...utils, queryClient, invalidateQueries };
}

const findAtAGlance = () => screen.findByTestId("summary-at-a-glance");
const chip = (label: string) => screen.getByText(label).closest("div") as HTMLElement;

describe("SupportSummaryPage", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    clearMockUrlParamsInitial();
    mockApi(ok(readySummary));
  });

  afterEach(() => {
    jest.useRealTimers();
    global.fetch = originalFetch;
  });

  describe("summaryErrorMessage", () => {
    it("explains a 403 as a permission problem", () => {
      expect(summaryErrorMessage(new ApiError(403))).toEqual({
        title: "You do not have permission to view the Support Summary",
        detail: "Ask an administrator for access.",
      });
    });

    it("explains a 404 as the feature being disabled", () => {
      expect(summaryErrorMessage(new ApiError(404))).toEqual({
        title: "Support Summary is not enabled",
        detail: "Enable the summary feature on the server to use this page.",
      });
    });

    it("explains SUMMARY_WINDOW_INVALID with the backend's window rules", () => {
      expect(summaryErrorMessage(new ApiError(400, "SUMMARY_WINDOW_INVALID"))).toEqual({
        title: "The selected range is invalid",
        detail: `The window must not exceed ${MAX_SUMMARY_WINDOW_DAYS} days, and the end date must not be before the start date.`,
      });
    });

    it("explains ANALYSIS_PROMPT_LOAD_FAILED as a server prompt configuration problem", () => {
      expect(summaryErrorMessage(new ApiError(500, "ANALYSIS_PROMPT_LOAD_FAILED"))).toEqual({
        title: "The classification prompt could not be loaded",
        detail: "The summary cannot be computed until the analysis prompt configuration on the server is fixed.",
      });
    });

    it.each([
      ["a plain Error", new Error("network failure")],
      ["an ApiError without a code", new ApiError(500)],
      ["an ApiError with an unknown code", new ApiError(400, "SOMETHING_ELSE")],
    ])("falls back to the generic message for %s", (_label, error) => {
      expect(summaryErrorMessage(error)).toEqual({ title: "Error loading support summary", detail: "Please try again later" });
    });
  });

  describe("Loading and error states", () => {
    it("shows the loading state while the first summary request is in flight", () => {
      mockApi(pending);

      renderPage();

      expect(screen.getByText("Loading support summary...")).toBeInTheDocument();
      expect(screen.queryByTestId("summary-error")).not.toBeInTheDocument();
      expect(screen.queryByTestId("summary-at-a-glance")).not.toBeInTheDocument();
    });

    // Only non-retried statuses are rendered here: `useSummary` retries 5xx twice with backoff,
    // which the unit tests above cover without waiting on real timers.
    it.each([
      [403, undefined, "You do not have permission to view the Support Summary", "Ask an administrator for access."],
      [404, undefined, "Support Summary is not enabled", "Enable the summary feature on the server to use this page."],
      [
        400,
        "SUMMARY_WINDOW_INVALID",
        "The selected range is invalid",
        `The window must not exceed ${MAX_SUMMARY_WINDOW_DAYS} days, and the end date must not be before the start date.`,
      ],
      [422, undefined, "Error loading support summary", "Please try again later"],
    ])("renders the error block for HTTP %s (code %s)", async (status, code, title, detail) => {
      mockApi(() => ({ status, body: { error: `Backend error: ${status}`, code } }));

      renderPage();

      const errorBlock = await screen.findByTestId("summary-error");
      expect(within(errorBlock).getByText(title)).toBeInTheDocument();
      expect(within(errorBlock).getByText(detail)).toBeInTheDocument();
      expect(screen.queryByText("Loading support summary...")).not.toBeInTheDocument();
      expect(screen.queryByTestId("summary-at-a-glance")).not.toBeInTheDocument();
    });

    it("renders the error block when the API route forwards no JSON body", async () => {
      global.fetch = jest.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url === "/api/registry") return { ok: true, status: 200, json: async () => emptyRegistry } as Response;
        return { ok: false, status: 404, json: async () => Promise.reject(new SyntaxError("no body")) } as unknown as Response;
      }) as jest.MockedFunction<typeof fetch>;

      renderPage();

      expect(await screen.findByText("Support Summary is not enabled")).toBeInTheDocument();
    });
  });

  describe("Feature flag", () => {
    it("shows the loading state and holds the summary request while the flag is checked", async () => {
      mockApi(ok(readySummary), emptyRegistry, pending);

      renderPage();

      expect(screen.getByText("Loading support summary...")).toBeInTheDocument();
      await waitFor(() => expect(enabledRequests()).toHaveLength(1));
      expect(summaryRequests()).toHaveLength(0);
      expect(screen.queryByTestId("summary-not-enabled")).not.toBeInTheDocument();
    });

    it("renders the not-enabled state and never requests the summary when the flag is off", async () => {
      mockApi(ok(readySummary), emptyRegistry, enabled({ enabled: false }));

      renderPage();

      const block = await screen.findByTestId("summary-not-enabled");
      // The same copy as the 404 mapping, so a direct visit and a refused request read alike.
      expect(within(block).getByText(SUMMARY_NOT_ENABLED.title)).toBeInTheDocument();
      expect(within(block).getByText(SUMMARY_NOT_ENABLED.detail)).toBeInTheDocument();
      expect(summaryErrorMessage(new ApiError(404))).toEqual(SUMMARY_NOT_ENABLED);
      expect(screen.getByRole("heading", { level: 1, name: "Support Summary" })).toBeInTheDocument();
      expect(screen.queryByText("Loading support summary...")).not.toBeInTheDocument();
      expect(screen.queryByTestId("summary-error")).not.toBeInTheDocument();
      expect(screen.queryByTestId("summary-at-a-glance")).not.toBeInTheDocument();
      // The page-level controls would only lead to more refused requests.
      expect(screen.queryByRole("button", { name: /View Prompts/ })).not.toBeInTheDocument();
      expect(screen.queryByTestId("summary-date-filter")).not.toBeInTheDocument();
      expect(summaryRequests()).toHaveLength(0);
    });

    it("loads the summary once the flag reports the feature on", async () => {
      renderPage();

      await findAtAGlance();
      expect(enabledRequests()).toHaveLength(1);
      expect(summaryRequests()).toHaveLength(1);
      expect(screen.queryByTestId("summary-not-enabled")).not.toBeInTheDocument();
    });

    it("still requests the summary when the flag check itself fails, so its own error explains why", async () => {
      mockApi(failing(404), emptyRegistry, failing(500));

      renderPage();

      const errorBlock = await screen.findByTestId("summary-error");
      expect(within(errorBlock).getByText(SUMMARY_NOT_ENABLED.title)).toBeInTheDocument();
      expect(summaryRequests()).toHaveLength(1);
    });
  });

  describe("Generating state", () => {
    it("shows thread progress with a percentage and progress bar while classifying", async () => {
      mockApi(ok(withSummary({ state: "generating", progress: { phase: "classifying", analysedThreads: 30, totalThreads: 120 } })));

      renderPage();

      const card = await findAtAGlance();
      const message = within(card).getByText(/Analysing threads/);
      expect(message).toHaveTextContent("Analysing threads... 30 of 120 complete");
      expect(within(card).getByText("25%")).toHaveClass("font-mono", "tabular-nums");
      const bar = card.querySelector<HTMLElement>(".bg-secondary.h-full");
      expect(bar).not.toBeNull();
      expect(bar).toHaveStyle({ width: "25%" });
      // The breakdowns are unaffected by the prose still generating.
      expect(screen.getByText("Top Support Areas")).toBeInTheDocument();
    });

    it("shows a check message without a bar before the backfill has counted threads", async () => {
      mockApi(ok(withSummary({ state: "generating", progress: { phase: "classifying" } })));

      renderPage();

      const card = await findAtAGlance();
      const message = within(card).getByText("Checking for threads to analyse...");
      // The status row carries no percentage, and no bar follows it.
      const statusRow = message.closest(".justify-between") as HTMLElement;
      expect(statusRow.textContent).not.toMatch(/%/);
      expect(card.querySelector(".bg-secondary.h-full")).toBeNull();
    });

    it("shows the summarising phase message once classification is done", async () => {
      mockApi(ok(withSummary({ state: "generating", progress: { phase: "summarising", analysedThreads: 120, totalThreads: 120 } })));

      renderPage();

      const card = await findAtAGlance();
      expect(within(card).getByText("Writing the summary...")).toBeInTheDocument();
      expect(within(card).getByText("100%")).toBeInTheDocument();
    });

    it("polls every 3 seconds while generating and stops once the summary is ready", async () => {
      jest.useFakeTimers();
      mockApi(
        sequence(
          withSummary({ state: "generating", progress: { phase: "classifying", analysedThreads: 30, totalThreads: 120 } }),
          withSummary({ state: "generating", progress: { phase: "summarising", analysedThreads: 120, totalThreads: 120 } }),
          readySummary
        )
      );

      renderPage();

      expect(await screen.findByText(/Analysing threads/)).toBeInTheDocument();
      expect(summaryRequests()).toHaveLength(1);

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      expect(await screen.findByText("Writing the summary...")).toBeInTheDocument();
      expect(summaryRequests()).toHaveLength(2);

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      expect(await screen.findByText(readySection.content as string)).toBeInTheDocument();
      expect(summaryRequests()).toHaveLength(3);

      // Ready: no further polls, however long the page stays open.
      await act(async () => {
        jest.advanceTimersByTime(30000);
      });
      expect(summaryRequests()).toHaveLength(3);
    });

    it("stops polling once the summary is unavailable", async () => {
      jest.useFakeTimers();
      mockApi(
        sequence(
          withSummary({ state: "generating", progress: { phase: "classifying" } }),
          withSummary({ state: "unavailable", error: "The model returned an empty response." })
        )
      );

      renderPage();

      expect(await screen.findByText("Checking for threads to analyse...")).toBeInTheDocument();
      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      expect(await screen.findByText("The model returned an empty response.")).toBeInTheDocument();
      expect(summaryRequests()).toHaveLength(2);

      await act(async () => {
        jest.advanceTimersByTime(30000);
      });
      expect(summaryRequests()).toHaveLength(2);
    });

    it("keeps the generating view with a retrying hint when a poll fails, and clears it on the next success", async () => {
      jest.useFakeTimers();
      const generating = withSummary({ state: "generating", progress: { phase: "classifying", analysedThreads: 30, totalThreads: 120 } });
      // A 422 is not retried, so each poll is exactly one request.
      mockApi(sequence(generating, failing(422), generating, readySummary));

      renderPage();
      const card = await findAtAGlance();
      expect(screen.queryByTestId("summary-refresh-failing")).not.toBeInTheDocument();

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      const hint = await screen.findByTestId("summary-refresh-failing");
      expect(hint).toHaveTextContent("Refresh failing – retrying. Error loading support summary.");
      // The last reply stays on screen; the page-level error block is for a page with nothing to show.
      expect(within(card).getByText(/Analysing threads/)).toBeInTheDocument();
      expect(screen.queryByTestId("summary-error")).not.toBeInTheDocument();
      expect(screen.getByText("Top Support Areas")).toBeInTheDocument();

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      await waitFor(() => expect(screen.queryByTestId("summary-refresh-failing")).not.toBeInTheDocument());
      expect(summaryRequests()).toHaveLength(3);

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      expect(await screen.findByText(readySection.content as string)).toBeInTheDocument();
    });

    it("stops polling after the maximum number of failed polls in a row and says so", async () => {
      jest.useFakeTimers();
      const generating = withSummary({ state: "generating", progress: { phase: "classifying", analysedThreads: 30, totalThreads: 120 } });
      mockApi(sequence(generating, failing(422)));

      renderPage();
      const card = await findAtAGlance();

      for (let failed = 1; failed < MAX_SUMMARY_POLL_FAILURES; failed++) {
        await act(async () => {
          jest.advanceTimersByTime(3000);
        });
        expect(await screen.findByTestId("summary-refresh-failing")).toHaveTextContent("Refresh failing – retrying.");
        expect(summaryRequests()).toHaveLength(1 + failed);
      }

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      await waitFor(() =>
        expect(screen.getByTestId("summary-refresh-failing")).toHaveTextContent(
          `Refresh failing – stopped after ${MAX_SUMMARY_POLL_FAILURES} attempts. Error loading support summary. Reload the page to try again.`
        )
      );
      expect(summaryRequests()).toHaveLength(1 + MAX_SUMMARY_POLL_FAILURES);

      // No further polls, however long the page stays open.
      await act(async () => {
        jest.advanceTimersByTime(60000);
      });
      expect(summaryRequests()).toHaveLength(1 + MAX_SUMMARY_POLL_FAILURES);
      expect(within(card).getByText(/Analysing threads/)).toBeInTheDocument();
    });
  });

  describe("Unavailable state", () => {
    it("shows the backend's reason and keeps the breakdowns", async () => {
      mockApi(ok(withSummary({ state: "unavailable", error: "The model returned an empty response." })));

      renderPage();

      const card = await findAtAGlance();
      expect(within(card).getByText("The model returned an empty response.")).toBeInTheDocument();
      expect(within(card).getByText("The breakdowns below are unaffected.")).toBeInTheDocument();
      expect(within(card).queryByText(/Last updated/)).not.toBeInTheDocument();
      expect(screen.getByText("Top Support Areas")).toBeInTheDocument();
      expect(screen.getByText("Knowledge Gap")).toBeInTheDocument();
    });

    it("falls back to a generic reason when the backend gives none", async () => {
      mockApi(ok(withSummary({ state: "unavailable" })));

      renderPage();

      expect(await screen.findByText("The summary could not be generated.")).toBeInTheDocument();
    });
  });

  describe("Ready state", () => {
    it("renders the page header and the narrative with its provenance", async () => {
      renderPage();

      expect(screen.getByRole("heading", { level: 1, name: "Support Summary" })).toBeInTheDocument();
      expect(screen.getByText("What tenants raised in the selected period, and why")).toBeInTheDocument();

      const card = await findAtAGlance();
      expect(within(card).getByText(readySection.content as string)).toBeInTheDocument();
      expect(within(card).getByText(/Generated by gemini-2\.5-pro/)).toBeInTheDocument();
      expect(within(card).getByText(/Last updated/)).toBeInTheDocument();
    });

    it("renders the At-a-glance chips from the top of each breakdown", async () => {
      renderPage();
      await findAtAGlance();

      expect(chip("Raised")).toHaveTextContent("42 tickets");
      // 24 of the 40 classified-driver tickets.
      expect(chip("Top driver")).toHaveTextContent("Knowledge Gap · 24 (60%)");
      expect(chip("Top subject")).toHaveTextContent("Connectivity and Networking · 12");
      expect(chip("Top feature")).toHaveTextContent("Kafka · 8");
      expect(chip("Top tenant")).toHaveTextContent("payments · 15");
      expect(chip("Awaiting classification")).toHaveTextContent("2");
      // Every metric in a chip is set in the tabular monospace face.
      const raised = within(chip("Raised")).getByText("42");
      expect(raised).toHaveClass("font-mono", "tabular-nums");
    });

    it("omits the awaiting-classification chip when every ticket is classified", async () => {
      mockApi(ok({ ...readySummary, classifiedTickets: 42, unclassifiedTickets: 0 }));

      renderPage();
      await findAtAGlance();

      expect(screen.queryByText("Awaiting classification")).not.toBeInTheDocument();
    });

    it("renders the window strip with the preset, the formatted window and the total", async () => {
      renderPage();

      const strip = await screen.findByTestId("summary-window");
      expect(within(strip).getByText("Last 2 weeks")).toBeInTheDocument();
      expect(within(strip).getByText("42")).toHaveClass("font-mono", "tabular-nums");
      expect(strip).toHaveTextContent("42 tickets raised");
      // A plain line above the cards, not a bordered chip.
      expect(strip).not.toHaveClass("border", "rounded-xl", "bg-card");
      expect(within(strip).queryByRole("status")).not.toBeInTheDocument();
    });

    it("formats a custom window compactly and labels it from the window shown", async () => {
      setMockUrlParamsInitial({ dateFilter: "custom", dateFrom: "2026-08-19", dateTo: "2026-08-31" });

      renderPage();

      const strip = await screen.findByTestId("summary-window");
      expect(within(strip).getByText("Custom range")).toBeInTheDocument();
      expect(within(strip).getByText("19 – 31 Aug 2026")).toBeInTheDocument();
    });

    it("lays the At-a-glance card out as a standard card", async () => {
      renderPage();

      const card = await findAtAGlance();
      expect(card).toHaveClass("bg-card", "rounded-xl", "border", "p-6");
      const heading = within(card).getByRole("heading", { level: 2, name: "At a glance" });
      expect(heading).toHaveClass("text-base", "font-semibold", "text-foreground");
      expect(heading.parentElement).toHaveClass("mb-4");
      expect(card.querySelector(".border-b")).toBeNull();
    });

    it("renders every breakdown card, with products only when the registry has product tags", async () => {
      renderPage();
      await findAtAGlance();

      for (const title of ["Top Support Areas", "Top categories", "Top knowledge gaps", "Top Platform Features", "Top Teams"]) {
        expect(screen.getByRole("heading", { level: 2, name: title })).toBeInTheDocument();
      }
      expect(screen.queryByText("Top products")).not.toBeInTheDocument();
      expect(screen.getByText("Top product: Checkout")).toBeInTheDocument();
    });

    it("renders the products card when the registry has an active product tag", async () => {
      mockApi(ok(readySummary), { impacts: [], tags: [{ code: "product-checkout", label: "Product - Checkout", active: true }] });

      renderPage();

      expect(await screen.findByRole("heading", { level: 2, name: "Top products" })).toBeInTheDocument();
      expect(screen.getByText("Checkout")).toBeInTheDocument();
    });

    it("keeps the figures on screen with a refresh-failing hint when a refetch fails", async () => {
      // A 422 is not retried, so the refetch is exactly one request.
      mockApi(sequence(readySummary, failing(422)));

      renderPage();
      const drivers = await screen.findByTestId("summary-drivers");
      expect(screen.queryByTestId("summary-refresh-failing")).not.toBeInTheDocument();

      // Saving a ticket invalidates the summary, which refetches it for the current window.
      fireEvent.click(within(drivers).getByRole("button", { name: /Knowledge Gap/ }));
      fireEvent.click(within(drivers).getByRole("button", { name: "View ticket 101" }));
      fireEvent.click(screen.getByRole("button", { name: "Trigger modal success" }));

      const hint = await screen.findByTestId("summary-refresh-failing");
      expect(hint).toHaveTextContent("Refresh failing – retrying. Error loading support summary.");
      expect(summaryRequests()).toHaveLength(2);
      // The stale figures stay rendered, with the hint above the cards rather than inside the prose block.
      const card = screen.getByTestId("summary-at-a-glance");
      expect(within(card).queryByTestId("summary-refresh-failing")).not.toBeInTheDocument();
      expect(within(card).getByText(readySection.content as string)).toBeInTheDocument();
      expect(chip("Raised")).toHaveTextContent("42 tickets");
      expect(screen.getByTestId("summary-window")).toHaveTextContent("42 tickets raised");
      expect(screen.getByText("Top Support Areas")).toBeInTheDocument();
      expect(screen.queryByTestId("summary-error")).not.toBeInTheDocument();
    });

    it("opens the prompt dialog from the View Prompts button", async () => {
      renderPage();
      await findAtAGlance();

      expect(screen.queryByTestId("prompt-dialog")).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole("button", { name: /View Prompts/ }));
      expect(screen.getByTestId("prompt-dialog")).toBeInTheDocument();
    });
  });

  describe("Date window", () => {
    it("requests the last 2 weeks ending yesterday (UTC) by default", async () => {
      renderPage();
      await findAtAGlance();

      const { from, to } = defaultWindow();
      expect(summaryRequests()).toEqual([summaryUrl(from, to)]);
      expect(screen.getByTestId("summary-date-filter")).toHaveTextContent("Last 2 Weeks");
      expect(screen.queryByLabelText("From date")).not.toBeInTheDocument();
    });

    it("honours a preset persisted in the URL", async () => {
      setMockUrlParamsInitial({ dateFilter: "lastWeek" });

      renderPage();
      const strip = await screen.findByTestId("summary-window");

      const { from, to } = windowEndingYesterday(PRESET_DAYS.lastWeek);
      expect(summaryRequests()).toEqual([summaryUrl(from, to)]);
      expect(screen.getByTestId("summary-date-filter")).toHaveTextContent("Last Week");
      expect(within(strip).getByText("Last week")).toBeInTheDocument();
    });

    it("requests a custom range persisted in the URL and shows its inputs", async () => {
      setMockUrlParamsInitial({ dateFilter: "custom", dateFrom: "2026-07-01", dateTo: "2026-07-31" });

      renderPage();
      const strip = await screen.findByTestId("summary-window");

      expect(summaryRequests()).toEqual([summaryUrl("2026-07-01", "2026-07-31")]);
      expect(screen.getByLabelText("From date")).toHaveValue("2026-07-01");
      expect(screen.getByLabelText("To date")).toHaveValue("2026-07-31");
      expect(within(strip).getByText("Custom range")).toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("switches to a preset and back to custom via the date filter", async () => {
      const user = userEvent.setup();
      renderPage();
      await findAtAGlance();

      await user.click(screen.getByTestId("summary-date-filter"));
      await user.click(await screen.findByRole("option", { name: "Last Month" }));

      const month = windowEndingYesterday(PRESET_DAYS.lastMonth);
      await waitFor(() => expect(summaryRequests()).toContain(summaryUrl(month.from, month.to)));
      expect(screen.getByTestId("summary-date-filter")).toHaveTextContent("Last Month");

      await user.click(screen.getByTestId("summary-date-filter"));
      await user.click(await screen.findByRole("option", { name: "Custom" }));

      // An incomplete custom range keeps the default window loaded rather than an empty page.
      const fallback = defaultWindow();
      expect(screen.getByLabelText("From date")).toHaveValue("");
      expect(summaryRequests()).not.toContain("/api/summary");
      fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-07-01" } });
      fireEvent.change(screen.getByLabelText("To date"), { target: { value: "2026-07-31" } });

      await waitFor(() => expect(summaryRequests()).toContain(summaryUrl("2026-07-01", "2026-07-31")));
      expect(summaryRequests().filter((url) => url === summaryUrl(fallback.from, fallback.to)).length).toBeGreaterThanOrEqual(1);
    });

    it("dims the previous window and labels it honestly while a newly chosen preset loads", async () => {
      const user = userEvent.setup();
      let releaseMonth: (() => void) | undefined;
      const month = windowEndingYesterday(PRESET_DAYS.lastMonth);
      mockApi((url) => {
        if (url === summaryUrl(month.from, month.to)) {
          return new Promise((resolve) => {
            releaseMonth = () => resolve(ok(readySummary)(url));
          }) as unknown as MockReply;
        }
        return ok(readySummary)(url);
      });

      renderPage();
      await findAtAGlance();
      const body = screen.getByTestId("summary-body");
      expect(body).not.toHaveClass("opacity-60");
      expect(body).toHaveAttribute("aria-busy", "false");

      await user.click(screen.getByTestId("summary-date-filter"));
      await user.click(await screen.findByRole("option", { name: "Last Month" }));

      await waitFor(() => expect(summaryRequests()).toContain(summaryUrl(month.from, month.to)));
      // The previous window's figures stand in, dimmed, and the strip still names that window.
      await waitFor(() => expect(screen.getByTestId("summary-body")).toHaveAttribute("aria-busy", "true"));
      expect(screen.getByTestId("summary-body")).toHaveClass("opacity-60");
      const strip = screen.getByTestId("summary-window");
      expect(within(strip).getByText("Last 2 weeks")).toBeInTheDocument();
      expect(within(strip).getByRole("status")).toHaveTextContent("Loading the selected window...");

      await act(async () => {
        releaseMonth?.();
      });

      await waitFor(() => expect(within(screen.getByTestId("summary-window")).getByText("Last month")).toBeInTheDocument());
      expect(screen.getByTestId("summary-body")).toHaveAttribute("aria-busy", "false");
      expect(screen.getByTestId("summary-body")).not.toHaveClass("opacity-60");
      expect(within(screen.getByTestId("summary-window")).queryByRole("status")).not.toBeInTheDocument();
    });

    it("clears the custom dates when a preset is chosen again", async () => {
      const user = userEvent.setup();
      setMockUrlParamsInitial({ dateFilter: "custom", dateFrom: "2026-07-01", dateTo: "2026-07-31" });

      renderPage();
      await findAtAGlance();

      await user.click(screen.getByTestId("summary-date-filter"));
      await user.click(await screen.findByRole("option", { name: "Last Week" }));

      expect(screen.queryByLabelText("From date")).not.toBeInTheDocument();
      const week = windowEndingYesterday(PRESET_DAYS.lastWeek);
      await waitFor(() => expect(summaryRequests()).toContain(summaryUrl(week.from, week.to)));
    });

    it("flags an inverted custom range and never requests it", async () => {
      setMockUrlParamsInitial({ dateFilter: "custom", dateFrom: "2026-07-31", dateTo: "2026-07-01" });

      renderPage();
      await findAtAGlance();

      expect(screen.getByRole("alert")).toHaveTextContent("Invalid range: end date is before start date");
      const { from, to } = defaultWindow();
      expect(summaryRequests()).toEqual([summaryUrl(from, to)]);
    });

    it("flags a custom range longer than the backend maximum and never requests it", async () => {
      // 367 inclusive days, one over the limit.
      setMockUrlParamsInitial({ dateFilter: "custom", dateFrom: "2025-01-01", dateTo: "2026-01-02" });

      renderPage();
      await findAtAGlance();

      expect(screen.getByRole("alert")).toHaveTextContent(`Invalid range: ${MAX_SUMMARY_WINDOW_DAYS} days at most`);
      expect(MAX_SUMMARY_WINDOW_DAYS).toBe(366);
      const { from, to } = defaultWindow();
      expect(summaryRequests()).toEqual([summaryUrl(from, to)]);
    });

    it("accepts a custom range of exactly the backend maximum", async () => {
      // 366 inclusive days: 2025-01-01 through 2026-01-01.
      setMockUrlParamsInitial({ dateFilter: "custom", dateFrom: "2025-01-01", dateTo: "2026-01-01" });

      renderPage();
      await findAtAGlance();

      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(summaryRequests()).toEqual([summaryUrl("2025-01-01", "2026-01-01")]);
    });

    it("clears the inline problem once the range is corrected", async () => {
      setMockUrlParamsInitial({ dateFilter: "custom", dateFrom: "2026-07-31", dateTo: "2026-07-01" });

      renderPage();
      await findAtAGlance();
      expect(screen.getByRole("alert")).toBeInTheDocument();

      fireEvent.change(screen.getByLabelText("To date"), { target: { value: "2026-08-15" } });

      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      await waitFor(() => expect(summaryRequests()).toContain(summaryUrl("2026-07-31", "2026-08-15")));
    });
  });

  describe("Breakdown rows and tickets", () => {
    it("expands and collapses a breakdown row to show its recent tickets", async () => {
      renderPage();
      const drivers = await screen.findByTestId("summary-drivers");

      const row = within(drivers).getByRole("button", { name: /Knowledge Gap/ });
      expect(row).toHaveAttribute("aria-expanded", "false");
      expect(within(drivers).queryByText("Up to 5 most recent tickets")).not.toBeInTheDocument();

      fireEvent.click(row);

      expect(row).toHaveAttribute("aria-expanded", "true");
      expect(within(drivers).getByText("Up to 5 most recent tickets")).toBeInTheDocument();
      expect(within(drivers).getByText("How do I rotate the Kafka credentials?")).toBeInTheDocument();
      expect(within(drivers).getByText("Where are the DNS rules documented?")).toBeInTheDocument();

      fireEvent.click(row);

      expect(row).toHaveAttribute("aria-expanded", "false");
      expect(within(drivers).queryByText("How do I rotate the Kafka credentials?")).not.toBeInTheDocument();
    });

    it("opens the ticket modal from a recent ticket", async () => {
      renderPage();
      const drivers = await screen.findByTestId("summary-drivers");

      expect(screen.queryByTestId("edit-ticket-modal")).not.toBeInTheDocument();
      fireEvent.click(within(drivers).getByRole("button", { name: /Knowledge Gap/ }));
      fireEvent.click(within(drivers).getByRole("button", { name: "View ticket 101" }));

      expect(screen.getByTestId("edit-ticket-modal")).toHaveTextContent("Ticket modal for 101");

      fireEvent.click(screen.getByRole("button", { name: "Close modal" }));
      expect(screen.queryByTestId("edit-ticket-modal")).not.toBeInTheDocument();
    });

    it("invalidates the summary, ticket list and ticket queries when a ticket is saved", async () => {
      const { invalidateQueries } = renderPage();
      const drivers = await screen.findByTestId("summary-drivers");

      fireEvent.click(within(drivers).getByRole("button", { name: /Knowledge Gap/ }));
      fireEvent.click(within(drivers).getByRole("button", { name: "View ticket 101" }));
      invalidateQueries.mockClear();

      fireEvent.click(screen.getByRole("button", { name: "Trigger modal success" }));

      expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["summary"] });
      expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["tickets"] });
      expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["ticket", "101"] });
      // The invalidation refetches the summary for the current window.
      await waitFor(() => expect(summaryRequests().length).toBeGreaterThanOrEqual(2));
    });
  });
});
