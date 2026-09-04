/**
 * BreakdownCard Unit Tests
 *
 * The ranked-breakdown widget behind every card on the Support Summary page: rows with
 * counts and shares, expand/collapse to recent tickets, the accent and palette variants,
 * and the stacked share bar.
 */

import { fireEvent, render, screen, within } from "@testing-library/react";
import type { SummaryCount, SummaryTicket } from "../../../lib/types/summary";
import BreakdownCard, { BREAKDOWN_ACCENTS, DISTINCT_ROW_COLORS, sharePercent, sumCounts } from "../breakdown-card";

const ticket = (ticketId: string, text: string, timestamp: string): SummaryTicket => ({ ticketId, text, timestamp });

// Shares of the total (40): 60%, 25%, 15%.
const counts: SummaryCount[] = [
  {
    label: "Knowledge Gap",
    count: 24,
    recent: [
      ticket("101", "How do I rotate the Kafka credentials?", "2026-08-31T09:15:00Z"),
      ticket("102", "Where are the DNS rules documented?", "2026-08-30T14:20:00Z"),
    ],
  },
  { label: "Product Temporary Issue", count: 10, recent: [ticket("103", "CI runners are timing out", "2026-08-29T10:00:00Z")] },
  { label: "Task Request", count: 6, recent: [] },
];

const rowButton = (label: string | RegExp) => screen.getByRole("button", { name: label });

describe("BreakdownCard", () => {
  describe("helpers", () => {
    it("sums counts and rounds shares, treating an empty total as zero", () => {
      expect(sumCounts(counts)).toBe(40);
      expect(sumCounts([])).toBe(0);
      expect(sharePercent(24, 40)).toBe(60);
      expect(sharePercent(1, 3)).toBe(33);
      expect(sharePercent(5, 0)).toBe(0);
    });
  });

  describe("rows", () => {
    it("renders the title and one ranked row per value with its count and share", () => {
      render(<BreakdownCard title="Top Support Areas" counts={counts} onOpenTicket={jest.fn()} testId="drivers" />);

      const card = screen.getByTestId("drivers");
      expect(within(card).getByRole("heading", { level: 2, name: "Top Support Areas" })).toBeInTheDocument();

      const rows = within(card).getAllByRole("button", { expanded: false });
      // The label is plain text, not a heading: a heading may not sit inside the row's button.
      expect(rows.map((row) => row.querySelector(".text-sm.font-medium")?.textContent)).toEqual([
        "Knowledge Gap",
        "Product Temporary Issue",
        "Task Request",
      ]);
      expect(rows[0]).toHaveTextContent("1Knowledge Gap2460%");
      expect(rows[1]).toHaveTextContent("2Product Temporary Issue1025%");
      expect(rows[2]).toHaveTextContent("3Task Request615%");
    });

    it("sets every count, share and rank badge in the tabular monospace face", () => {
      render(<BreakdownCard title="Top Support Areas" counts={counts} onOpenTicket={jest.fn()} />);

      for (const text of ["24", "60%", "10", "25%", "6", "15%"]) {
        expect(screen.getByText(text)).toHaveClass("font-mono", "tabular-nums");
      }
      expect(screen.getByText("1")).toHaveClass("font-mono", "tabular-nums");
    });

    it("scales each row's bar against the largest count", () => {
      const { container } = render(<BreakdownCard title="Top Support Areas" counts={counts} onOpenTicket={jest.fn()} />);

      const bars = container.querySelectorAll<HTMLElement>(".h-1\\.5 > div");
      expect(bars).toHaveLength(3);
      expect(bars[0]).toHaveStyle({ width: "100%" });
      expect(bars[1]).toHaveStyle({ width: "42%" });
      expect(bars[2]).toHaveStyle({ width: "25%" });
    });

    it("formats large counts with grouping separators", () => {
      render(<BreakdownCard title="Top Teams" counts={[{ label: "payments", count: 2127, recent: [] }]} onOpenTicket={jest.fn()} />);

      expect(screen.getByText("2,127")).toBeInTheDocument();
      expect(screen.getByText("100%")).toBeInTheDocument();
    });

    it("renders a subtitle under a row when the callback returns one", () => {
      const teams: SummaryCount[] = [
        { label: "payments", count: 15, recent: [], topProduct: "Checkout" },
        { label: "search", count: 4, recent: [] },
      ];

      render(
        <BreakdownCard
          title="Top Teams"
          counts={teams}
          subtitle={(team) => (team.topProduct ? `Top product: ${team.topProduct}` : undefined)}
          onOpenTicket={jest.fn()}
        />
      );

      expect(within(rowButton(/payments/)).getByText("Top product: Checkout")).toBeInTheDocument();
      expect(within(rowButton(/search/)).queryByText(/Top product/)).not.toBeInTheDocument();
    });
  });

  describe("expand and collapse", () => {
    it("starts collapsed and reveals the row's recent tickets when clicked", () => {
      render(<BreakdownCard title="Top Support Areas" counts={counts} onOpenTicket={jest.fn()} />);

      const row = rowButton(/Knowledge Gap/);
      expect(row).toHaveAttribute("aria-expanded", "false");
      expect(row).toHaveAttribute("aria-controls", "top-support-areas-0-recent");
      expect(screen.queryByText("Up to 5 most recent tickets")).not.toBeInTheDocument();

      fireEvent.click(row);

      expect(row).toHaveAttribute("aria-expanded", "true");
      const panel = document.getElementById("top-support-areas-0-recent") as HTMLElement;
      expect(panel).toBeInTheDocument();
      expect(within(panel).getByText("Up to 5 most recent tickets")).toBeInTheDocument();
      expect(within(panel).getByRole("button", { name: "View ticket 101" })).toHaveTextContent("How do I rotate the Kafka credentials?");
      expect(within(panel).getByRole("button", { name: "View ticket 102" })).toHaveTextContent("Where are the DNS rules documented?");
      // Timestamps are rendered in UTC.
      expect(within(panel).getByRole("button", { name: "View ticket 101" })).toHaveTextContent(/31 Aug 2026, 09:15/);

      fireEvent.click(row);

      expect(row).toHaveAttribute("aria-expanded", "false");
      expect(document.getElementById("top-support-areas-0-recent")).toBeNull();
    });

    it("expands rows independently", () => {
      render(<BreakdownCard title="Top Support Areas" counts={counts} onOpenTicket={jest.fn()} />);

      fireEvent.click(rowButton(/Knowledge Gap/));
      fireEvent.click(rowButton(/Product Temporary Issue/));

      expect(rowButton(/Knowledge Gap/)).toHaveAttribute("aria-expanded", "true");
      expect(rowButton(/Product Temporary Issue/)).toHaveAttribute("aria-expanded", "true");
      expect(rowButton(/Task Request/)).toHaveAttribute("aria-expanded", "false");
      expect(screen.getAllByText("Up to 5 most recent tickets")).toHaveLength(2);

      fireEvent.click(rowButton(/Knowledge Gap/));

      expect(rowButton(/Knowledge Gap/)).toHaveAttribute("aria-expanded", "false");
      expect(rowButton(/Product Temporary Issue/)).toHaveAttribute("aria-expanded", "true");
    });

    it("says so when an expanded row has no tickets", () => {
      render(<BreakdownCard title="Top Support Areas" counts={counts} onOpenTicket={jest.fn()} />);

      fireEvent.click(rowButton(/Task Request/));

      expect(screen.getByText("No tickets to show")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /View ticket/ })).not.toBeInTheDocument();
    });

    it("falls back to the ticket id when a ticket has no classifier text", () => {
      render(
        <BreakdownCard
          title="Top Teams"
          counts={[{ label: "payments", count: 1, recent: [ticket("555", "", "2026-08-31T09:15:00Z")] }]}
          onOpenTicket={jest.fn()}
        />
      );

      fireEvent.click(rowButton(/payments/));

      expect(screen.getByRole("button", { name: "View ticket 555" })).toHaveTextContent("Ticket 555");
    });

    it("invokes the open-ticket callback with the clicked ticket's id", () => {
      const onOpenTicket = jest.fn();
      render(<BreakdownCard title="Top Support Areas" counts={counts} onOpenTicket={onOpenTicket} />);

      fireEvent.click(rowButton(/Knowledge Gap/));
      fireEvent.click(screen.getByRole("button", { name: "View ticket 102" }));

      expect(onOpenTicket).toHaveBeenCalledTimes(1);
      expect(onOpenTicket).toHaveBeenCalledWith("102");
      // Opening a ticket does not collapse the row.
      expect(rowButton(/Knowledge Gap/)).toHaveAttribute("aria-expanded", "true");
    });
  });

  describe("empty state", () => {
    it("shows the default empty message and no rows or bar", () => {
      render(<BreakdownCard title="Top categories" counts={[]} stackedBar="Share by category" onOpenTicket={jest.fn()} />);

      expect(screen.getByText("No data for this period")).toBeInTheDocument();
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
      expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });

    it("shows a custom empty message", () => {
      render(<BreakdownCard title="Top products" counts={[]} emptyMessage="No product tags in this window" onOpenTicket={jest.fn()} />);

      expect(screen.getByText("No product tags in this window")).toBeInTheDocument();
    });
  });

  describe("accent and palette variants", () => {
    it("uses the primary accent by default and a numbered badge per row", () => {
      const { container } = render(<BreakdownCard title="Top categories" counts={counts} onOpenTicket={jest.fn()} />);

      expect(screen.getByText("1")).toHaveClass("bg-primary/10", "text-primary");
      expect(container.querySelectorAll(".h-1\\.5 > div.bg-primary")).toHaveLength(3);
      expect(container.querySelectorAll(".h-1\\.5.bg-primary\\/10")).toHaveLength(3);
    });

    it.each(Object.keys(BREAKDOWN_ACCENTS) as (keyof typeof BREAKDOWN_ACCENTS)[])(
      "applies the %s accent to badge, bar and track",
      (accent) => {
        const { container } = render(<BreakdownCard title="Top categories" counts={counts} accent={accent} onOpenTicket={jest.fn()} />);

        const { bar, track, badge } = BREAKDOWN_ACCENTS[accent];
        expect(screen.getByText("1")).toHaveClass(...badge.split(" "));
        expect(container.querySelectorAll(`.h-1\\.5 > div.${CSS.escape(bar)}`)).toHaveLength(3);
        expect(container.querySelectorAll(`.h-1\\.5.${CSS.escape(track)}`)).toHaveLength(3);
      }
    );

    it("uses a colour swatch per row instead of a badge when a palette is given, wrapping past its end", () => {
      const eight: SummaryCount[] = Array.from({ length: 8 }, (_, index) => ({
        label: `Driver ${index + 1}`,
        count: (8 - index) * 10,
        recent: [],
      }));
      const { container } = render(
        <BreakdownCard title="Top Support Areas" counts={eight} palette={DISTINCT_ROW_COLORS} onOpenTicket={jest.fn()} />
      );

      // No numbered rank badges.
      expect(container.querySelectorAll("button > div.h-7.w-7")).toHaveLength(0);
      const swatches = container.querySelectorAll<HTMLElement>("button > span.rounded-sm");
      expect(swatches).toHaveLength(8);
      expect(swatches[0]).toHaveClass(DISTINCT_ROW_COLORS[0]);
      expect(swatches[6]).toHaveClass(DISTINCT_ROW_COLORS[6]);
      // Rank 8 wraps back to the first palette colour.
      expect(swatches[7]).toHaveClass(DISTINCT_ROW_COLORS[0]);
      // Tracks fall back to the neutral surface so any palette colour reads against them.
      expect(container.querySelectorAll(".h-1\\.5.bg-muted")).toHaveLength(8);
    });

    it("only uses chart tokens in the distinct palette", () => {
      for (const color of DISTINCT_ROW_COLORS) {
        expect(color).toMatch(/^bg-chart-\d+$/);
      }
      expect(new Set(DISTINCT_ROW_COLORS).size).toBe(DISTINCT_ROW_COLORS.length);
    });
  });

  describe("stacked share bar", () => {
    // Shares of the total (100): 60%, 35%, 5% — the last is under the 8% label threshold.
    const shares: SummaryCount[] = [
      { label: "Knowledge Gap", count: 60, recent: [] },
      { label: "Product Temporary Issue", count: 35, recent: [] },
      { label: "Task Request", count: 5, recent: [] },
    ];

    it("is absent unless requested", () => {
      render(<BreakdownCard title="Top Support Areas" counts={shares} palette={DISTINCT_ROW_COLORS} onOpenTicket={jest.fn()} />);

      expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });

    it("renders one segment per row, coloured by rank with a chart token and sized by share", () => {
      render(
        <BreakdownCard
          title="Top Support Areas"
          counts={shares}
          palette={DISTINCT_ROW_COLORS}
          stackedBar="Share of tickets by driver"
          onOpenTicket={jest.fn()}
        />
      );

      const bar = screen.getByRole("img", { name: "Share of tickets by driver" });
      const segments = Array.from(bar.children) as HTMLElement[];
      expect(segments).toHaveLength(3);
      segments.forEach((segment, index) => {
        expect(segment).toHaveClass(DISTINCT_ROW_COLORS[index]);
        expect(segment.className).toMatch(/\bbg-chart-\d+\b/);
        expect(segment).toHaveClass("text-primary-foreground", "font-mono", "tabular-nums");
      });
      expect(segments[0]).toHaveStyle({ width: "60%" });
      expect(segments[1]).toHaveStyle({ width: "35%" });
      expect(segments[2]).toHaveStyle({ width: "5%" });
    });

    it("labels only the segments wide enough to carry a share, but titles every one", () => {
      render(
        <BreakdownCard
          title="Top Support Areas"
          counts={shares}
          palette={DISTINCT_ROW_COLORS}
          stackedBar="Share of tickets by driver"
          onOpenTicket={jest.fn()}
        />
      );

      const segments = Array.from(screen.getByRole("img").children) as HTMLElement[];
      expect(segments[0]).toHaveTextContent("60%");
      expect(segments[1]).toHaveTextContent("35%");
      expect(segments[2]).toHaveTextContent("");
      expect(segments[0]).toHaveAttribute("title", "Knowledge Gap: 60 (60%)");
      expect(segments[2]).toHaveAttribute("title", "Task Request: 5 (5%)");
    });

    it("labels a segment at exactly the 8% threshold", () => {
      const boundary: SummaryCount[] = [
        { label: "Big", count: 92, recent: [] },
        { label: "Small", count: 8, recent: [] },
      ];
      render(
        <BreakdownCard
          title="Top Support Areas"
          counts={boundary}
          palette={DISTINCT_ROW_COLORS}
          stackedBar="Share"
          onOpenTicket={jest.fn()}
        />
      );

      const segments = Array.from(screen.getByRole("img").children) as HTMLElement[];
      expect(segments[1]).toHaveTextContent("8%");
    });

    it("falls back to the accent colour for every segment when no palette is given", () => {
      render(<BreakdownCard title="Top Support Areas" counts={shares} accent="info" stackedBar="Share" onOpenTicket={jest.fn()} />);

      const segments = Array.from(screen.getByRole("img").children) as HTMLElement[];
      segments.forEach((segment) => expect(segment).toHaveClass("bg-info"));
    });
  });
});
