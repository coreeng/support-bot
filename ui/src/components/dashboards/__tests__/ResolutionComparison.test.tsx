// src/components/dashboards/__tests__/ResolutionComparison.test.tsx
import type { ResolutionByEscalation } from "@/lib/types/dashboard";
import { render, screen, within } from "@testing-library/react";
import { ResolutionComparison } from "../ResolutionComparison";

describe("ResolutionComparison", () => {
  const data: ResolutionByEscalation[] = [
    { escalated: true, tickets: 4, p50: 7200, p90: 14400 },
    { escalated: false, tickets: 6, p50: 3600, p90: 5400 },
  ];

  const bar = (title: string) => screen.getByTitle(title).querySelector("[style]") as HTMLElement;

  it("lists both groups with their ticket counts", () => {
    render(<ResolutionComparison data={data} />);

    expect(screen.getByText("(4 tickets)")).toBeInTheDocument();
    expect(screen.getByText("(6 tickets)")).toBeInTheDocument();
  });

  it("says that automatic escalations count as escalated", () => {
    render(<ResolutionComparison data={data} />);

    expect(screen.getByText(/whether they were ever escalated, manually or automatically/)).toBeInTheDocument();
  });

  it("shows each percentile with a labelled bar per group", () => {
    render(<ResolutionComparison data={data} />);

    expect(screen.getByText("Median resolution time")).toBeInTheDocument();
    expect(screen.getByText("90th percentile resolution time")).toBeInTheDocument();
    expect(within(screen.getByTitle("Escalated, P50: 2.0h")).getByText("2.0h")).toBeInTheDocument();
    expect(within(screen.getByTitle("Not escalated, P50: 1.0h")).getByText("1.0h")).toBeInTheDocument();
    expect(within(screen.getByTitle("Escalated, P90: 4.0h")).getByText("4.0h")).toBeInTheDocument();
    expect(within(screen.getByTitle("Not escalated, P90: 1.5h")).getByText("1.5h")).toBeInTheDocument();
  });

  it("scales the bars within each question", () => {
    render(<ResolutionComparison data={data} />);

    expect(bar("Escalated, P50: 2.0h").style.width).toBe("100%");
    expect(bar("Not escalated, P50: 1.0h").style.width).toBe("50%");
    expect(bar("Escalated, P90: 4.0h").style.width).toBe("100%");
    expect(bar("Not escalated, P90: 1.5h").style.width).toBe("37.5%");
  });

  it("uses singular wording for a single ticket", () => {
    render(<ResolutionComparison data={[{ escalated: true, tickets: 1, p50: 1800, p90: 1800 }]} />);

    expect(screen.getByText("(1 ticket)")).toBeInTheDocument();
  });

  it("dashes the missing group when only one group has data", () => {
    render(<ResolutionComparison data={[{ escalated: false, tickets: 2, p50: 60, p90: 120 }]} />);

    expect(screen.getByText("(0 tickets)")).toBeInTheDocument();
    expect(bar("Escalated, P50: —").style.width).toBe("0%");
    expect(bar("Escalated, P90: —").style.width).toBe("0%");
  });

  it("shows a real zero as 0s rather than a dash", () => {
    render(<ResolutionComparison data={[{ escalated: true, tickets: 1, p50: 0, p90: 0 }]} />);

    expect(within(screen.getByTitle("Escalated, P50: 0s")).getByText("0s")).toBeInTheDocument();
  });

  it("shows an empty state without data", () => {
    render(<ResolutionComparison data={undefined} />);

    expect(screen.getByText("No closed tickets in this range")).toBeInTheDocument();
  });
});
