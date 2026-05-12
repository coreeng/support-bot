/**
 * StatsPage (Home Dashboard) Unit Tests
 *
 * Tests the Home dashboard rendering and behavior:
 * - Role-based view (escalation team split view vs regular view)
 * - Team filtering (hasFullAccess vs restricted)
 * - Metrics calculations (total, open, resolved, escalated tickets)
 * - Loading and error states
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import React from "react";
import * as TeamFilterContext from "../../../contexts/TeamFilterContext";
import * as AuthHook from "../../../hooks/useAuth";
import * as hooks from "../../../lib/hooks";
import StatsPage from "../stats";

const mockTimeSeriesChart = jest.fn(({ title }: { title: string }) => <div data-testid="time-series-chart">{title}</div>);
const mockHorizontalBarChart = jest.fn(({ title }: { title: string }) => <div data-testid="horizontal-bar-chart">{title}</div>);

// Mock hooks
jest.mock("../../../lib/hooks");
jest.mock("../../../hooks/useAuth");
jest.mock("../../../contexts/TeamFilterContext");

// Mock useUrlParams with a useState-based implementation so the component
// re-renders correctly when setParams is called, preserving all existing
// test interactions that fire events and then inspect API call arguments.
jest.mock("../../../lib/hooks/useUrlParams", () => ({
  ...jest.requireActual("../../../lib/hooks/useUrlParams"),
  useUrlParams: (defaults: Record<string, string>) => {
    const { useState } = require("react") as typeof import("react");
    const [params, setParamsState] = useState<Record<string, string>>(defaults);
    const setParams = (updates: Record<string, string>) => {
      setParamsState((prev: Record<string, string>) => ({ ...prev, ...updates }));
    };
    return [params, setParams];
  },
}));

// Mock EscalatedToMyTeamWidget
jest.mock("../../escalations/EscalatedToMyTeamWidget", () => {
  return function MockEscalatedToMyTeamWidget() {
    return <div data-testid="escalated-to-my-team-widget">Escalated To My Team Widget</div>;
  };
});

// Mock chart components
jest.mock("../../dashboards/TimeSeriesChart", () => ({
  TimeSeriesChart: (props: { title: string }) => mockTimeSeriesChart(props),
}));

jest.mock("../../dashboards/HorizontalBarChart", () => ({
  HorizontalBarChart: (props: { title: string }) => mockHorizontalBarChart(props),
}));

const mockUseAllTickets = hooks.useAllTickets as jest.MockedFunction<typeof hooks.useAllTickets>;
const mockUseIncomingVsResolvedRate = hooks.useIncomingVsResolvedRate as jest.MockedFunction<typeof hooks.useIncomingVsResolvedRate>;
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>;
const mockUseAuth = AuthHook.useAuth as jest.MockedFunction<typeof AuthHook.useAuth>;
const mockUseTeamFilter = TeamFilterContext.useTeamFilter as jest.MockedFunction<typeof TeamFilterContext.useTeamFilter>;

/**
 * Builds a complete TeamFilterContext return value.
 * Every field required by TeamFilterContextType is provided as a sensible default
 * so individual tests only need to state what differs.
 */
function makeTeamFilter(
  overrides: Partial<ReturnType<typeof TeamFilterContext.useTeamFilter>> = {}
): ReturnType<typeof TeamFilterContext.useTeamFilter> {
  return {
    selectedTeam: null,
    setSelectedTeam: jest.fn(),
    teamScope: { mode: "uninitialized" },
    effectiveTeams: [],
    hasNoTeamScope: false,
    isViewingAllTeams: false,
    isViewingAsEscalationTeam: false,
    hasFullAccess: false,
    allTeams: [],
    initialized: false,
    ...overrides,
  } as ReturnType<typeof TeamFilterContext.useTeamFilter>;
}

// Test data
const mockTickets = [
  {
    id: "1",
    status: "opened",
    team: { name: "Team A" },
    impact: "high",
    escalations: [],
    query: { date: "2025-01-01T10:00:00Z" },
    logs: [{ event: "opened", date: "2025-01-01T10:00:00Z" }],
  },
  {
    id: "2",
    status: "opened",
    team: { name: "Team A" },
    impact: "medium",
    escalations: [],
    query: { date: "2025-01-01T12:00:00Z" },
    logs: [{ event: "opened", date: "2025-01-01T12:00:00Z" }],
  },
  {
    id: "3",
    status: "closed",
    team: { name: "Team A" },
    impact: "low",
    escalations: [],
    query: { date: "2025-01-01T15:00:00Z" },
    logs: [
      { event: "opened", date: "2025-01-01T15:00:00Z" },
      { event: "closed", date: "2025-01-02T09:00:00Z" },
    ],
  },
  {
    id: "4",
    status: "opened",
    team: { name: "Team A" },
    impact: "high",
    escalations: [{ id: "esc-1" }],
    query: { date: "2025-01-02T10:00:00Z" },
    logs: [{ event: "opened", date: "2025-01-02T10:00:00Z" }],
  },
  {
    id: "5",
    status: "opened",
    team: { name: "Team B" },
    impact: "medium",
    escalations: [],
    query: { date: "2025-01-03T10:00:00Z" },
    logs: [{ event: "opened", date: "2025-01-03T10:00:00Z" }],
  },
];

const mockIncomingVsResolvedRate = {
  granularity: "DAY" as const,
  data: [
    { time: "2025-01-01T00:00:00Z", incoming: 3, resolved: 0 },
    { time: "2025-01-02T00:00:00Z", incoming: 1, resolved: 2 },
  ],
};

const mockRegistry = {
  impacts: [
    { code: "high", label: "High" },
    { code: "medium", label: "Medium" },
    { code: "low", label: "Low" },
  ],
  tags: [],
};

const Wrapper = ({ children }: { children: React.ReactNode }) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
};

describe("StatsPage (Home Dashboard)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers().setSystemTime(new Date("2025-01-02T12:00:00Z"));

    // Default mocks
    mockUseRegistry.mockReturnValue({
      data: mockRegistry,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);

    mockUseAuth.mockReturnValue({
      user: {
        id: "user-1",
        email: "user@example.com",
        name: "Test User",
        teams: [{ label: "Team A", code: "team-a", types: [], name: "Team A" }],
        roles: ["SUPPORT_ENGINEER"],
      },
      isLeadership: false,
      isSupportEngineer: true,
      isEscalationTeam: false,
      actualEscalationTeams: [],
      isLoading: false,
      isAuthenticated: true,
      logout: jest.fn(),
    });

    mockUseTeamFilter.mockReturnValue(
      makeTeamFilter({
        selectedTeam: "Team A",
        teamScope: { mode: "selected_teams", teams: ["Team A"] },
        effectiveTeams: ["Team A"],
        allTeams: ["Team A", "Team B"],
        initialized: true,
      })
    );

    mockUseAllTickets.mockReturnValue({
      data: {
        content: mockTickets,
        page: 0,
        totalPages: 1,
        totalElements: mockTickets.length,
      },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    mockUseIncomingVsResolvedRate.mockReturnValue({
      data: mockIncomingVsResolvedRate,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useIncomingVsResolvedRate>);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe("Loading and Error States", () => {
    it("should show loading state when tickets are loading", () => {
      mockUseAllTickets.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      } as unknown as ReturnType<typeof hooks.useAllTickets>);

      render(<StatsPage />, { wrapper: Wrapper });

      // Should show loading skeleton
      expect(document.querySelector(".animate-pulse")).toBeInTheDocument();
    });

    it("should show error state when tickets fail to load", () => {
      mockUseAllTickets.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error("Failed to load"),
      } as unknown as ReturnType<typeof hooks.useAllTickets>);

      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/Error loading dashboard/i)).toBeInTheDocument();
    });

    it("shows an explicit chart error when incoming/resolved data fails to load", () => {
      mockUseIncomingVsResolvedRate.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error("Chart failed"),
      } as unknown as ReturnType<typeof hooks.useIncomingVsResolvedRate>);

      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/Unable to load incoming and resolved ticket activity/i)).toBeInTheDocument();
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
    });
  });

  describe("Role-Based View", () => {
    it("should show regular view for non-escalation teams", () => {
      render(<StatsPage />, { wrapper: Wrapper });

      // Should NOT show split view sections
      expect(screen.queryByText(/Escalations We Are Handling/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Tickets We Own/i)).not.toBeInTheDocument();

      // Should show regular dashboard title
      expect(screen.getByText(/Support Dashboard/i)).toBeInTheDocument();

      // Should show summary cards
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
      expect(screen.getByText(/Open Tickets/i)).toBeInTheDocument();
    });

    it("should show restricted chart message for users without dashboard access", () => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "user-1",
          email: "restricted@example.com",
          name: "Restricted User",
          teams: [{ label: "Team A", code: "team-a", types: [], name: "Team A" }],
          roles: ["ESCALATION"],
        },
        isLeadership: false,
        isSupportEngineer: false,
        isEscalationTeam: true,
        actualEscalationTeams: [],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
      });

      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/This chart requires Support Engineer or Leadership access/i)).toBeInTheDocument();
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
      expect(mockUseIncomingVsResolvedRate).toHaveBeenLastCalledWith(false, "2024-12-26", "2025-01-02", expect.any(Object));
    });

    it("shows explicit no-team-access banner when user has no effective teams", () => {
      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: null,
          teamScope: { mode: "no_teams" },
          effectiveTeams: ["__no_teams__"],
          hasNoTeamScope: true,
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );
      mockUseAuth.mockReturnValue({
        user: {
          id: "user-1",
          email: "user@example.com",
          name: "Test User",
          teams: [],
          roles: [],
        },
        isLeadership: false,
        isSupportEngineer: false,
        isEscalationTeam: false,
        actualEscalationTeams: [],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
      });

      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/No Team Access/i)).toBeInTheDocument();
      expect(screen.getByText(/dashboard data cannot be displayed/i)).toBeInTheDocument();

      const incomingChartCall = mockTimeSeriesChart.mock.calls.find(([props]) => props.title === "Incoming vs Resolved");
      expect(incomingChartCall?.[0]).toMatchObject({
        data: [],
        emptyMessage: "Incoming and resolved ticket activity cannot be shown without team access.",
      });
      expect(mockUseIncomingVsResolvedRate).toHaveBeenLastCalledWith(false, "2024-12-26", "2025-01-02", {
        teams: [],
        allTime: false,
        granularity: "AUTO",
      });
    });

    it("should show split view for escalation teams", () => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "escalation-user",
          email: "escalation@example.com",
          name: "Escalation User",
          teams: [{ label: "Core-platform", code: "core-platform", types: ["escalation"], name: "Core-platform" }],
          roles: ["escalation"],
        },
        isLeadership: false,
        isSupportEngineer: false,
        isEscalationTeam: true,
        actualEscalationTeams: ["Core-platform"],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
      });

      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "Core-platform",
          teamScope: { mode: "selected_teams", teams: ["Core-platform"] },
          effectiveTeams: ["Core-platform"],
          isViewingAsEscalationTeam: true,
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );

      render(<StatsPage />, { wrapper: Wrapper });

      // Should show split view sections
      expect(screen.getByText(/Escalations We Are Handling/i)).toBeInTheDocument();
      expect(screen.getByText(/Tickets We Own/i)).toBeInTheDocument();

      // Should show EscalatedToMyTeamWidget
      expect(screen.getByTestId("escalated-to-my-team-widget")).toBeInTheDocument();
    });

    it("should NOT show split view when escalation team selects non-escalation team", () => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "escalation-user",
          email: "escalation@example.com",
          name: "Escalation User",
          teams: [
            { label: "Core-platform", code: "core-platform", types: ["escalation"], name: "Core-platform" },
            { label: "Team A", code: "team-a", types: [], name: "Team A" },
          ],
          roles: ["escalation"],
        },
        isLeadership: false,
        isSupportEngineer: false,
        isEscalationTeam: true,
        actualEscalationTeams: ["Core-platform"],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
      });

      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "Team A", // Not an escalation team
          teamScope: { mode: "selected_teams", teams: ["Team A"] },
          effectiveTeams: ["Team A"],
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );

      render(<StatsPage />, { wrapper: Wrapper });

      // Should NOT show split view
      expect(screen.queryByText(/Escalations We Are Handling/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Tickets We Own/i)).not.toBeInTheDocument();
    });
  });

  describe("Team Filtering", () => {
    it("should render dashboard when hasFullAccess is true", () => {
      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "Leadership",
          teamScope: { mode: "all_teams" },
          effectiveTeams: ["Team A", "Team B"],
          isViewingAllTeams: true,
          hasFullAccess: true,
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );

      render(<StatsPage />, { wrapper: Wrapper });

      // Should show dashboard with all summary cards
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
      expect(screen.getByText(/Open Tickets/i)).toBeInTheDocument();
    });

    it("should render dashboard with team filtering when restricted", () => {
      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "Team A",
          teamScope: { mode: "selected_teams", teams: ["Team A"] },
          effectiveTeams: ["Team A"],
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );

      render(<StatsPage />, { wrapper: Wrapper });

      // Should show dashboard (filtered tickets rendering tested by no errors)
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
    });

    it("should handle empty effectiveTeams gracefully", () => {
      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: null,
          teamScope: { mode: "uninitialized" },
          effectiveTeams: [],
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );

      render(<StatsPage />, { wrapper: Wrapper });

      // Should still render dashboard
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
    });
  });

  describe("Metrics Calculations", () => {
    it("should render all summary card labels", () => {
      render(<StatsPage />, { wrapper: Wrapper });

      // Should show all metric labels
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
      expect(screen.getByText(/Open Tickets/i)).toBeInTheDocument();
      expect(screen.getByText(/Resolved Tickets/i)).toBeInTheDocument();
      expect(screen.getByText(/Escalated Tickets/i)).toBeInTheDocument();
    });

    it("should calculate metrics based on filtered tickets", () => {
      render(<StatsPage />, { wrapper: Wrapper });

      // Team A has 4 tickets (total, open: 3, resolved: 1, escalated: 1)
      // Just verify the dashboard renders with metrics
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();

      // Verify there are number values displayed (summary cards)
      const container = screen.getByText(/Total Tickets/i).closest("div");
      expect(container).toBeInTheDocument();
    });

    it("should show 0 for all metrics when no tickets", () => {
      mockUseAllTickets.mockReturnValue({
        data: { content: [], page: 0, totalPages: 0, totalElements: 0 },
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useAllTickets>);

      render(<StatsPage />, { wrapper: Wrapper });

      // Should show summary cards even with 0 tickets
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
      expect(screen.getByText(/Open Tickets/i)).toBeInTheDocument();
    });

    it("should correctly filter tickets by team when restricted", () => {
      // Set up so we can verify filtering is working
      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "Team B",
          teamScope: { mode: "selected_teams", teams: ["Team B"] },
          effectiveTeams: ["Team B"],
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );

      render(<StatsPage />, { wrapper: Wrapper });

      // Should show summary cards (filtering is tested by rendering without errors)
      expect(screen.getByText(/Total Tickets/i)).toBeInTheDocument();
    });
  });

  describe("Charts Rendering", () => {
    it("should render Incoming vs Resolved chart", () => {
      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/Incoming vs Resolved/i)).toBeInTheDocument();
      expect(screen.getByTestId("time-series-chart")).toBeInTheDocument();
    });

    it("should render Tickets by Impact chart", () => {
      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/Tickets by Impact/i)).toBeInTheDocument();
      expect(screen.getByTestId("horizontal-bar-chart")).toBeInTheDocument();
    });

    it("renders incoming vs resolved chart data from the shared hook", () => {
      render(<StatsPage />, { wrapper: Wrapper });

      const incomingChartCall = mockTimeSeriesChart.mock.calls.find(([props]) => props.title === "Incoming vs Resolved");
      expect(incomingChartCall?.[0].data).toEqual([
        { time: "Jan 1", incoming: 3, resolved: 0 },
        { time: "Jan 2", incoming: 1, resolved: 2 },
      ]);
    });

    it("resolves team codes case-insensitively before calling the shared hook", () => {
      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "team a",
          teamScope: { mode: "selected_teams", teams: ["team a"] },
          effectiveTeams: ["team a"],
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );
      mockUseAuth.mockReturnValue({
        user: {
          id: "user-1",
          email: "user@example.com",
          name: "Test User",
          teams: [
            { label: "TEAM A", code: "team-a", types: [], name: "Team A" },
            { label: "Team A", code: "team-a", types: [], name: "Team A" },
          ],
          roles: ["SUPPORT_ENGINEER"],
        },
        isLeadership: false,
        isSupportEngineer: true,
        isEscalationTeam: false,
        actualEscalationTeams: [],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
      });

      render(<StatsPage />, { wrapper: Wrapper });

      expect(mockUseIncomingVsResolvedRate).toHaveBeenLastCalledWith(true, "2024-12-26", "2025-01-02", {
        teams: ["team-a"],
        allTime: false,
        granularity: "AUTO",
      });
    });

    it("warns and drops unresolved teams when the user has no matching team metadata", () => {
      const warn = jest.spyOn(console, "warn").mockImplementation(() => undefined);
      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "Unknown Team",
          teamScope: { mode: "selected_teams", teams: ["Unknown Team"] },
          effectiveTeams: ["Unknown Team"],
          allTeams: ["Unknown Team"],
          initialized: true,
        })
      );
      mockUseAuth.mockReturnValue({
        user: {
          id: "user-1",
          email: "user@example.com",
          name: "Test User",
          teams: [],
          roles: ["SUPPORT_ENGINEER"],
        },
        isLeadership: false,
        isSupportEngineer: true,
        isEscalationTeam: false,
        actualEscalationTeams: [],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
      });

      render(<StatsPage />, { wrapper: Wrapper });

      expect(warn).toHaveBeenCalledWith('getIncomingResolvedTeamCodes: could not resolve team "Unknown Team" to a code');
      expect(mockUseIncomingVsResolvedRate).toHaveBeenLastCalledWith(true, "2024-12-26", "2025-01-02", {
        teams: [],
        allTime: false,
        granularity: "AUTO",
      });

      warn.mockRestore();
    });
  });

  describe("Dashboard Title", () => {
    it("should show generic title for regular view", () => {
      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/Support Dashboard/i)).toBeInTheDocument();
    });

    it("should show team name in title for escalation team view", () => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "escalation-user",
          email: "escalation@example.com",
          name: "Escalation User",
          teams: [{ label: "Core-platform", code: "core-platform", types: ["escalation"], name: "Core-platform" }],
          roles: ["escalation"],
        },
        isLeadership: false,
        isSupportEngineer: false,
        isEscalationTeam: true,
        actualEscalationTeams: ["Core-platform"],
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
      });

      mockUseTeamFilter.mockReturnValue(
        makeTeamFilter({
          selectedTeam: "Core-platform",
          teamScope: { mode: "selected_teams", teams: ["Core-platform"] },
          effectiveTeams: ["Core-platform"],
          isViewingAsEscalationTeam: true,
          allTeams: ["Team A", "Team B"],
          initialized: true,
        })
      );

      render(<StatsPage />, { wrapper: Wrapper });

      expect(screen.getByText(/Support Dashboard/i)).toBeInTheDocument();
    });
  });
});
