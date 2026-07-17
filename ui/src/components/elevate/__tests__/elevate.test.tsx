import { QueryClient, QueryClientProvider, type UseQueryResult } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import * as hooks from "../../../lib/hooks";
import type { ElevateIntegrityIssue, ElevateJourney, ElevatePage, ElevateProduct, ElevateStatus, ElevateUser } from "../../../lib/types";
import { isSyncOverdue } from "../ElevateStatusCards";
import ElevatePageView from "../elevate";

jest.mock("../../../lib/hooks", () => ({
  ...jest.requireActual("../../../lib/hooks"),
  useElevateStatus: jest.fn(),
  useElevateProducts: jest.fn(),
  useElevateProduct: jest.fn(),
  useElevateProductJourneys: jest.fn(),
  useElevateProductUsers: jest.fn(),
  useElevateJourney: jest.fn(),
  useElevateJourneyUsers: jest.fn(),
  useElevateUser: jest.fn(),
  useElevateUserJourneys: jest.fn(),
  useElevateIntegrity: jest.fn(),
}));

const mockUseElevateStatus = hooks.useElevateStatus as jest.MockedFunction<typeof hooks.useElevateStatus>;
const mockUseElevateProducts = hooks.useElevateProducts as jest.MockedFunction<typeof hooks.useElevateProducts>;
const mockUseElevateProduct = hooks.useElevateProduct as jest.MockedFunction<typeof hooks.useElevateProduct>;
const mockUseElevateProductJourneys = hooks.useElevateProductJourneys as jest.MockedFunction<typeof hooks.useElevateProductJourneys>;
const mockUseElevateProductUsers = hooks.useElevateProductUsers as jest.MockedFunction<typeof hooks.useElevateProductUsers>;
const mockUseElevateJourney = hooks.useElevateJourney as jest.MockedFunction<typeof hooks.useElevateJourney>;
const mockUseElevateJourneyUsers = hooks.useElevateJourneyUsers as jest.MockedFunction<typeof hooks.useElevateJourneyUsers>;
const mockUseElevateUser = hooks.useElevateUser as jest.MockedFunction<typeof hooks.useElevateUser>;
const mockUseElevateUserJourneys = hooks.useElevateUserJourneys as jest.MockedFunction<typeof hooks.useElevateUserJourneys>;
const mockUseElevateIntegrity = hooks.useElevateIntegrity as jest.MockedFunction<typeof hooks.useElevateIntegrity>;

const products: ElevateProduct[] = [
  {
    id: "product-1",
    slug: "platform",
    name: "Platform",
    customer: "Core Engineering",
    createdAt: "2026-01-01T08:00:00Z",
    lastUpdatedAt: "2026-07-12T08:00:00Z",
    journeyCount: 2,
    userCount: 2,
    assignmentCount: 2,
  },
  {
    id: "product-2",
    slug: "runtime",
    name: "Runtime",
    customer: "Core Engineering",
    createdAt: "2026-02-01T08:00:00Z",
    lastUpdatedAt: "2026-07-12T09:00:00Z",
    journeyCount: 1,
    userCount: 1,
    assignmentCount: 1,
  },
];

const journeys: ElevateJourney[] = [
  {
    id: "journey-1",
    slug: "first-deploy",
    name: "First deployment",
    productId: "product-1",
    productSlug: "platform",
    userDescription: "A team deploying its first service.",
    primaryProblems: "Finding the correct deployment path",
    createdAt: "2026-01-02T08:00:00Z",
    lastUpdatedAt: "2026-07-11T08:00:00Z",
    userCount: 2,
    missingUserCount: 0,
    crossProductUserCount: 0,
  },
  {
    id: "journey-2",
    slug: "operate-service",
    name: "Operate service",
    productId: "product-1",
    productSlug: "platform",
    userDescription: "An operator keeping a service healthy.",
    primaryProblems: "Understanding production health",
    createdAt: "2026-01-04T08:00:00Z",
    lastUpdatedAt: "2026-07-11T09:00:00Z",
    userCount: 0,
    missingUserCount: 0,
    crossProductUserCount: 0,
  },
  {
    id: "journey-3",
    slug: "incident-response",
    name: "Incident response",
    productId: "product-2",
    productSlug: "runtime",
    userDescription: "A responder restoring a runtime service.",
    primaryProblems: "Finding the source of an incident",
    createdAt: "2026-02-02T08:00:00Z",
    lastUpdatedAt: "2026-07-11T10:00:00Z",
    userCount: 1,
    missingUserCount: 0,
    crossProductUserCount: 0,
  },
];

const users: ElevateUser[] = [
  {
    id: "user-1",
    productId: "product-1",
    name: "Application team",
    description: "Engineers shipping workloads.",
    createdAt: "2026-01-03T08:00:00Z",
    lastUpdatedAt: "2026-07-10T08:00:00Z",
    journeyCount: 1,
  },
  {
    id: "user-2",
    productId: "product-1",
    name: "Platform operator",
    description: "Engineers operating production workloads.",
    createdAt: "2026-01-05T08:00:00Z",
    lastUpdatedAt: "2026-07-10T09:00:00Z",
    journeyCount: 1,
  },
  {
    id: "user-3",
    productId: "product-2",
    name: "Runtime responder",
    description: "Engineers responding to runtime incidents.",
    createdAt: "2026-02-03T08:00:00Z",
    lastUpdatedAt: "2026-07-10T10:00:00Z",
    journeyCount: 1,
  },
];

const connectedStatus: ElevateStatus = {
  configured: true,
  baseUrl: "https://elevate.example.com",
  statusInterval: "PT1H",
  syncInterval: "PT12H",
  lastPingAttemptAt: "2026-07-13T09:00:00Z",
  lastPingSuccessAt: "2026-07-13T09:00:00Z",
  lastPingSucceeded: true,
  lastPingError: null,
  lastSyncAttemptAt: "2026-07-13T08:00:00Z",
  lastSyncSuccessAt: "2026-07-13T08:00:00Z",
  lastSyncSucceeded: true,
  lastSyncError: null,
  snapshotVersion: "11111111-1111-1111-1111-111111111111",
  counts: { products: 2, journeys: 3, users: 3, assignments: 3 },
  integrity: { orphanJourneys: 0, orphanUsers: 0, missingAssignments: 0, crossProductAssignments: 0 },
};

function page<T>(content: T[], pageNumber = 0, totalPages = 1, totalElements = content.length): ElevatePage<T> {
  return { content, page: pageNumber, totalPages, totalElements };
}

function query<T>(data: T | undefined, error: Error | null = null, overrides: Record<string, unknown> = {}) {
  return {
    data,
    error,
    isLoading: false,
    isFetching: false,
    isPlaceholderData: false,
    refetch: jest.fn(),
    ...overrides,
  } as UseQueryResult<T, Error>;
}

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

function renderPage() {
  return render(<ElevatePageView />, { wrapper });
}

function installDefaultMocks() {
  mockUseElevateStatus.mockReturnValue(query(connectedStatus));
  mockUseElevateProducts.mockImplementation((request) => {
    const filtered = products.filter(
      (product) => !request.query || `${product.name} ${product.slug}`.toLowerCase().includes(request.query.toLowerCase())
    );
    return query(page(filtered));
  });
  mockUseElevateProduct.mockImplementation((id) => query(products.find((product) => product.id === id)));
  mockUseElevateProductJourneys.mockImplementation((productId, request) => {
    let filtered = journeys.filter((journey) => journey.productId === productId);
    if (request.query) {
      filtered = filtered.filter((journey) => `${journey.id} ${journey.name}`.toLowerCase().includes(request.query!.toLowerCase()));
    }
    if (request.relationship === "linked") filtered = filtered.filter((journey) => journey.userCount > 0);
    if (request.relationship === "unassigned") filtered = filtered.filter((journey) => journey.userCount === 0);
    return query(page(filtered));
  });
  mockUseElevateProductUsers.mockImplementation((productId, request) => {
    let filtered = users.filter((user) => user.productId === productId);
    if (request.query) {
      filtered = filtered.filter((user) => `${user.id} ${user.name}`.toLowerCase().includes(request.query!.toLowerCase()));
    }
    return query(page(filtered));
  });
  mockUseElevateJourney.mockImplementation((id) => query(journeys.find((journey) => journey.id === id)));
  mockUseElevateUser.mockImplementation((id) => query(users.find((user) => user.id === id)));
  mockUseElevateJourneyUsers.mockImplementation((id) =>
    query(page(id === "journey-1" ? users.filter((user) => user.id === "user-1" || user.id === "user-2") : users.slice(2)))
  );
  mockUseElevateUserJourneys.mockImplementation((id) =>
    query(
      page(
        id === "user-1"
          ? journeys.filter((journey) => journey.id === "journey-1")
          : journeys.filter((journey) => journey.id === "journey-3")
      )
    )
  );
  mockUseElevateIntegrity.mockReturnValue(query(page<ElevateIntegrityIssue>([])));
}

beforeEach(() => {
  jest.clearAllMocks();
  installDefaultMocks();
});

describe("ElevatePage", () => {
  it("follows direct relationships, moves focus to the new detail view, and re-queries the current DOM", async () => {
    const user = userEvent.setup();
    renderPage();

    const firstDeployment = await screen.findByRole("button", { name: "First deployment, 2 product users" });
    await user.click(firstDeployment);
    expect(screen.getByRole("heading", { name: "First deployment" })).toHaveFocus();

    await user.click(screen.getByRole("button", { name: "View Application team, 1 journey" }));

    expect(screen.getByRole("tab", { name: /Product users/ })).toHaveAttribute("data-state", "active");
    expect(await screen.findByRole("heading", { name: "Application team" })).toHaveFocus();
    expect(await screen.findByRole("button", { name: "Application team, 1 journey" })).toHaveAttribute("aria-current", "true");

    await user.click(screen.getByRole("button", { name: "Back to product users" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Product users" })).toHaveFocus());
    await user.click(screen.getByRole("button", { name: "Application team, 1 journey" }));
    await user.click(screen.getByRole("button", { name: "View First deployment, 2 product users" }));

    expect(screen.getByRole("tab", { name: /Journeys/ })).toHaveAttribute("data-state", "active");
    expect(await screen.findByRole("button", { name: "First deployment, 2 product users" })).toHaveAttribute("aria-current", "true");
  });

  it("server-filters the destination list when a related record is beyond its first page", async () => {
    const user = userEvent.setup();
    const firstPageUsers = Array.from({ length: 20 }, (_, index) => ({
      ...users[0],
      id: `page-user-${index + 1}`,
      name: `Page user ${index + 1}`,
    }));
    const relatedUser = {
      ...users[0],
      id: "user-21",
      name: "Twenty-first team",
    };
    mockUseElevateProductUsers.mockImplementation((_productId, request) =>
      request.query === relatedUser.id ? query(page([relatedUser])) : query(page(firstPageUsers, 0, 2, 21))
    );
    mockUseElevateJourneyUsers.mockReturnValue(query(page([relatedUser])));
    mockUseElevateUser.mockImplementation((id) => query(id === relatedUser.id ? relatedUser : users.find((item) => item.id === id)));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "First deployment, 2 product users" }));
    await user.click(screen.getByRole("button", { name: "View Twenty-first team, 1 journey" }));

    await waitFor(() =>
      expect(mockUseElevateProductUsers).toHaveBeenCalledWith(
        "product-1",
        expect.objectContaining({ page: 0, query: relatedUser.id }),
        true
      )
    );
    const destinationList = screen.getByRole("region", { name: "Product users list" });
    const relatedUserButton = await within(destinationList).findByRole("button", { name: "Twenty-first team, 1 journey" });
    expect(relatedUserButton).toHaveAttribute("aria-current", "true");
    expect(within(destinationList).queryByRole("button", { name: "Page user 1, 1 journey" })).not.toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "Search product users" })).toHaveValue(relatedUser.id);

    await user.click(screen.getByRole("button", { name: "Back to product users" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Product users" })).toHaveFocus());
    expect(relatedUserButton).toBeVisible();
  });

  it("uses compact list-detail navigation and restores focus when returning to the list", async () => {
    const user = userEvent.setup();
    renderPage();

    const list = await screen.findByRole("region", { name: "Journeys list" });
    const details = screen.getByRole("region", { name: "Selected relationship details" });
    expect(list).toHaveClass("block");
    expect(details).toHaveClass("hidden");

    await user.click(screen.getByRole("button", { name: "First deployment, 2 product users" }));
    expect(list).toHaveClass("hidden");
    expect(details).toHaveClass("block");

    await user.click(screen.getByRole("button", { name: "Back to journeys" }));
    expect(list).toHaveClass("block");
    await waitFor(() => expect(screen.getByRole("button", { name: "First deployment, 2 product users" })).toHaveFocus());
  });

  it("searches the server-backed product picker and switches products", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("combobox", { name: "Product" }));
    await user.type(screen.getByPlaceholderText("Search products…"), "runtime");
    await waitFor(() => expect(mockUseElevateProducts).toHaveBeenCalledWith(expect.objectContaining({ query: "runtime" }), true));
    await user.click(await screen.findByRole("option", { name: /Runtime/ }));

    expect(await screen.findByRole("heading", { name: "Runtime" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Incident response, 1 product user" })).toHaveAttribute("aria-current", "true");

    await user.click(screen.getByRole("combobox", { name: "Product" }));
    expect(screen.getByPlaceholderText("Search products…")).toHaveValue("");
    expect(mockUseElevateProducts).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, query: "" }), true);
  });

  it("hides and disables stale product-picker results while the replacement page loads", async () => {
    const user = userEvent.setup();
    mockUseElevateProducts.mockImplementation((_request, enabled) =>
      enabled === true ? query(page(products, 0, 2, 40), null, { isFetching: true, isPlaceholderData: true }) : query(page(products))
    );
    renderPage();

    await user.click(await screen.findByRole("combobox", { name: "Product" }));

    expect(screen.getByText("Loading products")).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Runtime/ })).not.toBeInTheDocument();
    const picker = screen.getByRole("listbox").closest('[data-slot="popover-content"]') as HTMLElement;
    expect(within(picker).getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(within(picker).getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("normalizes the selected product when the snapshot version changes", async () => {
    const user = userEvent.setup();
    const { rerender } = renderPage();

    await user.click(await screen.findByRole("combobox", { name: "Product" }));
    await user.click(screen.getByRole("option", { name: /Runtime/ }));
    expect(await screen.findByRole("heading", { name: "Runtime" })).toBeInTheDocument();

    const nextStatus = {
      ...connectedStatus,
      snapshotVersion: "22222222-2222-2222-2222-222222222222",
      counts: { ...connectedStatus.counts, products: 1 },
    };
    mockUseElevateStatus.mockReturnValue(query(nextStatus));
    mockUseElevateProducts.mockReturnValue(query(page(products.slice(0, 1))));
    rerender(<ElevatePageView />);

    expect(await screen.findByRole("heading", { name: "Platform" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Runtime" })).not.toBeInTheDocument();
  });

  it("delegates search, filtering, sorting, and pagination to the paged API hook", async () => {
    const user = userEvent.setup();
    const extraJourneys = Array.from({ length: 20 }, (_, index) => ({
      ...journeys[1],
      id: `journey-extra-${index}`,
      name: `Journey ${index}`,
    }));
    mockUseElevateProductJourneys.mockImplementation((_productId, request) => {
      const all = [...journeys.slice(0, 2), ...extraJourneys];
      const pageContent = request.page === 1 ? all.slice(20) : all.slice(0, 20);
      return query(page(pageContent, request.page ?? 0, 2, all.length));
    });
    renderPage();

    const list = await screen.findByRole("region", { name: "Journeys list" });
    await user.click(within(list).getByRole("button", { name: "Next" }));
    expect(mockUseElevateProductJourneys).toHaveBeenLastCalledWith("product-1", expect.objectContaining({ page: 1 }), true);

    await user.type(screen.getByRole("searchbox", { name: "Search journeys" }), "operate");
    await waitFor(() =>
      expect(mockUseElevateProductJourneys).toHaveBeenCalledWith("product-1", expect.objectContaining({ page: 0, query: "operate" }), true)
    );

    const primaryControls = screen.getByRole("tablist", { name: "Relationship type" }).parentElement!;
    await user.click(within(primaryControls).getByRole("button", { name: /^Relationship/ }));
    await user.click(screen.getByRole("option", { name: "No valid links" }));
    expect(mockUseElevateProductJourneys).toHaveBeenLastCalledWith(
      "product-1",
      expect.objectContaining({ relationship: "unassigned" }),
      true
    );
  });

  it("does not expose stale list records while a replacement page is loading", async () => {
    mockUseElevateProductJourneys.mockReturnValue(
      query(page(journeys.slice(0, 2), 0, 2, 22), null, { isFetching: true, isPlaceholderData: true })
    );
    renderPage();

    const list = await screen.findByRole("region", { name: "Journeys list" });
    expect(list).toHaveAttribute("aria-busy", "true");
    expect(within(list).getByText("Updating records…")).toBeInTheDocument();
    expect(within(list).queryByRole("button", { name: "First deployment, 2 product users" })).not.toBeInTheDocument();
    expect(within(list).getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(within(list).getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("delegates direct-relationship search, sorting, and pagination to the API hook", async () => {
    const user = userEvent.setup();
    mockUseElevateJourneyUsers.mockImplementation((_journeyId, request) => query(page(users.slice(0, 1), request.page ?? 0, 2, 21)));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "First deployment, 2 product users" }));
    const details = screen.getByRole("region", { name: "Selected relationship details" });
    await user.type(within(details).getByRole("searchbox", { name: "Search related product users" }), "application");
    await waitFor(() =>
      expect(mockUseElevateJourneyUsers).toHaveBeenCalledWith("journey-1", expect.objectContaining({ query: "application" }), true)
    );

    await user.click(within(details).getByRole("button", { name: /^Sort/ }));
    await user.click(screen.getByRole("option", { name: "Most linked" }));
    expect(mockUseElevateJourneyUsers).toHaveBeenLastCalledWith(
      "journey-1",
      expect.objectContaining({ sort: "relationships", direction: "desc" }),
      true
    );

    await user.click(within(details).getByRole("button", { name: "Next" }));
    expect(mockUseElevateJourneyUsers).toHaveBeenLastCalledWith("journey-1", expect.objectContaining({ page: 1 }), true);
  });

  it("does not expose stale direct relationships or entity details during cross-navigation", async () => {
    const user = userEvent.setup();
    mockUseElevateUser.mockImplementation((id) =>
      id === "user-1" ? query(users[0], null, { isFetching: true, isPlaceholderData: true }) : query(users.find((item) => item.id === id))
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "First deployment, 2 product users" }));
    await user.click(screen.getByRole("button", { name: "View Application team, 1 journey" }));

    expect(screen.getByText("Loading record details…")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Application team" })).not.toBeInTheDocument();
  });

  it("does not expose stale direct-relationship rows while a replacement page is loading", async () => {
    const user = userEvent.setup();
    mockUseElevateJourneyUsers.mockReturnValue(
      query(page(users.slice(0, 2), 0, 2, 22), null, { isFetching: true, isPlaceholderData: true })
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "First deployment, 2 product users" }));
    const details = screen.getByRole("region", { name: "Selected relationship details" });
    expect(within(details).getByText("Updating direct relationships…")).toBeInTheDocument();
    expect(within(details).queryByRole("button", { name: "View Application team, 1 journey" })).not.toBeInTheDocument();
  });

  it("keeps cached snapshot records visible when background refreshes fail", async () => {
    const refreshError = new Error("background refresh failed");
    mockUseElevateProducts.mockReturnValue(query(page(products), refreshError));
    mockUseElevateProduct.mockReturnValue(query(products[0], refreshError));
    mockUseElevateProductJourneys.mockReturnValue(query(page(journeys.slice(0, 2)), refreshError));
    mockUseElevateJourney.mockReturnValue(query(journeys[0], refreshError));
    mockUseElevateJourneyUsers.mockReturnValue(query(page(users.slice(0, 2)), refreshError));

    renderPage();

    expect(await screen.findByRole("heading", { name: "Platform" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "First deployment, 2 product users" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View Application team, 1 journey" })).toBeInTheDocument();
    expect(screen.queryByText(/Unable to load/)).not.toBeInTheDocument();
  });

  it("shows a cached empty product snapshot when its background refresh fails", () => {
    mockUseElevateStatus.mockReturnValue(query({ ...connectedStatus, counts: { ...connectedStatus.counts, products: 0 } }));
    mockUseElevateProducts.mockReturnValue(query(page<ElevateProduct>([]), new Error("background refresh failed")));

    renderPage();

    expect(screen.getByText("No products synced")).toBeInTheDocument();
    expect(screen.queryByText("Unable to load synced products.")).not.toBeInTheDocument();
  });

  it("announces a child-query error when there is no data to preserve", async () => {
    mockUseElevateProductJourneys.mockReturnValue(query(undefined, new Error("initial request failed")));

    renderPage();

    expect(await screen.findByRole("alert", { name: "" })).toHaveTextContent("Unable to load records.");
    expect(screen.queryByRole("button", { name: "First deployment, 2 product users" })).not.toBeInTheDocument();
  });

  it("loads unmatched records in a paginated, searchable disclosure", async () => {
    const user = userEvent.setup();
    mockUseElevateStatus.mockReturnValue(
      query({ ...connectedStatus, integrity: { ...connectedStatus.integrity, missingAssignments: 21 } })
    );
    mockUseElevateIntegrity.mockImplementation((request) =>
      query(
        page(
          [
            {
              type: "missingAssignment",
              journeyId: "journey-1",
              journeyName: "First deployment",
              journeyProductId: "product-1",
              userId: "missing-user",
            },
          ],
          request.page ?? 0,
          2,
          21
        )
      )
    );
    renderPage();

    expect(screen.getByText(/Apparent snapshot inconsistencies are retried automatically/)).toHaveTextContent(
      "21 records cannot be linked cleanly. Apparent snapshot inconsistencies are retried automatically. Invalid journey-to-product-user links can be reviewed in Elevate."
    );
    await user.click(screen.getByText("Review unmatched records"));
    expect(await screen.findByText("Assignment to a missing product user")).toBeInTheDocument();
    const region = screen.getByRole("region", { name: "Unmatched synced records" });
    await user.click(within(region).getByRole("button", { name: "Next" }));
    expect(mockUseElevateIntegrity).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }), true);
  });

  it("renders both sides and product contexts of a cross-product assignment", async () => {
    const user = userEvent.setup();
    mockUseElevateStatus.mockReturnValue(
      query({ ...connectedStatus, integrity: { ...connectedStatus.integrity, crossProductAssignments: 1 } })
    );
    mockUseElevateIntegrity.mockReturnValue(
      query(
        page([
          {
            type: "crossProductAssignment",
            journeyId: "journey-1",
            journeyName: "First deployment",
            journeyProductId: "platform-product",
            userId: "11111111-1111-1111-1111-111111111111",
            userName: "Runtime responder",
            userProductId: "runtime-product",
          },
        ]),
        new Error("background refresh failed")
      )
    );
    renderPage();

    await user.click(screen.getByText("Review unmatched records"));
    const integrityRegion = screen.getByRole("region", { name: "Unmatched synced records" });

    expect(within(integrityRegion).getByText("First deployment")).toBeInTheDocument();
    expect(within(integrityRegion).getByText("Runtime responder")).toBeInTheDocument();
    expect(
      within(integrityRegion).getByText("journey product platform-product · product user product runtime-product")
    ).toBeInTheDocument();
    expect(within(integrityRegion).queryByText("Unable to load unmatched records.")).not.toBeInTheDocument();
  });

  it("does not expose stale integrity records or pagination while replacements load", async () => {
    const user = userEvent.setup();
    mockUseElevateStatus.mockReturnValue(
      query({ ...connectedStatus, integrity: { ...connectedStatus.integrity, missingAssignments: 21 } })
    );
    mockUseElevateIntegrity.mockReturnValue(
      query(
        page(
          [
            {
              type: "missingAssignment",
              journeyId: "journey-1",
              journeyName: "First deployment",
              userId: "missing-user",
            },
          ],
          0,
          2,
          21
        ),
        null,
        { isFetching: true, isPlaceholderData: true }
      )
    );
    renderPage();

    await user.click(screen.getByText("Review unmatched records"));
    const region = screen.getByRole("region", { name: "Unmatched synced records" });
    expect(region).toHaveAttribute("aria-busy", "true");
    expect(within(region).getByText("Updating unmatched records…")).toBeInTheDocument();
    expect(within(region).queryByText("Assignment to a missing product user")).not.toBeInTheDocument();
    expect(within(region).getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(within(region).getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("refreshes status and resets snapshot queries after a 409", async () => {
    const refetch = jest.fn().mockResolvedValue({ data: connectedStatus });
    mockUseElevateStatus.mockReturnValue(query(connectedStatus, null, { refetch }));
    mockUseElevateProducts.mockReturnValue(query(undefined, new hooks.ApiError(409, "SNAPSHOT_CHANGED")));
    renderPage();

    await waitFor(() => expect(refetch).toHaveBeenCalled());
  });

  it("keeps the diagnostic page useful without a stored snapshot", () => {
    mockUseElevateStatus.mockReturnValue(
      query({
        ...connectedStatus,
        configured: false,
        baseUrl: null,
        snapshotVersion: null,
        counts: { products: 0, journeys: 0, users: 0, assignments: 0 },
      })
    );
    renderPage();

    expect(screen.getAllByText("Not configured")).toHaveLength(2);
    expect(screen.getByText("No snapshot available")).toBeInTheDocument();
    expect(screen.getByText("Not set")).toBeInTheDocument();
  });

  it("keeps the last successful sync visible and warns only after the sync interval is exceeded", () => {
    const lastSuccess = connectedStatus.lastSyncSuccessAt!;
    const intervalMilliseconds = 12 * 60 * 60 * 1000;
    const now = jest.spyOn(Date, "now").mockReturnValue(Date.parse(lastSuccess) + intervalMilliseconds);

    const { rerender } = renderPage();
    const lastSuccessfulTime = document.querySelector(`time[datetime="${lastSuccess}"]`);
    expect(lastSuccessfulTime).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: "Last successful sync is overdue" })).not.toBeInTheDocument();

    now.mockReturnValue(Date.parse(lastSuccess) + intervalMilliseconds + 1);
    rerender(<ElevatePageView />);

    expect(lastSuccessfulTime).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Last successful sync is overdue" })).toBeInTheDocument();
    now.mockRestore();
  });

  it("renders loading, initial error, and stale-data error states", () => {
    mockUseElevateStatus.mockReturnValue(query(undefined, null, { isLoading: true, isFetching: true }));
    const { rerender } = renderPage();
    expect(screen.getByLabelText("Loading Elevate connection data")).toHaveAttribute("aria-busy", "true");

    const refetch = jest.fn();
    mockUseElevateStatus.mockReturnValue(query(undefined, new Error("failed"), { refetch }));
    rerender(<ElevatePageView />);
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(refetch).toHaveBeenCalledTimes(1);

    mockUseElevateStatus.mockReturnValue(query(connectedStatus, new Error("refresh failed")));
    rerender(<ElevatePageView />);
    expect(screen.getByText("Could not refresh Elevate status. Showing the most recently loaded local data.")).toBeInTheDocument();
    expect(screen.getByText("Connected")).toBeInTheDocument();
  });
});

describe("isSyncOverdue", () => {
  it("fails closed for missing timestamps and unsupported durations", () => {
    expect(isSyncOverdue(null, "PT12H", Date.now())).toBe(false);
    expect(isSyncOverdue("2026-07-13T08:00:00Z", "tomorrow", Date.now())).toBe(false);
  });
});
