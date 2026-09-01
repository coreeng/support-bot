/**
 * Wire types for the Support Summary page (`GET /summary`).
 * Mirrors `com.coreeng.supportbot.summary.rest.SummaryUI`.
 */

/** One of the newest tickets carrying a breakdown value; opens the ticket when clicked. */
export interface SummaryTicket {
  ticketId: string;
  /** The classifier's one-line reason for the ticket; empty when not yet classified. */
  text: string;
  timestamp: string;
}

/** One bar of a ranked breakdown. */
export interface SummaryCount {
  label: string;
  count: number;
  /** Up to five of the newest tickets carrying this value, newest first. */
  recent: SummaryTicket[];
  /** Teams only: the product (by product tag) this team's tickets most often carry. */
  topProduct?: string;
}

/** Progress while the backfill classifies threads, then while the prose is generated. */
export interface SummaryProgress {
  phase: "classifying" | "summarising";
  analysedThreads?: number;
  totalThreads?: number;
}

/** The prose section, which carries its own state and never blocks the breakdowns. */
export interface SummarySection {
  state: "ready" | "generating" | "unavailable";
  content?: string;
  model?: string;
  generatedAt?: string;
  progress?: SummaryProgress;
  error?: string;
}

export interface SummaryData {
  from: string;
  to: string;
  totalTickets: number;
  classifiedTickets: number;
  unclassifiedTickets: number;
  drivers: SummaryCount[];
  categories: SummaryCount[];
  /** Categories of the window's Knowledge Gap tickets — the knowledge-gaps page's widget, scoped to the window. */
  knowledgeGaps: SummaryCount[];
  features: SummaryCount[];
  teams: SummaryCount[];
  /** Tickets per product tag ("Product - <name>"), once per ticket per product; untagged tickets have no row. */
  products: SummaryCount[];
  summary: SummarySection;
}

export interface SummaryStatus {
  enabled: boolean;
}
