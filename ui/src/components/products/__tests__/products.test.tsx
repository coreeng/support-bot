import { useMockUrlParams as mockUseUrlParams } from "@/test-utils/mock-url-params";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import React from "react";
import * as hooks from "../../../lib/hooks";
import Products from "../products";

// Mock recharts to avoid rendering errors in tests
jest.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  BarChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Bar: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  Cell: () => null,
}));

// Mock the hooks
jest.mock("../../../lib/hooks");

// Mock useUrlParams with a useState-based implementation so filter changes
// re-render the component correctly.
jest.mock("../../../lib/hooks/useUrlParams", () => ({
  ...jest.requireActual("../../../lib/hooks/useUrlParams"),
  useUrlParams: mockUseUrlParams,
}));

const mockUseAllTickets = hooks.useAllTickets as jest.MockedFunction<typeof hooks.useAllTickets>;
const mockUseRegistry = hooks.useRegistry as jest.MockedFunction<typeof hooks.useRegistry>;

// Mock team filter context
jest.mock("../../../contexts/TeamFilterContext", () => ({
  TeamFilterProvider: ({ children }: { children: React.ReactNode }) => children,
  useTeamFilter: jest.fn(),
}));
const mockUseTeamFilter = jest.requireMock("../../../contexts/TeamFilterContext").useTeamFilter as jest.MockedFunction<
  () => {
    effectiveTeams: string[];
    hasNoTeamScope: boolean;
  }
>;

const mockRegistry = {
  impacts: [],
  tags: [
    { code: "product-alpha", label: "Product - Alpha" },
    { code: "product-beta", label: "Product - Beta" },
    { code: "product-retired", label: "Product - Retired", active: false },
    { code: "bug", label: "Bug" },
  ],
};

const createMockTicket = (id: string, tags: string[]): any => ({
  id,
  status: "opened",
  team: { name: "Team A" },
  tags,
  escalations: [],
  logs: [{ event: "opened", date: new Date().toISOString() }],
});

const getMockPaginatedTickets = (tickets: ReturnType<typeof createMockTicket>[]) => ({
  content: tickets,
  page: 0,
  totalPages: 1,
  totalElements: tickets.length,
});

const Wrapper = ({ children }: { children: React.ReactNode }) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
};

const renderProducts = () => render(<Products />, { wrapper: Wrapper });

describe("Products Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();

    mockUseTeamFilter.mockReturnValue({
      effectiveTeams: [],
      hasNoTeamScope: false,
    });

    mockUseRegistry.mockReturnValue({
      data: mockRegistry,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);

    mockUseAllTickets.mockReturnValue({
      data: getMockPaginatedTickets([
        createMockTicket("1", ["product-alpha", "bug"]),
        createMockTicket("2", ["product-alpha"]),
        createMockTicket("3", ["bug"]),
        createMockTicket("4", ["product-alpha", "product-beta"]),
      ]),
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);
  });

  it("renders the tab description", () => {
    renderProducts();

    expect(screen.getByText("Ticket counts per product tag")).toBeInTheDocument();
  });

  it("counts tickets per product tag with the prefix stripped", () => {
    renderProducts();

    const alphaRow = screen.getByText("Alpha").closest("tr")!;
    expect(within(alphaRow).getByText("3")).toBeInTheDocument();

    const betaRow = screen.getByText("Beta").closest("tr")!;
    expect(within(betaRow).getByText("1")).toBeInTheDocument();

    // Prefix is removed in the table
    expect(screen.queryByText("Product - Alpha")).not.toBeInTheDocument();
  });

  it("counts tickets without a product tag under None", () => {
    renderProducts();

    // Ticket 3 only has the non-product "bug" tag
    const noneRow = screen.getByText("None").closest("tr")!;
    expect(within(noneRow).getByText("1")).toBeInTheDocument();
  });

  it("hides untagged tickets from the table and totals when the checkbox is checked", () => {
    renderProducts();

    fireEvent.click(screen.getByLabelText("Hide tickets without a product tag"));

    expect(screen.queryByText("None")).not.toBeInTheDocument();
    // Ticket 3 (untagged) is excluded entirely: 3 tickets remain and percentages rebase
    const totalsRow = screen.getByText("Totals").closest("tr")!;
    expect(within(totalsRow).getByText("3")).toBeInTheDocument();
    const alphaRow = screen.getByText("Alpha").closest("tr")!;
    expect(within(alphaRow).getByText("100.0%")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Hide tickets without a product tag"));
    expect(screen.getByText("None")).toBeInTheDocument();
  });

  it("shows each product's percentage of all tickets", () => {
    renderProducts();

    // Alpha appears on 3 of the 4 tickets
    const alphaRow = screen.getByText("Alpha").closest("tr")!;
    expect(within(alphaRow).getByText("75.0%")).toBeInTheDocument();

    const betaRow = screen.getByText("Beta").closest("tr")!;
    expect(within(betaRow).getByText("25.0%")).toBeInTheDocument();
  });

  it("renders a Totals row counting distinct tickets, always last regardless of sorting", () => {
    renderProducts();

    // Ticket 4 carries two product tags but counts once: 4 tickets, not the column sum of 5
    const totalsRow = screen.getByText("Totals").closest("tr")!;
    expect(within(totalsRow).getByText("4")).toBeInTheDocument();
    expect(within(totalsRow).getByText("100.0%")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Product"));
    fireEvent.click(screen.getByText("Product"));
    const rows = screen.getAllByRole("row");
    expect(within(rows[rows.length - 1]).getByText("Totals")).toBeInTheDocument();
  });

  it("does not list non-product tags", () => {
    renderProducts();

    expect(screen.queryByText("Bug")).not.toBeInTheDocument();
  });

  it("shows zero counts for active product tags with no tickets in range", () => {
    mockUseAllTickets.mockReturnValue({
      data: getMockPaginatedTickets([createMockTicket("1", ["bug"])]),
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    renderProducts();

    const alphaRow = screen.getByText("Alpha").closest("tr")!;
    expect(within(alphaRow).getByText("0")).toBeInTheDocument();
    // Retired product tags are not seeded
    expect(screen.queryByText("Retired")).not.toBeInTheDocument();
  });

  it("scopes counts to the effective teams", () => {
    mockUseTeamFilter.mockReturnValue({
      effectiveTeams: ["Team B"],
      hasNoTeamScope: false,
    });

    renderProducts();

    // All mock tickets belong to Team A, so Alpha keeps its seeded zero count
    const alphaRow = screen.getByText("Alpha").closest("tr")!;
    expect(within(alphaRow).getByText("0")).toBeInTheDocument();
  });

  it("shows the no-team-access warning when the user has no team scope", () => {
    mockUseTeamFilter.mockReturnValue({
      effectiveTeams: [],
      hasNoTeamScope: true,
    });

    renderProducts();

    expect(screen.getByText("No Team Access")).toBeInTheDocument();
  });

  it("shows an operator message instead of the chart and table when no product tags are configured", () => {
    mockUseRegistry.mockReturnValue({
      data: { impacts: [], tags: [{ code: "bug", label: "Bug" }] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);
    mockUseAllTickets.mockReturnValue({
      data: getMockPaginatedTickets([createMockTicket("1", ["bug"])]),
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    renderProducts();

    expect(screen.getByText("No product tags configured yet")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Hide tickets without a product tag")).not.toBeInTheDocument();
  });

  it("treats a registry with only inactive product tags as not configured", () => {
    mockUseRegistry.mockReturnValue({
      data: { impacts: [], tags: [{ code: "product-retired", label: "Product - Retired", active: false }] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);

    renderProducts();

    expect(screen.getByText("No product tags configured yet")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("sorts by count descending by default", () => {
    renderProducts();

    const rows = screen.getAllByRole("row").slice(1); // skip header row
    const products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["Alpha", "Beta", "None", "Totals"]);
  });

  it("sorts by product name when the Product header is clicked and flips direction on second click", () => {
    renderProducts();

    fireEvent.click(screen.getByText("Product"));
    let rows = screen.getAllByRole("row").slice(1);
    let products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["Alpha", "Beta", "None", "Totals"]);

    fireEvent.click(screen.getByText("Product"));
    rows = screen.getAllByRole("row").slice(1);
    products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["None", "Beta", "Alpha", "Totals"]);
  });

  it("flips count sort direction when the Tickets header is clicked", () => {
    renderProducts();

    fireEvent.click(screen.getByText("Tickets"));
    const rows = screen.getAllByRole("row").slice(1);
    const products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["Beta", "None", "Alpha", "Totals"]);
  });

  it("shows an error message when tickets fail to load", () => {
    mockUseAllTickets.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error("boom"),
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    renderProducts();

    expect(screen.getByText("Error loading products")).toBeInTheDocument();
  });
});
