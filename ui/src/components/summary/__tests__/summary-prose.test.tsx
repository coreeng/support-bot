import { act, fireEvent, render, screen, within } from "@testing-library/react";
import type { SummarySegment } from "../../../lib/types/summary";
import SummaryProse from "../summary-prose";

const cited: SummarySegment[] = [
  { text: "Tenants needed " },
  {
    text: "documentation on X",
    tickets: [
      {
        ticketId: "11",
        text: "Could not find how X is configured.",
        timestamp: "2026-03-12T09:00:00Z",
        channelId: "C123",
        queryTs: "1710234000.000100",
      },
      { ticketId: "12", text: "", timestamp: "2026-03-11T09:00:00Z", channelId: "C123", queryTs: "1710147600.000200" },
    ],
  },
  { text: " and fixes for\nrecurring problems." },
];

function renderProse(segments?: SummarySegment[], content = "Tenants needed documentation on X and fixes for\nrecurring problems.") {
  const onOpenTicket = jest.fn();
  render(<SummaryProse content={content} segments={segments} onOpenTicket={onOpenTicket} />);
  return { onOpenTicket };
}

const claim = () => screen.getByRole("button", { name: "documentation on X" });
const evidence = () => screen.queryByTestId("summary-evidence");

describe("SummaryProse", () => {
  // jsdom has no PointerEvent, so fired pointer events would carry no pointerType and every hover
  // would look like neither mouse nor touch.
  const originalPointerEvent = window.PointerEvent;
  beforeAll(() => {
    class TestPointerEvent extends MouseEvent {
      readonly pointerType: string;
      constructor(type: string, init: PointerEventInit = {}) {
        super(type, init);
        this.pointerType = init.pointerType ?? "";
      }
    }
    window.PointerEvent = TestPointerEvent as unknown as typeof PointerEvent;
  });
  afterAll(() => {
    window.PointerEvent = originalPointerEvent;
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders plain prose when there are no segments", () => {
    renderProse(undefined, "Just prose.");

    expect(screen.getByTestId("summary-prose")).toHaveTextContent("Just prose.");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("renders the cited prose as one paragraph with the claim as a trigger", () => {
    renderProse(cited);

    expect(screen.getByTestId("summary-prose").textContent).toBe("Tenants needed documentation on X and fixes for\nrecurring problems.");
    expect(screen.getAllByTestId("summary-cited-claim")).toHaveLength(1);
  });

  it("lists the cited tickets with their reason, date and Slack thread when clicked", async () => {
    renderProse(cited);

    fireEvent.click(claim());

    const popover = await screen.findByTestId("summary-evidence");
    expect(within(popover).getByText("Cited by the model ·", { exact: false })).toHaveTextContent("2 tickets");
    expect(within(popover).getByText("Could not find how X is configured.")).toBeInTheDocument();
    // A ticket the classifier gave no reason still gets a readable row.
    expect(within(popover).getByText("Ticket 12")).toBeInTheDocument();
    expect(within(popover).getByRole("link", { name: "Open ticket 11 in Slack" })).toHaveAttribute(
      "href",
      "https://slack.com/archives/C123/p1710234000000100"
    );
    expect(within(popover).getByRole("link", { name: "Open ticket 11 in Slack" })).toHaveAttribute("target", "_blank");
  });

  it("opens the ticket and closes the popover from View ticket", async () => {
    const { onOpenTicket } = renderProse(cited);
    fireEvent.click(claim());

    fireEvent.click(within(await screen.findByTestId("summary-evidence")).getByRole("button", { name: "View ticket 12" }));

    expect(onOpenTicket).toHaveBeenCalledWith("12");
    expect(evidence()).not.toBeInTheDocument();
  });

  it("toggles closed on a second click and closes on Escape", async () => {
    renderProse(cited);

    fireEvent.click(claim());
    await screen.findByTestId("summary-evidence");
    fireEvent.click(claim());
    expect(evidence()).not.toBeInTheDocument();

    fireEvent.click(claim());
    fireEvent.keyDown(await screen.findByTestId("summary-evidence"), { key: "Escape" });
    expect(evidence()).not.toBeInTheDocument();
  });

  it("opens on mouse hover after a short delay and closes when the pointer leaves", () => {
    jest.useFakeTimers();
    renderProse(cited);

    fireEvent.pointerEnter(claim(), { pointerType: "mouse" });
    expect(evidence()).not.toBeInTheDocument();
    act(() => jest.advanceTimersByTime(200));
    expect(evidence()).toBeInTheDocument();

    // Moving into the popover keeps it open; leaving it closes it.
    fireEvent.pointerLeave(claim(), { pointerType: "mouse" });
    fireEvent.pointerEnter(evidence() as HTMLElement, { pointerType: "mouse" });
    act(() => jest.advanceTimersByTime(500));
    expect(evidence()).toBeInTheDocument();

    fireEvent.pointerLeave(evidence() as HTMLElement, { pointerType: "mouse" });
    act(() => jest.advanceTimersByTime(500));
    expect(evidence()).not.toBeInTheDocument();
  });

  it("a click on a hover-opened popover pins it open", () => {
    jest.useFakeTimers();
    renderProse(cited);

    fireEvent.pointerEnter(claim(), { pointerType: "mouse" });
    act(() => jest.advanceTimersByTime(200));
    fireEvent.click(claim());
    fireEvent.pointerLeave(claim(), { pointerType: "mouse" });
    act(() => jest.advanceTimersByTime(500));

    expect(evidence()).toBeInTheDocument();
  });

  it("ignores touch hover, so the tap that follows opens it rather than toggling it shut", () => {
    jest.useFakeTimers();
    renderProse(cited);

    fireEvent.pointerEnter(claim(), { pointerType: "touch" });
    act(() => jest.advanceTimersByTime(500));
    expect(evidence()).not.toBeInTheDocument();

    fireEvent.click(claim());
    expect(evidence()).toBeInTheDocument();
  });
});
