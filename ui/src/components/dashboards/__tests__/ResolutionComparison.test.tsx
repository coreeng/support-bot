// src/components/dashboards/__tests__/ResolutionComparison.test.tsx
import type { ResolutionByEscalation } from "@/lib/types/dashboard";
import { render, screen } from "@testing-library/react";
import { ResolutionComparison } from "../ResolutionComparison";

describe("ResolutionComparison", () => {
  const data: ResolutionByEscalation[] = [
    { escalated: true, tickets: 4, p50: 7200, p90: 14400 },
    { escalated: false, tickets: 6, p50: 3600, p90: 5400 },
  ];

  it("shows counts and percentiles for both groups", () => {
    render(<ResolutionComparison data={data} />);

    expect(screen.getByText("(4 tickets)")).toBeInTheDocument();
    expect(screen.getByText("(6 tickets)")).toBeInTheDocument();
    expect(screen.getByText("P50 2.0h · P90 4.0h")).toBeInTheDocument();
    expect(screen.getByText("P50 1.0h · P90 1.5h")).toBeInTheDocument();
  });

  it("scales both groups against the largest P90", () => {
    render(<ResolutionComparison data={data} />);

    const escalatedTrack = screen.getByTitle("Escalated: P50 2.0h, P90 4.0h");
    const [escalatedP90, escalatedP50] = Array.from(escalatedTrack.children) as HTMLElement[];
    expect(escalatedP90.style.width).toBe("100%");
    expect(escalatedP50.style.width).toBe("50%");

    const notEscalatedTrack = screen.getByTitle("Not escalated: P50 1.0h, P90 1.5h");
    const [notEscalatedP90, notEscalatedP50] = Array.from(notEscalatedTrack.children) as HTMLElement[];
    expect(notEscalatedP90.style.width).toBe("37.5%");
    expect(notEscalatedP50.style.width).toBe("25%");
  });

  it("uses singular wording for a single ticket", () => {
    render(<ResolutionComparison data={[{ escalated: true, tickets: 1, p50: 1800, p90: 1800 }]} />);

    expect(screen.getByText("(1 ticket)")).toBeInTheDocument();
  });

  it("dashes the missing group when only one group has data", () => {
    render(<ResolutionComparison data={[{ escalated: false, tickets: 2, p50: 60, p90: 120 }]} />);

    expect(screen.getByText("P50 — · P90 —")).toBeInTheDocument();
    expect(screen.getByText("(0 tickets)")).toBeInTheDocument();
  });

  it("shows an empty state without data", () => {
    render(<ResolutionComparison data={undefined} />);

    expect(screen.getByText("No closed tickets in this range")).toBeInTheDocument();
  });
});
