import type { ElevateJourney, ElevateProduct, ElevateUser } from "@/lib/types";
import { buildRelationshipModel } from "../elevate-relationships";

const timestamps = {
  createdAt: "2026-01-01T08:00:00Z",
  lastUpdatedAt: "2026-07-12T08:00:00Z",
};

describe("buildRelationshipModel", () => {
  it("groups and sorts product records while building both relationship directions", () => {
    const products: ElevateProduct[] = [
      { id: "product-b", slug: "beta", name: "Beta", customer: "Customer B", ...timestamps },
      { id: "product-a", slug: "alpha", name: "Alpha", customer: "Customer A", ...timestamps },
    ];
    const journeys: ElevateJourney[] = [
      {
        id: "journey-a2",
        slug: "deploy",
        name: "Deploy",
        productId: "product-a",
        productSlug: "alpha",
        userDescription: "Deploy a workload.",
        primaryProblems: "Choosing a deployment path.",
        userIds: ["user-a2", "user-a1", "user-a1", "missing-user", "user-b"],
        ...timestamps,
      },
      {
        id: "journey-a1",
        slug: "build",
        name: "Build",
        productId: "product-a",
        productSlug: "alpha",
        userDescription: "Build a workload.",
        primaryProblems: "Choosing build tooling.",
        userIds: ["user-a1"],
        ...timestamps,
      },
      {
        id: "journey-b",
        slug: "observe",
        name: "Observe",
        productId: "product-b",
        productSlug: "beta",
        userDescription: "Observe a workload.",
        primaryProblems: "Finding useful signals.",
        userIds: ["user-b"],
        ...timestamps,
      },
      {
        id: "orphan-journey",
        slug: "orphan",
        name: "Orphan journey",
        productId: "missing-product",
        productSlug: "missing",
        userDescription: "An unmatched journey.",
        primaryProblems: "Its product is missing.",
        userIds: [],
        ...timestamps,
      },
    ];
    const users: ElevateUser[] = [
      { id: "user-a2", productId: "product-a", name: "Zed", description: "Alpha operator.", ...timestamps },
      { id: "user-a1", productId: "product-a", name: "Ada", description: "Alpha developer.", ...timestamps },
      { id: "user-b", productId: "product-b", name: "Bob", description: "Beta developer.", ...timestamps },
      {
        id: "orphan-user",
        productId: "missing-product",
        name: "Orphan product user",
        description: "An unmatched product user.",
        ...timestamps,
      },
    ];

    const model = buildRelationshipModel(products, journeys, users);

    expect(model.products.map(({ product }) => product.name)).toEqual(["Alpha", "Beta"]);
    expect(model.products[0].journeys.map(({ name }) => name)).toEqual(["Build", "Deploy"]);
    expect(model.products[0].users.map(({ name }) => name)).toEqual(["Ada", "Zed"]);
    expect(model.products[0].userIdsByJourneyId.get("journey-a2")).toEqual(["user-a2", "user-a1"]);
    expect(model.products[0].journeyIdsByUserId.get("user-a1")).toEqual(["journey-a1", "journey-a2"]);
    expect(model.products[0].missingUserIdsByJourneyId.get("journey-a2")).toEqual(["missing-user"]);
    expect(model.products[0].crossProductAssignmentsByJourneyId.get("journey-a2")).toEqual([
      {
        journeyId: "journey-a2",
        journeyName: "Deploy",
        journeyProductId: "product-a",
        userId: "user-b",
        userName: "Bob",
        userProductId: "product-b",
      },
    ]);
    expect(model.products[0].missingUserIdsByJourneyId.has("journey-a1")).toBe(false);
    expect(model.products[0].crossProductAssignmentsByJourneyId.has("journey-a1")).toBe(false);
    expect(model.integrity.orphanJourneys.map(({ id }) => id)).toEqual(["orphan-journey"]);
    expect(model.integrity.orphanUsers.map(({ id }) => id)).toEqual(["orphan-user"]);
    expect(model.integrity.missingAssignments).toEqual([
      {
        journeyId: "journey-a2",
        journeyName: "Deploy",
        journeyProductId: "product-a",
        userId: "missing-user",
      },
    ]);
    expect(model.integrity.crossProductAssignments).toEqual([
      {
        journeyId: "journey-a2",
        journeyName: "Deploy",
        journeyProductId: "product-a",
        userId: "user-b",
        userName: "Bob",
        userProductId: "product-b",
      },
    ]);
  });
});
