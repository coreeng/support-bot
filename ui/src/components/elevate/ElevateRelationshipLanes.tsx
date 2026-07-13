import type { ProductRelationship, RelationshipFocus } from "@/components/elevate/elevate-relationships";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { Link2, Route, Users } from "lucide-react";
import { useMemo, useState, type KeyboardEvent } from "react";

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function FocusBadge({ selected, linked }: { selected: boolean; linked: boolean }) {
  if (selected) return <Badge variant="outline">Focused</Badge>;
  if (!linked) return null;
  return (
    <Badge variant="outline" className="border-primary/30 bg-primary/10 text-foreground py-0.5 pr-2 pl-0.5">
      <Link2 className="size-3 shrink-0" /> Linked
    </Badge>
  );
}

const cardClassName =
  "bg-background focus-visible:ring-ring/50 hover:bg-accent/50 flex min-h-16 w-full min-w-0 cursor-pointer flex-col justify-center rounded-md border p-3 text-left outline-none focus-visible:ring-[3px]";

function moveLaneFocus(event: KeyboardEvent<HTMLButtonElement>, itemIndex: number, itemCount: number, onMove: (nextIndex: number) => void) {
  let nextIndex: number | undefined;
  if (event.key === "ArrowDown" || event.key === "ArrowRight") nextIndex = (itemIndex + 1) % itemCount;
  if (event.key === "ArrowUp" || event.key === "ArrowLeft") nextIndex = (itemIndex - 1 + itemCount) % itemCount;
  if (event.key === "Home") nextIndex = 0;
  if (event.key === "End") nextIndex = itemCount - 1;
  if (nextIndex === undefined) return;

  event.preventDefault();
  onMove(nextIndex);
  event.currentTarget.closest("ul")?.querySelectorAll<HTMLButtonElement>("button[data-relationship-card]")[nextIndex]?.focus();
}

export function JourneyLane({
  relationship,
  focus,
  linkedJourneyIds,
  onFocus,
}: {
  relationship: ProductRelationship;
  focus: RelationshipFocus;
  linkedJourneyIds: ReadonlySet<string>;
  onFocus: (focus: Exclude<RelationshipFocus, null>) => void;
}) {
  const focusedJourneyId = focus?.kind === "journey" ? focus.id : undefined;
  const userNamesById = useMemo(() => new Map(relationship.users.map((user) => [user.id, user.name])), [relationship.users]);
  const [tabStopId, setTabStopId] = useState(
    () => focusedJourneyId ?? relationship.journeys.find((journey) => linkedJourneyIds.has(journey.id))?.id ?? relationship.journeys[0]?.id
  );
  return (
    <div className="min-w-0">
      <div className="flex h-10 items-center gap-2">
        <Route className="text-muted-foreground size-4 shrink-0" />
        <h5 className="text-foreground text-sm font-medium">Journeys</h5>
        <span className="text-muted-foreground font-mono text-sm tabular-nums">{relationship.journeys.length}</span>
      </div>
      {relationship.journeys.length > 0 ? (
        <ul role="list" aria-describedby="journey-lane-keyboard-help">
          {relationship.journeys.map((journey, itemIndex) => {
            const userIds = relationship.userIdsByJourneyId.get(journey.id) ?? [];
            const userNames = userIds.map((id) => userNamesById.get(id)).filter(Boolean);
            const selected = focusedJourneyId === journey.id;
            const linked = linkedJourneyIds.has(journey.id);
            return (
              <li key={journey.id} className="flex py-2 md:h-20 md:items-center md:py-0">
                <button
                  type="button"
                  data-relationship-card
                  tabIndex={journey.id === tabStopId ? 0 : -1}
                  aria-pressed={selected}
                  aria-label={`${journey.name}, ${countLabel(userIds.length, "linked product user")}`}
                  className={cn(
                    cardClassName,
                    selected && "border-primary bg-primary/10",
                    linked && !selected && "border-primary/40 bg-primary/5"
                  )}
                  onFocus={() => setTabStopId(journey.id)}
                  onClick={() => onFocus({ kind: "journey", id: journey.id })}
                  onKeyDown={(event) =>
                    moveLaneFocus(event, itemIndex, relationship.journeys.length, (nextIndex) =>
                      setTabStopId(relationship.journeys[nextIndex].id)
                    )
                  }
                >
                  <span className="flex min-w-0 items-center justify-between gap-2">
                    <span className="text-foreground min-w-0 truncate text-sm font-medium">{journey.name}</span>
                    <FocusBadge selected={selected} linked={linked} />
                  </span>
                  <span className="text-muted-foreground truncate text-sm">
                    {userNames.length > 0 ? `With ${userNames.join(", ")}` : "No product users assigned"}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      ) : (
        <div className="text-muted-foreground flex min-h-20 items-center rounded-md border border-dashed p-4 text-base sm:text-sm">
          No journeys synced for this product.
        </div>
      )}
      <span id="journey-lane-keyboard-help" className="sr-only">
        Use arrow keys to move between journeys, then press Enter or Space to trace one.
      </span>
    </div>
  );
}

export function ProductUserLane({
  relationship,
  focus,
  linkedUserIds,
  onFocus,
}: {
  relationship: ProductRelationship;
  focus: RelationshipFocus;
  linkedUserIds: ReadonlySet<string>;
  onFocus: (focus: Exclude<RelationshipFocus, null>) => void;
}) {
  const focusedUserId = focus?.kind === "user" ? focus.id : undefined;
  const journeyNamesById = useMemo(
    () => new Map(relationship.journeys.map((journey) => [journey.id, journey.name])),
    [relationship.journeys]
  );
  const [tabStopId, setTabStopId] = useState(
    () => focusedUserId ?? relationship.users.find((user) => linkedUserIds.has(user.id))?.id ?? relationship.users[0]?.id
  );
  return (
    <div className="min-w-0">
      <div className="flex h-10 items-center gap-2">
        <Users className="text-muted-foreground size-4 shrink-0" />
        <h5 className="text-foreground text-sm font-medium">Product users</h5>
        <span className="text-muted-foreground font-mono text-sm tabular-nums">{relationship.users.length}</span>
      </div>
      {relationship.users.length > 0 ? (
        <ul role="list" aria-describedby="product-user-lane-keyboard-help">
          {relationship.users.map((user, itemIndex) => {
            const journeyIds = relationship.journeyIdsByUserId.get(user.id) ?? [];
            const journeyNames = journeyIds.map((id) => journeyNamesById.get(id)).filter(Boolean);
            const selected = focusedUserId === user.id;
            const linked = linkedUserIds.has(user.id);
            return (
              <li key={user.id} className="flex py-2 md:h-20 md:items-center md:py-0">
                <button
                  type="button"
                  data-relationship-card
                  tabIndex={user.id === tabStopId ? 0 : -1}
                  aria-pressed={selected}
                  aria-label={`${user.name}, ${countLabel(journeyIds.length, "linked journey")}`}
                  className={cn(
                    cardClassName,
                    selected && "border-primary bg-primary/10",
                    linked && !selected && "border-primary/40 bg-primary/5"
                  )}
                  onFocus={() => setTabStopId(user.id)}
                  onClick={() => onFocus({ kind: "user", id: user.id })}
                  onKeyDown={(event) =>
                    moveLaneFocus(event, itemIndex, relationship.users.length, (nextIndex) =>
                      setTabStopId(relationship.users[nextIndex].id)
                    )
                  }
                >
                  <span className="flex min-w-0 items-center justify-between gap-2">
                    <span className="text-foreground min-w-0 truncate text-sm font-medium">{user.name}</span>
                    <FocusBadge selected={selected} linked={linked} />
                  </span>
                  <span className="text-muted-foreground truncate text-sm">
                    {journeyNames.length > 0 ? `In ${journeyNames.join(", ")}` : "Not assigned to a journey"}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      ) : (
        <div className="text-muted-foreground flex min-h-20 items-center rounded-md border border-dashed p-4 text-base sm:text-sm">
          No product users synced for this product.
        </div>
      )}
      <span id="product-user-lane-keyboard-help" className="sr-only">
        Use arrow keys to move between product users, then press Enter or Space to trace one.
      </span>
    </div>
  );
}
