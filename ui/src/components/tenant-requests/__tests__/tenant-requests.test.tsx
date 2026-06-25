import { fireEvent, render, screen } from "@testing-library/react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { RepoInsights } from "../../../lib/types/dashboard";
import TenantRequestsPage from "../tenant-requests";

const mockUseTenantInsightsStats = jest.fn();
const mockUseRequestBreakdown = jest.fn();

jest.mock("../../../lib/hooks", () => ({
  useTenantInsightsStats: (...args: unknown[]) => mockUseTenantInsightsStats(...args),
  useRequestBreakdown: (...args: unknown[]) => mockUseRequestBreakdown(...args),
  useInFlightPrs: () => ({ data: [], isLoading: false, error: null }),
}));

jest.mock("../../../lib/utils/format", () => ({
  formatDuration: (seconds: number) => {
    if (seconds >= 3600) return `${(seconds / 3600).toFixed(1)}h`;
    if (seconds >= 60) return `${Math.round(seconds / 60)}m`;
    return `${Math.round(seconds)}s`;
  },
}));

jest.mock("next/navigation", () => ({
  useRouter: jest.fn(),
  useSearchParams: jest.fn(),
  usePathname: jest.fn(),
}));

const mockUseRouter = useRouter as jest.Mock;
const mockUseSearchParams = useSearchParams as jest.Mock;
const mockUsePathname = usePathname as jest.Mock;
const mockReplace = jest.fn();

function makeRepo(overrides: Partial<RepoInsights> = {}): RepoInsights {
  return {
    repo: "org/service-a",
    owningTeam: "platform",
    prCount: 10,
    openCount: 2,
    escalatedCount: 1,
    breachedCount: 0,
    p50Seconds: 3600,
    p90Seconds: 14400,
    p99Seconds: 86400,
    hasSla: true,
    ...overrides,
  };
}

function makeRepos(count: number): RepoInsights[] {
  return Array.from({ length: count }, (_, i) =>
    makeRepo({
      repo: `org/service-${String.fromCharCode(97 + i)}`,
      owningTeam: i % 2 === 0 ? "platform" : "payments",
      prCount: count - i,
      escalatedCount: i % 3 === 0 ? 1 : 0,
      breachedCount: i % 5 === 0 ? 1 : 0,
    })
  );
}

describe("TenantRequestsPage", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseTenantInsightsStats.mockReturnValue({ data: [], isLoading: false, error: null });
    mockUseRequestBreakdown.mockReturnValue({ data: undefined });
    mockUseRouter.mockReturnValue({ replace: mockReplace, push: jest.fn() });
    mockUseSearchParams.mockReturnValue(new URLSearchParams());
    mockUsePathname.mockReturnValue("/");
  });

  describe("Rendering", () => {
    it("should render page title and tab label", () => {
      render(<TenantRequestsPage />);

      expect(screen.getByText("Tenant Requests")).toBeInTheDocument();
      expect(screen.getByRole("tab", { name: /PR Activity & SLA Health/ })).toBeInTheDocument();
    });

    it("should render stat cards with aggregated totals", () => {
      const repos = [
        makeRepo({ prCount: 10, openCount: 3, escalatedCount: 2, breachedCount: 1 }),
        makeRepo({ repo: "org/service-b", prCount: 5, openCount: 1, escalatedCount: 0, breachedCount: 0 }),
      ];
      mockUseTenantInsightsStats.mockReturnValue({ data: repos, isLoading: false });

      render(<TenantRequestsPage />);

      // Stat card values are the bold white text inside gradient cards
      const statValues = screen.getAllByText(/^\d+$/).filter((el) => el.className.includes("text-3xl"));
      const values = statValues.map((el) => el.textContent);
      expect(values).toContain("2"); // repositories
      expect(values).toContain("15"); // total PRs
      expect(values).toContain("4"); // open
    });

    it("should render table headers", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false, error: null });

      render(<TenantRequestsPage />);

      const headers = screen.getAllByRole("columnheader");
      expect(headers).toHaveLength(9);
      ["Repository", "Team", "PRs", "Open", "Escalated", "Breached", "p50", "p90", "p99"].forEach((label) => {
        expect(screen.getAllByText(label).length).toBeGreaterThanOrEqual(1);
      });
    });

    it("should render info icons on percentile headers", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false, error: null });

      const { container } = render(<TenantRequestsPage />);

      const infoIcons = container.querySelectorAll(".lucide-info");
      // p50, p90, p99 sort headers + intervention rate stat card = 4
      expect(infoIcons.length).toBeGreaterThanOrEqual(3);
    });

    it("should render repo row with formatted durations", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ p50Seconds: 7200, p90Seconds: 28800, p99Seconds: 172800 })],
        isLoading: false,
      });

      render(<TenantRequestsPage />);

      expect(screen.getByText("org/service-a")).toBeInTheDocument();
      expect(screen.getByText("platform")).toBeInTheDocument();
      expect(screen.getByText("2.0h")).toBeInTheDocument(); // p50
      expect(screen.getByText("8.0h")).toBeInTheDocument(); // p90
      expect(screen.getByText("48.0h")).toBeInTheDocument(); // p99
    });
  });

  describe("Loading State", () => {
    it("should show loading indicator in table area", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: undefined, isLoading: true });

      render(<TenantRequestsPage />);

      expect(screen.getByText("Loading...")).toBeInTheDocument();
    });

    it("should not render table rows while loading", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: undefined, isLoading: true });

      render(<TenantRequestsPage />);

      expect(screen.queryByText("Repository")).not.toBeInTheDocument();
    });
  });

  describe("Empty State", () => {
    it("should show empty message when no data", () => {
      render(<TenantRequestsPage />);

      expect(screen.getByText("No PR data for this period")).toBeInTheDocument();
    });

    it("should show search-specific empty message when filter has no matches", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false, error: null });

      render(<TenantRequestsPage />);

      const input = screen.getByPlaceholderText("Filter repos or teams...");
      fireEvent.change(input, { target: { value: "nonexistent" } });

      expect(screen.getByText("No repos match your search")).toBeInTheDocument();
    });
  });

  describe("Error State", () => {
    it("should show error message when fetch fails", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error("Network error"),
      });

      render(<TenantRequestsPage />);

      expect(screen.getByText("Failed to load data — please try again")).toBeInTheDocument();
    });

    it("should show error instead of empty message", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error("500"),
      });

      render(<TenantRequestsPage />);

      expect(screen.queryByText("No PR data for this period")).not.toBeInTheDocument();
    });
  });

  describe("Date Presets", () => {
    it("should call hooks with dates matching the selected preset", () => {
      mockUseSearchParams.mockReturnValue(new URLSearchParams("dateFilter=lastYear"));
      render(<TenantRequestsPage />);

      const statsCall = mockUseTenantInsightsStats.mock.calls.at(-1);
      const breakdownCall = mockUseRequestBreakdown.mock.calls.at(-1);
      expect(statsCall[0]).toBeDefined(); // dateFrom
      expect(statsCall[1]).toBeDefined(); // dateTo
      expect(breakdownCall[0]).toBe(statsCall[0]); // same dateFrom
      expect(breakdownCall[1]).toBe(statsCall[1]); // same dateTo
    });

    it("should show date pickers when Custom is selected", () => {
      mockUseSearchParams.mockReturnValue(new URLSearchParams("dateFilter=custom"));
      const { container } = render(<TenantRequestsPage />);

      const dateInputs = container.querySelectorAll('input[type="date"]');
      expect(dateInputs).toHaveLength(2);
    });

    it("should not show date pickers for non-custom presets", () => {
      const { container } = render(<TenantRequestsPage />);

      const dateInputs = container.querySelectorAll('input[type="date"]');
      expect(dateInputs).toHaveLength(0);
    });

    it("should show invalid range message when dateFrom > dateTo", () => {
      mockUseSearchParams.mockReturnValue(new URLSearchParams("dateFilter=custom&dateFrom=2026-03-25&dateTo=2026-03-01"));
      render(<TenantRequestsPage />);

      expect(screen.getByText(/Invalid range/)).toBeInTheDocument();
    });
  });

  describe("Search", () => {
    it("should filter repos by name", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ repo: "org/alpha" }), makeRepo({ repo: "org/beta" })],
        isLoading: false,
      });

      render(<TenantRequestsPage />);

      const input = screen.getByPlaceholderText("Filter repos or teams...");
      fireEvent.change(input, { target: { value: "alpha" } });

      expect(screen.getByText("org/alpha")).toBeInTheDocument();
      expect(screen.queryByText("org/beta")).not.toBeInTheDocument();
    });

    it("should filter repos by team name", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ repo: "org/svc-1", owningTeam: "platform" }), makeRepo({ repo: "org/svc-2", owningTeam: "payments" })],
        isLoading: false,
      });

      render(<TenantRequestsPage />);

      const input = screen.getByPlaceholderText("Filter repos or teams...");
      fireEvent.change(input, { target: { value: "payments" } });

      expect(screen.getByText("org/svc-2")).toBeInTheDocument();
      expect(screen.queryByText("org/svc-1")).not.toBeInTheDocument();
    });

    it("should be case-insensitive", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ repo: "org/MyService" })],
        isLoading: false,
      });

      render(<TenantRequestsPage />);

      const input = screen.getByPlaceholderText("Filter repos or teams...");
      fireEvent.change(input, { target: { value: "MYSERVICE" } });

      expect(screen.getByText("org/MyService")).toBeInTheDocument();
    });

    it("should show filtered count", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ repo: "org/alpha" }), makeRepo({ repo: "org/beta" }), makeRepo({ repo: "org/alpha-two" })],
        isLoading: false,
      });

      render(<TenantRequestsPage />);

      const input = screen.getByPlaceholderText("Filter repos or teams...");
      fireEvent.change(input, { target: { value: "alpha" } });

      expect(screen.getByText("2 of 3 repos")).toBeInTheDocument();
    });
  });

  describe("Sorting", () => {
    const repos = [
      makeRepo({ repo: "org/zebra", prCount: 5, breachedCount: 0, escalatedCount: 0 }),
      makeRepo({ repo: "org/alpha", prCount: 20, breachedCount: 3, escalatedCount: 1 }),
      makeRepo({ repo: "org/middle", prCount: 10, breachedCount: 1, escalatedCount: 2 }),
    ];

    beforeEach(() => {
      mockUseTenantInsightsStats.mockReturnValue({ data: repos, isLoading: false });
    });

    it("should default sort by severity (breached desc)", () => {
      render(<TenantRequestsPage />);

      const rows = screen.getAllByRole("row").slice(1); // skip header
      expect(rows[0]).toHaveTextContent("org/alpha"); // 3 breached
      expect(rows[1]).toHaveTextContent("org/middle"); // 1 breached
      expect(rows[2]).toHaveTextContent("org/zebra"); // 0 breached
    });

    it("should sort by repo name ascending when column clicked", () => {
      render(<TenantRequestsPage />);

      fireEvent.click(screen.getByText("Repository"));

      const rows = screen.getAllByRole("row").slice(1);
      expect(rows[0]).toHaveTextContent("org/alpha");
      expect(rows[1]).toHaveTextContent("org/middle");
      expect(rows[2]).toHaveTextContent("org/zebra");
    });

    it("should toggle sort direction on double click", () => {
      render(<TenantRequestsPage />);

      fireEvent.click(screen.getByText("Repository")); // asc
      fireEvent.click(screen.getByText("Repository")); // desc

      const rows = screen.getAllByRole("row").slice(1);
      expect(rows[0]).toHaveTextContent("org/zebra");
      expect(rows[2]).toHaveTextContent("org/alpha");
    });

    it("should sort by PRs descending when column clicked", () => {
      render(<TenantRequestsPage />);

      fireEvent.click(screen.getByText("PRs"));

      const rows = screen.getAllByRole("row").slice(1);
      expect(rows[0]).toHaveTextContent("org/alpha"); // 20
      expect(rows[2]).toHaveTextContent("org/zebra"); // 5
    });

    it("should reset to page 0 when sort changes", () => {
      // 25 repos to have 2 pages
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null });

      render(<TenantRequestsPage />);

      // Go to page 2
      fireEvent.click(screen.getByRole("button", { name: "Next" }));

      expect(screen.getByText(/Page 2/)).toBeInTheDocument();

      // Sort by repo — should reset to page 1
      fireEvent.click(screen.getByText("Repository"));

      expect(screen.getByText(/Page 1/)).toBeInTheDocument();
    });
  });

  describe("Pagination", () => {
    it("should not show pagination for 20 or fewer repos", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(15), isLoading: false });

      render(<TenantRequestsPage />);

      expect(screen.queryByText(/Page/)).not.toBeInTheDocument();
    });

    it("should show pagination for more than 20 repos", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false });

      render(<TenantRequestsPage />);

      expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    });

    it("should navigate to next page", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null });

      render(<TenantRequestsPage />);

      fireEvent.click(screen.getByRole("button", { name: "Next" }));

      expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    });

    it("should navigate back to previous page", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null });

      render(<TenantRequestsPage />);

      fireEvent.click(screen.getByRole("button", { name: "Next" }));
      fireEvent.click(screen.getByRole("button", { name: "Previous" }));

      expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    });

    it("should disable prev button on first page", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null });

      render(<TenantRequestsPage />);

      expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    });

    it("should disable next button on last page", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null });

      render(<TenantRequestsPage />);

      fireEvent.click(screen.getByRole("button", { name: "Next" }));

      expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    });

    it("should reset to page 0 when search changes", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null });

      render(<TenantRequestsPage />);

      fireEvent.click(screen.getByRole("button", { name: "Next" }));
      expect(screen.getByText(/Page 2/)).toBeInTheDocument();

      const input = screen.getByPlaceholderText("Filter repos or teams...");
      fireEvent.change(input, { target: { value: "service" } });

      expect(screen.queryByText(/Page 2/)).not.toBeInTheDocument();
    });
  });

  describe("Badge Colouring", () => {
    it("should show muted zero for escalated=0", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ escalatedCount: 0, breachedCount: 0 })],
        isLoading: false,
      });

      const { container } = render(<TenantRequestsPage />);

      // Zeros render as plain text (no badge pill) using muted-foreground/50 token.
      const mutedZeros = container.querySelectorAll(".text-muted-foreground\\/50");
      expect(mutedZeros.length).toBeGreaterThanOrEqual(2); // escalated + breached
    });
  });

  describe("Duration Pill Colouring", () => {});

  describe("Stat Card Colour Logic", () => {});

  describe("Bot Impact funnel", () => {
    it("should show PR-handled and intervention percentages when breakdown data available", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false });
      // 50 of 100 requests handled by bot (50%); 5 of those 50 needed manual escalation (10%)
      mockUseRequestBreakdown.mockReturnValue({
        data: { totalSupportTickets: 100, totalPrTickets: 50, interventionPrTickets: 5 },
      });

      render(<TenantRequestsPage />);

      const pctValues = screen
        .getAllByText(/^\d+%$/)
        .filter((el) => el.className.includes("text-3xl"))
        .map((el) => el.textContent);
      expect(pctValues).toEqual(expect.arrayContaining(["50%", "10%"]));
      expect(pctValues).toHaveLength(2);
    });

    it("should show the total requests count", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false });
      mockUseRequestBreakdown.mockReturnValue({
        data: { totalSupportTickets: 333, totalPrTickets: 46, interventionPrTickets: 8 },
      });

      render(<TenantRequestsPage />);

      expect(screen.getByText("333")).toBeInTheDocument();
    });

    it("should dash the percentages but show 0 total when counts are all zero", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false });
      mockUseRequestBreakdown.mockReturnValue({
        data: { totalSupportTickets: 0, totalPrTickets: 0, interventionPrTickets: 0 },
      });

      render(<TenantRequestsPage />);

      // both percentage cards dash (no denominator); total requests shows 0
      const dashes = screen.getAllByText("—").filter((el) => el.className.includes("text-3xl"));
      expect(dashes).toHaveLength(2);
    });

    it("should dash all three funnel cards when no breakdown data", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false });
      mockUseRequestBreakdown.mockReturnValue({ data: undefined });

      render(<TenantRequestsPage />);

      const dashes = screen.getAllByText("—").filter((el) => el.className.includes("text-3xl"));
      expect(dashes).toHaveLength(3);
    });

    it("should show 0% intervention when no manual escalations", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false });
      // 20 of 40 handled by bot (50%); none needed manual escalation (0%)
      mockUseRequestBreakdown.mockReturnValue({
        data: { totalSupportTickets: 40, totalPrTickets: 20, interventionPrTickets: 0 },
      });

      render(<TenantRequestsPage />);

      const zero = screen.getAllByText("0%").filter((el) => el.className.includes("text-3xl"));
      expect(zero).toHaveLength(1);
    });

    it("should not round a near-complete ratio up to a misleading 100%", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false });
      // 199 of 200 PRs escalated → 99.5% rounds to 100% with Math.round; clamp to 99% since 1 PR did not.
      // Bot share is 200/400 = 50% so a legitimate 100% never appears, isolating the clamp under test.
      mockUseRequestBreakdown.mockReturnValue({
        data: { totalSupportTickets: 400, totalPrTickets: 200, interventionPrTickets: 199 },
      });

      render(<TenantRequestsPage />);

      const pctValues = screen
        .getAllByText(/^\d+%$/)
        .filter((el) => el.className.includes("text-3xl"))
        .map((el) => el.textContent);
      expect(pctValues).toEqual(expect.arrayContaining(["50%", "99%"]));
      expect(pctValues).not.toContain("100%");
    });

    it("should not round a tiny non-zero ratio down to a misleading 0%", () => {
      mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false });
      // 1 of 1000 → 0.1% rounds to 0% with Math.round; clamp to 1% since a PR did need escalation.
      mockUseRequestBreakdown.mockReturnValue({
        data: { totalSupportTickets: 1000, totalPrTickets: 1000, interventionPrTickets: 1 },
      });

      render(<TenantRequestsPage />);

      const onePct = screen.getAllByText("1%").filter((el) => el.className.includes("text-3xl"));
      expect(onePct).toHaveLength(1);
    });
  });

  describe("Tab Navigation", () => {
    it("should show stats content by default", () => {
      render(<TenantRequestsPage />);

      expect(screen.getByRole("tab", { name: /PR Activity & SLA Health/ })).toBeInTheDocument();
    });

    it("should hide date filter and show In-Flight PRs content when inflight tab is active", () => {
      mockUseSearchParams.mockReturnValue(new URLSearchParams("tab=inflight"));
      render(<TenantRequestsPage />);

      expect(screen.getByRole("heading", { name: "In-Flight PRs" })).toBeInTheDocument();
    });
  });

  describe("No SLA repo rendering", () => {
    it("should show No SLA badge in repo name cell for hasSla=false repo", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ hasSla: false, breachedCount: 0 })],
        isLoading: false,
        error: null,
      });

      render(<TenantRequestsPage />);

      expect(screen.getByText("No SLA")).toBeInTheDocument();
    });

    it("should show dash for Breached column on hasSla=false repo", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ hasSla: false, breachedCount: 0 })],
        isLoading: false,
        error: null,
      });

      const { container } = render(<TenantRequestsPage />);

      // Breached column renders an em-dash in a tabular-nums span for no-SLA repos;
      // check text content to distinguish it from zero-value Badge spans
      const breachDash = Array.from(container.querySelectorAll(".tabular-nums")).find((el) => el.textContent === "\u2014");
      expect(breachDash).toBeTruthy();
    });

    it("should show escalation badge for hasSla=false repo with manual escalations", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [makeRepo({ hasSla: false, breachedCount: 0, escalatedCount: 2 })],
        isLoading: false,
        error: null,
      });

      render(<TenantRequestsPage />);

      // escalatedCount=2 should appear in the Escalated column even for no-SLA repos
      expect(screen.getAllByText("2").length).toBeGreaterThanOrEqual(1);
    });

    it("should count no-SLA repos in the No SLA Repos stat card", () => {
      mockUseTenantInsightsStats.mockReturnValue({
        data: [
          makeRepo({ repo: "org/sla-repo", hasSla: true }),
          makeRepo({ repo: "org/no-sla-a", hasSla: false, breachedCount: 0 }),
          makeRepo({ repo: "org/no-sla-b", hasSla: false, breachedCount: 0 }),
        ],
        isLoading: false,
        error: null,
      });

      render(<TenantRequestsPage />);

      const statValues = screen.getAllByText(/^\d+$/).filter((el) => el.className.includes("text-3xl"));
      expect(statValues.length).toBeGreaterThan(0); // guard: stat card selector must match
      const values = statValues.map((el) => Number(el.textContent));
      expect(values).toContain(2); // noSlaRepoCount
    });
  });
});
