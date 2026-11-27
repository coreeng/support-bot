import { useTeamFilter } from "@/contexts/TeamFilterContext";
import { useAuth } from "@/hooks/useAuth";
import { useDocAnalysisEnabled, useElevateEnabled, useSummaryEnabled, useTenantInsightsEnabled } from "@/lib/hooks";
import { render, screen, waitFor } from "@testing-library/react";
import { useRouter } from "next/navigation";
import DashboardLayoutComponent from "../(dashboard)/layout";
import Dashboard from "../(dashboard)/page";

jest.mock("next/navigation", () => ({
  useRouter: jest.fn(),
  usePathname: jest.fn(() => "/"),
  useSearchParams: jest.fn(() => new URLSearchParams()),
}));

jest.mock("../../hooks/useAuth", () => ({
  useAuth: jest.fn(),
}));

jest.mock("../../contexts/TeamFilterContext", () => ({
  useTeamFilter: jest.fn(),
}));

jest.mock("../../lib/hooks", () => ({
  useSummaryEnabled: jest.fn(),
  useDocAnalysisEnabled: jest.fn(),
  useTenantInsightsEnabled: jest.fn(),
  useElevateEnabled: jest.fn(),
}));

// Mock all the page components
jest.mock("../../components/stats/stats", () => ({
  __esModule: true,
  default: () => <div>Stats Page</div>,
}));

jest.mock("../../components/tickets/tickets", () => ({
  __esModule: true,
  default: () => <div>Tickets Page</div>,
}));

jest.mock("../../components/escalations/escalations", () => ({
  __esModule: true,
  default: () => <div>Escalations Page</div>,
}));

jest.mock("../../components/health/health", () => ({
  __esModule: true,
  default: () => <div>Health Page</div>,
}));

jest.mock("../../components/dashboards/dashboards", () => ({
  __esModule: true,
  default: () => <div>Dashboards Page</div>,
}));

jest.mock("../../components/TeamSelector", () => ({
  __esModule: true,
  default: () => <div>Team Selector</div>,
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: (props: any) => {
    // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text
    return <img {...props} />;
  },
}));

const mockRouter = {
  push: jest.fn(),
};

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockUseTeamFilter = useTeamFilter as jest.MockedFunction<typeof useTeamFilter>;
const mockUseSummaryEnabled = useSummaryEnabled as jest.MockedFunction<typeof useSummaryEnabled>;
const mockUseDocAnalysisEnabled = useDocAnalysisEnabled as jest.MockedFunction<typeof useDocAnalysisEnabled>;
const mockUseTenantInsightsEnabled = useTenantInsightsEnabled as jest.MockedFunction<typeof useTenantInsightsEnabled>;
const mockUseElevateEnabled = useElevateEnabled as jest.MockedFunction<typeof useElevateEnabled>;
const mockUseRouter = useRouter as jest.MockedFunction<typeof useRouter>;

// Helper to render Dashboard with Layout (simulating the route group structure)
const renderDashboard = () => {
  return render(
    <DashboardLayoutComponent>
      <Dashboard />
    </DashboardLayoutComponent>
  );
};

describe("Dashboard - restricted tabs visibility", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseRouter.mockReturnValue(mockRouter as any);
    mockUseSummaryEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseDocAnalysisEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseTenantInsightsEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseElevateEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseTeamFilter.mockReturnValue({
      hasUnrestrictedDataScope: true,
      selectedTeam: null,
      setSelectedTeam: jest.fn(),
      effectiveTeams: [],
      allTeams: [],
      initialized: true,
      teamScope: { mode: "uninitialized" as const },
      hasNoTeamScope: false,
      isViewingAllTeams: false,
      isViewingAsEscalationTeam: false,
    });
  });

  describe("restricted tabs visibility", () => {
    beforeEach(() => {
      mockUseTenantInsightsEnabled.mockReturnValue({ data: true, isLoading: false, error: null } as any);
    });

    it.each([
      { description: "USER", roles: ["USER"] },
      { description: "ESCALATION", roles: ["ESCALATION"] },
      { description: "USER and ESCALATION", roles: ["USER", "ESCALATION"] },
    ])("hides every restricted tab for $description roles", async ({ roles }) => {
      mockUseTeamFilter.mockReturnValue({
        hasUnrestrictedDataScope: true,
        selectedTeam: null,
        setSelectedTeam: jest.fn(),
        effectiveTeams: [],
        allTeams: ["tenant-team"],
        initialized: true,
        teamScope: { mode: "all_teams" as const },
        hasNoTeamScope: false,
        isViewingAllTeams: true,
        isViewingAsEscalationTeam: false,
      });

      mockUseAuth.mockReturnValue({
        user: {
          id: "user@example.com",
          name: "User",
          email: "user@example.com",
          roles,
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
      });

      renderDashboard();

      await waitFor(() => {
        expect(screen.queryByText("Analytics & Operations")).not.toBeInTheDocument();
        expect(screen.queryByText("SLA Dashboard")).not.toBeInTheDocument();
        expect(screen.queryByText("Tenant Requests")).not.toBeInTheDocument();
      });
    });

    it("shows sidebar placeholders until an authorized session has loaded", async () => {
      mockUseAuth.mockReturnValue({
        user: null,
        isLoading: true,
        isAuthenticated: false,
        logout: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
      });

      const view = renderDashboard();

      expect(view.container.querySelector('[data-sidebar="menu-skeleton"]')).toBeInTheDocument();
      expect(screen.queryByText("Home")).not.toBeInTheDocument();
      expect(screen.queryByText("Analytics & Operations")).not.toBeInTheDocument();

      mockUseAuth.mockReturnValue({
        user: {
          id: "support@example.com",
          name: "Support Engineer",
          email: "support@example.com",
          roles: ["SUPPORT_ENGINEER"],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      });

      view.rerender(
        <DashboardLayoutComponent>
          <Dashboard />
        </DashboardLayoutComponent>
      );

      await waitFor(() => {
        expect(view.container.querySelector('[data-sidebar="menu-skeleton"]')).not.toBeInTheDocument();
        expect(screen.getByText("Home")).toBeInTheDocument();
        expect(screen.getByText("Analytics & Operations")).toBeInTheDocument();
      });
    });

    it("keeps feature flags independent from the role capability", async () => {
      mockUseSummaryEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
      mockUseTenantInsightsEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
      mockUseElevateEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);

      mockUseTeamFilter.mockReturnValue({
        hasUnrestrictedDataScope: false,
        selectedTeam: "tenant-team",
        setSelectedTeam: jest.fn(),
        effectiveTeams: ["tenant-team"],
        allTeams: ["tenant-team"],
        initialized: true,
        teamScope: { mode: "selected_teams" as const, teams: ["tenant-team"] },
        hasNoTeamScope: false,
        isViewingAllTeams: false,
        isViewingAsEscalationTeam: false,
      });

      mockUseAuth.mockReturnValue({
        user: {
          id: "user@example.com",
          name: "User",
          email: "user@example.com",
          roles: ["SUPPORT_ENGINEER"],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: true,
        actualEscalationTeams: [],
      });

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText("Analytics & Operations")).toBeInTheDocument();
        expect(screen.getByText("SLA Dashboard")).toBeInTheDocument();
        expect(screen.queryByText("Tenant Requests")).not.toBeInTheDocument();
      });
    });

    it("shows each feature-enabled restricted tab while viewing a selected team", async () => {
      mockUseTenantInsightsEnabled.mockReturnValue({ data: true, isLoading: false, error: null } as any);
      mockUseTeamFilter.mockReturnValue({
        hasUnrestrictedDataScope: false,
        selectedTeam: "tenant-team",
        setSelectedTeam: jest.fn(),
        effectiveTeams: ["tenant-team"],
        allTeams: ["tenant-team"],
        initialized: true,
        teamScope: { mode: "selected_teams" as const, teams: ["tenant-team"] },
        hasNoTeamScope: false,
        isViewingAllTeams: false,
        isViewingAsEscalationTeam: false,
      });
      mockUseAuth.mockReturnValue({
        user: {
          id: "leader@example.com",
          name: "Leader",
          email: "leader@example.com",
          roles: ["LEADERSHIP"],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        isLeadership: true,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
      });

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText("Analytics & Operations")).toBeInTheDocument();
        expect(screen.getByText("SLA Dashboard")).toBeInTheDocument();
        expect(screen.getByText("Tenant Requests")).toBeInTheDocument();
      });
    });

    it("shows all basic tabs for any authenticated user", async () => {
      mockUseTeamFilter.mockReturnValue({
        hasUnrestrictedDataScope: false,
        selectedTeam: "tenant-team",
        setSelectedTeam: jest.fn(),
        effectiveTeams: ["tenant-team"],
        allTeams: ["tenant-team"],
        initialized: true,
        teamScope: { mode: "selected_teams" as const, teams: ["tenant-team"] },
        hasNoTeamScope: false,
        isViewingAllTeams: false,
        isViewingAsEscalationTeam: false,
      });

      mockUseAuth.mockReturnValue({
        user: {
          id: "user@example.com",
          name: "User",
          email: "user@example.com",
          roles: ["USER"],
          teams: [],
        },
        isLoading: false,
        isAuthenticated: true,
        logout: jest.fn(),
        isLeadership: false,
        isEscalationTeam: false,
        isSupportEngineer: false,
        actualEscalationTeams: [],
      });

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText("Home")).toBeInTheDocument();
        expect(screen.getByText("Escalations")).toBeInTheDocument();
      });
    });
  });
});

describe("Dashboard - Elevate nav item visibility", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseRouter.mockReturnValue(mockRouter as any);
    mockUseSummaryEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseDocAnalysisEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseTenantInsightsEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseTeamFilter.mockReturnValue({
      hasUnrestrictedDataScope: true,
      selectedTeam: null,
      setSelectedTeam: jest.fn(),
      effectiveTeams: [],
      allTeams: [],
      initialized: true,
      teamScope: { mode: "uninitialized" as const },
      hasNoTeamScope: false,
      isViewingAllTeams: false,
      isViewingAsEscalationTeam: false,
    });
    mockUseAuth.mockReturnValue({
      user: {
        id: "user@example.com",
        name: "User",
        email: "user@example.com",
        roles: ["SUPPORT_ENGINEER"],
        teams: [],
      },
      isLoading: false,
      isAuthenticated: true,
      logout: jest.fn(),
      isLeadership: false,
      isEscalationTeam: false,
      isSupportEngineer: true,
      actualEscalationTeams: [],
    });
  });

  it("hides Elevate when the integration is not configured, even with the required role", async () => {
    // Regression: the Elevate nav item previously had no requiresFeatureFlag, so it was visible to
    // any user with the restricted-dashboards role regardless of whether Elevate was ever configured
    // (base-url/client-id/client-secret all blank) — see /elevate/enabled.
    mockUseElevateEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("Home")).toBeInTheDocument();
    });
    expect(screen.queryByText("Elevate")).not.toBeInTheDocument();
    expect(screen.queryByText("Integrations")).not.toBeInTheDocument();
  });

  it("shows Elevate once the integration is configured and the role capability is present", async () => {
    mockUseElevateEnabled.mockReturnValue({ data: true, isLoading: false, error: null } as any);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("Elevate")).toBeInTheDocument();
    });
    expect(screen.getByText("Integrations")).toBeInTheDocument();
  });
});

describe("Dashboard - Documentation Analysis nav item visibility", () => {
  const signInWithRoles = (roles: string[]) =>
    mockUseAuth.mockReturnValue({
      user: { id: "user@example.com", name: "User", email: "user@example.com", roles, teams: [] },
      isLoading: false,
      isAuthenticated: true,
      logout: jest.fn(),
      isLeadership: roles.includes("LEADERSHIP"),
      isEscalationTeam: false,
      isSupportEngineer: roles.includes("SUPPORT_ENGINEER"),
      actualEscalationTeams: [],
    });

  beforeEach(() => {
    jest.clearAllMocks();
    mockUseRouter.mockReturnValue(mockRouter as any);
    mockUseSummaryEnabled.mockReturnValue({ data: true, isLoading: false, error: null } as any);
    mockUseDocAnalysisEnabled.mockReturnValue({ data: true, isLoading: false, error: null } as any);
    mockUseTenantInsightsEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseElevateEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);
    mockUseTeamFilter.mockReturnValue({
      hasUnrestrictedDataScope: true,
      selectedTeam: null,
      setSelectedTeam: jest.fn(),
      effectiveTeams: [],
      allTeams: [],
      initialized: true,
      teamScope: { mode: "uninitialized" as const },
      hasNoTeamScope: false,
      isViewingAllTeams: false,
      isViewingAsEscalationTeam: false,
    });
  });

  it.each([["LEADERSHIP"], ["SUPPORT_ENGINEER"]])(
    "lists the page right under Support Summary for %s when the feature is on",
    async (role) => {
      signInWithRoles([role]);

      renderDashboard();

      const link = await screen.findByRole("link", { name: "Documentation Analysis" });
      expect(link).toHaveAttribute("href", "/documentation-analysis");
      const titles = screen.getAllByRole("link").map((each) => each.textContent);
      expect(titles.indexOf("Documentation Analysis")).toBe(titles.indexOf("Support Summary") + 1);
    }
  );

  it("hides the page when the feature is off", async () => {
    signInWithRoles(["SUPPORT_ENGINEER"]);
    mockUseDocAnalysisEnabled.mockReturnValue({ data: false, isLoading: false, error: null } as any);

    renderDashboard();

    expect(await screen.findByText("Support Summary")).toBeInTheDocument();
    expect(screen.queryByText("Documentation Analysis")).not.toBeInTheDocument();
  });

  it("hides the page from a user with neither leadership nor support engineer", async () => {
    signInWithRoles(["USER", "ESCALATION"]);

    renderDashboard();

    expect(await screen.findByText("Home")).toBeInTheDocument();
    expect(screen.queryByText("Documentation Analysis")).not.toBeInTheDocument();
  });
});
