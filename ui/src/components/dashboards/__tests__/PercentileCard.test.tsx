// src/components/dashboards/__tests__/PercentileCard.test.tsx
import { render, screen } from "@testing-library/react";
import { Activity } from "lucide-react";
import { PercentileCard } from "../PercentileCard";

describe("PercentileCard", () => {
  describe("Rendering", () => {
    it("should render title", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      expect(screen.getByText("Response Time")).toBeInTheDocument();
    });

    it("should render P50 and P90 values", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      expect(screen.getByText("P50")).toBeInTheDocument();
      expect(screen.getByText("2h 30m")).toBeInTheDocument();
      expect(screen.getByText("P90")).toBeInTheDocument();
      expect(screen.getByText("8h 15m")).toBeInTheDocument();
    });

    it("should render P75 when provided", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p75="5h 45m" p90="8h 15m" />);

      expect(screen.getByText("P75")).toBeInTheDocument();
      expect(screen.getByText("5h 45m")).toBeInTheDocument();
    });

    it("should not render P75 when not provided", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      expect(screen.queryByText("P75")).not.toBeInTheDocument();
    });

    it("should render default Clock icon when no custom icon provided", () => {
      const { container } = render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      // Clock icon from lucide-react will be rendered
      const svg = container.querySelector("svg");
      expect(svg).toBeInTheDocument();
    });

    it("should render custom icon when provided", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" icon={<Activity data-testid="custom-icon" />} />);

      expect(screen.getByTestId("custom-icon")).toBeInTheDocument();
    });
  });

  describe("Color Schemes", () => {
    it("renders flat tokenized card regardless of colorScheme prop", () => {
      // colorScheme is now a no-op — card uses bg-card / text-foreground tokens.
      const { container } = render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" colorScheme="green" />);

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain("bg-card");
      expect(card.className).toContain("border");
    });
  });

  describe("Layout", () => {
    it("uses a grid for percentile columns", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      const p50Label = screen.getByText("P50");
      const grid = p50Label.parentElement?.parentElement as HTMLElement;
      expect(grid.className).toContain("grid");
    });

    it("should show 2 percentile columns when P75 not provided", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      const percentileLabels = screen.getAllByText(/^P\d+$/);
      expect(percentileLabels).toHaveLength(2); // P50 and P90
    });

    it("should show 3 percentile columns when P75 provided", () => {
      render(<PercentileCard title="Response Time" p50="2h 30m" p75="5h 45m" p90="8h 15m" />);

      const percentileLabels = screen.getAllByText(/^P\d+$/);
      expect(percentileLabels).toHaveLength(3); // P50, P75, and P90
    });
  });

  describe("Value Formatting", () => {
    it("should handle zero values", () => {
      render(<PercentileCard title="Response Time" p50="0h 0m" p90="0h 0m" />);

      // Both P50 and P90 will show "0h 0m", so getAllByText
      const zeroValues = screen.getAllByText("0h 0m");
      expect(zeroValues).toHaveLength(2); // P50 and P90
    });

    it("should handle very long duration strings", () => {
      render(<PercentileCard title="Response Time" p50="15 days 23h 59m" p90="30 days 12h 30m" />);

      expect(screen.getByText("15 days 23h 59m")).toBeInTheDocument();
      expect(screen.getByText("30 days 12h 30m")).toBeInTheDocument();
    });

    it("should handle simple numeric strings", () => {
      render(<PercentileCard title="Count" p50="42" p90="100" />);

      expect(screen.getByText("42")).toBeInTheDocument();
      expect(screen.getByText("100")).toBeInTheDocument();
    });
  });

  describe("Styling", () => {
    it("should have rounded corners", () => {
      const { container } = render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain("rounded-xl");
    });

    it("should have padding", () => {
      const { container } = render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain("p-6");
    });

    it("should have border", () => {
      const { container } = render(<PercentileCard title="Response Time" p50="2h 30m" p90="8h 15m" />);

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain("border");
    });
  });

  describe("Edge Cases", () => {
    it("should handle empty strings", () => {
      render(<PercentileCard title="Test" p50="" p90="" />);

      // Should render but values will be empty
      expect(screen.getByText("P50")).toBeInTheDocument();
      expect(screen.getByText("P90")).toBeInTheDocument();
    });

    it("should handle special characters in values", () => {
      render(<PercentileCard title="Test" p50="N/A" p90="∞" />);

      expect(screen.getByText("N/A")).toBeInTheDocument();
      expect(screen.getByText("∞")).toBeInTheDocument();
    });

    it("should handle very long title", () => {
      const longTitle = "This is a very long title that describes something complex";

      render(<PercentileCard title={longTitle} p50="2h" p90="8h" />);

      expect(screen.getByText(longTitle)).toBeInTheDocument();
    });
  });
});
