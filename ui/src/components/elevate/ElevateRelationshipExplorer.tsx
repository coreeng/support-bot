"use client";

import { ElevateIntegrityNotice } from "@/components/elevate/ElevateIntegrityNotice";
import { ElevateRelationshipMap } from "@/components/elevate/ElevateRelationshipMap";
import { formatTimestamp } from "@/components/elevate/ElevateStatusCards";
import { buildRelationshipModel, type ProductRelationship } from "@/components/elevate/elevate-relationships";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { ElevateJourney, ElevateProduct, ElevateUser } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useMemo, useState } from "react";

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function ProductButton({
  relationship,
  selected,
  onSelect,
}: {
  relationship: ProductRelationship;
  selected: boolean;
  onSelect: () => void;
}) {
  const { product, journeys, users } = relationship;
  return (
    <li>
      <button
        type="button"
        aria-pressed={selected}
        aria-label={`${product.name}, ${countLabel(journeys.length, "journey")}, ${countLabel(users.length, "product user")}`}
        className={cn(
          "focus-visible:ring-ring/50 hover:bg-accent/50 w-full cursor-pointer rounded-md px-3 py-2.5 text-left outline-none focus-visible:ring-[3px]",
          selected && "bg-accent text-accent-foreground"
        )}
        onClick={onSelect}
      >
        <span className="text-foreground block truncate text-sm font-medium">{product.name}</span>
        <span className="text-muted-foreground block truncate font-mono text-sm">{product.slug}</span>
        <span className="text-muted-foreground block text-sm">
          {countLabel(journeys.length, "journey")} · {countLabel(users.length, "product user")}
        </span>
      </button>
    </li>
  );
}

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
          Explore how each product connects its journeys and product users in the last complete local snapshot.
        </p>
      </header>

      <ElevateIntegrityNotice {...model.integrity} />

      {selectedRelationship ? (
        <div className="xl:grid xl:grid-cols-[17rem_minmax(0,1fr)]">
          <aside className="hidden border-r p-3 xl:block" aria-label="Synced products">
            <div className="px-3 py-2">
              <h3 className="text-foreground text-sm font-medium">Products</h3>
              <p className="text-muted-foreground text-sm">Choose a product to inspect its map.</p>
            </div>
            <ul role="list" className="max-h-[42rem] space-y-1 overflow-y-auto">
              {model.products.map((relationship) => (
                <ProductButton
                  key={relationship.product.id}
                  relationship={relationship}
                  selected={relationship.product.id === selectedRelationship.product.id}
                  onSelect={() => setSelectedProductId(relationship.product.id)}
                />
              ))}
            </ul>
          </aside>

          <div className="min-w-0">
            <div className="border-b p-4 sm:p-5 xl:hidden">
              <label id="product-selector-label" className="text-foreground text-sm font-medium">
                Product
              </label>
              <Select value={selectedRelationship.product.id} onValueChange={setSelectedProductId}>
                <SelectTrigger className="mt-2 w-full" aria-labelledby="product-selector-label">
                  <SelectValue>{selectedRelationship.product.name}</SelectValue>
                </SelectTrigger>
                <SelectContent align="start">
                  {model.products.map((relationship) => (
                    <SelectItem key={relationship.product.id} value={relationship.product.id}>
                      {relationship.product.name} · {countLabel(relationship.journeys.length, "journey")} ·{" "}
                      {countLabel(relationship.users.length, "product user")}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <ProductOverview relationship={selectedRelationship} />
            <div className="p-4 sm:p-5">
              <ElevateRelationshipMap key={selectedRelationship.product.id} relationship={selectedRelationship} />
            </div>
          </div>
        </div>
      ) : (
        <div className="p-10 text-center" role="status">
          <p className="text-foreground text-sm font-medium">No products synced</p>
          <p className="text-muted-foreground mt-1 text-base text-pretty sm:text-sm">
            The last complete Elevate snapshot did not contain any products to map.
          </p>
        </div>
      )}
    </section>
  );
}
