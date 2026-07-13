import type { ElevateStatus } from "@/lib/types";
import type { UseQueryResult } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
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
      userIds: ["user-1"],
      createdAt: "2026-01-02T08:00:00Z",
      lastUpdatedAt: "2026-07-11T08:00:00Z",
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
  it("renders connection status and all synchronized collections", async () => {
    const user = userEvent.setup();
    mockQuery({ data: connectedStatus });
    render(<ElevatePage />);

    expect(screen.getByText("Connected")).toBeInTheDocument();
    expect(screen.getByText("Platform")).toBeInTheDocument();
    expect(screen.getByText("1h")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Journeys/ }));
    expect(screen.getByText("First deployment")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Users/ }));
    expect(screen.getByText("Application team")).toBeInTheDocument();
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
    expect(screen.getByText("Platform")).toBeInTheDocument();
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
    expect(screen.getByText("Platform")).toBeInTheDocument();
    expect(screen.queryByText("Unable to load Elevate status")).not.toBeInTheDocument();
  });
});
