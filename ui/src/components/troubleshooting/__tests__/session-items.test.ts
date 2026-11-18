import type { TroubleshootingEvent } from "@/lib/types/troubleshooting";
import { events, sessionWith, slackEvents, threadNotAvailable } from "../../../test-utils/troubleshooting-fixtures";
import { actorLabel, buildSessionView, createItemReuser, mergeEvents } from "../session-items";

describe("buildSessionView", () => {
  it("keeps one item per visible event, in order, and hides status events", () => {
    const view = buildSessionView(events);

    expect(view.items.map((item) => [item.seq, item.kind])).toEqual([
      [1, "slack-message"],
      [3, "router-message"],
      [5, "engineer-message"],
      [6, "engineer-message"],
      [7, "proposal"],
    ]);
    expect(view.routerBusy).toBe(false);
    expect(view.lastSeq).toBe(7);
  });

  it("is busy while the last router status is THINKING", () => {
    expect(buildSessionView(events.slice(0, 2)).routerBusy).toBe(true);
  });

  it("folds the first decision into its proposal and ignores later ones", () => {
    const decided: TroubleshootingEvent[] = [
      ...events,
      {
        seq: 8,
        type: "DECISION",
        actor: "bob@example.com",
        createdAt: "",
        payload: { proposalSeq: 7, decision: "APPROVED", text: "Edited" },
      },
      {
        seq: 9,
        type: "DECISION",
        actor: "alice@example.com",
        createdAt: "",
        payload: { proposalSeq: 7, decision: "REJECTED", text: null },
      },
    ];

    const proposal = buildSessionView(decided).items.find((item) => item.kind === "proposal");

    expect(proposal).toMatchObject({ state: "APPROVED", decidedBy: "bob@example.com", decidedText: "Edited" });
  });

  it("shows a failed router turn as a note", () => {
    const failed: TroubleshootingEvent[] = [
      { seq: 1, type: "ROUTER_STATUS", actor: "router", createdAt: "", payload: { state: "FAILED", error: "bad JSON" } },
    ];

    expect(buildSessionView(failed).items).toEqual([expect.objectContaining({ kind: "router-failed", error: "bad JSON" })]);
  });

  it("keeps one item per Slack message, in order", () => {
    const view = buildSessionView(slackEvents);

    expect(view.items.map((item) => [item.seq, item.kind, item.actor])).toEqual([
      [1, "slack-message", "slack:U1"],
      [2, "slack-message", "slack:U2"],
      [3, "slack-message", "slack:bot:B1"],
    ]);
    expect(view.lastSeq).toBe(3);
  });

  it("keeps the note that the thread could not be read", () => {
    expect(buildSessionView([threadNotAvailable]).items).toEqual([expect.objectContaining({ kind: "slack-message", available: false })]);
  });
});

describe("mergeEvents", () => {
  it("adds new events in seq order and drops ones already held", () => {
    const merged = mergeEvents(sessionWith(slackEvents.slice(0, 1)), [slackEvents[2], slackEvents[0], slackEvents[1]]);

    expect(merged.events.map((event) => event.seq)).toEqual([1, 2, 3]);
    expect(merged.lastSeq).toBe(3);
  });

  it("returns the same session when nothing is new", () => {
    const session = sessionWith(slackEvents);

    expect(mergeEvents(session, [slackEvents[0]])).toBe(session);
  });
});

describe("createItemReuser", () => {
  it("keeps the previous object for unchanged items", () => {
    const reuse = createItemReuser();
    const first = reuse(buildSessionView(slackEvents).items);
    const second = reuse(buildSessionView(slackEvents).items);

    expect(second[0]).toBe(first[0]);
    expect(second[2]).toBe(first[2]);
  });
});

describe("actorLabel", () => {
  it("shows Slack ids without their prefix and keeps erased actors readable", () => {
    expect(actorLabel("slack:U1")).toBe("U1");
    expect(actorLabel("system")).toBe("System");
    expect(actorLabel("router")).toBe("Router");
    expect(actorLabel("alice@example.com")).toBe("alice");
    expect(actorLabel("erased")).toBe("erased");
  });
});
