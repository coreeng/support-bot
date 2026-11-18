/**
 * Wire types for troubleshooting sessions (`/troubleshooting/ticket/{id}/session`).
 * Mirrors `com.coreeng.supportbot.troubleshooting.rest.TroubleshootingSessionUI`.
 */

export type TroubleshootingMode = "MANUAL" | "AUTOMATIC";

export type TroubleshootingEventType = "SLACK_MESSAGE";

export interface SlackMessagePayload {
  ts: string | null;
  text: string;
  available: boolean;
}

interface EventBase<T extends TroubleshootingEventType, P> {
  seq: number;
  type: T;
  /** `slack:<user id>`, `system`, or `erased` once an erasure request has removed who it was. */
  actor: string;
  payload: P;
  createdAt: string;
}

export type TroubleshootingEvent = EventBase<"SLACK_MESSAGE", SlackMessagePayload>;

export interface TroubleshootingSession {
  id: number;
  ticketId: number;
  mode: TroubleshootingMode;
  lastSeq: number;
  createdAt: string;
  events: TroubleshootingEvent[];
}

export interface TroubleshootingStatus {
  enabled: boolean;
}
