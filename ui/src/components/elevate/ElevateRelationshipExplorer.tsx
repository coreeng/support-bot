"use client";

import { ElevateIntegrityNotice } from "@/components/elevate/ElevateIntegrityNotice";
import { ElevateProductPicker } from "@/components/elevate/ElevateProductPicker";
import { ElevateRelationshipBrowser } from "@/components/elevate/ElevateRelationshipBrowser";
import { formatTimestamp } from "@/components/elevate/ElevateStatusCards";
import { buildRelationshipModel, type ProductRelationship } from "@/components/elevate/elevate-relationships";
import type { ElevateJourney, ElevateProduct, ElevateUser } from "@/lib/types";
import { useMemo, useState } from "react";

function ProductOverview({ relationship }: { relationship: ProductRelationship }) {
  const assignments = [...relationship.userIdsByJourneyId.values()].reduce((total, userIds) => total + userIds.length, 0);
  return (
    <div className="border-b p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h3 className="text-foreground truncate text-lg font-semibold">{relationship.product.name}</h3>
          <p className="text-muted-foreground text-base text-pretty sm:text-sm">
            {relationship.product.customer || "No customer recorded"} · <span className="font-mono">{relationship.product.slug}</span>
          </p>
        </div>
        <p className="text-muted-foreground text-base sm:text-sm">
          Updated{" "}
          <time className="font-mono tabular-nums" dateTime={relationship.product.lastUpdatedAt}>
            {formatTimestamp(relationship.product.lastUpdatedAt)}
          </time>
        </p>
      </div>
      <dl className="mt-4 grid grid-cols-3 divide-x border-t pt-4">
        {[
          ["Journeys", relationship.journeys.length],
          ["Product users", relationship.users.length],
          ["Assignments", assignments],
        ].map(([label, value]) => (
          <div key={label} className="px-3 first:pl-0 last:pr-0">
            <dt className="text-muted-foreground min-h-8 text-xs leading-tight sm:min-h-0 sm:text-sm">{label}</dt>
            <dd className="text-foreground font-mono text-xl font-semibold tracking-tight tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

export function ElevateRelationshipExplorer({
  products,
  journeys,
  users,
}: {
  products: ElevateProduct[];
  journeys: ElevateJourney[];
  users: ElevateUser[];
}) {
  const model = useMemo(() => buildRelationshipModel(products, journeys, users), [journeys, products, users]);
  const [selectedProductId, setSelectedProductId] = useState(() => model.products[0]?.product.id ?? "");
  const selectedRelationship = model.products.find((relationship) => relationship.product.id === selectedProductId) ?? model.products[0];

  return (
    <section className="bg-card overflow-hidden rounded-xl border" aria-labelledby="synced-relationships-title">
      <header className="border-b p-4 sm:p-5">
        <h2 id="synced-relationships-title" className="text-foreground text-base font-semibold">
          Synced relationships
        </h2>
        <p className="text-muted-foreground max-w-[75ch] text-base text-pretty sm:text-sm">
          Browse products, journeys, and product users from the last complete local snapshot.
        </p>
      </header>

      <ElevateIntegrityNotice {...model.integrity} />

      {selectedRelationship ? (
        <>
          <div className="bg-muted/20 border-b p-4 sm:p-5">
            <div className="flex flex-wrap items-end justify-between gap-2 sm:max-w-xl">
              <label className="text-foreground text-sm font-medium" htmlFor="elevate-product-picker">
                Product
              </label>
              <span className="text-muted-foreground text-sm">{model.products.length} available</span>
            </div>
            <div className="mt-2">
              <ElevateProductPicker
                relationships={model.products}
                selectedProductId={selectedRelationship.product.id}
                onSelect={setSelectedProductId}
              />
            </div>
          </div>
          <ProductOverview relationship={selectedRelationship} />
          <ElevateRelationshipBrowser key={selectedRelationship.product.id} relationship={selectedRelationship} />
        </>
      ) : (
        <div className="p-10 text-center" role="status">
          <p className="text-foreground text-sm font-medium">No products synced</p>
          <p className="text-muted-foreground mt-1 text-base text-pretty sm:text-sm">
            The last complete Elevate snapshot did not contain any products to browse.
          </p>
        </div>
      )}
    </section>
  );
}
