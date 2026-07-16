import { formatTimestamp } from "@/components/elevate/ElevateStatusCards";
import type { ProductRelationship, RelationshipFocus } from "@/components/elevate/elevate-relationships";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ArrowRight, Link2 } from "lucide-react";
import type { ReactNode } from "react";

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

function DetailHeader({ name, identifier, label }: { name: string; identifier: string; label: string }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0">
        <h4 className="text-foreground text-base font-semibold text-pretty">{name}</h4>
        <p className="text-muted-foreground truncate font-mono text-sm">{identifier}</p>
      </div>
      <Badge variant="outline">{label}</Badge>
    </div>
  );
}

function RelatedRecords({
  records,
  emptyMessage,
  countForRecord,
  onSelect,
}: {
  records: { id: string; name: string }[];
  emptyMessage: string;
  countForRecord: (id: string) => { count: number; label: string };
  onSelect: (id: string) => void;
}) {
  return (
    <section className="mt-5 overflow-hidden rounded-lg border" aria-labelledby="direct-relationships-title">
      <header className="bg-muted/20 flex items-center gap-2 border-b p-3">
        <Link2 className="text-muted-foreground size-4" />
        <h5 id="direct-relationships-title" className="text-foreground text-sm font-medium">
          Direct relationships
        </h5>
        <span className="text-muted-foreground font-mono text-sm tabular-nums">{records.length}</span>
      </header>
      {records.length > 0 ? (
        <ul role="list" className="divide-y">
          {records.map((record) => {
            const relationshipCount = countForRecord(record.id);
            return (
              <li key={record.id}>
                <Button
                  type="button"
                  variant="ghost"
                  className="h-auto w-full justify-start rounded-none px-3 py-3 text-left whitespace-normal"
                  aria-label={`View ${record.name}, ${countLabel(relationshipCount.count, relationshipCount.label)}`}
                  onClick={() => onSelect(record.id)}
                >
                  <span className="min-w-0 flex-1">
                    <span className="text-foreground block text-sm font-medium">{record.name}</span>
                    <span className="text-muted-foreground block text-sm">
                      {countLabel(relationshipCount.count, relationshipCount.label)}
                    </span>
                  </span>
                  <ArrowRight className="text-muted-foreground ml-auto size-4" />
                </Button>
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="text-muted-foreground p-4 text-sm" role="status">
          {emptyMessage}
        </p>
      )}
    </section>
  );
}

export function ElevateRelationshipDetails({
  relationship,
  focus,
  onSelectRelated,
}: {
  relationship: ProductRelationship;
  focus: RelationshipFocus;
  onSelectRelated: (focus: Exclude<RelationshipFocus, null>) => void;
}) {
  const journey = focus?.kind === "journey" ? relationship.journeys.find((item) => item.id === focus.id) : undefined;
  const user = focus?.kind === "user" ? relationship.users.find((item) => item.id === focus.id) : undefined;

  if (journey) {
    const userIds = relationship.userIdsByJourneyId.get(journey.id) ?? [];
    const relatedUsers = userIds.map((id) => relationship.users.find((item) => item.id === id)).filter((item) => item !== undefined);
    const missingUserIds = relationship.missingUserIdsByJourneyId.get(journey.id) ?? [];
    const crossProductAssignments = relationship.crossProductAssignmentsByJourneyId.get(journey.id) ?? [];
    return (
      <div className="p-4 sm:p-5">
        <DetailHeader name={journey.name} identifier={journey.slug} label="Journey" />
        <RelatedRecords
          records={relatedUsers}
          emptyMessage="No product users are assigned to this journey."
          countForRecord={(id) => ({ count: relationship.journeyIdsByUserId.get(id)?.length ?? 0, label: "journey" })}
          onSelect={(id) => onSelectRelated({ kind: "user", id })}
        />
        <dl className="mt-3 divide-y">
          <DetailField label="User description">{journey.userDescription || "Not provided"}</DetailField>
          <DetailField label="Primary problems">{journey.primaryProblems || "Not provided"}</DetailField>
          {missingUserIds.length > 0 ? (
            <DetailField label="Missing user IDs">
              <span className="font-mono">{missingUserIds.join(", ")}</span>
            </DetailField>
          ) : null}
          {crossProductAssignments.length > 0 ? (
            <DetailField label="Cross-product links">
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
    const relatedJourneys = journeyIds
      .map((id) => relationship.journeys.find((item) => item.id === id))
      .filter((item) => item !== undefined);
    return (
      <div className="p-4 sm:p-5">
        <DetailHeader name={user.name} identifier={user.id} label="Product user" />
        <RelatedRecords
          records={relatedJourneys}
          emptyMessage="This product user is not assigned to a journey."
          countForRecord={(id) => ({ count: relationship.userIdsByJourneyId.get(id)?.length ?? 0, label: "product user" })}
          onSelect={(id) => onSelectRelated({ kind: "journey", id })}
        />
        <dl className="mt-3 divide-y">
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
