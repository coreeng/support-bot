// src/components/dashboards/__tests__/EscalationRateByTeam.test.tsx
import { render, screen, within } from "@testing-library/react";
import { EscalationRateByTeam } from "../EscalationRateByTeam";

jest.mock("../../../lib/hooks", () => ({
  useTenantTeams: () => ({ data: [{ name: "team-a", label: "Team A" }] }),
}));

describe("EscalationRateByTeam", () => {
  it("lists teams with labels, counts and a rate bar", () => {
    render(
      <EscalationRateByTeam
        data={[
          { team: "team-a", tickets: 4, escalatedTickets: 2, rate: 0.5 },
          { team: "team-b", tickets: 1, escalatedTickets: 1, rate: 1 },
        ]}
      />
    );

    const teamARow = screen.getByText("Team A").closest("tr") as HTMLElement;
    expect(within(teamARow).getByText("4")).toBeInTheDocument();
    expect(within(teamARow).getByText("2")).toBeInTheDocument();
    expect(within(teamARow).getByText("50.0%")).toBeInTheDocument();
    expect((teamARow.querySelector("[style]") as HTMLElement).style.width).toBe("50%");

    // Unknown codes fall back to the raw team code.
    const teamBRow = screen.getByText("team-b").closest("tr") as HTMLElement;
    expect(within(teamBRow).getByText("100.0%")).toBeInTheDocument();
  });

  it("shows an empty state without data", () => {
    render(<EscalationRateByTeam data={undefined} />);

    expect(screen.getByText("No escalated tickets in this range")).toBeInTheDocument();
  });
});
