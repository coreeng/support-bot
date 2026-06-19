import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import TeamSelector from "../TeamSelector";

jest.mock("next/navigation", () => ({
  useRouter: jest.fn(),
  useSearchParams: jest.fn(),
  usePathname: jest.fn(),
}));

jest.mock("../../hooks/useAuth", () => ({
  useAuth: jest.fn(),
}));

jest.mock("../../contexts/TeamFilterContext", () => ({
  useTeamFilter: jest.fn(),
}));

const mockUseAuth = jest.requireMock("../../hooks/useAuth").useAuth as jest.Mock;
const mockUseTeamFilter = jest.requireMock("../../contexts/TeamFilterContext").useTeamFilter as jest.Mock;
const mockUseRouter = useRouter as jest.Mock;
const mockUseSearchParams = useSearchParams as jest.Mock;
const mockUsePathname = usePathname as jest.Mock;
const mockReplace = jest.fn();

const baseTeamFilter = () => ({
  selectedTeam: null,
  setSelectedTeam: jest.fn(),
});

const renderSelector = () => render(<TeamSelector />);

const openMenu = async () => {
  const user = userEvent.setup();
  await user.click(screen.getByTestId("team-selector-trigger"));
  return user;
};

describe("TeamSelector", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseRouter.mockReturnValue({ replace: mockReplace });
    mockUseSearchParams.mockReturnValue(new URLSearchParams());
    mockUsePathname.mockReturnValue("/");
    mockUseTeamFilter.mockReturnValue(baseTeamFilter());
  });

  it("does not render when there is no user", () => {
    mockUseAuth.mockReturnValue({ user: null });

    const { container } = renderSelector();
    expect(container).toBeEmptyDOMElement();
  });

  it("shows warning message when user has no teams", () => {
    mockUseAuth.mockReturnValue({
      user: { teams: [] },
      isLeadership: false,
      isSupportEngineer: false,
    });

    renderSelector();

    expect(screen.queryByTestId("team-selector-trigger")).toBeNull();
    expect(screen.getByText("No Teams Assigned")).toBeInTheDocument();
    expect(screen.getByText(/not a member of any teams/i)).toBeInTheDocument();
    expect(screen.getByText(/contact your administrator/i)).toBeInTheDocument();
  });

  it("shows dropdown when user has only role teams (leadership/support) and is leadership or support engineer", async () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Leadership Team", types: ["leadership"], groupRefs: [] },
          { name: "Support Engineers", types: ["support"], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: true,
    });

    renderSelector();
    await openMenu();

    expect(screen.getByTestId("team-selector-trigger")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Leadership Team/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Support Engineers/i })).toBeInTheDocument();
  });

  it("shows dropdown when a non-role team exists and includes role teams as options", async () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Leadership Team", types: ["leadership"], groupRefs: [] },
          { name: "Support Engineers", types: ["support"], groupRefs: [] },
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: true,
    });

    renderSelector();
    await openMenu();

    expect(screen.getByTestId("team-selector-trigger")).toBeInTheDocument();
    const itemTexts = screen.getAllByRole("menuitem").map((o) => o.textContent || "");
    expect(itemTexts.some((t) => /Tenant A/.test(t))).toBe(true);
    expect(itemTexts.some((t) => /Leadership Team.*Leadership/.test(t))).toBe(true);
    expect(itemTexts.some((t) => /Support Engineers.*Support/.test(t))).toBe(true);
  });

  it("deduplicates team names when building options", async () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
        ],
      },
      isLeadership: false,
      isSupportEngineer: false,
    });

    renderSelector();
    await openMenu();

    expect(screen.getAllByRole("menuitem")).toHaveLength(1);
  });

  it("renders tenant options from session teams for the current logged-in user", async () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
          { name: "Tenant B", types: ["tenant"], groupRefs: [] },
        ],
      },
      isLeadership: false,
      isSupportEngineer: false,
    });

    renderSelector();
    await openMenu();

    expect(screen.getByTestId("team-selector-trigger")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Tenant A/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Tenant B/ })).toBeInTheDocument();
  });

  it("shows only tenant teams from the logged-in user session", async () => {
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Leadership Team", types: ["leadership"], groupRefs: [] },
          { name: "Support Engineers", types: ["support"], groupRefs: [] },
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: true,
    });

    renderSelector();
    await openMenu();

    expect(screen.getByRole("menuitem", { name: /Tenant A/ })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Tenant X/ })).not.toBeInTheDocument();
  });

  it("resets selected team to first option if current selection is no longer valid", () => {
    const setSelectedTeam = jest.fn();
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: "Old Team",
      setSelectedTeam,
    });
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
          { name: "Leadership Team", types: ["leadership"], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: false,
    });

    renderSelector();

    expect(setSelectedTeam).toHaveBeenCalledWith("Tenant A");
  });

  it("writes the new team into the URL when the user picks an item", async () => {
    const setSelectedTeam = jest.fn();
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: "Tenant A",
      setSelectedTeam,
    });
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
          { name: "Leadership Team", types: ["leadership"], groupRefs: [] },
        ],
      },
      isLeadership: true,
      isSupportEngineer: false,
    });

    renderSelector();
    const user = await openMenu();
    await user.click(screen.getByRole("menuitem", { name: /Leadership Team/ }));

    expect(mockReplace).toHaveBeenCalledWith(expect.stringContaining("team=Leadership+Team"));
    expect(setSelectedTeam).not.toHaveBeenCalledWith("Leadership Team");
  });

  it("initialises selected team from URL ?team param when valid", () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ team: "Tenant B" }));
    const setSelectedTeam = jest.fn();
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: null,
      setSelectedTeam,
    });
    mockUseAuth.mockReturnValue({
      user: {
        teams: [
          { name: "Tenant A", types: ["tenant"], groupRefs: [] },
          { name: "Tenant B", types: ["tenant"], groupRefs: [] },
        ],
      },
      isLeadership: false,
      isSupportEngineer: false,
    });

    renderSelector();

    expect(setSelectedTeam).toHaveBeenCalledWith("Tenant B");
  });

  it("ignores URL ?team param when it is not a valid selection for this user", () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ team: "Other Team" }));
    const setSelectedTeam = jest.fn();
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: null,
      setSelectedTeam,
    });
    mockUseAuth.mockReturnValue({
      user: {
        teams: [{ name: "Tenant A", types: ["tenant"], groupRefs: [] }],
      },
      isLeadership: false,
      isSupportEngineer: false,
    });

    renderSelector();

    expect(setSelectedTeam).toHaveBeenCalledWith("Tenant A");
    expect(setSelectedTeam).not.toHaveBeenCalledWith("Other Team");
    const modal = screen.getByTestId("team-access-denied-modal");
    expect(modal).toBeInTheDocument();
    expect(within(modal).getByText(/Other Team/)).toBeInTheDocument();
    expect(within(modal).getByText(/Tenant A/)).toBeInTheDocument();
  });

  it("rewrites a stale invalid ?team URL param when selectedTeam is already valid", () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ team: "OldTeam" }));
    const setSelectedTeam = jest.fn();
    mockUseTeamFilter.mockReturnValue({
      selectedTeam: "Tenant A",
      setSelectedTeam,
    });
    mockUseAuth.mockReturnValue({
      user: {
        teams: [{ name: "Tenant A", types: ["tenant"], groupRefs: [] }],
      },
      isLeadership: false,
      isSupportEngineer: false,
    });

    renderSelector();

    expect(setSelectedTeam).not.toHaveBeenCalled();
    const modal = screen.getByTestId("team-access-denied-modal");
    expect(modal).toBeInTheDocument();
    expect(within(modal).getByText(/OldTeam/)).toBeInTheDocument();
    expect(within(modal).getByText(/Tenant A/)).toBeInTheDocument();
    expect(mockReplace).toHaveBeenCalledWith(expect.stringContaining("team=Tenant+A"));
    expect(mockReplace).not.toHaveBeenCalledWith(expect.stringContaining("OldTeam"));
  });

  it("shows the team display label while selecting by its code", async () => {
    const setSelectedTeam = jest.fn();
    mockUseTeamFilter.mockReturnValue({ selectedTeam: "pe", setSelectedTeam });
    mockUseAuth.mockReturnValue({
      user: {
        teams: [{ name: "pe", label: "PE Core", types: ["tenant"], groupRefs: [] }],
      },
      isLeadership: false,
      isSupportEngineer: false,
    });

    renderSelector();

    // Trigger shows the friendly label, not the code.
    expect(screen.getByTestId("team-selector-trigger")).toHaveTextContent("PE Core");

    const user = await openMenu();
    expect(screen.getByRole("menuitem", { name: /PE Core/ })).toBeInTheDocument();

    // Selecting it writes the immutable code into the URL.
    await user.click(screen.getByRole("menuitem", { name: /PE Core/ }));
    expect(mockReplace).toHaveBeenCalledWith(expect.stringContaining("team=pe"));
  });
});
