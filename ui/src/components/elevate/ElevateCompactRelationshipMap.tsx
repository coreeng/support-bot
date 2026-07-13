"use client";

import type { ProductRelationship, RelationshipFocus } from "@/components/elevate/elevate-relationships";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { Check, Link2, Route, Users } from "lucide-react";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function moveLinkedFocus(
  event: KeyboardEvent<HTMLButtonElement>,
  itemIndex: number,
  itemCount: number,
  onMove: (nextIndex: number) => void
) {
  let nextIndex: number | undefined;
  if (event.key === "ArrowDown" || event.key === "ArrowRight") nextIndex = (itemIndex + 1) % itemCount;
  if (event.key === "ArrowUp" || event.key === "ArrowLeft") nextIndex = (itemIndex - 1 + itemCount) % itemCount;
  if (event.key === "Home") nextIndex = 0;
  if (event.key === "End") nextIndex = itemCount - 1;
  if (nextIndex === undefined) return;

  event.preventDefault();
  onMove(nextIndex);
  event.currentTarget.closest("ul")?.querySelectorAll<HTMLButtonElement>("button[data-compact-linked-card]")[nextIndex]?.focus();
}

export function ElevateCompactRelationshipMap({
  relationship,
  focus,
  onFocus,
}: {
  relationship: ProductRelationship;
  focus: RelationshipFocus;
  onFocus: (focus: Exclude<RelationshipFocus, null>) => void;
}) {
  const focusKind = focus?.kind ?? (relationship.journeys.length > 0 ? "journey" : "user");
  const options = focusKind === "journey" ? relationship.journeys : relationship.users;
  const selectedItem = options.find((item) => item.id === focus?.id) ?? options[0];
  const linkedItems =
    focusKind === "journey"
      ? (relationship.userIdsByJourneyId.get(selectedItem?.id ?? "") ?? [])
          .map((id) => relationship.users.find((user) => user.id === id))
          .filter((user) => user !== undefined)
      : (relationship.journeyIdsByUserId.get(selectedItem?.id ?? "") ?? [])
          .map((id) => relationship.journeys.find((journey) => journey.id === id))
          .filter((journey) => journey !== undefined);
  const linkedKind = focusKind === "journey" ? "product user" : "journey";
  const [linkedTabStopId, setLinkedTabStopId] = useState("");
  const activeLinkedTabStopId = linkedItems.some((item) => item.id === linkedTabStopId) ? linkedTabStopId : linkedItems[0]?.id;
  const focusSelectorRef = useRef<HTMLButtonElement>(null);
  const restoreFocusAfterTrace = useRef(false);

  useEffect(() => {
    if (!restoreFocusAfterTrace.current) return;
    restoreFocusAfterTrace.current = false;
    focusSelectorRef.current?.focus();
  }, [focusKind, selectedItem?.id]);

  function traceLinkedItem(kind: "journey" | "user", id: string) {
    restoreFocusAfterTrace.current = true;
    onFocus({ kind, id });
  }

  return (
    <div className="mt-4 md:hidden">
      <p className="text-foreground text-sm font-medium">Trace from</p>
      <div className="mt-2 grid grid-cols-2 gap-2" role="group" aria-label="Choose which relationship side to trace from">
        <Button
          type="button"
          variant="outline"
          className={cn("w-full", focusKind === "journey" && "border-primary bg-primary/10 text-foreground hover:bg-primary/15")}
          aria-pressed={focusKind === "journey"}
          disabled={relationship.journeys.length === 0}
          onClick={() => onFocus({ kind: "journey", id: relationship.journeys[0].id })}
        >
          <Route /> Journey
          {focusKind === "journey" ? <Check className="ml-auto" /> : null}
        </Button>
        <Button
          type="button"
          variant="outline"
          className={cn("w-full", focusKind === "user" && "border-primary bg-primary/10 text-foreground hover:bg-primary/15")}
          aria-pressed={focusKind === "user"}
          disabled={relationship.users.length === 0}
          onClick={() => onFocus({ kind: "user", id: relationship.users[0].id })}
        >
          <Users /> Product user
          {focusKind === "user" ? <Check className="ml-auto" /> : null}
        </Button>
      </div>

      {selectedItem ? (
        <>
          <label id="relationship-focus-selector-label" className="text-foreground mt-4 block text-sm font-medium">
            {focusKind === "journey" ? "Journey" : "Product user"}
          </label>
          <Select value={selectedItem.id} onValueChange={(id) => onFocus({ kind: focusKind, id })}>
            <SelectTrigger ref={focusSelectorRef} className="mt-2 w-full" aria-labelledby="relationship-focus-selector-label">
              <SelectValue>{selectedItem.name}</SelectValue>
            </SelectTrigger>
            <SelectContent align="start">
              {options.map((item) => (
                <SelectItem key={item.id} value={item.id}>
                  {item.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <section className="mt-4 overflow-hidden rounded-lg border" aria-labelledby="compact-linked-items-title">
            <header className="bg-muted/20 flex items-center gap-2 border-b p-3">
              <Link2 className="text-muted-foreground size-4 shrink-0" />
              <h5 id="compact-linked-items-title" className="text-foreground text-sm font-medium">
                Linked {focusKind === "journey" ? "product users" : "journeys"}
              </h5>
              <span className="text-muted-foreground font-mono text-sm tabular-nums">{linkedItems.length}</span>
            </header>
            {linkedItems.length > 0 ? (
              <ul role="list" className="max-h-72 divide-y overflow-y-auto" aria-describedby="compact-linked-keyboard-help">
                {linkedItems.map((item, itemIndex) => {
                  const relationshipCount =
                    focusKind === "journey"
                      ? (relationship.journeyIdsByUserId.get(item.id)?.length ?? 0)
                      : (relationship.userIdsByJourneyId.get(item.id)?.length ?? 0);
                  return (
                    <li key={item.id}>
                      <button
                        type="button"
                        data-compact-linked-card
                        tabIndex={item.id === activeLinkedTabStopId ? 0 : -1}
                        className="hover:bg-accent/50 focus-visible:ring-ring/50 flex w-full cursor-pointer items-center justify-between gap-3 p-3 text-left outline-none focus-visible:ring-[3px]"
                        aria-label={`${item.name}, trace ${countLabel(relationshipCount, focusKind === "journey" ? "journey" : "product user")}`}
                        onFocus={() => setLinkedTabStopId(item.id)}
                        onKeyDown={(event) =>
                          moveLinkedFocus(event, itemIndex, linkedItems.length, (nextIndex) =>
                            setLinkedTabStopId(linkedItems[nextIndex].id)
                          )
                        }
                        onClick={() => traceLinkedItem(focusKind === "journey" ? "user" : "journey", item.id)}
                      >
                        <span className="min-w-0">
                          <span className="text-foreground block text-sm font-medium">{item.name}</span>
                          <span className="text-muted-foreground block text-sm">
                            {countLabel(relationshipCount, focusKind === "journey" ? "journey" : "product user")}
                          </span>
                        </span>
                        <Badge variant="outline" className="border-primary/30 bg-primary/10 text-foreground shrink-0 py-0.5 pr-2 pl-0.5">
                          <Link2 className="size-3" /> Trace
                        </Badge>
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="text-muted-foreground p-4 text-sm" role="status">
                No {linkedKind}s linked to this {focusKind === "journey" ? "journey" : "product user"}.
              </p>
            )}
            <span id="compact-linked-keyboard-help" className="sr-only">
              Use arrow keys to move between linked records, then press Enter or Space to trace one.
            </span>
          </section>
        </>
      ) : (
        <p className="text-muted-foreground mt-4 rounded-md border border-dashed p-4 text-sm" role="status">
          This product has no journeys or product users to trace.
        </p>
      )}
    </div>
  );
}
