"use client";

import ResultItem, { CLASSIFICATION_LABELS, NO_AREA_LABEL, shortVersion } from "@/components/documentation-analysis/result-item";
import BreakdownCard from "@/components/summary/breakdown-card";
import {
  chosenWindow,
  formatWindow,
  PRESET_LABELS,
  presetForWindow,
  WINDOW_PARAM_DEFAULTS,
  WINDOW_PARAM_VALIDATORS,
  WindowPicker,
} from "@/components/summary/window-picker";
import EditTicketModal from "@/components/tickets/EditTicketModal";
import { SingleSelectFilter } from "@/components/ui/single-select-filter";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { isApiError, MAX_SUMMARY_POLL_FAILURES, useDocAnalysis, useDocAnalysisEnabled } from "@/lib/hooks";
import { useUrlParams } from "@/lib/hooks/useUrlParams";
import type {
  DocAnalysisData,
  DocAnalysisDocumentation,
  DocAnalysisResult,
  DocAnalysisRun,
  DocClassification,
} from "@/lib/types/documentation-analysis";
import type { SummaryCount } from "@/lib/types/summary";
import { cn, formatUtcDateTime } from "@/lib/utils";
import { MAX_SUMMARY_WINDOW_DAYS } from "@/lib/utils/summary-window";
import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";

/** Shown both when the feature flag reports the page off and when the backend answers 404. */
export const DOC_ANALYSIS_NOT_ENABLED = {
  title: "Documentation Analysis is not enabled",
  detail: "Enable documentation analysis on the server to use this page.",
} as const;

export function docAnalysisErrorMessage(error: unknown): { title: string; detail: string } {
  if (isApiError(error, 403)) {
    return { title: "You do not have permission to view the Documentation Analysis", detail: "Ask an administrator for access." };
  }
  if (isApiError(error, 404)) {
    return DOC_ANALYSIS_NOT_ENABLED;
  }
  if (isApiError(error) && error.reason === "DOCUMENTATION_ANALYSIS_WINDOW_INVALID") {
    return {
      title: "The selected range is invalid",
      detail:
        error.detail ?? `The window must not exceed ${MAX_SUMMARY_WINDOW_DAYS} days, and the end date must not be before the start date.`,
    };
  }
  return { title: "Error loading the documentation analysis", detail: "Please try again later" };
}

const FILTER_ORDER: DocClassification[] = ["incorrect", "missing", "correct", "not_applicable"];

function Metric({ children }: { children: ReactNode }) {
  return <span className="font-mono tabular-nums">{children}</span>;
}

function Spinner() {
  return <span className="border-primary inline-block h-4 w-4 shrink-0 animate-spin rounded-full border-b-2" />;
}

function Notice({ tone, children }: { tone: "progress" | "warning" | "destructive"; children: ReactNode }) {
  return (
    <div className="bg-card rounded-xl border p-6" role="status" data-testid="doc-analysis-run">
      <div
        className={cn(
          "flex items-start gap-3 text-sm",
          tone === "warning" && "text-warning",
          tone === "destructive" && "text-destructive",
          tone === "progress" && "text-foreground"
        )}
      >
        {tone === "progress" ? <Spinner /> : <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />}
        <div className="min-w-0 flex-1 space-y-1">{children}</div>
      </div>
    </div>
  );
}

function ProgressBar({ done, total }: { done: number; total: number }) {
  const percent = Math.min(100, Math.round((done / total) * 100));
  return (
    <div className="flex items-center gap-3 pt-2">
      <div className="bg-muted h-2.5 flex-1 overflow-hidden rounded-full">
        <div className="bg-secondary h-full rounded-full transition-all duration-500 ease-out" style={{ width: `${percent}%` }} />
      </div>
      <span className="text-foreground font-mono text-sm font-medium tabular-nums">{percent}%</span>
    </div>
  );
}

/** What the run is doing for this window; nothing when it is idle. Saved results stay on the page below. */
function RunNotice({ run }: { run: DocAnalysisRun }) {
  const progress = run.progress;
  switch (run.state) {
    case "running":
      return (
        <Notice tone="progress">
          {progress && progress.total > 0 ? (
            <>
              <p className="font-medium">
                Classifying tickets against the documentation... <Metric>{progress.done.toLocaleString()}</Metric> of{" "}
                <Metric>{progress.total.toLocaleString()}</Metric> done
              </p>
              <ProgressBar done={progress.done} total={progress.total} />
            </>
          ) : (
            <p className="font-medium">Finding the tickets to classify...</p>
          )}
          <p className="text-muted-foreground">Results appear below as they are saved. A large window fills in over time.</p>
        </Notice>
      );
    case "busy":
      return (
        <Notice tone="progress">
          {progress ? (
            <p className="font-medium">
              Another window is being analysed ({formatWindow(progress.from, progress.to)})
              {progress.total > 0 && (
                <>
                  : <Metric>{progress.done.toLocaleString()}</Metric> of <Metric>{progress.total.toLocaleString()}</Metric> done
                </>
              )}
              .
            </p>
          ) : (
            <p className="font-medium">Another analysis is running.</p>
          )}
          <p className="text-muted-foreground">Only one runs at a time. This window starts once it finishes, while the page is open.</p>
        </Notice>
      );
    case "source_unavailable":
      return (
        <Notice tone="destructive">
          <p className="font-medium">The documentation could not be read, so no tickets are being classified.</p>
          {run.reason && <p className="text-muted-foreground">{run.reason}</p>}
        </Notice>
      );
    case "provider_outage":
      return (
        <Notice tone="warning">
          <p className="font-medium">The LLM provider is failing, so classification has paused.</p>
          <p className="text-muted-foreground">
            {run.retryAt ? `It resumes on a visit after ${formatUtcDateTime(run.retryAt)} UTC.` : "It resumes on a later visit."}
          </p>
        </Notice>
      );
    case "daily_limit_reached":
      return (
        <Notice tone="warning">
          <p className="font-medium">The daily limit on tickets classified has been reached.</p>
          <p className="text-muted-foreground">The rest of this window is classified on a later visit, once the limit allows.</p>
        </Notice>
      );
    case "failed":
      return (
        <Notice tone="destructive">
          <p className="font-medium">The analysis run failed.</p>
          <p className="text-muted-foreground">Reload the page to try again.</p>
        </Notice>
      );
    case "no_prompt":
      return (
        <Notice tone="destructive">
          <p className="font-medium">No documentation classification prompt is in use, so no ticket can be classified.</p>
          <p className="text-muted-foreground">An administrator needs to put a prompt version in use.</p>
        </Notice>
      );
    case "idle":
      return null;
  }
}

function DocumentationLine({ documentation }: { documentation?: DocAnalysisDocumentation }) {
  return (
    <p className="text-muted-foreground text-sm" data-testid="doc-analysis-documentation">
      {documentation ? (
        <>
          Documentation version{" "}
          <span className="text-foreground font-mono font-semibold" title={documentation.version}>
            {shortVersion(documentation.version)}
          </span>
          , last refreshed <span className="text-foreground">{formatUtcDateTime(documentation.refreshedAt)} UTC</span>
        </>
      ) : (
        "The documentation has not been fetched yet."
      )}
    </p>
  );
}

const STAT_ACCENTS = {
  destructive: "bg-destructive/15",
  warning: "bg-warning/15",
  success: "bg-success/15",
  info: "bg-info/15",
  purple: "bg-chart-4/15",
} as const;

function StatCard({
  label,
  value,
  description,
  accent,
  testId,
}: {
  label: string;
  value: number;
  description: string;
  accent: keyof typeof STAT_ACCENTS;
  testId: string;
}) {
  return (
    <div className="bg-card relative overflow-hidden rounded-xl border p-6" data-testid={testId}>
      <div className={`${STAT_ACCENTS[accent]} absolute -top-4 -right-4 h-24 w-24 rounded-full`} />
      <div className={`${STAT_ACCENTS[accent]} absolute -right-6 -bottom-6 h-20 w-20 rounded-full`} />
      <div className="relative">
        <p className="text-muted-foreground mb-2 text-sm font-medium">{label}</p>
        <p className="text-foreground font-mono text-3xl font-semibold tracking-tight tabular-nums">{value.toLocaleString()}</p>
        <p className="text-muted-foreground mt-1 text-xs">{description}</p>
      </div>
    </div>
  );
}

function Counts({ data }: { data: DocAnalysisData }) {
  const { counts } = data;
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
      <StatCard
        label={CLASSIFICATION_LABELS.incorrect}
        value={counts.incorrect}
        description="The documentation and support's answer disagree"
        accent="destructive"
        testId="doc-analysis-count-incorrect"
      />
      <StatCard
        label={CLASSIFICATION_LABELS.missing}
        value={counts.missing}
        description="The documentation does not cover the question"
        accent="warning"
        testId="doc-analysis-count-missing"
      />
      <StatCard
        label={CLASSIFICATION_LABELS.correct}
        value={counts.correct}
        description="The documentation answers the question"
        accent="success"
        testId="doc-analysis-count-correct"
      />
      <StatCard
        label={CLASSIFICATION_LABELS.not_applicable}
        value={counts.notApplicable}
        description="Not a question for the documentation"
        accent="info"
        testId="doc-analysis-count-not-applicable"
      />
      <StatCard
        label="Not yet classified"
        value={counts.unclassified}
        description={counts.failed > 0 ? `${counts.failed.toLocaleString()} of them failed to classify` : "None failed to classify"}
        accent="purple"
        testId="doc-analysis-count-unclassified"
      />
    </div>
  );
}

function ProductAreaTable({ data }: { data: DocAnalysisData }) {
  return (
    <div className="bg-card rounded-xl border p-6" data-testid="doc-analysis-product-areas">
      <h2 className="text-foreground mb-4 text-base font-semibold">By product area</h2>
      {data.productAreas.length === 0 ? (
        <p className="text-muted-foreground p-16 text-center text-sm">No ticket in this window has been classified yet.</p>
      ) : (
        <Table>
          <TableHeader className="bg-muted">
            <TableRow>
              <TableHead>Product area</TableHead>
              <TableHead className="text-right">Conflicts</TableHead>
              <TableHead className="text-right">Missing</TableHead>
              <TableHead className="text-right">Correct</TableHead>
              <TableHead className="text-right">Not applicable</TableHead>
              <TableHead className="text-right">Total</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.productAreas.map((area) => (
              <TableRow key={area.area ?? ""}>
                <TableCell className={cn(!area.area && "text-muted-foreground")}>{area.area ?? NO_AREA_LABEL}</TableCell>
                {[area.incorrect, area.missing, area.correct, area.notApplicable, area.total].map((value, index) => (
                  <TableCell key={index} className="text-right font-mono text-sm tabular-nums">
                    {value.toLocaleString()}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}

/** A group's newest tickets, as the breakdown card lists them when a row is expanded. */
function newestTickets(ticketIds: string[], resultsById: Map<string, DocAnalysisResult>): SummaryCount["recent"] {
  return ticketIds
    .flatMap((id) => {
      const result = resultsById.get(id);
      return result ? [{ ticketId: id, text: result.question, timestamp: result.raisedAt }] : [];
    })
    .sort((a, b) => b.timestamp.localeCompare(a.timestamp))
    .slice(0, 5);
}

function Groupings({ data, onOpenTicket }: { data: DocAnalysisData; onOpenTicket: (ticketId: string) => void }) {
  const resultsById = useMemo(() => new Map(data.results.map((result) => [result.ticketId, result])), [data.results]);
  const missing: SummaryCount[] = data.missingByArea.map((group) => ({
    label: group.area ?? NO_AREA_LABEL,
    count: group.count,
    recent: newestTickets(group.ticketIds, resultsById),
  }));
  const pageSplits = new Map(data.citedPages.map((group) => [group.page, group]));
  const pages: SummaryCount[] = data.citedPages.map((group) => ({
    label: group.page,
    count: group.count,
    recent: newestTickets(group.ticketIds, resultsById),
  }));

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <BreakdownCard
        title="Missing documentation by product area"
        counts={missing}
        accent="warning"
        onOpenTicket={onOpenTicket}
        emptyMessage="No ticket in this window is missing documentation."
        testId="doc-analysis-missing-by-area"
      />
      <BreakdownCard
        title="Pages cited"
        counts={pages}
        accent="info"
        subtitle={(page) => {
          const split = pageSplits.get(page.label);
          return split ? `${split.incorrect.toLocaleString()} conflicting · ${split.correct.toLocaleString()} correct` : undefined;
        }}
        onOpenTicket={onOpenTicket}
        emptyMessage="No result cites a page yet."
        testId="doc-analysis-cited-pages"
      />
    </div>
  );
}

function ResultList({ data, onOpenTicket }: { data: DocAnalysisData; onOpenTicket: (ticketId: string) => void }) {
  const [filter, setFilter] = useState<DocClassification | undefined>(undefined);
  const shown = filter ? data.results.filter((result) => result.classification === filter) : data.results;
  const runActive = data.run.state === "running" || data.run.state === "busy";

  let empty: string | null = null;
  if (data.results.length === 0) {
    empty = runActive
      ? "No ticket has been classified yet. Results appear here as they are saved."
      : "No ticket in this window has been classified yet.";
  } else if (shown.length === 0) {
    empty = "No ticket has this classification.";
  }

  return (
    <div className="bg-card rounded-xl border p-6" data-testid="doc-analysis-results">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-foreground text-base font-semibold">Tickets</h2>
          <p className="text-muted-foreground text-sm">Conflicts and missing documentation first, newest first within each</p>
        </div>
        <span data-testid="doc-analysis-filter">
          <SingleSelectFilter
            title="Classification"
            value={filter}
            onChange={(value) => setFilter(value as DocClassification | undefined)}
            options={FILTER_ORDER.map((classification) => ({ label: CLASSIFICATION_LABELS[classification], value: classification }))}
            showSearch={false}
          />
        </span>
      </div>
      {empty ? (
        <p className="text-muted-foreground p-16 text-center text-sm">{empty}</p>
      ) : (
        <ul className="divide-y">
          {shown.map((result) => (
            <ResultItem key={result.ticketId} result={result} currentVersion={data.documentation?.version} onOpenTicket={onOpenTicket} />
          ))}
        </ul>
      )}
    </div>
  );
}

function CenteredMessage({
  testId,
  title,
  detail,
  destructive = false,
}: {
  testId?: string;
  title: string;
  detail?: string;
  destructive?: boolean;
}) {
  return (
    <div className="flex h-full items-center justify-center" data-testid={testId}>
      <div className="text-center">
        <p className={destructive ? "text-destructive" : "text-foreground"}>{title}</p>
        {detail && <p className="text-muted-foreground mt-2 text-sm">{detail}</p>}
      </div>
    </div>
  );
}

export default function DocumentationAnalysisPage() {
  const [params, setParams] = useUrlParams(WINDOW_PARAM_DEFAULTS, WINDOW_PARAM_VALIDATORS);
  const { dateFilter, dateFrom, dateTo } = params;
  const analysisWindow = useMemo(() => chosenWindow({ dateFilter, dateFrom, dateTo }), [dateFilter, dateFrom, dateTo]);

  // The sidebar hides the entry while the feature is off, but a direct visit must not fall through
  // to a request the backend will refuse. When the flag check itself fails, the request still goes
  // ahead so its own error explains why.
  const { data: enabled, isLoading: isCheckingEnabled, isError: enabledCheckFailed } = useDocAnalysisEnabled();
  const { data, isLoading, error, isFetching, isPlaceholderData, pollFailures } = useDocAnalysis(
    analysisWindow.from,
    analysisWindow.to,
    enabled === true || enabledCheckFailed
  );
  const refreshing = isFetching && isPlaceholderData;

  const queryClient = useQueryClient();
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const openTicket = (ticketId: string) => setSelectedTicketId(ticketId);
  const handleTicketSaved = () => {
    if (selectedTicketId) {
      queryClient.invalidateQueries({ queryKey: ["ticket", selectedTicketId] });
    }
    queryClient.invalidateQueries({ queryKey: ["tickets"] });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Documentation Analysis</h1>
          <p className="text-muted-foreground text-sm">Whether the documentation answers what tenants asked in the selected period</p>
        </div>
        {enabled !== false && (
          <div className="flex flex-wrap items-center gap-2">
            <WindowPicker params={params} onChange={setParams} testId="doc-analysis-date-filter" />
          </div>
        )}
      </div>

      {(isCheckingEnabled || (isLoading && !data)) && (
        <div className="flex h-full items-center justify-center">
          <div className="text-center">
            <div className="border-primary mb-4 inline-block h-12 w-12 animate-spin rounded-full border-b-2"></div>
            <p className="text-muted-foreground">Loading documentation analysis...</p>
          </div>
        </div>
      )}

      {enabled === false && (
        <CenteredMessage
          testId="doc-analysis-not-enabled"
          title={DOC_ANALYSIS_NOT_ENABLED.title}
          detail={DOC_ANALYSIS_NOT_ENABLED.detail}
        />
      )}

      {error && !data && (
        <CenteredMessage
          testId="doc-analysis-error"
          title={docAnalysisErrorMessage(error).title}
          detail={docAnalysisErrorMessage(error).detail}
          destructive
        />
      )}

      {data && (
        <div
          className={cn("space-y-6 transition-opacity duration-[400ms]", refreshing && "opacity-60")}
          aria-busy={refreshing}
          data-testid="doc-analysis-body"
        >
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm" data-testid="doc-analysis-window">
              <span className="text-muted-foreground text-xs font-semibold tracking-wider uppercase">
                {PRESET_LABELS[presetForWindow(data.from, data.to)]}
              </span>
              <span className="text-foreground font-semibold">{formatWindow(data.from, data.to)}</span>
              <span className="text-muted-foreground">
                · <span className="text-foreground font-mono font-semibold tabular-nums">{data.counts.total.toLocaleString()}</span> closed
                tickets
              </span>
              {refreshing && (
                <span className="text-muted-foreground flex items-center gap-2" role="status">
                  <span className="border-primary inline-block h-3.5 w-3.5 animate-spin rounded-full border-b-2" />
                  Loading the selected window...
                </span>
              )}
            </div>
            <DocumentationLine documentation={data.documentation} />
            {error && (
              <p className="text-warning flex items-center gap-2 text-sm" role="status" data-testid="doc-analysis-refresh-failing">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>
                  {pollFailures < MAX_SUMMARY_POLL_FAILURES
                    ? `Refresh failing – retrying. ${docAnalysisErrorMessage(error).title}.`
                    : `Refresh failing – stopped after ${MAX_SUMMARY_POLL_FAILURES} attempts. ${docAnalysisErrorMessage(error).title}. Reload the page to try again.`}
                </span>
              </p>
            )}
          </div>

          <RunNotice run={data.run} />

          {data.counts.total === 0 ? (
            <div className="bg-card rounded-xl border p-6">
              <p className="text-muted-foreground p-16 text-center text-sm">No closed tickets were raised in this window.</p>
            </div>
          ) : (
            <>
              <Counts data={data} />
              <Groupings data={data} onOpenTicket={openTicket} />
              <ProductAreaTable data={data} />
              <ResultList data={data} onOpenTicket={openTicket} />
            </>
          )}
        </div>
      )}

      <EditTicketModal
        ticketId={selectedTicketId}
        open={selectedTicketId !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedTicketId(null);
        }}
        onSuccess={handleTicketSaved}
      />
    </div>
  );
}
