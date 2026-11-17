import type { DecisionKind, RouterAction, RouterUsage, TroubleshootingEvent, TroubleshootingSession } from "@/lib/types/troubleshooting";

export type ProposalState = "PENDING" | DecisionKind;

interface ItemBase {
  seq: number;
  actor: string;
  createdAt: string;
}

export interface EngineerMessageItem extends ItemBase {
  kind: "engineer-message";
  text: string;
}

export interface RouterMessageItem extends ItemBase {
  kind: "router-message";
  text: string;
  usage: RouterUsage;
}

export interface SlackMessageItem extends ItemBase {
  kind: "slack-message";
  userId: string | null;
  text: string;
  available: boolean;
}

export interface ProposalItem extends ItemBase {
  kind: "proposal";
  action: RouterAction;
  text: string;
  reason: string;
  usage: RouterUsage;
  state: ProposalState;
  decidedBy: string | null;
  decidedText: string | null;
}

export interface RunUpdateItem extends ItemBase {
  kind: "run-update";
  runKind: string;
  text: string;
}

export interface RouterFailedItem extends ItemBase {
  kind: "router-failed";
  error: string;
}

export type SessionItem = EngineerMessageItem | RouterMessageItem | SlackMessageItem | ProposalItem | RunUpdateItem | RouterFailedItem;

export interface SessionView {
  items: SessionItem[];
  routerBusy: boolean;
  lastSeq: number;
}

function usageOf(payload: RouterUsage): RouterUsage {
  return {
    model: payload.model,
    inputTokens: payload.inputTokens,
    outputTokens: payload.outputTokens,
    durationMs: payload.durationMs,
  };
}

/** Turns the event log into what the timeline shows. Decisions fold into their proposal; status events only drive `routerBusy`. */
export function buildSessionView(events: readonly TroubleshootingEvent[]): SessionView {
  const items: SessionItem[] = [];
  const proposals = new Map<number, ProposalItem>();
  let routerBusy = false;
  let lastSeq = 0;

  for (const event of events) {
    lastSeq = Math.max(lastSeq, event.seq);
    const base = { seq: event.seq, actor: event.actor, createdAt: event.createdAt };
    switch (event.type) {
      case "ENGINEER_MESSAGE":
        items.push({ ...base, kind: "engineer-message", text: event.payload.text });
        break;
      case "ROUTER_MESSAGE":
        items.push({ ...base, kind: "router-message", text: event.payload.text, usage: usageOf(event.payload) });
        break;
      case "SLACK_MESSAGE":
        items.push({
          ...base,
          kind: "slack-message",
          userId: event.payload.userId,
          text: event.payload.text,
          available: event.payload.available,
        });
        break;
      case "PROPOSAL": {
        const proposal: ProposalItem = {
          ...base,
          kind: "proposal",
          action: event.payload.action,
          text: event.payload.text,
          reason: event.payload.reason,
          usage: usageOf(event.payload),
          state: "PENDING",
          decidedBy: null,
          decidedText: null,
        };
        proposals.set(event.seq, proposal);
        items.push(proposal);
        break;
      }
      case "DECISION": {
        const proposal = proposals.get(event.payload.proposalSeq);
        if (proposal && proposal.state === "PENDING") {
          proposal.state = event.payload.decision;
          proposal.decidedBy = event.actor;
          proposal.decidedText = event.payload.text;
        }
        break;
      }
      case "RUN_UPDATE":
        items.push({ ...base, kind: "run-update", runKind: event.payload.kind, text: event.payload.text });
        break;
      case "ROUTER_STATUS":
        routerBusy = event.payload.state === "THINKING";
        if (event.payload.state === "FAILED") {
          items.push({ ...base, kind: "router-failed", error: event.payload.error ?? "The router could not answer." });
        }
        break;
    }
  }

  return { items, routerBusy, lastSeq };
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
  if (actor === "router") return "Router";
  if (actor === "system") return "System";
  if (actor.startsWith("slack:")) return actor.slice("slack:".length);
  const at = actor.indexOf("@");
  return at > 0 ? actor.slice(0, at) : actor;
}
