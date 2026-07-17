"use client";

import { ElevatePagination } from "@/components/elevate/ElevatePagination";
import { ElevateRelationshipDetails, type RelationshipFocus } from "@/components/elevate/ElevateRelationshipDetails";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SingleSelectFilter } from "@/components/ui/single-select-filter";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { isApiError, useElevateProductJourneys, useElevateProductUsers } from "@/lib/hooks";
import type { ElevateJourney, ElevateProduct, ElevateRelationshipFilter, ElevateRelationshipSort, ElevateUser } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Route, Search, Users } from "lucide-react";
import { useEffect, useRef, useState } from "react";

type RelationshipKind = "journey" | "user";

const FILTER_OPTIONS = [
  { value: "linked", label: "With links" },
  { value: "unassigned", label: "No valid links" },
];

const SORT_OPTIONS = [
  { value: "name", label: "Name" },
  { value: "relationships", label: "Most linked" },
];

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function ElevateRelationshipBrowser({
  product,
  snapshotVersion,
  onSnapshotChanged,
}: {
  product: ElevateProduct;
  snapshotVersion: string;
  onSnapshotChanged: () => void;
}) {
  const initialKind: RelationshipKind = product.journeyCount > 0 ? "journey" : "user";
  const [kind, setKind] = useState<RelationshipKind>(initialKind);
  const [selectedIds, setSelectedIds] = useState({ journey: "", user: "" });
  const [query, setQuery] = useState("");
  const deferredQuery = useDebouncedValue(query);
  const [filter, setFilter] = useState<ElevateRelationshipFilter>("all");
  const [sort, setSort] = useState<ElevateRelationshipSort>("name");
  const [page, setPage] = useState(0);
  const [mobileDetailOpen, setMobileDetailOpen] = useState(false);
  const [focusRequest, setFocusRequest] = useState(0);
  const lastListTriggerRef = useRef<HTMLElement | null>(null);
  const listHeadingRef = useRef<HTMLHeadingElement>(null);
  const request = {
    snapshotVersion,
    page,
    query: deferredQuery,
    relationship: filter,
    sort,
    direction: sort === "relationships" ? ("desc" as const) : ("asc" as const),
  };
  const journeys = useElevateProductJourneys(product.id, request, kind === "journey");
  const users = useElevateProductUsers(product.id, request, kind === "user");
  const result = kind === "journey" ? journeys : users;
  const waitingForSearch = query !== deferredQuery;
  const showingPlaceholder = waitingForSearch || result.isPlaceholderData;
  const busy = waitingForSearch || result.isFetching;
  const items = showingPlaceholder ? undefined : result.data?.content;
  const selectedId = selectedIds[kind] || (showingPlaceholder ? "" : items?.[0]?.id || "");
  const focus: RelationshipFocus | null = selectedId ? { kind, id: selectedId } : null;

  useEffect(() => {
    if (isApiError(result.error, 409)) onSnapshotChanged();
  }, [onSnapshotChanged, result.error]);

  function resetList(nextKind = kind) {
    setQuery("");
    setFilter("all");
    setSort("name");
    setPage(0);
    setSelectedIds((current) => ({ ...current, [nextKind]: "" }));
    setMobileDetailOpen(false);
  }

  function selectKind(nextKind: RelationshipKind) {
    setKind(nextKind);
    resetList(nextKind);
  }

  function selectItem(id: string, trigger: HTMLElement) {
    lastListTriggerRef.current = trigger;
    setSelectedIds((current) => ({ ...current, [kind]: id }));
    setMobileDetailOpen(true);
    setFocusRequest((current) => current + 1);
  }

  function selectRelated(nextFocus: RelationshipFocus) {
    setKind(nextFocus.kind);
    setSelectedIds((current) => ({ ...current, [nextFocus.kind]: nextFocus.id }));
    // A related record may live beyond the first page of its product-level
    // list. Querying by its ID keeps the destination list contextual and makes
    // compact-view Back navigation return to a list that contains the record.
    setQuery(nextFocus.id);
    setFilter("all");
    setSort("name");
    setPage(0);
    setMobileDetailOpen(true);
    setFocusRequest((current) => current + 1);
  }

  function returnToList() {
    setMobileDetailOpen(false);
    requestAnimationFrame(() => {
      if (lastListTriggerRef.current?.isConnected) lastListTriggerRef.current.focus();
      // Cross-navigation replaces the originating list, so its named heading is
      // the stable return point for keyboard users in the new compact view.
      else listHeadingRef.current?.focus();
    });
  }

  return (
    <Tabs value={kind} onValueChange={(value) => selectKind(value as RelationshipKind)} className="gap-0">
      <div className="flex flex-col gap-4 border-b p-4 sm:p-5 lg:flex-row lg:items-end lg:justify-between">
        <TabsList aria-label="Relationship type">
          <TabsTrigger value="journey" className="cursor-pointer">
            <Route /> Journeys
            <span className="font-mono tabular-nums">{product.journeyCount}</span>
          </TabsTrigger>
          <TabsTrigger value="user" className="cursor-pointer">
            <Users /> Product users
            <span className="font-mono tabular-nums">{product.userCount}</span>
          </TabsTrigger>
        </TabsList>

        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div className="relative min-w-0 sm:w-64">
            <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
            <Input
              type="search"
              value={query}
              aria-label={`Search ${kind === "journey" ? "journeys" : "product users"}`}
              placeholder={`Search ${kind === "journey" ? "journeys" : "product users"}…`}
              className="pl-9"
              onChange={(event) => {
                setQuery(event.target.value);
                setPage(0);
                setSelectedIds((current) => ({ ...current, [kind]: "" }));
                setMobileDetailOpen(false);
              }}
            />
          </div>
          <SingleSelectFilter
            title="Relationship"
            value={filter === "all" ? undefined : filter}
            options={FILTER_OPTIONS}
            onChange={(value) => {
              setFilter((value as ElevateRelationshipFilter | undefined) ?? "all");
              setPage(0);
              setSelectedIds((current) => ({ ...current, [kind]: "" }));
              setMobileDetailOpen(false);
            }}
          />
          <SingleSelectFilter
            title="Sort"
            value={sort}
            options={SORT_OPTIONS}
            showSearch={false}
            onChange={(value) => {
              setSort(value === "relationships" ? "relationships" : "name");
              setPage(0);
              setSelectedIds((current) => ({ ...current, [kind]: "" }));
              setMobileDetailOpen(false);
            }}
          />
        </div>
      </div>

      <TabsContent value={kind} className="lg:grid lg:grid-cols-[minmax(18rem,0.85fr)_minmax(22rem,1.15fr)]">
        <section
          className={cn("min-w-0 border-b lg:block lg:border-r lg:border-b-0", mobileDetailOpen ? "hidden" : "block")}
          aria-label={`${kind === "journey" ? "Journeys" : "Product users"} list`}
          aria-busy={busy || undefined}
        >
          <h3 ref={listHeadingRef} tabIndex={-1} className="sr-only outline-none">
            {kind === "journey" ? "Journeys" : "Product users"}
          </h3>
          {result.isLoading || showingPlaceholder ? (
            <p className="text-muted-foreground p-16 text-center text-sm" role="status">
              {query ? "Searching records…" : "Updating records…"}
            </p>
          ) : null}
          {!result.isLoading && !showingPlaceholder && result.error && !result.data ? (
            <p className="text-destructive p-16 text-center text-sm" role="alert">
              Unable to load records.
            </p>
          ) : null}
          {!result.isLoading && !showingPlaceholder && items && items.length > 0 ? (
            <ul role="list" className="max-h-[34rem] divide-y overflow-y-auto">
              {items.map((item) => {
                const isJourney = kind === "journey";
                const count = isJourney ? (item as ElevateJourney).userCount : (item as ElevateUser).journeyCount;
                const identifier = isJourney ? (item as ElevateJourney).slug : item.id;
                const selected = item.id === selectedId;
                const relationshipLabel =
                  count > 0 ? countLabel(count, isJourney ? "product user" : "journey") : "no valid same-product links";
                return (
                  <li key={item.id}>
                    <Button
                      type="button"
                      variant="ghost"
                      size="default"
                      aria-current={selected ? "true" : undefined}
                      aria-label={`${item.name}, ${relationshipLabel}`}
                      className={cn(
                        "h-auto w-full justify-start rounded-none p-3 text-left whitespace-normal sm:p-4",
                        selected && "bg-accent text-accent-foreground"
                      )}
                      onClick={(event) => selectItem(item.id, event.currentTarget)}
                    >
                      <span className="min-w-0 flex-1">
                        <span className="text-foreground block truncate text-sm font-medium">{item.name}</span>
                        <span className="text-muted-foreground block truncate font-mono text-xs">{identifier}</span>
                      </span>
                      <Badge variant={count > 0 ? "secondary" : "outline"}>
                        {count > 0 ? (
                          <span className="font-mono tabular-nums">{countLabel(count, isJourney ? "user" : "journey")}</span>
                        ) : (
                          "No valid links"
                        )}
                      </Badge>
                    </Button>
                  </li>
                );
              })}
            </ul>
          ) : null}
          {!result.isLoading && !showingPlaceholder && items?.length === 0 ? (
            <div className="flex min-h-64 items-center justify-center p-6 text-center" role="status">
              <div>
                <p className="text-foreground text-sm font-medium">No {kind === "journey" ? "journeys" : "product users"} found</p>
                <p className="text-muted-foreground mt-1 text-sm">
                  {query || filter !== "all"
                    ? "Try changing the search or relationship filter."
                    : `This product has no ${kind === "journey" ? "journeys" : "product users"}.`}
                </p>
              </div>
            </div>
          ) : null}
          {result.data ? (
            <ElevatePagination
              page={result.data.page}
              totalPages={result.data.totalPages}
              busy={busy}
              onPageChange={(nextPage) => {
                setPage(nextPage);
                setSelectedIds((current) => ({ ...current, [kind]: "" }));
                setMobileDetailOpen(false);
              }}
            />
          ) : null}
        </section>

        <section className={cn("min-w-0 lg:block", mobileDetailOpen ? "block" : "hidden")} aria-label="Selected relationship details">
          {focus ? (
            <ElevateRelationshipDetails
              key={`${focus.kind}-${focus.id}`}
              snapshotVersion={snapshotVersion}
              focus={focus}
              focusRequest={focusRequest}
              onSelectRelated={selectRelated}
              onBack={returnToList}
              onSnapshotChanged={onSnapshotChanged}
            />
          ) : (
            <div className="flex min-h-64 items-center justify-center p-6 text-center" role="status">
              <div>
                <p className="text-foreground text-sm font-medium">Nothing selected</p>
                <p className="text-muted-foreground mt-1 text-sm">Choose a record from the list to inspect its direct relationships.</p>
              </div>
            </div>
          )}
        </section>
      </TabsContent>
    </Tabs>
  );
}
