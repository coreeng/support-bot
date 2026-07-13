import type { CrossProductAssignment, MissingAssignment, RelationshipIntegrity } from "@/components/elevate/elevate-relationships";
import { AlertTriangle } from "lucide-react";
import type { ReactNode } from "react";

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function assignmentGroups<T extends MissingAssignment>(assignments: T[]) {
  const groups = new Map<string, { journeyId: string; journeyName: string; journeyProductId: string; assignments: T[] }>();
  for (const assignment of assignments) {
    const group = groups.get(assignment.journeyId) ?? {
      journeyId: assignment.journeyId,
      journeyName: assignment.journeyName,
      journeyProductId: assignment.journeyProductId,
      assignments: [],
    };
    group.assignments.push(assignment);
    groups.set(assignment.journeyId, group);
  }
  return [...groups.values()].sort((left, right) => left.journeyName.localeCompare(right.journeyName));
}

function IssueSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="text-foreground text-sm font-medium">{title}</h3>
      <ul className="mt-2 space-y-2">{children}</ul>
    </section>
  );
}

function RecordIdentifiers({ id, productId }: { id: string; productId: string }) {
  return (
    <span className="text-muted-foreground block font-mono text-xs break-all">
      ID {id} · product {productId}
    </span>
  );
}

export function ElevateIntegrityNotice({
  orphanJourneys,
  orphanUsers,
  missingAssignments,
  crossProductAssignments,
}: RelationshipIntegrity) {
  const issues = [
    orphanJourneys.length > 0 ? countLabel(orphanJourneys.length, "journey without a synced product") : null,
    orphanUsers.length > 0 ? countLabel(orphanUsers.length, "product user without a synced product") : null,
    missingAssignments.length > 0 ? countLabel(missingAssignments.length, "assignment to a missing product user") : null,
    crossProductAssignments.length > 0 ? countLabel(crossProductAssignments.length, "cross-product assignment") : null,
  ].filter(Boolean);

  if (issues.length === 0) return null;
  return (
    <div className="border-warning/30 bg-warning/10 flex items-start gap-3 border-b p-4 sm:p-5" role="alert">
      <AlertTriangle className="text-warning mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-foreground text-sm font-medium">Unmatched synced data</p>
        <p className="text-muted-foreground text-base text-pretty sm:text-sm">
          The relationship map excludes {issues.join(", ")}. Reconcile these records in Elevate; Support Bot has retained the complete raw
          snapshot.
        </p>
        <details className="mt-3">
          <summary className="text-foreground focus-visible:ring-ring/50 w-fit cursor-pointer rounded-sm text-sm font-medium outline-none focus-visible:ring-[3px]">
            Review unmatched records
          </summary>
          <div
            className="bg-background/70 focus-visible:ring-ring/50 mt-3 grid max-h-80 gap-5 overflow-y-auto rounded-md border p-3 outline-none focus-visible:ring-[3px] sm:grid-cols-2"
            role="region"
            aria-label="Unmatched synced records"
            tabIndex={0}
          >
            {orphanJourneys.length > 0 ? (
              <IssueSection title="Journeys without products">
                {orphanJourneys.map((journey) => (
                  <li key={journey.id} className="text-sm">
                    <span className="text-foreground font-medium">{journey.name}</span>
                    <RecordIdentifiers id={journey.id} productId={journey.productId} />
                  </li>
                ))}
              </IssueSection>
            ) : null}

            {orphanUsers.length > 0 ? (
              <IssueSection title="Product users without products">
                {orphanUsers.map((user) => (
                  <li key={user.id} className="text-sm">
                    <span className="text-foreground font-medium">{user.name}</span>
                    <RecordIdentifiers id={user.id} productId={user.productId} />
                  </li>
                ))}
              </IssueSection>
            ) : null}

            {missingAssignments.length > 0 ? (
              <IssueSection title="Missing product users">
                {assignmentGroups(missingAssignments).map((group) => (
                  <li key={group.journeyId} className="text-sm">
                    <span className="text-foreground font-medium">{group.journeyName}</span>
                    <RecordIdentifiers id={group.journeyId} productId={group.journeyProductId} />
                    <span className="text-muted-foreground block text-xs">
                      Missing user {group.assignments.map((assignment) => assignment.userId).join(", ")}
                    </span>
                  </li>
                ))}
              </IssueSection>
            ) : null}

            {crossProductAssignments.length > 0 ? (
              <IssueSection title="Product users assigned across products">
                {assignmentGroups(crossProductAssignments).map((group) => (
                  <li key={group.journeyId} className="text-sm">
                    <span className="text-foreground font-medium">{group.journeyName}</span>
                    <RecordIdentifiers id={group.journeyId} productId={group.journeyProductId} />
                    <span className="text-muted-foreground block text-xs">
                      {group.assignments
                        .map(
                          (assignment: CrossProductAssignment) =>
                            `${assignment.userName} (${assignment.userId}; product ${assignment.userProductId})`
                        )
                        .join(", ")}
                    </span>
                  </li>
                ))}
              </IssueSection>
            ) : null}
          </div>
        </details>
      </div>
    </div>
  );
}
