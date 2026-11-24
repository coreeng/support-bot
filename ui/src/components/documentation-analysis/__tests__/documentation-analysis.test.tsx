/**
 * Rendered against the real `useDocAnalysis` / `useDocAnalysisEnabled` hooks and a real
 * QueryClient; only `fetch` is mocked, at the API-route level, with replies shaped like the
 * backend's `DocAnalysisUI`.
 */

import { clearMockUrlParamsInitial, useMockUrlParams as mockUseUrlParams } from "@/test-utils/mock-url-params";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MAX_SUMMARY_POLL_FAILURES } from "../../../lib/hooks";
import type { DocAnalysisData, DocAnalysisRun } from "../../../lib/types/documentation-analysis";
import { presetWindow } from "../../../lib/utils/summary-window";
import DocumentationAnalysisPage, { DOC_ANALYSIS_NOT_ENABLED } from "../documentation-analysis";

jest.mock("next-auth/react", () => ({
  getCsrfToken: jest.fn(() => Promise.resolve("mock-csrf-token")),
  signOut: jest.fn(() => Promise.resolve()),
}));

jest.mock("../../../lib/hooks/useUrlParams", () => ({
  ...jest.requireActual("../../../lib/hooks/useUrlParams"),
  useUrlParams: mockUseUrlParams,
}));

jest.mock("../../tickets/EditTicketModal", () => ({
  __esModule: true,
  default: ({ ticketId, open }: { ticketId: string | null; open: boolean }) =>
    open ? <div data-testid="edit-ticket-modal">Ticket modal for {ticketId}</div> : null,
}));

const COMMIT_A = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678";
const COMMIT_B = "b2c3d4e5f60718293a4b5c6d7e8f901234567890";

const window = { from: "2026-03-10", to: "2026-03-23" };

const example: DocAnalysisData = {
  ...window,
  counts: { total: 8, correct: 1, missing: 2, incorrect: 1, notApplicable: 1, unclassified: 3, failed: 1 },
  productAreas: [
    { area: "access", total: 2, correct: 1, missing: 0, incorrect: 1, notApplicable: 0 },
    { area: "builds", total: 1, correct: 0, missing: 1, incorrect: 0, notApplicable: 0 },
    { total: 2, correct: 0, missing: 1, incorrect: 0, notApplicable: 1 },
  ],
  citedPages: [{ page: "guides/keys.md", count: 2, correct: 1, incorrect: 1, ticketIds: ["1", "3"] }],
  missingByArea: [
    { area: "builds", count: 1, ticketIds: ["4"] },
    { count: 1, ticketIds: ["2"] },
  ],
  results: [
    {
      ticketId: "1",
      channelId: "C123",
      queryTs: "1710000001.000100",
      raisedAt: "2026-03-11T10:00:00Z",
      classification: "incorrect",
      question: "How do I rotate a deploy key?",
      answerSummary: "Support said only the platform team can rotate keys.",
      tags: ["deploy-key"],
      docPaths: ["guides/keys.md"],
      evidenceQuote: "To rotate a key, run the rotate command.",
      rationale: "The page says tenants rotate keys; support said they cannot.",
      productArea: "access",
      productAreas: ["access"],
      docsVersion: COMMIT_A,
      analysedAt: "2026-03-24T08:00:00Z",
    },
    {
      ticketId: "4",
      channelId: "C123",
      queryTs: "1710000004.000100",
      raisedAt: "2026-03-14T10:00:00Z",
      classification: "missing",
      question: "How do I cache build layers?",
      tags: [],
      docPaths: ["guides/builds.md"],
      rationale: "No page covers layer caching.",
      productArea: "builds",
      productAreas: ["builds"],
      docsVersion: COMMIT_B,
      analysedAt: "2026-03-25T08:00:00Z",
    },
    {
      ticketId: "2",
      channelId: "C123",
      queryTs: "1710000002.000100",
      raisedAt: "2026-03-12T10:00:00Z",
      classification: "missing",
      question: "Can I pin a runtime version?",
      tags: [],
      docPaths: [],
      rationale: "Nothing in the documentation covers runtime pinning.",
      productAreas: [],
      docsVersion: COMMIT_B,
      analysedAt: "2026-03-25T08:00:00Z",
    },
    {
      ticketId: "3",
      channelId: "C123",
      queryTs: "1710000003.000100",
      raisedAt: "2026-03-13T10:00:00Z",
      classification: "correct",
      question: "Where do I find my deploy keys?",
      tags: [],
      docPaths: ["guides/keys.md"],
      evidenceQuote: "Keys are listed under Settings.",
      rationale: "The page answers it.",
      productArea: "access",
      productAreas: ["access"],
      docsVersion: COMMIT_B,
      analysedAt: "2026-03-25T08:00:00Z",
    },
    {
      ticketId: "5",
      channelId: "C123",
      queryTs: "1710000005.000100",
      raisedAt: "2026-03-15T10:00:00Z",
      classification: "not_applicable",
      question: "Please add a new region.",
      tags: [],
      docPaths: [],
      rationale: "A request for a change, not a documentation question.",
      productAreas: [],
      docsVersion: COMMIT_B,
      analysedAt: "2026-03-25T08:00:00Z",
    },
  ],
  run: { state: "idle" },
  documentation: { version: COMMIT_B, refreshedAt: "2026-03-26T06:00:00Z" },
};

const withRun = (run: DocAnalysisRun, data: DocAnalysisData = example): DocAnalysisData => ({ ...data, run });

// ===== fetch mocking at the API-route level =====

type MockReply = { status: number; body?: unknown } | Promise<never>;
type RouteHandler = (url: string) => MockReply;

const originalFetch = global.fetch;
const pending: RouteHandler = () => new Promise<never>(() => {});
const flag =
  (enabled: boolean): RouteHandler =>
  () => ({ status: 200, body: { enabled } });

function mockApi(analysisReply: RouteHandler, enabledReply: RouteHandler = flag(true)) {
  global.fetch = jest.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    let reply: MockReply;
    if (url === "/api/documentation-analysis/enabled") reply = enabledReply(url);
    else if (url.startsWith("/api/documentation-analysis?")) reply = analysisReply(url);
    else throw new Error(`Unexpected fetch: ${url}`);
    const { status, body } = await reply;
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
  }) as jest.MockedFunction<typeof fetch>;
}

/** Like the backend, echoes the requested window. */
const ok =
  (body: DocAnalysisData): RouteHandler =>
  (url) => {
    const requested = new URLSearchParams(url.split("?")[1]);
    return { status: 200, body: { ...body, from: requested.get("from"), to: requested.get("to") } };
  };

function sequence(...replies: (DocAnalysisData | RouteHandler)[]): RouteHandler {
  let index = 0;
  return (url) => {
    const reply = replies[Math.min(index++, replies.length - 1)];
    return typeof reply === "function" ? reply(url) : ok(reply)(url);
  };
}

const analysisRequests = () =>
  (global.fetch as jest.MockedFunction<typeof fetch>).mock.calls
    .map(([input]) => String(input))
    .filter((url) => url.startsWith("/api/documentation-analysis?"));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <DocumentationAnalysisPage />
    </QueryClientProvider>
  );
}

const findBody = () => screen.findByTestId("doc-analysis-body");
const results = () => screen.getAllByTestId("doc-analysis-result");

describe("DocumentationAnalysisPage", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    clearMockUrlParamsInitial();
    mockApi(ok(example));
  });

  afterEach(() => {
    jest.useRealTimers();
    global.fetch = originalFetch;
  });

  describe("feature flag and window", () => {
    it("says the page is off and asks for nothing when the feature is disabled", async () => {
      mockApi(ok(example), flag(false));

      renderPage();

      const block = await screen.findByTestId("doc-analysis-not-enabled");
      expect(within(block).getByText(DOC_ANALYSIS_NOT_ENABLED.title)).toBeInTheDocument();
      expect(screen.queryByTestId("doc-analysis-date-filter")).not.toBeInTheDocument();
      expect(analysisRequests()).toHaveLength(0);
    });

    it("holds the request while the flag is checked", async () => {
      mockApi(ok(example), pending);

      renderPage();

      expect(screen.getByText("Loading documentation analysis...")).toBeInTheDocument();
      await act(async () => {});
      expect(analysisRequests()).toHaveLength(0);
    });

    it("opens on the Support Summary's default window and sends the CSRF token, as the request starts work", async () => {
      renderPage();

      await findBody();
      const { from, to } = presetWindow("last2Weeks");
      expect(analysisRequests()).toEqual([`/api/documentation-analysis?from=${from}&to=${to}`]);
      const [, init] = (global.fetch as jest.Mock).mock.calls.find(([url]) => String(url).startsWith("/api/documentation-analysis?"));
      expect(init.headers).toEqual({ "X-CSRF-Token": "mock-csrf-token" });
      expect(screen.getByTestId("doc-analysis-window")).toHaveTextContent("Last 2 weeks");
      expect(screen.getByTestId("doc-analysis-window")).toHaveTextContent("8 closed tickets");
    });

    it("requests a custom range once both dates are set", async () => {
      renderPage();
      await findBody();

      fireEvent.click(screen.getByTestId("doc-analysis-date-filter"));
      fireEvent.click(await screen.findByRole("option", { name: "Custom" }));
      fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-02-02" } });
      fireEvent.change(screen.getByLabelText("To date"), { target: { value: "2026-02-20" } });

      await waitFor(() => expect(analysisRequests()).toContain("/api/documentation-analysis?from=2026-02-02&to=2026-02-20"));
      await waitFor(() => expect(screen.getByTestId("doc-analysis-window")).toHaveTextContent("2 – 20 Feb 2026"));
    });

    it("shows the backend's explanation of an invalid window", async () => {
      mockApi(() => ({
        status: 400,
        body: {
          error: "Backend error: 400",
          code: "DOCUMENTATION_ANALYSIS_WINDOW_INVALID",
          detail: "The window must not exceed 92 days (about one quarter)",
        },
      }));

      renderPage();

      const block = await screen.findByTestId("doc-analysis-error");
      expect(within(block).getByText("The selected range is invalid")).toBeInTheDocument();
      expect(within(block).getByText("The window must not exceed 92 days (about one quarter)")).toBeInTheDocument();
    });

    it("explains a refusal for lack of access", async () => {
      mockApi(() => ({ status: 403, body: { error: "Backend error: 403" } }));

      renderPage();

      const block = await screen.findByTestId("doc-analysis-error");
      expect(within(block).getByText("You do not have permission to view the Documentation Analysis")).toBeInTheDocument();
    });
  });

  describe("results", () => {
    it("shows the count of each classification, the tickets not yet classified and those that failed", async () => {
      renderPage();
      await findBody();

      expect(screen.getByTestId("doc-analysis-count-incorrect")).toHaveTextContent("Conflicts with support's answer1");
      expect(screen.getByTestId("doc-analysis-count-missing")).toHaveTextContent("Missing2");
      expect(screen.getByTestId("doc-analysis-count-correct")).toHaveTextContent("Correct1");
      expect(screen.getByTestId("doc-analysis-count-not-applicable")).toHaveTextContent("Not applicable1");
      const unclassified = screen.getByTestId("doc-analysis-count-unclassified");
      expect(unclassified).toHaveTextContent("Not yet classified3");
      expect(unclassified).toHaveTextContent("1 of them failed to classify");
    });

    it("counts per product area, with the tickets that have none last as No area", async () => {
      renderPage();
      await findBody();

      const rows = within(screen.getByTestId("doc-analysis-product-areas")).getAllByRole("row").slice(1);
      expect(rows.map((row) => row.textContent)).toEqual(["access10102", "builds01001", "No area01012"]);
    });

    it("groups missing tickets by product area and cited pages with their split", async () => {
      renderPage();
      await findBody();

      const missing = screen.getByTestId("doc-analysis-missing-by-area");
      expect(within(missing).getByText("builds")).toBeInTheDocument();
      expect(within(missing).getByText("No area")).toBeInTheDocument();

      const pages = screen.getByTestId("doc-analysis-cited-pages");
      expect(within(pages).getByText("guides/keys.md")).toBeInTheDocument();
      expect(within(pages).getByText("1 conflicting · 1 correct")).toBeInTheDocument();

      fireEvent.click(within(pages).getByText("guides/keys.md"));
      expect(within(pages).getByRole("button", { name: "View ticket 1" })).toBeInTheDocument();
      expect(within(pages).getByRole("button", { name: "View ticket 3" })).toBeInTheDocument();
    });

    it("lists the tickets in the order the API gives, with conflicts labelled as such", async () => {
      renderPage();
      await findBody();

      expect(results().map((item) => within(item).getByText(/^Ticket/).textContent)).toEqual([
        expect.stringContaining("#1"),
        expect.stringContaining("#4"),
        expect.stringContaining("#2"),
        expect.stringContaining("#3"),
        expect.stringContaining("#5"),
      ]);
      expect(within(results()[0]).getByText("Conflicts with support's answer")).toBeInTheDocument();
      expect(screen.queryByText(/incorrect/i)).not.toBeInTheDocument();
    });

    it("shows a conflict's quoted passage beside support's answer, with its page, rationale and tags", async () => {
      renderPage();
      await findBody();

      const conflict = results()[0];
      expect(within(conflict).getByText("How do I rotate a deploy key?")).toBeInTheDocument();
      expect(within(conflict).getByText("The documentation says")).toBeInTheDocument();
      expect(within(conflict).getByText("To rotate a key, run the rotate command.")).toBeInTheDocument();
      expect(within(conflict).getByText("Support answered")).toBeInTheDocument();
      expect(within(conflict).getByText("Support said only the platform team can rotate keys.")).toBeInTheDocument();
      expect(within(conflict).getByText("Cited page")).toBeInTheDocument();
      expect(within(conflict).getByText("guides/keys.md")).toBeInTheDocument();
      expect(within(conflict).getByText("The page says tenants rotate keys; support said they cannot.")).toBeInTheDocument();
      expect(within(conflict).getByText("deploy-key")).toBeInTheDocument();
      expect(within(conflict).getByText("access")).toBeInTheDocument();
    });

    it("shows a missing ticket's closest page, and No area when it has none", async () => {
      renderPage();
      await findBody();

      expect(within(results()[1]).getByText("Closest page")).toBeInTheDocument();
      expect(within(results()[1]).getByText("guides/builds.md")).toBeInTheDocument();
      expect(within(results()[2]).getByText("No area")).toBeInTheDocument();
      expect(within(results()[2]).queryByText(/Closest/)).not.toBeInTheDocument();
    });

    it("shows each result's documentation version, shortened, and marks one that is not the current version", async () => {
      renderPage();
      await findBody();

      expect(within(results()[0]).getByTestId("doc-analysis-result-version")).toHaveTextContent(
        "Made against documentation a1b2c3d (not the current version)"
      );
      expect(within(results()[1]).getByTestId("doc-analysis-result-version")).toHaveTextContent("Made against documentation b2c3d4e");
      expect(within(results()[1]).getByTestId("doc-analysis-result-version")).not.toHaveTextContent("not the current version");
    });

    it("shows the documentation's current version and last refresh", async () => {
      renderPage();
      await findBody();

      expect(screen.getByTestId("doc-analysis-documentation")).toHaveTextContent(
        "Documentation version b2c3d4e, last refreshed 26 Mar 2026, 06:00 UTC"
      );
    });

    it("says so when the documentation has not been fetched yet", async () => {
      mockApi(ok({ ...example, documentation: undefined }));

      renderPage();
      await findBody();

      expect(screen.getByTestId("doc-analysis-documentation")).toHaveTextContent("The documentation has not been fetched yet.");
    });

    it("links each ticket to its Slack thread and opens the ticket", async () => {
      renderPage();
      await findBody();

      expect(within(results()[0]).getByRole("link", { name: "Open ticket 1 in Slack" })).toHaveAttribute(
        "href",
        "https://slack.com/archives/C123/p1710000001000100"
      );
      fireEvent.click(within(results()[0]).getByRole("button", { name: "View ticket 1" }));
      expect(screen.getByTestId("edit-ticket-modal")).toHaveTextContent("Ticket modal for 1");
    });

    it("filters the list to one classification in the browser", async () => {
      const user = userEvent.setup();
      renderPage();
      await findBody();

      await user.click(within(screen.getByTestId("doc-analysis-filter")).getByRole("button", { name: /Classification/ }));
      await user.click(await screen.findByRole("option", { name: "Missing" }));

      await waitFor(() => expect(results()).toHaveLength(2));
      expect(results().every((item) => within(item).queryByText("Missing") !== null)).toBe(true);
      expect(analysisRequests()).toHaveLength(1);
    });

    it("says when no closed tickets were raised in the window", async () => {
      mockApi(
        ok({
          ...example,
          counts: { total: 0, correct: 0, missing: 0, incorrect: 0, notApplicable: 0, unclassified: 0, failed: 0 },
          productAreas: [],
          citedPages: [],
          missingByArea: [],
          results: [],
        })
      );

      renderPage();
      await findBody();

      expect(screen.getByText("No closed tickets were raised in this window.")).toBeInTheDocument();
      expect(screen.queryByTestId("doc-analysis-results")).not.toBeInTheDocument();
    });

    it("says results appear as they are saved while nothing is classified yet", async () => {
      mockApi(
        ok(
          withRun(
            { state: "running", progress: { ...window, done: 0, total: 0 } },
            {
              ...example,
              counts: { total: 4, correct: 0, missing: 0, incorrect: 0, notApplicable: 0, unclassified: 4, failed: 0 },
              productAreas: [],
              citedPages: [],
              missingByArea: [],
              results: [],
            }
          )
        )
      );

      renderPage();
      await findBody();

      expect(
        within(screen.getByTestId("doc-analysis-results")).getByText(
          "No ticket has been classified yet. Results appear here as they are saved."
        )
      ).toBeInTheDocument();
      expect(screen.getByText("No ticket in this window is missing documentation.")).toBeInTheDocument();
      expect(screen.getByText("No result cites a page yet.")).toBeInTheDocument();
    });
  });

  describe("run state", () => {
    it("shows the run's progress for this window", async () => {
      mockApi(ok(withRun({ state: "running", progress: { ...window, done: 10, total: 40 } })));

      renderPage();

      const notice = await screen.findByTestId("doc-analysis-run");
      expect(notice).toHaveTextContent("Classifying tickets against the documentation... 10 of 40 done");
      expect(within(notice).getByText("25%")).toBeInTheDocument();
      expect(notice).toHaveTextContent("A large window fills in over time.");
    });

    it("says it is finding the tickets before the run has counted them", async () => {
      mockApi(ok(withRun({ state: "running", progress: { ...window, done: 0, total: 0 } })));

      renderPage();

      const notice = await screen.findByTestId("doc-analysis-run");
      expect(notice).toHaveTextContent("Finding the tickets to classify...");
      expect(within(notice).queryByText(/%/)).not.toBeInTheDocument();
    });

    it.each<[string, DocAnalysisRun, string[]]>([
      [
        "busy with another window",
        { state: "busy", progress: { from: "2026-01-01", to: "2026-01-14", done: 5, total: 20 } },
        ["Another window is being analysed (1 – 14 Jan 2026): 5 of 20 done.", "This window starts once it finishes"],
      ],
      ["busy elsewhere", { state: "busy" }, ["Another analysis is running.", "This window starts once it finishes"]],
      [
        "source unavailable",
        { state: "source_unavailable", reason: "The documentation could not be fetched from GitHub, and no earlier copy is held." },
        [
          "The documentation could not be read, so no tickets are being classified.",
          "The documentation could not be fetched from GitHub, and no earlier copy is held.",
        ],
      ],
      [
        "provider outage",
        { state: "provider_outage", retryAt: "2026-03-26T09:25:00Z" },
        ["The LLM provider is failing, so classification has paused.", "It resumes on a visit after 26 Mar 2026, 09:25 UTC."],
      ],
      [
        "daily limit reached",
        { state: "daily_limit_reached" },
        ["The daily limit on tickets classified has been reached.", "classified on a later visit"],
      ],
      ["failed", { state: "failed" }, ["The analysis run failed.", "Reload the page to try again."]],
      ["no prompt", { state: "no_prompt" }, ["No documentation classification prompt is in use, so no ticket can be classified."]],
    ])("explains a run that is %s and keeps the saved results on screen", async (_label, run, messages) => {
      mockApi(ok(withRun(run)));

      renderPage();

      const notice = await screen.findByTestId("doc-analysis-run");
      for (const message of messages) expect(notice).toHaveTextContent(message);
      expect(results()).toHaveLength(5);
      expect(screen.getByTestId("doc-analysis-count-incorrect")).toHaveTextContent("1");
    });

    it("shows no run message when the run is idle", async () => {
      renderPage();
      await findBody();

      expect(screen.queryByTestId("doc-analysis-run")).not.toBeInTheDocument();
    });

    it("polls every 3 seconds while running or busy and stops once idle", async () => {
      jest.useFakeTimers();
      mockApi(
        sequence(
          withRun({ state: "busy", progress: { from: "2026-01-01", to: "2026-01-14", done: 5, total: 20 } }),
          withRun({ state: "running", progress: { ...window, done: 1, total: 4 } }),
          example
        )
      );

      renderPage();

      expect(await screen.findByText(/Another window is being analysed/)).toBeInTheDocument();
      expect(analysisRequests()).toHaveLength(1);

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      await waitFor(() => expect(screen.getByTestId("doc-analysis-run")).toHaveTextContent("1 of 4 done"));

      await act(async () => {
        jest.advanceTimersByTime(3000);
      });
      await waitFor(() => expect(screen.queryByTestId("doc-analysis-run")).not.toBeInTheDocument());
      expect(analysisRequests()).toHaveLength(3);

      await act(async () => {
        jest.advanceTimersByTime(30000);
      });
      expect(analysisRequests()).toHaveLength(3);
    });

    it("does not poll a stopped run", async () => {
      jest.useFakeTimers();
      mockApi(ok(withRun({ state: "daily_limit_reached" })));

      renderPage();
      await findBody();

      await act(async () => {
        jest.advanceTimersByTime(30000);
      });
      expect(analysisRequests()).toHaveLength(1);
    });

    it("keeps the last reply on screen when polls fail, and stops after the maximum in a row", async () => {
      jest.useFakeTimers();
      mockApi(sequence(withRun({ state: "running", progress: { ...window, done: 1, total: 4 } }), () => ({ status: 422, body: {} })));

      renderPage();
      await findBody();

      for (let failed = 1; failed <= MAX_SUMMARY_POLL_FAILURES; failed++) {
        await act(async () => {
          jest.advanceTimersByTime(3000);
        });
        await waitFor(() => expect(analysisRequests()).toHaveLength(1 + failed));
      }

      await waitFor(() =>
        expect(screen.getByTestId("doc-analysis-refresh-failing")).toHaveTextContent(`stopped after ${MAX_SUMMARY_POLL_FAILURES} attempts`)
      );
      expect(results()).toHaveLength(5);
      await act(async () => {
        jest.advanceTimersByTime(30000);
      });
      expect(analysisRequests()).toHaveLength(1 + MAX_SUMMARY_POLL_FAILURES);
    });
  });
});
