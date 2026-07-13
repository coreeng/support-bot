"use client";

import { ElevateCompactRelationshipMap } from "@/components/elevate/ElevateCompactRelationshipMap";
import { ElevateRelationshipDetails } from "@/components/elevate/ElevateRelationshipDetails";
import { JourneyLane, ProductUserLane } from "@/components/elevate/ElevateRelationshipLanes";
import type { ProductRelationship, RelationshipFocus } from "@/components/elevate/elevate-relationships";
import { useMemo, useState } from "react";

const ROW_HEIGHT = 80;

function defaultFocus(relationship: ProductRelationship): RelationshipFocus {
  if (relationship.journeys[0]) return { kind: "journey", id: relationship.journeys[0].id };
  if (relationship.users[0]) return { kind: "user", id: relationship.users[0].id };
  return null;
}

export function ElevateRelationshipMap({ relationship }: { relationship: ProductRelationship }) {
  const [requestedFocus, setFocus] = useState<RelationshipFocus>(() => defaultFocus(relationship));
  const focusStillAvailable =
    requestedFocus?.kind === "journey"
      ? relationship.journeys.some((journey) => journey.id === requestedFocus.id)
      : requestedFocus?.kind === "user"
        ? relationship.users.some((user) => user.id === requestedFocus.id)
        : false;
  const focus = focusStillAvailable ? requestedFocus : defaultFocus(relationship);

  const focusedJourney = focus?.kind === "journey" ? relationship.journeys.find((journey) => journey.id === focus.id) : undefined;
  const focusedUser = focus?.kind === "user" ? relationship.users.find((user) => user.id === focus.id) : undefined;
  const linkedUserIds = useMemo(
    () => new Set(focusedJourney ? (relationship.userIdsByJourneyId.get(focusedJourney.id) ?? []) : []),
    [focusedJourney, relationship.userIdsByJourneyId]
  );
  const linkedJourneyIds = useMemo(
    () => new Set(focusedUser ? (relationship.journeyIdsByUserId.get(focusedUser.id) ?? []) : []),
    [focusedUser, relationship.journeyIdsByUserId]
  );
  const rowCount = Math.max(relationship.journeys.length, relationship.users.length, 1);
  const connectorHeight = rowCount * ROW_HEIGHT;
  const connections = useMemo(() => {
    if (focusedJourney) {
      const journeyIndex = relationship.journeys.findIndex((journey) => journey.id === focusedJourney.id);
      return [...linkedUserIds]
        .map((userId) => ({ journeyIndex, userIndex: relationship.users.findIndex((user) => user.id === userId), key: userId }))
        .filter((connection) => connection.userIndex >= 0);
    }
    if (focusedUser) {
      const userIndex = relationship.users.findIndex((user) => user.id === focusedUser.id);
      return [...linkedJourneyIds]
        .map((journeyId) => ({
          journeyIndex: relationship.journeys.findIndex((journey) => journey.id === journeyId),
          userIndex,
          key: journeyId,
        }))
        .filter((connection) => connection.journeyIndex >= 0);
    }
    return [];
  }, [focusedJourney, focusedUser, linkedJourneyIds, linkedUserIds, relationship.journeys, relationship.users]);

  const relationshipSummary = focusedJourney
    ? `${focusedJourney.name} is linked to ${linkedUserIds.size} of ${relationship.users.length} product ${relationship.users.length === 1 ? "user" : "users"}.`
    : focusedUser
      ? `${focusedUser.name} participates in ${linkedJourneyIds.size} of ${relationship.journeys.length} ${relationship.journeys.length === 1 ? "journey" : "journeys"}.`
      : "This product has no journeys or product users to map.";

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h4 className="text-foreground text-base font-semibold">Journey and product-user map</h4>
          <p className="text-muted-foreground max-w-[70ch] text-base text-pretty sm:text-sm">
            Choose either side to trace its direct relationships. Only focused connections are drawn to keep large snapshots readable.
          </p>
        </div>
        <p className="text-muted-foreground text-base sm:text-sm" role="status" aria-live="polite" aria-atomic="true">
          {relationshipSummary}
        </p>
      </div>

      <ElevateCompactRelationshipMap relationship={relationship} focus={focus} onFocus={setFocus} />

      <div
        className="focus-visible:ring-ring/50 mt-4 hidden max-h-[42rem] overflow-y-auto pr-1 outline-none focus-visible:ring-[3px] md:block"
        role="region"
        aria-label="Journey and product-user relationship lanes"
        tabIndex={0}
      >
        <div className="grid grid-cols-[minmax(0,1fr)_4rem_minmax(0,1fr)]">
          <JourneyLane relationship={relationship} focus={focus} linkedJourneyIds={linkedJourneyIds} onFocus={setFocus} />

          <div aria-hidden="true">
            <div className="h-10" />
            <svg
              data-testid="relationship-connectors"
              className="w-full overflow-visible"
              style={{ height: connectorHeight }}
              viewBox={`0 0 64 ${connectorHeight}`}
              preserveAspectRatio="none"
              focusable="false"
            >
              {connections.map(({ journeyIndex, userIndex, key }) => {
                const journeyY = journeyIndex * ROW_HEIGHT + ROW_HEIGHT / 2;
                const userY = userIndex * ROW_HEIGHT + ROW_HEIGHT / 2;
                return (
                  <g key={key}>
                    <path
                      className="stroke-primary/50"
                      d={`M 0 ${journeyY} C 24 ${journeyY}, 40 ${userY}, 64 ${userY}`}
                      fill="none"
                      strokeWidth="1.5"
                      vectorEffect="non-scaling-stroke"
                    />
                    <circle className="fill-primary" cx="2" cy={journeyY} r="2.5" />
                    <circle className="fill-primary" cx="62" cy={userY} r="2.5" />
                  </g>
                );
              })}
            </svg>
          </div>

          <ProductUserLane relationship={relationship} focus={focus} linkedUserIds={linkedUserIds} onFocus={setFocus} />
        </div>
      </div>

      <ElevateRelationshipDetails relationship={relationship} focus={focus} />
    </div>
  );
}
