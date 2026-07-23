import { useMockUrlParams as mockUseUrlParams } from "@/test-utils/mock-url-params";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import React from "react";
import * as hooks from "../../../lib/hooks";
import Products, { hasActiveProductTags } from "../products";

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

  it("excludes untagged tickets from rows, Totals, and the percentage denominator", () => {
    renderProducts();

    // Ticket 3 only has the non-product "bug" tag: no None row, and it doesn't
    // count — Totals is 3 (tickets 1, 2, 4) and Alpha's share is 3/3
    expect(screen.queryByText("None")).not.toBeInTheDocument();
    const totalsRow = screen.getByText("Totals").closest("tr")!;
    expect(within(totalsRow).getByText("3")).toBeInTheDocument();
    const alphaRow = screen.getByText("Alpha").closest("tr")!;
    expect(within(alphaRow).getByText("100.0%")).toBeInTheDocument();
  });

  it("does not render a hide-untagged control", () => {
    renderProducts();

    expect(screen.queryByLabelText("Hide tickets without a product tag")).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("shows each product's percentage of product-tagged tickets", () => {
    renderProducts();

    // 3 tickets carry product tags; Alpha appears on all 3, Beta on 1
    const alphaRow = screen.getByText("Alpha").closest("tr")!;
    expect(within(alphaRow).getByText("100.0%")).toBeInTheDocument();

    const betaRow = screen.getByText("Beta").closest("tr")!;
    expect(within(betaRow).getByText("33.3%")).toBeInTheDocument();
  });

  it("renders a Totals row counting distinct product-tagged tickets, always last regardless of sorting", () => {
    renderProducts();

    // Ticket 4 carries two product tags but counts once: 3 tagged tickets, not the column sum of 4
    const totalsRow = screen.getByText("Totals").closest("tr")!;
    expect(within(totalsRow).getByText("3")).toBeInTheDocument();
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

  it("sorts by count descending by default", () => {
    renderProducts();

    const rows = screen.getAllByRole("row").slice(1); // skip header row
    const products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["Alpha", "Beta", "Totals"]);
  });

  it("sorts by product name when the Product header is clicked and flips direction on second click", () => {
    renderProducts();

    fireEvent.click(screen.getByText("Product"));
    let rows = screen.getAllByRole("row").slice(1);
    let products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["Alpha", "Beta", "Totals"]);

    fireEvent.click(screen.getByText("Product"));
    rows = screen.getAllByRole("row").slice(1);
    products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["Beta", "Alpha", "Totals"]);
  });

  it("flips count sort direction when the Tickets header is clicked", () => {
    renderProducts();

    fireEvent.click(screen.getByText("Tickets"));
    const rows = screen.getAllByRole("row").slice(1);
    const products = rows.map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(products).toEqual(["Beta", "Alpha", "Totals"]);
  });

  it("counts tickets tagged with a retired product under its own row", () => {
    mockUseAllTickets.mockReturnValue({
      data: getMockPaginatedTickets([createMockTicket("1", ["product-retired"]), createMockTicket("2", ["product-alpha"])]),
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    renderProducts();

    // Retired products are not seeded, but their tickets still count when in range
    const retiredRow = screen.getByText("Retired").closest("tr")!;
    expect(within(retiredRow).getByText("1")).toBeInTheDocument();
  });

  it("tolerates case and dash variants in product tag labels", () => {
    mockUseRegistry.mockReturnValue({
      data: {
        impacts: [],
        tags: [
          { code: "product-gamma", label: "Product – Gamma" }, // en dash
          { code: "product-delta", label: "product - delta" }, // lowercase
        ],
      },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);
    mockUseAllTickets.mockReturnValue({
      data: getMockPaginatedTickets([createMockTicket("1", ["product-gamma"]), createMockTicket("2", ["product-delta"])]),
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    renderProducts();

    const gammaRow = screen.getByText("Gamma").closest("tr")!;
    expect(within(gammaRow).getByText("1")).toBeInTheDocument();
    const deltaRow = screen.getByText("delta").closest("tr")!;
    expect(within(deltaRow).getByText("1")).toBeInTheDocument();
  });

  it("treats prefix-only labels as non-product tags", () => {
    mockUseRegistry.mockReturnValue({
      data: {
        impacts: [],
        tags: [
          { code: "product-alpha", label: "Product - Alpha" },
          { code: "product-empty", label: "Product -" },
        ],
      },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);
    mockUseAllTickets.mockReturnValue({
      data: getMockPaginatedTickets([createMockTicket("1", ["product-empty"])]),
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof hooks.useAllTickets>);

    renderProducts();

    // The prefix-only tag seeds no blank row; its ticket counts as untagged
    // and is excluded entirely, leaving only the seeded Alpha row at zero
    expect(screen.queryByText("None")).not.toBeInTheDocument();
    const totalsRow = screen.getByText("Totals").closest("tr")!;
    expect(within(totalsRow).getByText("0")).toBeInTheDocument();
  });

  describe("hasActiveProductTags", () => {
    it("ignores prefix-only labels", () => {
      expect(hasActiveProductTags({ tags: [{ code: "p", label: "Product -" }] })).toBe(false);
    });

    it("accepts case and dash variants", () => {
      expect(hasActiveProductTags({ tags: [{ code: "p", label: "product – alpha" }] })).toBe(true);
    });

    it("ignores inactive product tags", () => {
      expect(hasActiveProductTags({ tags: [{ code: "p", label: "Product - Alpha", active: false }] })).toBe(false);
    });
  });

  it("shows the loading skeleton while the registry is still loading", () => {
    mockUseRegistry.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as unknown as ReturnType<typeof hooks.useRegistry>);

    const { container } = renderProducts();

    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
    // No final-looking data while the code→label map is unavailable
    expect(screen.queryByText("Totals")).not.toBeInTheDocument();
  });

  it("shows an error message when the registry fails to load", () => {
    mockUseRegistry.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error("registry down"),
    } as unknown as ReturnType<typeof hooks.useRegistry>);

    renderProducts();

    expect(screen.getByText("Error loading products")).toBeInTheDocument();
    expect(screen.queryByText("Totals")).not.toBeInTheDocument();
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
