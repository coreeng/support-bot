import type { TroubleshootingEvent, TroubleshootingSession } from "@/lib/types/troubleshooting";

const at = "2026-11-18T10:00:00Z";

export const slackEvents: TroubleshootingEvent[] = [
  { seq: 1, type: "SLACK_MESSAGE", actor: "slack:U1", createdAt: at, payload: { ts: "1.1", text: "Pods are pending", available: true } },
  { seq: 2, type: "SLACK_MESSAGE", actor: "slack:U2", createdAt: at, payload: { ts: "1.2", text: "Same in staging", available: true } },
  { seq: 3, type: "SLACK_MESSAGE", actor: "slack:bot:B1", createdAt: at, payload: { ts: "1.3", text: "Ticket created", available: true } },
];

export const threadNotAvailable: TroubleshootingEvent = {
  seq: 1,
  type: "SLACK_MESSAGE",
  actor: "system",
  createdAt: at,
  payload: { ts: null, text: "The query's Slack thread could not be read.", available: false },
};

export function sessionWith(list: TroubleshootingEvent[]): TroubleshootingSession {
  return { id: 1, ticketId: 42, mode: "MANUAL", lastSeq: list[list.length - 1]?.seq ?? 0, createdAt: at, events: list };
}
