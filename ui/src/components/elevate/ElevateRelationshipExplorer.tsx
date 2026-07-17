"use client";

import { ElevateIntegrityNotice } from "@/components/elevate/ElevateIntegrityNotice";
import { ElevateProductPicker } from "@/components/elevate/ElevateProductPicker";
import { ElevateRelationshipBrowser } from "@/components/elevate/ElevateRelationshipBrowser";
import { formatTimestamp } from "@/components/elevate/ElevateStatusCards";
import { isApiError, useElevateProduct, useElevateProducts } from "@/lib/hooks";
import type { ElevateIntegrityCounts, ElevateProduct } from "@/lib/types";
import { useEffect, useState } from "react";

function ProductOverview({ product }: { product: ElevateProduct }) {
  return (
    <div className="border-b p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h3 className="text-foreground truncate text-lg font-semibold">{product.name}</h3>
          <p className="text-muted-foreground text-sm text-pretty">
            {product.customer || "No customer recorded"} · <span className="font-mono">{product.slug}</span>
          </p>
        </div>
        <p className="text-muted-foreground text-sm">
          Updated{" "}
          <time className="font-mono tabular-nums" dateTime={product.lastUpdatedAt}>
            {formatTimestamp(product.lastUpdatedAt)}
          </time>
        </p>
      </div>
      <dl className="mt-4 grid grid-cols-3 divide-x border-t pt-4">
        {[
          ["Journeys", product.journeyCount],
          ["Product users", product.userCount],
          ["Assignments", product.assignmentCount],
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
  snapshotVersion,
  productCount,
  integrity,
  onSnapshotChanged,
}: {
  snapshotVersion: string;
  productCount: number;
  integrity: ElevateIntegrityCounts;
  onSnapshotChanged: () => void;
}) {
  const [selectedProductId, setSelectedProductId] = useState("");
  const products = useElevateProducts({ snapshotVersion, page: 0, pageSize: 20, sort: "name", direction: "asc" });
  const currentProducts = products.isPlaceholderData ? undefined : products.data;
  const effectiveProductId = selectedProductId || currentProducts?.content[0]?.id || "";
  const product = useElevateProduct(effectiveProductId, snapshotVersion, Boolean(effectiveProductId));
  const selectedProduct = product.isPlaceholderData ? undefined : product.data;
  const busy = products.isFetching || product.isFetching;

  useEffect(() => {
    if (isApiError(products.error, 409) || isApiError(product.error, 409)) onSnapshotChanged();
  }, [onSnapshotChanged, product.error, products.error]);

  return (
    <section
      className="bg-card overflow-hidden rounded-xl border"
      aria-labelledby="synced-relationships-title"
      aria-busy={busy || undefined}
    >
      <header className="border-b p-4 sm:p-5">
        <h2 id="synced-relationships-title" className="text-foreground text-base font-semibold">
          Synced relationships
        </h2>
        <p className="text-muted-foreground max-w-[75ch] text-sm text-pretty">
          Browse products, journeys, and product users from the last complete local snapshot.
        </p>
      </header>

      <ElevateIntegrityNotice counts={integrity} snapshotVersion={snapshotVersion} onSnapshotChanged={onSnapshotChanged} />

      {products.isLoading || products.isPlaceholderData || (effectiveProductId && (product.isLoading || product.isPlaceholderData)) ? (
        <p className="text-muted-foreground p-16 text-center text-sm">Loading synced products…</p>
      ) : null}
      {products.error && !currentProducts && !isApiError(products.error, 409) ? (
        <p className="text-destructive p-16 text-center text-sm" role="alert">
          Unable to load synced products.
        </p>
      ) : null}
      {product.error && !selectedProduct && !isApiError(product.error, 409) ? (
        <p className="text-destructive p-16 text-center text-sm" role="alert">
          Unable to load the selected product.
        </p>
      ) : null}

      {selectedProduct ? (
        <>
          <div className="bg-muted/20 border-b p-4 sm:p-5">
            <div className="flex flex-wrap items-end justify-between gap-2 sm:max-w-xl">
              <label className="text-foreground text-sm font-medium" htmlFor="elevate-product-picker">
                Product
              </label>
              <span className="text-muted-foreground text-sm">
                <span className="font-mono tabular-nums">{productCount}</span> available
              </span>
            </div>
            <div className="mt-2">
              <ElevateProductPicker
                snapshotVersion={snapshotVersion}
                selectedProduct={selectedProduct}
                onSelect={setSelectedProductId}
                onSnapshotChanged={onSnapshotChanged}
              />
            </div>
          </div>
          <ProductOverview product={selectedProduct} />
          <ElevateRelationshipBrowser
            key={selectedProduct.id}
            product={selectedProduct}
            snapshotVersion={snapshotVersion}
            onSnapshotChanged={onSnapshotChanged}
          />
        </>
      ) : null}

      {!products.isLoading && !products.isPlaceholderData && currentProducts?.totalElements === 0 ? (
        <div className="p-10 text-center" role="status">
          <p className="text-foreground text-sm font-medium">No products synced</p>
          <p className="text-muted-foreground mt-1 text-sm text-pretty">
            The last complete Elevate snapshot did not contain any products to browse.
          </p>
        </div>
      ) : null}
    </section>
  );
}
