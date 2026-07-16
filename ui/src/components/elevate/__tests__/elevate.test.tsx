import type { ElevateStatus } from "@/lib/types";
import type { UseQueryResult } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as hooks from "../../../lib/hooks";
import ElevatePage from "../elevate";

jest.mock("../../../lib/hooks");

const mockUseElevateStatus = hooks.useElevateStatus as jest.MockedFunction<typeof hooks.useElevateStatus>;

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
  products: [
    {
      id: "product-1",
      slug: "platform",
      name: "Platform",
      customer: "Core Engineering",
      createdAt: "2026-01-01T08:00:00Z",
      lastUpdatedAt: "2026-07-12T08:00:00Z",
    },
    {
      id: "product-2",
      slug: "runtime",
      name: "Runtime",
      customer: "Core Engineering",
      createdAt: "2026-02-01T08:00:00Z",
      lastUpdatedAt: "2026-07-12T09:00:00Z",
    },
  ],
  journeys: [
    {
      id: "journey-1",
      slug: "first-deploy",
      name: "First deployment",
      productId: "product-1",
      productSlug: "platform",
      userDescription: "A team deploying its first service.",
      primaryProblems: "Finding the correct deployment path",
      userIds: ["user-1", "user-2"],
      createdAt: "2026-01-02T08:00:00Z",
      lastUpdatedAt: "2026-07-11T08:00:00Z",
    },
    {
      id: "journey-2",
      slug: "operate-service",
      name: "Operate service",
      productId: "product-1",
      productSlug: "platform",
      userDescription: "An operator keeping a service healthy.",
      primaryProblems: "Understanding production health",
      userIds: ["user-2"],
      createdAt: "2026-01-04T08:00:00Z",
      lastUpdatedAt: "2026-07-11T09:00:00Z",
    },
    {
      id: "journey-3",
      slug: "incident-response",
      name: "Incident response",
      productId: "product-2",
      productSlug: "runtime",
      userDescription: "A responder restoring a runtime service.",
      primaryProblems: "Finding the source of an incident",
      userIds: ["user-3"],
      createdAt: "2026-02-02T08:00:00Z",
      lastUpdatedAt: "2026-07-11T10:00:00Z",
    },
  ],
  users: [
    {
      id: "user-1",
      productId: "product-1",
      name: "Application team",
      description: "Engineers shipping workloads.",
      createdAt: "2026-01-03T08:00:00Z",
      lastUpdatedAt: "2026-07-10T08:00:00Z",
    },
    {
      id: "user-2",
      productId: "product-1",
      name: "Platform operator",
      description: "Engineers operating production workloads.",
      createdAt: "2026-01-05T08:00:00Z",
      lastUpdatedAt: "2026-07-10T09:00:00Z",
    },
    {
      id: "user-3",
      productId: "product-2",
      name: "Runtime responder",
      description: "Engineers responding to runtime incidents.",
      createdAt: "2026-02-03T08:00:00Z",
      lastUpdatedAt: "2026-07-10T10:00:00Z",
    },
  ],
};

function mockQuery(overrides: Partial<UseQueryResult<ElevateStatus, Error>>) {
  mockUseElevateStatus.mockReturnValue({
    data: undefined,
    error: null,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
    ...overrides,
  } as UseQueryResult<ElevateStatus, Error>);
}

describe("ElevatePage", () => {
  it("shows direct relationships and follows them in either direction", async () => {
    const user = userEvent.setup();
    mockQuery({ data: connectedStatus });
    render(<ElevatePage />);

    expect(screen.getByText("Connected")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Synced relationships" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Platform" })).toBeInTheDocument();
    expect(screen.getByText("1h")).toBeInTheDocument();

    const firstDeployment = screen.getByRole("button", { name: "First deployment, 2 product users" });
    expect(firstDeployment).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("heading", { name: "Direct relationships" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "View Application team, 1 journey" }));

    expect(screen.getByRole("tab", { name: /Product users/ })).toHaveAttribute("data-state", "active");
    expect(screen.getByRole("button", { name: "Application team, 1 journey" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByText("Engineers shipping workloads.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "View First deployment, 2 product users" }));

    expect(screen.getByRole("tab", { name: /Journeys/ })).toHaveAttribute("data-state", "active");
    expect(firstDeployment).toHaveAttribute("aria-current", "true");
  });

  it("searches and switches products without scanning a long rail", async () => {
    const user = userEvent.setup();
    mockQuery({ data: connectedStatus });
    render(<ElevatePage />);

    await user.click(screen.getByRole("combobox", { name: "Product" }));
    await user.type(screen.getByPlaceholderText("Search products…"), "runtime");
    await user.click(screen.getByRole("option", { name: /Runtime/ }));

    expect(screen.getByRole("heading", { name: "Runtime" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Incident response, 1 product user" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("button", { name: "View Runtime responder, 1 journey" })).toBeInTheDocument();
  });

  it("searches records and filters by whether they have relationships", async () => {
    const user = userEvent.setup();
    mockQuery({ data: connectedStatus });
    render(<ElevatePage />);

    await user.type(screen.getByRole("searchbox", { name: "Search journeys" }), "operate");
    expect(screen.getByRole("button", { name: "Operate service, 1 product user" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "First deployment, 2 product users" })).not.toBeInTheDocument();
    expect(screen.getByText("1–1 of 1 · Page 1 of 1")).toBeInTheDocument();

    await user.clear(screen.getByRole("searchbox", { name: "Search journeys" }));
    await user.click(screen.getByRole("combobox", { name: "Relationship filter" }));
    await user.click(screen.getByRole("option", { name: "Unassigned" }));

    expect(screen.getByText("No journeys found")).toBeInTheDocument();
    expect(screen.getByText("Try changing the search or relationship filter.")).toBeInTheDocument();
  });

  it("paginates large journey sets and resets to the first page when searching", async () => {
    const user = userEvent.setup();
    const additionalJourneys = Array.from({ length: 21 }, (_, index) => ({
      ...connectedStatus.journeys[0],
      id: `journey-extra-${index + 1}`,
      slug: `journey-extra-${index + 1}`,
      name: `Journey ${String(index + 1).padStart(2, "0")}`,
      userIds: [],
    }));
    mockQuery({ data: { ...connectedStatus, journeys: [...connectedStatus.journeys, ...additionalJourneys] } });
    render(<ElevatePage />);

    const previous = screen.getByRole("button", { name: "Previous journey page" });
    const next = screen.getByRole("button", { name: "Next journey page" });
    expect(screen.getByText("1–20 of 23 · Page 1 of 2")).toBeInTheDocument();
    expect(previous).toBeDisabled();
    expect(next).toBeEnabled();

    await user.click(next);
    expect(screen.getByText("21–23 of 23 · Page 2 of 2")).toBeInTheDocument();
    expect(previous).toBeEnabled();
    expect(next).toBeDisabled();

    await user.type(screen.getByRole("searchbox", { name: "Search journeys" }), "Journey 21");
    expect(screen.getByText("1–1 of 1 · Page 1 of 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Journey 21, 0 product users" })).toHaveAttribute("aria-current", "true");
  });

  it("identifies unmatched records and cross-product assignments", async () => {
    const user = userEvent.setup();
    mockQuery({
      data: {
        ...connectedStatus,
        journeys: [
          { ...connectedStatus.journeys[0], userIds: ["user-1", "missing-user", "user-3"] },
          ...connectedStatus.journeys.slice(1),
          {
            ...connectedStatus.journeys[0],
            id: "orphan-journey",
            name: "Orphan journey",
            productId: "missing-product",
            userIds: [],
          },
        ],
        users: [
          ...connectedStatus.users,
          {
            ...connectedStatus.users[0],
            id: "orphan-user",
            name: "Orphan product user",
            productId: "missing-product",
          },
        ],
      },
    });
    render(<ElevatePage />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Unmatched synced data");
    expect(alert).toHaveTextContent("1 journey without a synced product");
    expect(alert).toHaveTextContent("1 product user without a synced product");
    expect(alert).toHaveTextContent("1 assignment to a missing product user");
    expect(alert).toHaveTextContent("1 cross-product assignment");

    const review = within(alert).getByText("Review unmatched records");
    await user.click(review);
    expect(review.closest("details")).toHaveAttribute("open");
    expect(within(alert).getByRole("region", { name: "Unmatched synced records" })).toHaveAttribute("tabindex", "0");

    expect(within(alert).getByRole("heading", { name: "Journeys without products" })).toBeInTheDocument();
    expect(within(alert).getByText("Orphan journey")).toBeInTheDocument();
    expect(within(alert).getByText("ID orphan-journey · product missing-product")).toBeInTheDocument();
    expect(within(alert).getByRole("heading", { name: "Product users without products" })).toBeInTheDocument();
    expect(within(alert).getByText("Orphan product user")).toBeInTheDocument();
    expect(within(alert).getByText("ID orphan-user · product missing-product")).toBeInTheDocument();
    expect(within(alert).getByRole("heading", { name: "Missing product users" })).toBeInTheDocument();
    expect(within(alert).getByText("Missing user missing-user")).toBeInTheDocument();
    expect(within(alert).getAllByText("ID journey-1 · product product-1")).toHaveLength(2);
    expect(within(alert).getByRole("heading", { name: "Product users assigned across products" })).toBeInTheDocument();
    expect(within(alert).getByText("Runtime responder (user-3; product product-2)")).toBeInTheDocument();
  });

  it("keeps the diagnostic page useful when Elevate is not configured", () => {
    mockQuery({
      data: {
        ...connectedStatus,
        configured: false,
        baseUrl: null,
        lastPingAttemptAt: null,
        lastPingSuccessAt: null,
        lastPingSucceeded: null,
        lastSyncAttemptAt: null,
        lastSyncSuccessAt: null,
        lastSyncSucceeded: null,
        products: [],
        journeys: [],
        users: [],
      },
    });
    render(<ElevatePage />);

    expect(screen.getAllByText("Not configured")).toHaveLength(2);
    expect(screen.getByText("No products synced")).toBeInTheDocument();
    expect(screen.getByText("Not set")).toBeInTheDocument();
  });

  it("shows the latest failure without hiding the last good snapshot", () => {
    mockQuery({
      data: {
        ...connectedStatus,
        lastPingSucceeded: false,
        lastPingError: "Elevate status request failed (401)",
        lastSyncSucceeded: false,
        lastSyncError: "Elevate products request failed (502)",
      },
    });
    render(<ElevatePage />);

    expect(screen.getAllByText("Failed")).toHaveLength(2);
    expect(screen.getByText("Elevate status request failed (401)")).toBeInTheDocument();
    expect(screen.getByText("Elevate products request failed (502)")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Platform" })).toBeInTheDocument();
  });

  it("renders a loading state", () => {
    mockQuery({ isLoading: true, isFetching: true });
    render(<ElevatePage />);

    expect(screen.getByLabelText("Loading Elevate connection data")).toHaveAttribute("aria-busy", "true");
  });

  it("offers a retry when the local status request fails", () => {
    const refetch = jest.fn();
    mockQuery({ error: new Error("failed"), refetch });
    render(<ElevatePage />);

    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it("keeps stale data visible when a background refresh fails", () => {
    mockQuery({ data: connectedStatus, error: new Error("refresh failed") });
    render(<ElevatePage />);

    expect(screen.getByText("Could not refresh Elevate status. Showing the most recently loaded local data.")).toBeInTheDocument();
    expect(screen.getByText("Connected")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Platform" })).toBeInTheDocument();
    expect(screen.queryByText("Unable to load Elevate status")).not.toBeInTheDocument();
  });
});
