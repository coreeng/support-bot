/**
 * Wire types for troubleshooting sessions (`/troubleshooting/ticket/{id}/session`).
 * Mirrors `com.coreeng.supportbot.troubleshooting.rest.TroubleshootingSessionUI`.
 */

export type TroubleshootingMode = "MANUAL" | "AUTOMATIC";

export type TroubleshootingEventType =
  | "SLACK_MESSAGE"
  | "ENGINEER_MESSAGE"
  | "ROUTER_MESSAGE"
  | "PROPOSAL"
  | "DECISION"
  | "RUN_UPDATE"
  | "ROUTER_STATUS";

export type RouterAction = "REPLY" | "INVESTIGATE" | "CLOSE";

export type DecisionKind = "APPROVED" | "REJECTED" | "SUPERSEDED";

export type RouterState = "THINKING" | "IDLE" | "FAILED";

export interface RouterUsage {
  model: string;
  inputTokens: number;
  outputTokens: number;
  durationMs: number;
}

export interface SlackMessagePayload {
  ts: string | null;
  userId: string | null;
  text: string;
  available: boolean;
}

export interface EngineerMessagePayload {
  text: string;
}

export interface RouterMessagePayload extends RouterUsage {
  text: string;
}

export interface ProposalPayload extends RouterUsage {
  action: RouterAction;
  text: string;
  reason: string;
}

export interface DecisionPayload {
  proposalSeq: number;
  decision: DecisionKind;
  text: string | null;
}

export interface RunUpdatePayload {
  kind: string;
  text: string;
}

export interface RouterStatusPayload {
  state: RouterState;
  error: string | null;
}

interface EventBase<T extends TroubleshootingEventType, P> {
  seq: number;
  type: T;
  /** Engineer email, `router`, `slack:<user id>` or `system`. */
  actor: string;
  payload: P;
  createdAt: string;
}

export type TroubleshootingEvent =
  | EventBase<"SLACK_MESSAGE", SlackMessagePayload>
  | EventBase<"ENGINEER_MESSAGE", EngineerMessagePayload>
  | EventBase<"ROUTER_MESSAGE", RouterMessagePayload>
  | EventBase<"PROPOSAL", ProposalPayload>
  | EventBase<"DECISION", DecisionPayload>
  | EventBase<"RUN_UPDATE", RunUpdatePayload>
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
