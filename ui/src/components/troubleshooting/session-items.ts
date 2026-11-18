import type { TroubleshootingEvent, TroubleshootingSession } from "@/lib/types/troubleshooting";

interface ItemBase {
  seq: number;
  actor: string;
  createdAt: string;
}

export interface SlackMessageItem extends ItemBase {
  kind: "slack-message";
  text: string;
  available: boolean;
}

export type SessionItem = SlackMessageItem;

export interface SessionView {
  items: SessionItem[];
  lastSeq: number;
}

/** Turns the event log into what the timeline shows. */
export function buildSessionView(events: readonly TroubleshootingEvent[]): SessionView {
  const items: SessionItem[] = [];
  let lastSeq = 0;

  for (const event of events) {
    lastSeq = Math.max(lastSeq, event.seq);
    const base = { seq: event.seq, actor: event.actor, createdAt: event.createdAt };
    switch (event.type) {
      case "SLACK_MESSAGE":
        items.push({ ...base, kind: "slack-message", text: event.payload.text, available: event.payload.available });
        break;
    }
  }

  return { items, lastSeq };
}

/** Adds events to a session, keeping order by `seq` and dropping any already present. */
export function mergeEvents(session: TroubleshootingSession, incoming: readonly TroubleshootingEvent[]): TroubleshootingSession {
  const known = new Set(session.events.map((event) => event.seq));
  const added = incoming.filter((event) => !known.has(event.seq));
  if (added.length === 0) return session;
  const events = [...session.events, ...added].sort((a, b) => a.seq - b.seq);
  return { ...session, events, lastSeq: Math.max(session.lastSeq, events[events.length - 1].seq) };
}

/**
 * Returns a function that keeps the previous object for an item whose content did not change, so
 * the runtime's per-message conversion cache holds and unchanged messages do not re-render.
 */
export function createItemReuser(): (next: readonly SessionItem[]) => SessionItem[] {
  let previous = new Map<number, SessionItem>();
  return (next) => {
    const stable = next.map((item) => {
      const before = previous.get(item.seq);
      return before && JSON.stringify(before) === JSON.stringify(item) ? before : item;
    });
    previous = new Map(stable.map((item) => [item.seq, item]));
    return stable;
  };
}

/** `alex@example.com` reads better as `alex`; other actors are shown as they are. */
export function actorLabel(actor: string): string {
  if (actor === "system") return "System";
  if (actor.startsWith("slack:")) return actor.slice("slack:".length);
  const at = actor.indexOf("@");
  return at > 0 ? actor.slice(0, at) : actor;
}
