"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { DocAnalysisResult, DocClassification } from "@/lib/types/documentation-analysis";
import { cn, formatUtcDateTime, slackDeepLink } from "@/lib/utils";
import { Slack } from "lucide-react";
import type { ReactNode } from "react";

export const CLASSIFICATION_LABELS: Record<DocClassification, string> = {
  incorrect: "Conflicts with support's answer",
  missing: "Missing",
  correct: "Correct",
  not_applicable: "Not applicable",
};

const CLASSIFICATION_STYLES: Record<DocClassification, string> = {
  incorrect: "bg-destructive/10 text-destructive border-destructive/20",
  missing: "bg-warning/10 text-warning border-warning/20",
  correct: "bg-success/10 text-success border-success/20",
  not_applicable: "bg-muted text-muted-foreground",
};

export const NO_AREA_LABEL = "No area";

/** A commit hash is shortened as git does; any other version is shown whole. */
export function shortVersion(version: string): string {
  return /^[0-9a-f]{12,}$/i.test(version) ? version.slice(0, 7) : version;
}

export function ClassificationBadge({ classification }: { classification: DocClassification }) {
  return (
    <Badge variant="outline" className={CLASSIFICATION_STYLES[classification]}>
      {CLASSIFICATION_LABELS[classification]}
    </Badge>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-muted-foreground text-xs font-semibold tracking-wider uppercase">{label}</p>
      {children}
    </div>
  );
}

/**
 * One classified ticket with its evidence. A conflict puts the quoted passage beside support's
 * answer and says no more: either may be the one that is wrong.
 */
export default function ResultItem({
  result,
  currentVersion,
  onOpenTicket,
}: {
  result: DocAnalysisResult;
  currentVersion?: string;
  onOpenTicket: (ticketId: string) => void;
}) {
  const cites = result.classification === "correct" || result.classification === "incorrect";
  const pagesLabel = cites ? "Cited page" : result.docPaths.length === 1 ? "Closest page" : "Closest pages";

  return (
    <li className="space-y-4 py-5" data-testid="doc-analysis-result">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <ClassificationBadge classification={result.classification} />
        <span className="text-muted-foreground text-xs">
          Ticket <span className="font-mono tabular-nums">#{result.ticketId}</span> · raised {formatUtcDateTime(result.raisedAt)}
        </span>
      </div>

      <p className="text-foreground text-sm font-medium">{result.question}</p>

      <div className="text-muted-foreground flex flex-wrap items-center gap-x-4 gap-y-2 text-xs">
        <span>
          Product area:{" "}
          <span className="text-foreground">{result.productAreas.length ? result.productAreas.join(", ") : NO_AREA_LABEL}</span>
        </span>
        {result.tags.length > 0 && (
          <span className="flex flex-wrap items-center gap-1">
            Tags:
            {result.tags.map((tag) => (
              <Badge key={tag} variant="outline" className="font-normal">
                {tag}
              </Badge>
            ))}
          </span>
        )}
      </div>

      {(result.evidenceQuote || result.answerSummary) && (
        <div className={cn("grid grid-cols-1 gap-4", result.evidenceQuote && result.answerSummary && "md:grid-cols-2")}>
          {result.evidenceQuote && (
            <Field label="The documentation says">
              <blockquote className="bg-muted/40 text-foreground rounded-md border-l-2 px-3 py-2 text-sm whitespace-pre-wrap">
                {result.evidenceQuote}
              </blockquote>
            </Field>
          )}
          {result.answerSummary && (
            <Field label="Support answered">
              <p className="bg-muted/40 text-foreground rounded-md border-l-2 px-3 py-2 text-sm whitespace-pre-wrap">
                {result.answerSummary}
              </p>
            </Field>
          )}
        </div>
      )}

      {result.docPaths.length > 0 && (
        <Field label={pagesLabel}>
          <ul className="space-y-0.5">
            {result.docPaths.map((path) => (
              <li key={path} className="text-foreground font-mono text-xs break-all">
                {path}
              </li>
            ))}
          </ul>
        </Field>
      )}

      <Field label="Rationale">
        <p className="text-foreground text-sm whitespace-pre-wrap">{result.rationale}</p>
      </Field>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-muted-foreground text-xs" data-testid="doc-analysis-result-version">
          {result.docsVersion ? (
            <>
              Made against documentation{" "}
              <span className="text-foreground font-mono" title={result.docsVersion}>
                {shortVersion(result.docsVersion)}
              </span>
              {currentVersion && currentVersion !== result.docsVersion && " (not the current version)"}
            </>
          ) : (
            "Documentation version not recorded"
          )}
        </span>
        <div className="flex gap-1">
          <Button asChild variant="outline" size="sm">
            <a
              href={slackDeepLink(result.channelId, result.queryTs)}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Open ticket ${result.ticketId} in Slack`}
            >
              <Slack className="h-4 w-4" />
              Open in Slack
            </a>
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onOpenTicket(result.ticketId)}
            aria-label={`View ticket ${result.ticketId}`}
            className="cursor-pointer"
          >
            View ticket
          </Button>
        </div>
      </div>
    </li>
  );
}
