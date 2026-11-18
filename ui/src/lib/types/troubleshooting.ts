/**
 * Wire types for troubleshooting sessions (`/troubleshooting/ticket/{id}/session`).
 * Mirrors `com.coreeng.supportbot.troubleshooting.rest.TroubleshootingSessionUI`.
 */

export type TroubleshootingMode = "MANUAL" | "AUTOMATIC";

export type TroubleshootingEventType = "SLACK_MESSAGE" | "ENGINEER_MESSAGE" | "ROUTER_MESSAGE" | "ROUTER_STATUS";

export type RouterState = "THINKING" | "IDLE" | "FAILED";

export interface RouterUsage {
  model: string;
  inputTokens: number;
  outputTokens: number;
  durationMs: number;
}

export interface SlackMessagePayload {
  ts: string | null;
  text: string;
  available: boolean;
}

export interface EngineerMessagePayload {
  text: string;
}

export interface RouterMessagePayload extends RouterUsage {
  text: string;
}

export interface RouterStatusPayload {
  state: RouterState;
  error: string | null;
}

interface EventBase<T extends TroubleshootingEventType, P> {
  seq: number;
  type: T;
  /** Engineer email, `router`, `slack:<user id>`, `system`, or `erased` once an erasure request has removed who it was. */
  actor: string;
  payload: P;
  createdAt: string;
}

export type TroubleshootingEvent =
  | EventBase<"SLACK_MESSAGE", SlackMessagePayload>
  | EventBase<"ENGINEER_MESSAGE", EngineerMessagePayload>
  | EventBase<"ROUTER_MESSAGE", RouterMessagePayload>
  | EventBase<"ROUTER_STATUS", RouterStatusPayload>;

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
