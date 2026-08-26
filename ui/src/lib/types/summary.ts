/**
 * Wire types for the Support Summary page (`GET /summary`).
 * Mirrors `com.coreeng.supportbot.summary.rest.SummaryUI`.
 */

/** One bar of a ranked breakdown. */
export interface SummaryCount {
  label: string;
  count: number;
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
  features: SummaryCount[];
  teams: SummaryCount[];
  summary: SummarySection;
}

export interface SummaryStatus {
  enabled: boolean;
}
