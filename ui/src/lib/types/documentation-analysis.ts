/**
 * Wire types for the Documentation Analysis page (`GET /documentation-analysis`).
 * Mirrors `com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI`. A field the backend has no
 * value for is left out of the JSON, never sent as `null`.
 */

export type DocClassification = "incorrect" | "missing" | "correct" | "not_applicable";

export interface DocAnalysisCounts {
  /** Closed tickets in the window. */
  total: number;
  correct: number;
  missing: number;
  incorrect: number;
  notApplicable: number;
  /** Tickets without a current result; the classifications and this sum to `total`. */
  unclassified: number;
  /** Those of `unclassified` whose last attempt failed. */
  failed: number;
}

export interface DocAnalysisAreaCounts {
  /** Absent for the tickets that count under no area; that entry comes last. */
  area?: string;
  total: number;
  correct: number;
  missing: number;
  incorrect: number;
  notApplicable: number;
}

export interface DocAnalysisPageGroup {
  page: string;
  count: number;
  correct: number;
  incorrect: number;
  /** In the order of `results`. */
  ticketIds: string[];
}

export interface DocAnalysisAreaGroup {
  /** Absent for the tickets that count under no area; that entry comes last. */
  area?: string;
  count: number;
  /** In the order of `results`. */
  ticketIds: string[];
}

export interface DocAnalysisResult {
  ticketId: string;
  channelId: string;
  /** Slack timestamp of the thread's opening message. */
  queryTs: string;
  raisedAt: string;
  classification: DocClassification;
  question: string;
  /** What support answered, when the model gave it. */
  answerSummary?: string;
  tags: string[];
  /** The one page cited for `correct` and `incorrect`; the closest pages, if any, for `missing`. */
  docPaths: string[];
  /** The quoted passage of the cited page, for `correct` and `incorrect`. */
  evidenceQuote?: string;
  rationale: string;
  /** The area the ticket counts under; absent for none. */
  productArea?: string;
  /** The areas the ticket counts under, `productArea` first. */
  productAreas: string[];
  /** The documentation version the result was made against. */
  docsVersion?: string;
  analysedAt: string;
}

export type DocAnalysisRunState =
  | "running"
  | "busy"
  | "idle"
  | "source_unavailable"
  | "provider_outage"
  | "daily_limit_reached"
  | "failed"
  | "no_prompt";

export interface DocAnalysisProgress {
  /** The run's window, which for `busy` is another window than the one requested. */
  from: string;
  to: string;
  /** Tickets attempted so far. */
  done: number;
  /** Tickets due when the run started; zero until it has counted them. */
  total: number;
}

export interface DocAnalysisRun {
  state: DocAnalysisRunState;
  progress?: DocAnalysisProgress;
  /** Why the source is unavailable; safe to show. */
  reason?: string;
  /** During a provider outage: no run starts before it. */
  retryAt?: string;
}

export interface DocAnalysisDocumentation {
  version: string;
  refreshedAt: string;
}

export interface DocAnalysisData {
  from: string;
  to: string;
  counts: DocAnalysisCounts;
  productAreas: DocAnalysisAreaCounts[];
  citedPages: DocAnalysisPageGroup[];
  missingByArea: DocAnalysisAreaGroup[];
  /** `incorrect`, then `missing`, `correct` and `not_applicable`; newest first within each. */
  results: DocAnalysisResult[];
  run: DocAnalysisRun;
  /** Absent while the documentation source holds nothing yet. */
  documentation?: DocAnalysisDocumentation;
}

export interface DocAnalysisStatus {
  enabled: boolean;
}
