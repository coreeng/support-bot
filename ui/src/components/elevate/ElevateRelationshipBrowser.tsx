"use client";

import { ElevateRelationshipDetails } from "@/components/elevate/ElevateRelationshipDetails";
import type { ProductRelationship, RelationshipFocus } from "@/components/elevate/elevate-relationships";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { ChevronLeft, ChevronRight, Route, Search, Users } from "lucide-react";
import { useMemo, useState } from "react";

const PAGE_SIZE = 20;

type RelationshipKind = "journey" | "user";
type RelationshipFilter = "all" | "linked" | "unassigned";
type RelationshipSort = "name" | "relationships";

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function relationshipCount(relationship: ProductRelationship, focus: Exclude<RelationshipFocus, null>) {
  return focus.kind === "journey"
    ? (relationship.userIdsByJourneyId.get(focus.id)?.length ?? 0)
    : (relationship.journeyIdsByUserId.get(focus.id)?.length ?? 0);
}

export function ElevateRelationshipBrowser({ relationship }: { relationship: ProductRelationship }) {
  const initialKind: RelationshipKind = relationship.journeys.length > 0 ? "journey" : "user";
  const [kind, setKind] = useState<RelationshipKind>(initialKind);
  const [selectedIds, setSelectedIds] = useState({
    journey: relationship.journeys[0]?.id ?? "",
    user: relationship.users[0]?.id ?? "",
  });
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<RelationshipFilter>("all");
  const [sort, setSort] = useState<RelationshipSort>("name");
  const [page, setPage] = useState(0);

  const items = kind === "journey" ? relationship.journeys : relationship.users;
  const filteredItems = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    return items
      .filter((item) => {
        const focus = { kind, id: item.id } as const;
        const count = relationshipCount(relationship, focus);
        const identifier = kind === "journey" ? relationship.journeys.find(({ id }) => id === item.id)?.slug : item.id;
        const matchesQuery = !normalizedQuery || `${item.name} ${identifier ?? ""}`.toLocaleLowerCase().includes(normalizedQuery);
        const matchesFilter = filter === "all" || (filter === "linked" ? count > 0 : count === 0);
        return matchesQuery && matchesFilter;
      })
      .sort((left, right) => {
        if (sort === "relationships") {
          const difference =
            relationshipCount(relationship, { kind, id: right.id }) - relationshipCount(relationship, { kind, id: left.id });
          if (difference !== 0) return difference;
        }
        return left.name.localeCompare(right.name, undefined, { sensitivity: "base" });
      });
  }, [filter, items, kind, query, relationship, sort]);

  const totalPages = Math.max(1, Math.ceil(filteredItems.length / PAGE_SIZE));
  const activePage = Math.min(page, totalPages - 1);
  const pageItems = filteredItems.slice(activePage * PAGE_SIZE, (activePage + 1) * PAGE_SIZE);
  const selectedId = selectedIds[kind];
  const selectedItem = pageItems.find((item) => item.id === selectedId) ?? pageItems[0];
  const focus: RelationshipFocus = selectedItem ? { kind, id: selectedItem.id } : null;
  const firstResult = filteredItems.length === 0 ? 0 : activePage * PAGE_SIZE + 1;
  const lastResult = Math.min((activePage + 1) * PAGE_SIZE, filteredItems.length);
  const kindLabel = kind === "journey" ? "journey" : "product user";

  function selectKind(nextKind: RelationshipKind) {
    setKind(nextKind);
    setQuery("");
    setFilter("all");
    setPage(0);
  }

  function selectItem(id: string) {
    setSelectedIds((current) => ({ ...current, [kind]: id }));
  }

  function selectRelated(nextFocus: Exclude<RelationshipFocus, null>) {
    const nextItems = nextFocus.kind === "journey" ? relationship.journeys : relationship.users;
    const sortedNextItems = [...nextItems].sort((left, right) => {
      if (sort === "relationships") {
        const difference =
          relationshipCount(relationship, { kind: nextFocus.kind, id: right.id }) -
          relationshipCount(relationship, { kind: nextFocus.kind, id: left.id });
        if (difference !== 0) return difference;
      }
      return left.name.localeCompare(right.name, undefined, { sensitivity: "base" });
    });
    const targetIndex = sortedNextItems.findIndex(({ id }) => id === nextFocus.id);

    setKind(nextFocus.kind);
    setSelectedIds((current) => ({ ...current, [nextFocus.kind]: nextFocus.id }));
    setQuery("");
    setFilter("all");
    setPage(targetIndex < 0 ? 0 : Math.floor(targetIndex / PAGE_SIZE));
  }

  return (
    <Tabs value={kind} onValueChange={(value) => selectKind(value as RelationshipKind)} className="gap-0">
      <div className="flex flex-col gap-4 border-b p-4 sm:p-5 lg:flex-row lg:items-end lg:justify-between">
        <TabsList aria-label="Relationship type">
          <TabsTrigger value="journey">
            <Route /> Journeys
            <span className="font-mono tabular-nums">{relationship.journeys.length}</span>
          </TabsTrigger>
          <TabsTrigger value="user">
            <Users /> Product users
            <span className="font-mono tabular-nums">{relationship.users.length}</span>
          </TabsTrigger>
        </TabsList>

        <div className="grid gap-2 sm:grid-cols-[minmax(12rem,1fr)_10rem_10rem] lg:w-auto">
          <div className="relative">
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
              }}
            />
          </div>
          <Select
            value={filter}
            onValueChange={(value) => {
              setFilter(value as RelationshipFilter);
              setPage(0);
            }}
          >
            <SelectTrigger aria-label="Relationship filter">
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="end">
              <SelectItem value="all">All records</SelectItem>
              <SelectItem value="linked">With links</SelectItem>
              <SelectItem value="unassigned">Unassigned</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={sort}
            onValueChange={(value) => {
              setSort(value as RelationshipSort);
              setPage(0);
            }}
          >
            <SelectTrigger aria-label="Sort records">
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="end">
              <SelectItem value="name">Name</SelectItem>
              <SelectItem value="relationships">Most linked</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <TabsContent value={kind} className="lg:grid lg:grid-cols-[minmax(18rem,0.85fr)_minmax(22rem,1.15fr)]">
        <section
          className="min-w-0 border-b lg:border-r lg:border-b-0"
          aria-label={`${kind === "journey" ? "Journeys" : "Product users"} list`}
        >
          {pageItems.length > 0 ? (
            <ul role="list" className="max-h-[34rem] divide-y overflow-y-auto">
              {pageItems.map((item) => {
                const itemFocus = { kind, id: item.id } as const;
                const count = relationshipCount(relationship, itemFocus);
                const selected = item.id === selectedItem?.id;
                const identifier = kind === "journey" ? relationship.journeys.find(({ id }) => id === item.id)?.slug : item.id;
                return (
                  <li key={item.id}>
                    <Button
                      type="button"
                      variant="ghost"
                      aria-current={selected ? "true" : undefined}
                      aria-label={`${item.name}, ${countLabel(count, kind === "journey" ? "product user" : "journey")}`}
                      className={cn(
                        "h-auto w-full justify-start rounded-none p-3 text-left whitespace-normal sm:p-4",
                        selected && "bg-accent text-accent-foreground"
                      )}
                      onClick={() => selectItem(item.id)}
                    >
                      <span className="min-w-0 flex-1">
                        <span className="text-foreground block truncate text-sm font-medium">{item.name}</span>
                        <span className="text-muted-foreground block truncate font-mono text-xs">{identifier}</span>
                      </span>
                      <Badge variant={count > 0 ? "secondary" : "outline"}>
                        {count > 0 ? countLabel(count, kind === "journey" ? "user" : "journey") : "Unassigned"}
                      </Badge>
                    </Button>
                  </li>
                );
              })}
            </ul>
          ) : (
            <div className="flex min-h-64 items-center justify-center p-6 text-center" role="status">
              <div>
                <p className="text-foreground text-sm font-medium">No {kind === "journey" ? "journeys" : "product users"} found</p>
                <p className="text-muted-foreground mt-1 text-sm">
                  {items.length === 0
                    ? `This product has no ${kind === "journey" ? "journeys" : "product users"}.`
                    : "Try changing the search or relationship filter."}
                </p>
              </div>
            </div>
          )}

          <footer className="flex flex-wrap items-center justify-end gap-3 border-t p-3 sm:px-4">
            <p className="text-muted-foreground mr-auto text-sm" aria-live="polite">
              {firstResult}–{lastResult} of {filteredItems.length} · Page {activePage + 1} of {totalPages}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                aria-label={`Previous ${kindLabel} page`}
                disabled={activePage === 0}
                onClick={() => setPage(Math.max(0, activePage - 1))}
              >
                <ChevronLeft /> Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                aria-label={`Next ${kindLabel} page`}
                disabled={activePage >= totalPages - 1}
                onClick={() => setPage(Math.min(totalPages - 1, activePage + 1))}
              >
                Next <ChevronRight />
              </Button>
            </div>
          </footer>
        </section>

        <section className="min-w-0" aria-label="Selected relationship details">
          {focus ? (
            <ElevateRelationshipDetails relationship={relationship} focus={focus} onSelectRelated={selectRelated} />
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
