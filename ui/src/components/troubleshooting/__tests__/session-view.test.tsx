import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import React from "react";
import { useAuth } from "../../../hooks/useAuth";
import * as hooks from "../../../lib/hooks";
import type { TroubleshootingEvent, TroubleshootingSession } from "../../../lib/types/troubleshooting";
import "../../../test-utils/assistant-ui-env";
import { events, sessionWith, slackEvents, threadNotAvailable } from "../../../test-utils/troubleshooting-fixtures";
import SessionView from "../session-view";

jest.mock("../../../hooks/useAuth");
jest.mock("../use-session-stream", () => ({ useSessionStream: () => "open" }));
jest.mock("../../../lib/hooks", () => ({
  useTroubleshootingSession: jest.fn(),
  useTicket: jest.fn(() => ({ data: undefined })),
  sendTroubleshootingMessage: jest.fn(() => Promise.resolve({})),
  decideTroubleshootingProposal: jest.fn(() => Promise.resolve({})),
  startTroubleshootingSession: jest.fn(),
  troubleshootingSessionKey: (id: number) => ["troubleshooting", "session", id],
  isApiError: () => false,
}));

const mockUseSession = hooks.useTroubleshootingSession as jest.MockedFunction<typeof hooks.useTroubleshootingSession>;
const mockSend = hooks.sendTroubleshootingMessage as jest.MockedFunction<typeof hooks.sendTroubleshootingMessage>;
const mockDecide = hooks.decideTroubleshootingProposal as jest.MockedFunction<typeof hooks.decideTroubleshootingProposal>;
const mockStart = hooks.startTroubleshootingSession as jest.MockedFunction<typeof hooks.startTroubleshootingSession>;

function withSession(session: TroubleshootingSession | null) {
  mockUseSession.mockReturnValue({ data: session, isLoading: false, error: null } as unknown as ReturnType<
    typeof hooks.useTroubleshootingSession
  >);
}

function renderView() {
  const client = new QueryClient();
  const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  return render(<SessionView ticketId={42} />, { wrapper });
}

function renderedSeqs(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll("[data-seq]")).map((node) => node.getAttribute("data-seq") ?? "");
}

describe("SessionView with assistant-ui", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useAuth as jest.Mock).mockReturnValue({ user: { email: "alice@example.com" } });
    Element.prototype.scrollTo = jest.fn();
  });

  it("renders every visible event as its own message, in order", () => {
    withSession(sessionWith(events));

    const { container } = renderView();

    expect(renderedSeqs(container)).toEqual(["1", "3", "5", "6", "7"]);
    expect(screen.getByText("Pods are pending")).toBeInTheDocument();
    expect(screen.getByText("Looks like scheduling.")).toBeInTheDocument();
    expect(screen.getByText("Proposal: Reply to the author")).toBeInTheDocument();
  });

  it("tells the two engineers and the Slack author apart", () => {
    withSession(sessionWith(events));

    renderView();

    expect(screen.getByText("You")).toBeInTheDocument();
    expect(screen.getByText("bob")).toBeInTheDocument();
    expect(screen.getAllByText("U1").length).toBeGreaterThan(0);
  });

  it("shows the busy state and still lets an engineer send", async () => {
    const busy: TroubleshootingEvent[] = [
      ...events,
      { seq: 8, type: "ROUTER_STATUS", actor: "router", createdAt: "2026-11-18T10:00:00Z", payload: { state: "THINKING", error: null } },
    ];
    withSession(sessionWith(busy));
    renderView();

    expect(screen.getByTestId("router-busy")).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "One more thing" } });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: /Send/ }));
    });

    await waitFor(() => expect(mockSend).toHaveBeenCalledWith(42, "One more thing"));
  });

  it("approves a proposal from its card", async () => {
    withSession(sessionWith(events));
    renderView();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: /^Approve$/ }));
    });

    expect(mockDecide).toHaveBeenCalledWith(42, 7, "APPROVED", undefined);
  });

  it("renders every Slack message as its own message, in order", () => {
    withSession(sessionWith(slackEvents));

    const { container } = renderView();

    expect(renderedSeqs(container)).toEqual(["1", "2", "3"]);
    expect(screen.getByText("Pods are pending")).toBeInTheDocument();
    expect(screen.getByText("Same in staging")).toBeInTheDocument();
    expect(screen.getByText("Manual")).toBeInTheDocument();
  });

  it("tells the Slack authors apart", () => {
    withSession(sessionWith(slackEvents));

    renderView();

    expect(screen.getAllByText("U1").length).toBeGreaterThan(0);
    expect(screen.getAllByText("U2").length).toBeGreaterThan(0);
    expect(screen.getAllByText("bot:B1").length).toBeGreaterThan(0);
  });

  it("adds streamed events without duplicating or remounting existing messages", () => {
    withSession(sessionWith(slackEvents.slice(0, 1)));
    const { container, rerender } = renderView();
    const firstNode = container.querySelector('[data-seq="1"]');

    withSession(sessionWith(slackEvents));
    rerender(<SessionView ticketId={42} />);

    expect(renderedSeqs(container)).toEqual(["1", "2", "3"]);
    expect(container.querySelector('[data-seq="1"]')).toBe(firstNode);
  });

  it("says when the Slack thread could not be read", () => {
    withSession(sessionWith([threadNotAvailable]));

    renderView();

    expect(screen.getByText("The query's Slack thread could not be read.")).toBeInTheDocument();
  });

  it("starts a session when there is none", async () => {
    withSession(null);
    mockStart.mockResolvedValue(sessionWith([]));
    renderView();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: /Start troubleshooting/ }));
    });

    expect(mockStart).toHaveBeenCalledWith(42);
  });
});
