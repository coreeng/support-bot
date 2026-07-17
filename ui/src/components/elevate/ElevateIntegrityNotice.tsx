"use client";

import { ElevatePagination } from "@/components/elevate/ElevatePagination";
import { Input } from "@/components/ui/input";
import { SingleSelectFilter } from "@/components/ui/single-select-filter";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { isApiError, useElevateIntegrity } from "@/lib/hooks";
import type { ElevateIntegrityCounts, ElevateIntegrityIssue, ElevateIntegrityIssueType } from "@/lib/types";
import { AlertTriangle, ArrowRight, Search } from "lucide-react";
import { useEffect, useState } from "react";

const ISSUE_OPTIONS = [
  { value: "orphanJourney", label: "Journeys without products" },
  { value: "orphanUser", label: "Product users without products" },
  { value: "missingAssignment", label: "Missing product users" },
  { value: "crossProductAssignment", label: "Cross-product assignments" },
];

const DIRECTION_OPTIONS = [
  { value: "asc", label: "Name A–Z" },
  { value: "desc", label: "Name Z–A" },
];

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function issueLabel(issue: ElevateIntegrityIssue) {
  switch (issue.type) {
    case "orphanJourney":
      return "Journey without a synced product";
    case "orphanUser":
      return "Product user without a synced product";
    case "missingAssignment":
      return "Assignment to a missing product user";
    case "crossProductAssignment":
      return "Cross-product assignment";
  }
}

function IntegrityRecord({ issue }: { issue: ElevateIntegrityIssue }) {
  if (issue.type === "crossProductAssignment") {
    const journey = issue.journeyName ?? issue.journeyId ?? "Unknown journey";
    const user = issue.userName ?? issue.userId ?? "Unknown product user";
    return (
      <li className="p-3 text-sm">
        <p className="text-foreground flex flex-wrap items-center gap-2 font-medium">
          <span>{journey}</span>
          <ArrowRight className="text-muted-foreground size-4" aria-hidden="true" />
          <span className="sr-only">to product user</span>
          <span>{user}</span>
        </p>
        <p className="text-muted-foreground">Cross-product assignment</p>
        <p className="text-muted-foreground font-mono text-xs break-all">
          journey {issue.journeyId ?? "unknown"} · user {issue.userId ?? "unknown"}
        </p>
        <p className="text-muted-foreground font-mono text-xs break-all">
          journey product {issue.journeyProductId ?? "unknown"} · product user product {issue.userProductId ?? "unknown"}
        </p>
      </li>
    );
  }

  const title = issue.journeyName ?? issue.userName ?? issue.journeyId ?? issue.userId ?? "Unknown record";
  return (
    <li className="p-3 text-sm">
      <p className="text-foreground font-medium">{title}</p>
      <p className="text-muted-foreground">{issueLabel(issue)}</p>
      <p className="text-muted-foreground font-mono text-xs break-all">
        {[issue.journeyId ? `journey ${issue.journeyId}` : null, issue.userId ? `user ${issue.userId}` : null].filter(Boolean).join(" · ")}
      </p>
      {issue.journeyProductId || issue.userProductId ? (
        <p className="text-muted-foreground font-mono text-xs break-all">
          {[
            issue.journeyProductId ? `journey product ${issue.journeyProductId}` : null,
            issue.userProductId ? `user product ${issue.userProductId}` : null,
          ]
            .filter(Boolean)
            .join(" · ")}
        </p>
      ) : null}
    </li>
  );
}

export function ElevateIntegrityNotice({
  counts,
  snapshotVersion,
  onSnapshotChanged,
}: {
  counts: ElevateIntegrityCounts;
  snapshotVersion: string;
  onSnapshotChanged: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const deferredQuery = useDebouncedValue(query);
  const [type, setType] = useState<"all" | ElevateIntegrityIssueType>("all");
  const [direction, setDirection] = useState<"asc" | "desc">("asc");
  const issues = useElevateIntegrity({ snapshotVersion, page, query: deferredQuery, type, sort: "name", direction }, open);
  const waitingForSearch = query !== deferredQuery;
  const showingPlaceholder = waitingForSearch || issues.isPlaceholderData;
  const busy = waitingForSearch || issues.isFetching;
  const totalIssues = counts.orphanJourneys + counts.orphanUsers + counts.missingAssignments + counts.crossProductAssignments;

  useEffect(() => {
    if (isApiError(issues.error, 409)) onSnapshotChanged();
  }, [issues.error, onSnapshotChanged]);

  if (totalIssues === 0) return null;

  return (
    <div className="border-warning/30 bg-warning/10 flex items-start gap-3 border-b p-4 sm:p-5" role="alert">
      <AlertTriangle className="text-warning mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-foreground text-sm font-medium">Unmatched synced data</p>
        <p className="text-muted-foreground text-sm text-pretty">
          <span className="font-mono tabular-nums">{countLabel(totalIssues, "record")}</span> cannot be linked cleanly. Apparent snapshot
          inconsistencies are retried automatically. Invalid journey-to-product-user links can be reviewed in Elevate.
        </p>
        <details
          className="mt-3"
          open={open}
          onToggle={(event) => {
            setOpen(event.currentTarget.open);
            if (!event.currentTarget.open) setPage(0);
          }}
        >
          <summary className="text-foreground focus-visible:ring-ring/50 w-fit cursor-pointer rounded-sm text-sm font-medium outline-none focus-visible:ring-[3px]">
            Review unmatched records
          </summary>
          <div
            className="bg-background/70 mt-3 overflow-hidden rounded-md border"
            role="region"
            aria-label="Unmatched synced records"
            aria-busy={busy || undefined}
          >
            <div className="flex flex-col gap-2 border-b p-3 sm:flex-row sm:items-center">
              <div className="relative min-w-0 flex-1">
                <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
                <Input
                  type="search"
                  value={query}
                  aria-label="Search unmatched records"
                  placeholder="Search unmatched records…"
                  className="pl-9"
                  onChange={(event) => {
                    setQuery(event.target.value);
                    setPage(0);
                  }}
                />
              </div>
              <SingleSelectFilter
                title="Issue type"
                value={type === "all" ? undefined : type}
                options={ISSUE_OPTIONS}
                onChange={(value) => {
                  setType((value as ElevateIntegrityIssueType | undefined) ?? "all");
                  setPage(0);
                }}
              />
              <SingleSelectFilter
                title="Sort"
                value={direction}
                options={DIRECTION_OPTIONS}
                showSearch={false}
                onChange={(value) => {
                  setDirection(value === "desc" ? "desc" : "asc");
                  setPage(0);
                }}
              />
            </div>
            {issues.isLoading || showingPlaceholder ? (
              <p className="text-muted-foreground p-16 text-center text-sm" role="status">
                {query ? "Searching unmatched records…" : "Updating unmatched records…"}
              </p>
            ) : null}
            {!issues.isLoading && !showingPlaceholder && issues.error && !issues.data ? (
              <p className="text-destructive p-16 text-center text-sm" role="alert">
                Unable to load unmatched records.
              </p>
            ) : null}
            {!issues.isLoading && !showingPlaceholder && issues.data?.content.length === 0 ? (
              <p className="text-muted-foreground p-16 text-center text-sm">No unmatched records match these filters.</p>
            ) : null}
            {!showingPlaceholder && issues.data?.content.length ? (
              <ul className="max-h-80 divide-y overflow-y-auto" role="list">
                {issues.data.content.map((issue, index) => (
                  <IntegrityRecord key={`${issue.type}-${issue.journeyId ?? ""}-${issue.userId ?? ""}-${index}`} issue={issue} />
                ))}
              </ul>
            ) : null}
            {issues.data ? (
              <ElevatePagination page={issues.data.page} totalPages={issues.data.totalPages} busy={busy} onPageChange={setPage} />
            ) : null}
          </div>
        </details>
      </div>
    </div>
  );
}
