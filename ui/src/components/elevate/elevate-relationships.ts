import type { ElevateJourney, ElevateProduct, ElevateUser } from "@/lib/types";

export interface ProductRelationship {
  product: ElevateProduct;
  journeys: ElevateJourney[];
  users: ElevateUser[];
  userIdsByJourneyId: ReadonlyMap<string, string[]>;
  journeyIdsByUserId: ReadonlyMap<string, string[]>;
  missingUserIdsByJourneyId: ReadonlyMap<string, string[]>;
  crossProductAssignmentsByJourneyId: ReadonlyMap<string, CrossProductAssignment[]>;
}

export interface MissingAssignment {
  journeyId: string;
  journeyName: string;
  journeyProductId: string;
  userId: string;
}

export interface CrossProductAssignment extends MissingAssignment {
  userName: string;
  userProductId: string;
}

export interface RelationshipIntegrity {
  orphanJourneys: ElevateJourney[];
  orphanUsers: ElevateUser[];
  missingAssignments: MissingAssignment[];
  crossProductAssignments: CrossProductAssignment[];
}

export interface ElevateRelationshipModel {
  products: ProductRelationship[];
  integrity: RelationshipIntegrity;
}

export type RelationshipFocus = { kind: "journey" | "user"; id: string } | null;

function byName<T extends { name: string }>(left: T, right: T) {
  return left.name.localeCompare(right.name, undefined, { sensitivity: "base" });
}

export function buildRelationshipModel(
  products: ElevateProduct[],
  journeys: ElevateJourney[],
  users: ElevateUser[]
): ElevateRelationshipModel {
  const productIds = new Set(products.map((product) => product.id));
  const usersById = new Map(users.map((user) => [user.id, user]));
  const orphanJourneys = journeys.filter((journey) => !productIds.has(journey.productId)).sort(byName);
  const orphanUsers = users.filter((user) => !productIds.has(user.productId)).sort(byName);
  const missingAssignments: RelationshipIntegrity["missingAssignments"] = [];
  const crossProductAssignments: RelationshipIntegrity["crossProductAssignments"] = [];

  for (const journey of journeys) {
    for (const userId of new Set(journey.userIds)) {
      const user = usersById.get(userId);
      if (!user) {
        missingAssignments.push({ journeyId: journey.id, journeyName: journey.name, journeyProductId: journey.productId, userId });
      } else if (user.productId !== journey.productId) {
        crossProductAssignments.push({
          journeyId: journey.id,
          journeyName: journey.name,
          journeyProductId: journey.productId,
          userId,
          userName: user.name,
          userProductId: user.productId,
        });
      }
    }
  }

  const relationships = [...products].sort(byName).map((product): ProductRelationship => {
    const productJourneys = journeys.filter((journey) => journey.productId === product.id).sort(byName);
    const productUsers = users.filter((user) => user.productId === product.id).sort(byName);
    const productUserIds = new Set(productUsers.map((user) => user.id));
    const userIdsByJourneyId = new Map<string, string[]>();
    const journeyIdsByUserId = new Map(productUsers.map((user) => [user.id, [] as string[]]));
    const missingUserIdsByJourneyId = new Map<string, string[]>();
    const crossProductAssignmentsByJourneyId = new Map<string, CrossProductAssignment[]>();

    for (const journey of productJourneys) {
      const uniqueUserIds = [...new Set(journey.userIds)];
      const validUserIds = uniqueUserIds.filter((userId) => productUserIds.has(userId));
      const missingUserIds = uniqueUserIds.filter((userId) => !usersById.has(userId));
      const journeyCrossProductAssignments = crossProductAssignments.filter((assignment) => assignment.journeyId === journey.id);
      userIdsByJourneyId.set(journey.id, validUserIds);
      if (missingUserIds.length > 0) missingUserIdsByJourneyId.set(journey.id, missingUserIds);
      if (journeyCrossProductAssignments.length > 0) {
        crossProductAssignmentsByJourneyId.set(journey.id, journeyCrossProductAssignments);
      }
      for (const userId of validUserIds) journeyIdsByUserId.get(userId)?.push(journey.id);
    }

    return {
      product,
      journeys: productJourneys,
      users: productUsers,
      userIdsByJourneyId,
      journeyIdsByUserId,
      missingUserIdsByJourneyId,
      crossProductAssignmentsByJourneyId,
    };
  });

  return {
    products: relationships,
    integrity: { orphanJourneys, orphanUsers, missingAssignments, crossProductAssignments },
  };
}
