import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as hooks from "../../../lib/hooks";
import RatingFeedback, { isPositiveRating } from "../rating-feedback";

jest.mock("../../../lib/hooks", () => ({
  ...jest.requireActual("../../../lib/hooks"),
  useRatingFeedback: jest.fn(),
}));
jest.mock("../../tickets/EditTicketModal", () => ({
  __esModule: true,
  default: ({ ticketId, open }: { ticketId: string | null; open: boolean }) =>
    open ? <div data-testid="ticket-modal">Ticket modal {ticketId}</div> : null,
}));

const mockUseRatingFeedback = hooks.useRatingFeedback as jest.MockedFunction<typeof hooks.useRatingFeedback>;

const items: hooks.RatingFeedbackItem[] = [
  { rating: 2, weekStart: "2025-10-06", feedback: "Took two days to get a first reply", ticketId: null },
  { rating: 5, weekStart: "2025-10-06", feedback: "Sorted in minutes, thank you", ticketId: 42 },
  { rating: 4, weekStart: "2025-09-29", feedback: "Clear answer and good docs link", ticketId: null },
];

function mockFeedback(result: Partial<ReturnType<typeof hooks.useRatingFeedback>>) {
  mockUseRatingFeedback.mockReturnValue({ data: undefined, isLoading: false, error: null, ...result } as ReturnType<
    typeof hooks.useRatingFeedback
  >);
}

describe("RatingFeedback", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockFeedback({ data: items });
  });

  it("treats 4 and 5 stars as positive, matching the backend", () => {
    expect(isPositiveRating(3)).toBe(false);
    expect(isPositiveRating(4)).toBe(true);
  });

  it("fetches feedback for the selected range", () => {
    render(<RatingFeedback from="2025-09-01" to="2025-10-31" />);

    expect(screen.getByText("What people said")).toBeInTheDocument();
    expect(mockUseRatingFeedback).toHaveBeenCalledWith("2025-09-01", "2025-10-31");
  });

  it("summarises the feedback", () => {
    render(<RatingFeedback />);

    expect(within(tile("Feedback received")).getByText("3")).toBeInTheDocument();
    expect(within(tile("Needs attention")).getByText("1")).toBeInTheDocument();
    expect(within(tile("Positive")).getByText("2")).toBeInTheDocument();
    expect(within(tile("Named")).getByText("33% shared their name")).toBeInTheDocument();
  });

  it("groups feedback by week, keeping the backend's order", () => {
    render(<RatingFeedback />);

    const weeks = screen.getAllByRole("region");
    expect(weeks).toHaveLength(2);
    expect(within(weeks[0]).getByText("Took two days to get a first reply")).toBeInTheDocument();
    expect(within(weeks[0]).getByText("Sorted in minutes, thank you")).toBeInTheDocument();
    expect(within(weeks[1]).getByText("Clear answer and good docs link")).toBeInTheDocument();
  });

  it("shows anonymous feedback without a ticket link", () => {
    render(<RatingFeedback />);

    const row = screen.getByText("Took two days to get a first reply").closest("li")!;
    expect(within(row).getByText("Anonymous")).toBeInTheDocument();
    expect(within(row).queryByRole("button")).not.toBeInTheDocument();
  });

  it("opens the ticket for named feedback", () => {
    render(<RatingFeedback />);

    fireEvent.click(screen.getByRole("button", { name: /Ticket #42/ }));

    expect(screen.getByTestId("ticket-modal")).toHaveTextContent("Ticket modal 42");
  });

  it("filters by search text", () => {
    render(<RatingFeedback />);

    fireEvent.change(screen.getByLabelText("Search feedback"), { target: { value: "docs" } });

    expect(screen.getByText("Clear answer and good docs link")).toBeInTheDocument();
    expect(screen.queryByText("Sorted in minutes, thank you")).not.toBeInTheDocument();
    expect(screen.getByText("1 of 3")).toBeInTheDocument();
  });

  it("paginates beyond ten items", () => {
    mockFeedback({
      data: Array.from({ length: 12 }, (_, i) => ({ rating: 3, weekStart: "2025-10-06", feedback: `Item ${i + 1}`, ticketId: null })),
    });
    render(<RatingFeedback />);

    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    expect(screen.queryByText("Item 11")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(screen.getByText("Item 11")).toBeInTheDocument();
  });

  it("counts a week's responses across pages", () => {
    mockFeedback({
      data: Array.from({ length: 12 }, (_, i) => ({ rating: 3, weekStart: "2025-10-06", feedback: `Item ${i + 1}`, ticketId: null })),
    });
    render(<RatingFeedback />);

    expect(screen.getByText("12 responses")).toBeInTheDocument();
  });

  it("filters by sentiment", async () => {
    const user = userEvent.setup();
    render(<RatingFeedback />);

    await user.click(screen.getByRole("button", { name: "Sentiment" }));
    await user.click(screen.getByRole("option", { name: /Needs attention/ }));

    expect(screen.getByText("Took two days to get a first reply")).toBeInTheDocument();
    expect(screen.queryByText("Sorted in minutes, thank you")).not.toBeInTheDocument();
    expect(screen.queryByText("Clear answer and good docs link")).not.toBeInTheDocument();
    expect(screen.getByText("1 of 3")).toBeInTheDocument();
  });

  it("filters by attribution", async () => {
    const user = userEvent.setup();
    render(<RatingFeedback />);

    await user.click(screen.getByRole("button", { name: "Attribution" }));
    await user.click(screen.getByRole("option", { name: /Anonymous/ }));

    expect(screen.queryByRole("button", { name: /Ticket #/ })).not.toBeInTheDocument();
    expect(screen.getByText("2 of 3")).toBeInTheDocument();
  });

  it("says when the filters match nothing", async () => {
    const user = userEvent.setup();
    render(<RatingFeedback />);

    await user.click(screen.getByRole("button", { name: "Sentiment" }));
    await user.click(screen.getByRole("option", { name: /Needs attention/ }));
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: "Attribution" }));
    await user.click(screen.getByRole("option", { name: /Named/ }));

    expect(screen.getByText("No feedback matches these filters.")).toBeInTheDocument();
    expect(screen.queryByText("No written feedback in this period yet.")).not.toBeInTheDocument();
  });

  it("shows an empty state", () => {
    mockFeedback({ data: [] });

    render(<RatingFeedback />);

    expect(screen.getByText("No written feedback in this period yet.")).toBeInTheDocument();
  });

  it("explains a permission error", () => {
    mockFeedback({ error: new hooks.ApiError(403) });

    render(<RatingFeedback />);

    expect(screen.getByText("Feedback requires Support Engineer or Leadership access.")).toBeInTheDocument();
  });

  it("shows a failure without pretending there is no feedback", () => {
    mockFeedback({ error: new hooks.ApiError(500) });

    render(<RatingFeedback />);

    expect(screen.getByText("Couldn't load feedback. Try again later.")).toBeInTheDocument();
    expect(screen.queryByText("Feedback requires Support Engineer or Leadership access.")).not.toBeInTheDocument();
    expect(within(tile("Feedback received")).getByText("—")).toBeInTheDocument();
    expect(within(tile("Needs attention")).queryByText("0")).not.toBeInTheDocument();
    expect(screen.queryByText("0 of 0")).not.toBeInTheDocument();
  });

  it("shows placeholders rather than zeros while loading", () => {
    mockFeedback({ isLoading: true });

    render(<RatingFeedback />);

    expect(screen.getAllByTestId("stat-loading")).toHaveLength(4);
    expect(screen.getByTestId("feedback-loading")).toBeInTheDocument();
    expect(within(tile("Feedback received")).queryByText("0")).not.toBeInTheDocument();
    expect(screen.queryByText("0 of 0")).not.toBeInTheDocument();
  });
});

function tile(label: string): HTMLElement {
  return screen.getByText(label, { selector: "p" }).parentElement!;
}
