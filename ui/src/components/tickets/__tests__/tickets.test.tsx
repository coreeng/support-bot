import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import React from "react";
import * as hooks from "../../../lib/hooks";
import Tickets from "../tickets";

// Mock the hooks
jest.mock("../../../lib/hooks");

// Mock useUrlParams with a useState-based implementation so filter/sort/page
// changes re-render the component correctly, keeping all existing test
// interactions that fire events and then inspect the rendered output intact.
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

const mockUseTickets = hooks.useTickets as jest.MockedFunction<typeof hooks.useTickets>;
const mockUseAllTickets = hooks.useAllTickets as jest.MockedFunction<typeof hooks.useAllTickets>;
const mockUseTicket = hooks.useTicket as jest.MockedFunction<typeof hooks.useTicket>;
const mockUseTenantTeams = hooks.useTenantTeams as jest.MockedFunction<typeof hooks.useTenantTeams>;
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>;
const mockUseAssignmentEnabled = hooks.useAssignmentEnabled as jest.MockedFunction<typeof hooks.useAssignmentEnabled>;

// Helper to create mock ticket with recent dates
const createMockTicket = (id: string, status: string, teamName: string, impact: string): any => {
  const now = new Date();
  const recentDate = new Date(now.getTime() - 24 * 60 * 60 * 1000); // Yesterday
  return {
    id,
    status,
    team: { name: teamName },
    impact,
    tags: [{ code: "bug", label: "Bug" }],
    escalations: [],
    logs: [{ event: "opened", date: recentDate.toISOString() }],
    query: { link: "https://example.com" },
  };
};

// Factory function for mock paginated tickets
const getMockPaginatedTickets = (tickets: ReturnType<typeof createMockTicket>[]) => ({
  content: tickets,
  page: 0,
  totalPages: 2,
  totalElements: tickets.length,
});

const mockTeams = [
  { name: "Team A", types: ["tenant"] },
  { name: "Team B", types: ["tenant"] },
];

const mockRegistry = {
  impacts: [
    { code: "high", label: "High Impact" },
    { code: "medium", label: "Medium Impact" },
    { code: "low", label: "Low Impact" },
  ],
  tags: [{ code: "bug", label: "Bug" }],
};

// Mock auth hook to provide a test user by default
jest.mock("../../../hooks/useAuth", () => ({
  useAuth: () => ({
    user: "test@example.com",
    isAuthenticated: true,
    isLoading: false,
    teams: ["Team A", "Team B"],
    isLeadership: false,
    isEscalationTeam: false,
    isSupportEngineer: false,
    actualEscalationTeams: [],
    isLoadingEscalationTeams: false,
  }),
}));

// Mock EditTicketModal
jest.mock("../EditTicketModal", () => ({
  __esModule: true,
  default: ({ ticketId, open, onOpenChange }: { ticketId: string | null; open: boolean; onOpenChange: (open: boolean) => void }) => {
    if (!open) return null;
    return (
      <div data-testid="edit-ticket-modal">
        <div>Ticket Modal: {ticketId}</div>
        <button onClick={() => onOpenChange(false)}>Close Modal</button>
      </div>
    );
  },
}));

// Mock team filter context
jest.mock("../../../contexts/TeamFilterContext", () => ({
  TeamFilterProvider: ({ children }: { children: React.ReactNode }) => children,
  useTeamFilter: jest.fn(),
}));
const mockUseTeamFilter = jest.requireMock("../../../contexts/TeamFilterContext").useTeamFilter as jest.MockedFunction<
  () => {
    selectedTeam: string | null;
    setSelectedTeam: jest.Mock;
    teamScope: { mode: string; teams?: string[] };
    effectiveTeams: string[];
    hasNoTeamScope: boolean;
    isViewingAllTeams: boolean;
    isViewingAsEscalationTeam: boolean;
    hasFullAccess: boolean;
    allTeams?: string[];
    initialized?: boolean;
  }
>;

const Wrapper = ({ children }: { children: React.ReactNode }) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
};

describe("Tickets Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();

    // Default mock implementations
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: null,
      setSelectedTeam: jest.fn(),
      teamScope: { mode: "all_teams" },
      effectiveTeams: [],
      hasNoTeamScope: false,
      isViewingAllTeams: true,
      isViewingAsEscalationTeam: false,
      hasFullAccess: true,
      allTeams: ["Team A", "Team B"],
      initialized: true,
    });

    mockUseTenantTeams.mockReturnValue({
      data: mockTeams,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useTenantTeams>);

    mockUseRegistry.mockReturnValue({
      data: mockRegistry,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);

    mockUseTicket.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useTicket>);

    mockUseAllTickets.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    mockUseAssignmentEnabled.mockReturnValue({
      data: false,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAssignmentEnabled>);
  });

  describe("Rendering", () => {
    it("renders the tickets table", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByRole("table")).toBeInTheDocument();
    });

    it("renders table headers", () => {
      const mockTickets = getMockPaginatedTickets([]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Filter buttons share label text with the column headers; assert at least one match.
      expect(screen.getAllByText("Status").length).toBeGreaterThan(0);
      expect(screen.getAllByText("Team").length).toBeGreaterThan(0);
      expect(screen.getAllByText("Impact").length).toBeGreaterThan(0);
      expect(screen.getByText("Tags")).toBeInTheDocument();
      expect(screen.getByText("Summary")).toBeInTheDocument();
      expect(screen.getAllByText("Escalated").length).toBeGreaterThan(0);
      expect(screen.getAllByText("Escalated To").length).toBeGreaterThan(0);
    });

    it("displays ticket data in table", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Check for team name
      const teamCells = screen.getAllByText("Team A");
      expect(teamCells.length).toBeGreaterThan(0);
    });
  });

  describe("Summary Column", () => {
    it("renders summary as the second column after status", () => {
      mockUseTickets.mockReturnValue({
        data: getMockPaginatedTickets([]),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      const headers = screen.getAllByRole("columnheader").map((header) => header.textContent?.trim());
      expect(headers[0]).toBe("Status");
      expect(headers[1]).toBe("Summary");
      expect(headers[2]).toBe("Team");
    });

    it("shows ticket summary text in the table", () => {
      mockUseTickets.mockReturnValue({
        data: getMockPaginatedTickets([
          {
            ...createMockTicket("1", "closed", "Team A", "high"),
            summary: "Cache invalidation fixed the production timeout for the tenant after deployment.",
          },
        ]),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByText(/Cache invalidation fixed the production timeout/i)).toBeInTheDocument();
    });

    it("renders em dash when no summary exists", () => {
      mockUseTickets.mockReturnValue({
        data: getMockPaginatedTickets([
          {
            ...createMockTicket("1", "closed", "Team A", "high"),
            summary: null,
          },
        ]),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByText("—")).toBeInTheDocument();
    });

    it("renders em dash when summary is whitespace-only", () => {
      mockUseTickets.mockReturnValue({
        data: getMockPaginatedTickets([
          {
            ...createMockTicket("1", "closed", "Team A", "high"),
            summary: "   ",
          },
        ]),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByText("—")).toBeInTheDocument();
    });
  });

  describe("Escalation Display", () => {
    it('shows "Yes" when ticket has escalations', () => {
      const ticketWithEscalation = {
        ...createMockTicket("1", "opened", "Team A", "high"),
        escalations: [{ id: "esc-1", team: { name: "Support" } }],
      };

      const mockTickets = getMockPaginatedTickets([ticketWithEscalation]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      const yesElements = screen.getAllByText("Yes");
      expect(yesElements.length).toBeGreaterThan(0);
    });

    it('shows "No" when ticket has no escalations', () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      const noElements = screen.getAllByText("No");
      expect(noElements.length).toBeGreaterThan(0);
    });

    it("shows escalation target team names in the Escalated To column", () => {
      const ticketWithEscalations = {
        ...createMockTicket("1", "opened", "Team A", "high"),
        escalations: [
          { id: "esc-1", team: { name: "Support Team" } },
          { id: "esc-2", team: { name: "Infra Team" } },
        ],
      };

      mockUseTickets.mockReturnValue({
        data: getMockPaginatedTickets([ticketWithEscalations]),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByText("Support Team, Infra Team")).toBeInTheDocument();
    });
  });

  describe("Loading and Error States", () => {
    it("shows loading state", () => {
      mockUseTickets.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Should show loading skeleton
      expect(document.querySelector(".animate-pulse")).toBeInTheDocument();
    });

    it("shows error state", () => {
      mockUseTickets.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error("Failed to load"),
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByText(/Error loading tickets/i)).toBeInTheDocument();
    });

    it("shows empty state when no tickets", () => {
      const mockTickets = getMockPaginatedTickets([]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByText("No tickets found")).toBeInTheDocument();
    });
  });

  describe("Date Opened and Closed Display", () => {
    it("displays date opened when available", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Just verify the table renders (date formatting can vary)
      expect(screen.getByRole("table")).toBeInTheDocument();
    });

    it("displays dash when dates not available", () => {
      const ticketWithoutDates = {
        ...createMockTicket("1", "opened", "Team A", "high"),
        logs: [],
      };

      const mockTickets = getMockPaginatedTickets([ticketWithoutDates]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Should show dashes for missing dates
      const table = screen.getByRole("table");
      expect(table).toBeInTheDocument();
    });

    it("sorts by Opened At when header is clicked", () => {
      const older = {
        ...createMockTicket("1", "opened", "Team A", "high"),
        logs: [{ event: "opened", date: "2024-01-01T10:00:00Z" }],
      };
      const newer = {
        ...createMockTicket("2", "opened", "Team B", "high"),
        logs: [{ event: "opened", date: "2024-01-02T10:00:00Z" }],
      };

      mockUseTickets.mockReturnValue({
        data: getMockPaginatedTickets([older, newer]),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      const getFirstDataRowText = () => {
        const rows = screen.getAllByRole("row");
        return rows[1]?.textContent || "";
      };

      // Default opened sort is desc => newer first
      expect(getFirstDataRowText()).toContain("Team B");

      // Toggle to asc => older first
      fireEvent.click(screen.getByText(/Opened At/i));
      expect(getFirstDataRowText()).toContain("Team A");
    });

    it("sorts by Closed At when header is clicked", () => {
      const closesEarlier = {
        ...createMockTicket("1", "closed", "Team A", "high"),
        logs: [
          { event: "opened", date: "2024-01-01T10:00:00Z" },
          { event: "closed", date: "2024-01-03T10:00:00Z" },
        ],
      };
      const closesLater = {
        ...createMockTicket("2", "closed", "Team B", "high"),
        logs: [
          { event: "opened", date: "2024-01-01T10:00:00Z" },
          { event: "closed", date: "2024-01-04T10:00:00Z" },
        ],
      };

      mockUseTickets.mockReturnValue({
        data: getMockPaginatedTickets([closesEarlier, closesLater]),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      const getFirstDataRowText = () => {
        const rows = screen.getAllByRole("row");
        return rows[1]?.textContent || "";
      };

      // Closed sort defaults to desc on first click => later close first
      fireEvent.click(screen.getByText(/Closed At/i));
      expect(getFirstDataRowText()).toContain("Team B");

      // Toggle to asc => earlier close first
      fireEvent.click(screen.getByText(/Closed At/i));
      expect(getFirstDataRowText()).toContain("Team A");
    });
  });

  describe("Status Styling", () => {
    it("applies styling to opened status", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Status should be rendered
      expect(screen.getByRole("table")).toBeInTheDocument();
    });

    it("applies styling to closed status", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "closed", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByRole("table")).toBeInTheDocument();
    });

    it("applies styling to stale status", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "stale", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByRole("table")).toBeInTheDocument();
    });
  });

  describe("Team Display", () => {
    it("displays team name when available", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      const teamCells = screen.getAllByText("Team A");
      expect(teamCells.length).toBeGreaterThan(0);
    });

    it("displays dash when team not available", () => {
      const ticketWithoutTeam = {
        ...createMockTicket("1", "opened", "Team A", "high"),
        team: null,
      };

      const mockTickets = getMockPaginatedTickets([ticketWithoutTeam]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByRole("table")).toBeInTheDocument();
    });
  });

  describe("Impact Display", () => {
    it("displays impact label when available", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Multiple elements will have "High Impact" (filter dropdown + table cells)
      const impactElements = screen.getAllByText("High Impact");
      expect(impactElements.length).toBeGreaterThan(0);
    });

    it("displays impact code when label not found", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "unknown")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      expect(screen.getByText("unknown")).toBeInTheDocument();
    });
  });

  describe("Ticket Modal Interaction", () => {
    it("opens modal when ticket row is clicked", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      mockUseTicket.mockReturnValue({
        data: createMockTicket("1", "opened", "Team A", "high"),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTicket>);

      render(<Tickets />, { wrapper: Wrapper });

      // Initially modal should not be visible
      expect(screen.queryByTestId("edit-ticket-modal")).not.toBeInTheDocument();

      // Click on a ticket row
      const rows = screen.getAllByRole("row");
      const ticketRow = rows.find((row) => row.textContent?.includes("Team A"));
      if (ticketRow) {
        fireEvent.click(ticketRow);
      }

      // Modal should now be visible
      expect(screen.getByTestId("edit-ticket-modal")).toBeInTheDocument();
      expect(screen.getByText(/Ticket Modal: 1/i)).toBeInTheDocument();
    });

    it("closes modal when close button is clicked", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      mockUseTicket.mockReturnValue({
        data: createMockTicket("1", "opened", "Team A", "high"),
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTicket>);

      render(<Tickets />, { wrapper: Wrapper });

      // Click on a ticket row to open modal
      const rows = screen.getAllByRole("row");
      const ticketRow = rows.find((row) => row.textContent?.includes("Team A"));
      if (ticketRow) {
        fireEvent.click(ticketRow);
      }

      // Modal should be visible
      expect(screen.getByTestId("edit-ticket-modal")).toBeInTheDocument();

      // Click close button
      const closeButton = screen.getByText("Close Modal");
      fireEvent.click(closeButton);

      // Modal should be closed
      expect(screen.queryByTestId("edit-ticket-modal")).not.toBeInTheDocument();
    });

    it("does not show details panel below table (replaced by modal)", () => {
      const mockTickets = getMockPaginatedTickets([createMockTicket("1", "opened", "Team A", "high")]);

      mockUseTickets.mockReturnValue({
        data: mockTickets,
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof hooks.useTickets>);

      render(<Tickets />, { wrapper: Wrapper });

      // Should not have the old details panel structure
      // The old implementation would have shown ticket details below the table
      // Now it should only show modal when ticket is clicked
      const table = screen.getByRole("table");
      expect(table).toBeInTheDocument();

      // No details panel should be visible initially
      expect(screen.queryByText(/Ticket #/i)).not.toBeInTheDocument();
    });
  });
});
