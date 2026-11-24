// src/components/dashboards/__tests__/EscalationSLASection.test.tsx
import type { EscalationRate, EscalationTeamScorecardRow, EscalationTopic, ResolutionByEscalation } from "@/lib/types/dashboard";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { EscalationSLASection } from "../EscalationSLASection";

jest.mock("../../../lib/hooks", () => ({
  useEscalationTeams: () => ({ data: [{ name: "team-a", label: "Team A", types: ["escalation"] }] }),
  useTenantTeams: () => ({ data: [{ name: "tenant-a", label: "Tenant Team A" }] }),
}));

jest.mock("../TimeSeriesChart", () => ({
  TimeSeriesChart: ({ title, data }: { title: string; data: { rate: number }[] }) => (
    <div data-testid="time-series-chart">
      <h3>{title}</h3>
      <div>Rates: {data.map((d) => d.rate).join(",")}</div>
    </div>
  ),
}));

jest.mock("../RefreshButton", () => ({
  RefreshButton: ({ onRefresh, isRefreshing }: { onRefresh: () => void; isRefreshing: boolean }) => (
    <button data-testid="refresh-button" onClick={onRefresh} disabled={isRefreshing}>
      {isRefreshing ? "Refreshing..." : "Refresh"}
    </button>
  ),
}));

describe("EscalationSLASection", () => {
  const escalationRate: EscalationRate = {
    granularity: "DAY",
    data: [
      { date: "2024-01-01", tickets: 4, escalatedTickets: 1, rate: 0.25 },
      { date: "2024-01-02", tickets: 6, escalatedTickets: 3, rate: 0.5 },
    ],
  };

  const resolutionEscalatedVsNot: ResolutionByEscalation[] = [
    { escalated: true, tickets: 4, p50: 7200, p90: 14400 },
    { escalated: false, tickets: 6, p50: 3600, p90: 5400 },
  ];

  const scorecard: EscalationTeamScorecardRow[] = [
    { team: "team-a", escalations: 3, open: 1, resolutionP50: 5400, resolutionP90: 9000 },
    { team: "team-b", escalations: 1, open: 1, resolutionP50: null, resolutionP90: null },
  ];

  const topics: EscalationTopic[] = [
    { tag: "networking", escalations: 2, avgSeconds: 18000, totalSeconds: 36000, avgWaitSeconds: 7200 },
    { tag: "access", escalations: 3, avgSeconds: 3600, totalSeconds: 10800, avgWaitSeconds: null },
  ];

  const renderSection = (overrides: Partial<React.ComponentProps<typeof EscalationSLASection>> = {}) =>
    render(
      <EscalationSLASection
        escalationRate={escalationRate}
        resolutionEscalatedVsNot={resolutionEscalatedVsNot}
        escalationTeamScorecard={scorecard}
        escalationTopics={topics}
        timeBeforeEscalation={{ escalations: 2, p50: 1800, p90: 3240 }}
        escalationRateByTeam={[{ team: "tenant-a", tickets: 8, escalatedTickets: 2, rate: 0.25 }]}
        isRefreshing={false}
        onRefresh={jest.fn()}
        {...overrides}
      />
    );

  it("renders the header and wires the refresh button", () => {
    const onRefresh = jest.fn();
    renderSection({ onRefresh });

    expect(screen.getByText("Escalation Analysis")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("refresh-button"));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it("shows the overall escalation rate from the summed cohort", () => {
    renderSection();

    expect(screen.getByText("40.0%")).toBeInTheDocument();
    expect(screen.getByText("4 of 10 tickets opened in this range were escalated")).toBeInTheDocument();
  });

  it("plots the rate as a percentage per bucket", () => {
    renderSection();

    expect(screen.getByText("Escalation Rate Over Time (daily)")).toBeInTheDocument();
    expect(screen.getByText("Rates: 25,50")).toBeInTheDocument();
  });

  it("leads with the escalated vs not-escalated resolution comparison", () => {
    renderSection();

    expect(screen.getByText("Resolution: Escalated vs Not Escalated")).toBeInTheDocument();
    expect(screen.getByTitle("Escalated, P50: 2.0h")).toBeInTheDocument();
  });

  it("uses singular wording for a single ticket", () => {
    renderSection({
      escalationRate: { granularity: "DAY", data: [{ date: "2024-01-01", tickets: 1, escalatedTickets: 1, rate: 1 }] },
      resolutionEscalatedVsNot: [{ escalated: true, tickets: 1, p50: 60, p90: 60 }],
    });

    expect(screen.getByText("(1 ticket)")).toBeInTheDocument();
    expect(screen.getByText("1 of 1 ticket opened in this range was escalated")).toBeInTheDocument();
  });

  it("renders escalations by receiving team with labels and a dash for unresolved teams", () => {
    renderSection();

    expect(screen.getByText("Escalations by Receiving Team")).toBeInTheDocument();

    const teamARow = screen.getByText("Team A").closest("tr") as HTMLElement;
    expect(within(teamARow).getByText("1.5h")).toBeInTheDocument();
    expect(within(teamARow).getByText("2.5h")).toBeInTheDocument();

    const teamBRow = screen.getByText("team-b").closest("tr") as HTMLElement;
    expect(within(teamBRow).getAllByText("—")).toHaveLength(2);
  });

  it("ranks topics in the order the API returns them", () => {
    renderSection();

    const firstRow = screen.getByText("networking").closest("tr") as HTMLElement;
    expect(within(firstRow).getByText("1")).toBeInTheDocument();
    expect(within(firstRow).getByText("10.0h")).toBeInTheDocument();
    expect(within(firstRow).getByText("2.0h")).toBeInTheDocument();
    const secondRow = screen.getByText("access").closest("tr") as HTMLElement;
    expect(within(secondRow).getByText("2")).toBeInTheDocument();
    expect(within(secondRow).getByText("—")).toBeInTheDocument();
  });

  it("handles missing data without crashing", () => {
    renderSection({
      escalationRate: undefined,
      resolutionEscalatedVsNot: undefined,
      escalationTeamScorecard: undefined,
      escalationTopics: undefined,
      timeBeforeEscalation: undefined,
      escalationRateByTeam: undefined,
    });

    expect(screen.getByText("No escalations in this range")).toBeInTheDocument();
    expect(screen.getByText("No closed tickets in this range")).toBeInTheDocument();
    expect(screen.getByText("No resolved, tagged escalations in this range")).toBeInTheDocument();
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("No escalated tickets in this range")).toBeInTheDocument();
    expect(screen.getByText("0 of 0 tickets opened in this range were escalated")).toBeInTheDocument();
  });

  it("shows time before escalating and the rate by requesting team", () => {
    renderSection();

    expect(screen.getByText("Time Before Escalating")).toBeInTheDocument();
    expect(screen.getByText("30m")).toBeInTheDocument();
    expect(screen.getByText("54m")).toBeInTheDocument();
    const tenantRow = screen.getByText("Tenant Team A").closest("tr") as HTMLElement;
    expect(within(tenantRow).getByText("25.0%")).toBeInTheDocument();
  });

  it("treats a non-object rate payload as empty", () => {
    renderSection({ escalationRate: [] as unknown as EscalationRate });

    expect(screen.getByText("Rates:")).toBeInTheDocument();
  });
});
