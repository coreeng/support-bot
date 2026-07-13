import { formatTimestamp } from "@/components/elevate/ElevateStatusCards";
import type { ProductRelationship, RelationshipFocus } from "@/components/elevate/elevate-relationships";
import { Badge } from "@/components/ui/badge";
import type { ReactNode } from "react";

function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr]">
      <dt className="text-foreground text-sm font-medium">{label}</dt>
      <dd className="text-muted-foreground min-w-0 text-sm">{children}</dd>
    </div>
  );
}

function DetailHeader({ name, identifier, label }: { name: string; identifier: string; label: string }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <p className="text-foreground text-sm font-medium">{name}</p>
        <p className="text-muted-foreground font-mono text-sm">{identifier}</p>
      </div>
      <Badge variant="outline">{label}</Badge>
    </div>
  );
}

export function ElevateRelationshipDetails({ relationship, focus }: { relationship: ProductRelationship; focus: RelationshipFocus }) {
  const journey = focus?.kind === "journey" ? relationship.journeys.find((item) => item.id === focus.id) : undefined;
  const user = focus?.kind === "user" ? relationship.users.find((item) => item.id === focus.id) : undefined;

  if (journey) {
    const userIds = relationship.userIdsByJourneyId.get(journey.id) ?? [];
    const userNames = userIds.map((id) => relationship.users.find((item) => item.id === id)?.name).filter(Boolean);
    const missingUserIds = relationship.missingUserIdsByJourneyId.get(journey.id) ?? [];
    const crossProductAssignments = relationship.crossProductAssignmentsByJourneyId.get(journey.id) ?? [];
    return (
      <div className="bg-muted/20 border-t p-4 sm:p-5">
        <DetailHeader name={journey.name} identifier={journey.slug} label="Journey details" />
        <dl className="mt-2 divide-y">
          <DetailField label="Product users">{userNames.length > 0 ? userNames.join(", ") : "None assigned"}</DetailField>
          <DetailField label="User description">{journey.userDescription || "Not provided"}</DetailField>
          <DetailField label="Primary problems">{journey.primaryProblems || "Not provided"}</DetailField>
          {missingUserIds.length > 0 ? (
            <DetailField label="Missing product-user IDs">
              <span className="font-mono">{missingUserIds.join(", ")}</span>
            </DetailField>
          ) : null}
          {crossProductAssignments.length > 0 ? (
            <DetailField label="Cross-product assignments">
              {crossProductAssignments
                .map((assignment) => `${assignment.userName} (${assignment.userId}; product ${assignment.userProductId})`)
                .join(", ")}
            </DetailField>
          ) : null}
          <DetailField label="Last updated">
            <time className="font-mono tabular-nums" dateTime={journey.lastUpdatedAt}>
              {formatTimestamp(journey.lastUpdatedAt)}
            </time>
          </DetailField>
        </dl>
      </div>
    );
  }

  if (user) {
    const journeyIds = relationship.journeyIdsByUserId.get(user.id) ?? [];
    const journeyNames = journeyIds.map((id) => relationship.journeys.find((item) => item.id === id)?.name).filter(Boolean);
    return (
      <div className="bg-muted/20 border-t p-4 sm:p-5">
        <DetailHeader name={user.name} identifier={user.id} label="Product-user details" />
        <dl className="mt-2 divide-y">
          <DetailField label="Journeys">{journeyNames.length > 0 ? journeyNames.join(", ") : "No journey assignments"}</DetailField>
          <DetailField label="Description">{user.description || "Not provided"}</DetailField>
          <DetailField label="Last updated">
            <time className="font-mono tabular-nums" dateTime={user.lastUpdatedAt}>
              {formatTimestamp(user.lastUpdatedAt)}
            </time>
          </DetailField>
        </dl>
      </div>
    );
  }

  return null;
}
