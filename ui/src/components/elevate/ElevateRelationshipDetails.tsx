"use client";

import { ElevatePagination } from "@/components/elevate/ElevatePagination";
import { formatTimestamp } from "@/components/elevate/ElevateStatusCards";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SingleSelectFilter } from "@/components/ui/single-select-filter";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { isApiError, useElevateJourney, useElevateJourneyUsers, useElevateUser, useElevateUserJourneys } from "@/lib/hooks";
import type { ElevateJourney, ElevateRelationshipSort, ElevateUser } from "@/lib/types";
import { ArrowLeft, ArrowRight, Link2, Search } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useRef, useState } from "react";

export type RelationshipFocus = { kind: "journey" | "user"; id: string };

const SORT_OPTIONS = [
  { value: "name", label: "Name" },
  { value: "relationships", label: "Most linked" },
];

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[9rem_1fr]">
      <dt className="text-foreground text-sm font-medium">{label}</dt>
      <dd className="text-muted-foreground min-w-0 text-sm">{children}</dd>
    </div>
  );
}

type RelatedRecord = {
  id: string;
  name: string;
  identifier: string;
  relationshipCount: number;
  relationshipLabel: string;
};

function RelatedRecords({
  records,
  totalElements,
  page,
  totalPages,
  busy,
  loading,
  query,
  sort,
  recordLabel,
  emptyMessage,
  onQueryChange,
  onSortChange,
  onPageChange,
  onSelect,
}: {
  records: RelatedRecord[];
  totalElements: number;
  page: number;
  totalPages: number;
  busy: boolean;
  loading: boolean;
  query: string;
  sort: ElevateRelationshipSort;
  recordLabel: string;
  emptyMessage: string;
  onQueryChange: (query: string) => void;
  onSortChange: (sort: ElevateRelationshipSort) => void;
  onPageChange: (page: number) => void;
  onSelect: (id: string) => void;
}) {
  return (
    <section className="mt-5 overflow-hidden rounded-lg border" aria-labelledby="direct-relationships-title" aria-busy={busy || undefined}>
      <header className="bg-muted/20 flex items-center gap-2 border-b p-3">
        <Link2 className="text-muted-foreground size-4" />
        <h5 id="direct-relationships-title" className="text-foreground text-base font-semibold">
          Direct relationships
        </h5>
        <span className="text-muted-foreground font-mono text-sm tabular-nums">{loading ? "…" : totalElements}</span>
      </header>
      <div className="flex flex-col gap-2 border-b p-3 sm:flex-row sm:items-center">
        <div className="relative min-w-0 flex-1">
          <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
          <Input
            type="search"
            value={query}
            aria-label={`Search related ${recordLabel}`}
            placeholder={`Search related ${recordLabel}…`}
            className="pl-9"
            onChange={(event) => onQueryChange(event.target.value)}
          />
        </div>
        <SingleSelectFilter
          title="Sort"
          value={sort}
          options={SORT_OPTIONS}
          showSearch={false}
          onChange={(value) => onSortChange(value === "relationships" ? "relationships" : "name")}
        />
      </div>
      {loading ? (
        <p className="text-muted-foreground p-6 text-center text-sm" role="status">
          {query ? "Searching direct relationships…" : "Updating direct relationships…"}
        </p>
      ) : records.length > 0 ? (
        <ul role="list" className="divide-y">
          {records.map((record) => (
            <li key={record.id}>
              <Button
                type="button"
                variant="ghost"
                size="default"
                disabled={busy}
                className="h-auto w-full justify-start rounded-none px-3 py-3 text-left whitespace-normal"
                aria-label={`View ${record.name}, ${countLabel(record.relationshipCount, record.relationshipLabel)}`}
                onClick={() => onSelect(record.id)}
              >
                <span className="min-w-0 flex-1">
                  <span className="text-foreground block text-sm font-medium">{record.name}</span>
                  <span className="text-muted-foreground block truncate font-mono text-xs">{record.identifier}</span>
                  <span className="text-muted-foreground block font-mono text-sm tabular-nums">
                    {countLabel(record.relationshipCount, record.relationshipLabel)}
                  </span>
                </span>
                <ArrowRight className="text-muted-foreground ml-auto size-4" />
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-muted-foreground p-6 text-center text-sm" role="status">
          {query ? "No direct relationships match this search." : emptyMessage}
        </p>
      )}
      <ElevatePagination page={page} totalPages={totalPages} busy={busy} onPageChange={onPageChange} />
    </section>
  );
}

export function ElevateRelationshipDetails({
  snapshotVersion,
  focus,
  focusRequest,
  onSelectRelated,
  onBack,
  onSnapshotChanged,
}: {
  snapshotVersion: string;
  focus: RelationshipFocus;
  focusRequest: number;
  onSelectRelated: (focus: RelationshipFocus) => void;
  onBack: () => void;
  onSnapshotChanged: () => void;
}) {
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const deferredQuery = useDebouncedValue(query);
  const [sort, setSort] = useState<ElevateRelationshipSort>("name");
  const headingRef = useRef<HTMLHeadingElement>(null);
  const journey = useElevateJourney(focus.id, snapshotVersion, focus.kind === "journey");
  const user = useElevateUser(focus.id, snapshotVersion, focus.kind === "user");
  const request = {
    snapshotVersion,
    page,
    query: deferredQuery,
    sort,
    direction: sort === "relationships" ? ("desc" as const) : ("asc" as const),
  };
  const journeyUsers = useElevateJourneyUsers(focus.id, request, focus.kind === "journey");
  const userJourneys = useElevateUserJourneys(focus.id, request, focus.kind === "user");
  const detail = focus.kind === "journey" ? journey : user;
  const related = focus.kind === "journey" ? journeyUsers : userJourneys;
  const waitingForSearch = query !== deferredQuery;
  const showingRelatedPlaceholder = waitingForSearch || related.isPlaceholderData;
  const busy = detail.isFetching || waitingForSearch || related.isFetching;

  useEffect(() => {
    if ([detail.error, related.error].some((error) => isApiError(error, 409))) onSnapshotChanged();
  }, [detail.error, onSnapshotChanged, related.error]);

  useEffect(() => {
    if (!detail.data || focusRequest === 0) return;
    headingRef.current?.focus();
  }, [detail.data, focusRequest]);

  if (detail.isLoading || detail.isPlaceholderData) {
    return <p className="text-muted-foreground p-16 text-center text-sm">Loading record details…</p>;
  }

  if (detail.error || !detail.data) {
    return <p className="text-destructive p-16 text-center text-sm">Unable to load record details.</p>;
  }

  const isJourney = focus.kind === "journey";
  const record = detail.data;
  const identifier = isJourney ? (record as ElevateJourney).slug : record.id;
  const relatedRecords: RelatedRecord[] = isJourney
    ? ((related.data?.content ?? []) as ElevateUser[]).map((item) => ({
        id: item.id,
        name: item.name,
        identifier: item.id,
        relationshipCount: item.journeyCount,
        relationshipLabel: "journey",
      }))
    : ((related.data?.content ?? []) as ElevateJourney[]).map((item) => ({
        id: item.id,
        name: item.name,
        identifier: item.slug,
        relationshipCount: item.userCount,
        relationshipLabel: "product user",
      }));

  return (
    <div className="p-4 sm:p-5" aria-busy={busy || undefined}>
      <Button variant="ghost" size="sm" className="mb-3 lg:hidden" onClick={onBack}>
        <ArrowLeft /> Back to {isJourney ? "journeys" : "product users"}
      </Button>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h4 ref={headingRef} tabIndex={-1} className="text-foreground text-base font-semibold text-pretty outline-none">
            {record.name}
          </h4>
          <p className="text-muted-foreground truncate font-mono text-sm">{identifier}</p>
        </div>
        <Badge variant="outline">{isJourney ? "Journey" : "Product user"}</Badge>
      </div>

      {related.isLoading && !related.data ? (
        <p className="text-muted-foreground mt-5 p-6 text-center text-sm">Loading direct relationships…</p>
      ) : null}
      {!showingRelatedPlaceholder && related.error ? (
        <p className="text-destructive mt-5 p-6 text-center text-sm">Unable to load direct relationships.</p>
      ) : null}
      {!related.error && related.data ? (
        <RelatedRecords
          records={showingRelatedPlaceholder ? [] : relatedRecords}
          totalElements={related.data.totalElements}
          page={related.data.page}
          totalPages={related.data.totalPages}
          busy={busy}
          loading={showingRelatedPlaceholder}
          query={query}
          sort={sort}
          recordLabel={isJourney ? "product users" : "journeys"}
          emptyMessage={isJourney ? "This journey has no assigned product users." : "This product user is not assigned to a journey."}
          onQueryChange={(value) => {
            setQuery(value);
            setPage(0);
          }}
          onSortChange={(value) => {
            setSort(value);
            setPage(0);
          }}
          onPageChange={setPage}
          onSelect={(id) => onSelectRelated({ kind: isJourney ? "user" : "journey", id })}
        />
      ) : null}

      <dl className="mt-3 divide-y">
        {isJourney ? (
          <>
            <DetailField label="User description">{(record as ElevateJourney).userDescription || "Not provided"}</DetailField>
            <DetailField label="Primary problems">{(record as ElevateJourney).primaryProblems || "Not provided"}</DetailField>
            <DetailField label="Missing users">
              <span className="font-mono tabular-nums">{(record as ElevateJourney).missingUserCount}</span>
            </DetailField>
            <DetailField label="Cross-product links">
              <span className="font-mono tabular-nums">{(record as ElevateJourney).crossProductUserCount}</span>
            </DetailField>
          </>
        ) : (
          <DetailField label="Description">{(record as ElevateUser).description || "Not provided"}</DetailField>
        )}
        <DetailField label="Last updated">
          <time className="font-mono tabular-nums" dateTime={record.lastUpdatedAt}>
            {formatTimestamp(record.lastUpdatedAt)}
          </time>
        </DetailField>
      </dl>
    </div>
  );
}
