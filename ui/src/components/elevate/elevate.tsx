"use client";

import { ElevateRelationshipExplorer } from "@/components/elevate/ElevateRelationshipExplorer";
import { ElevateStatusCards } from "@/components/elevate/ElevateStatusCards";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useElevateStatus } from "@/lib/hooks";
import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle, RefreshCw } from "lucide-react";
import { useCallback, useRef } from "react";

function ElevateLoading() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Loading Elevate connection data">
      <div className="grid gap-6 lg:grid-cols-2">
        {["connection", "snapshot"].map((key) => (
          <div key={key} className="bg-card space-y-4 rounded-xl border p-6">
            <Skeleton className="h-5 w-36" />
            <Skeleton className="h-4 w-64 max-w-full" />
            <Skeleton className="h-40 w-full" />
          </div>
        ))}
      </div>
      <Skeleton className="h-10 w-72 max-w-full" />
      <Skeleton className="h-72 w-full" />
    </div>
  );
}

export default function ElevatePage() {
  const { data, isLoading, isFetching, error, refetch } = useElevateStatus();
  const queryClient = useQueryClient();
  const recoveringSnapshot = useRef(false);
  const handleSnapshotChanged = useCallback(async () => {
    if (recoveringSnapshot.current) return;
    recoveringSnapshot.current = true;
    try {
      await queryClient.cancelQueries({ queryKey: ["elevate"] });
      queryClient.removeQueries({
        predicate: (query) => query.queryKey[0] === "elevate" && query.queryKey[1] !== "status",
      });
      await refetch();
    } finally {
      recoveringSnapshot.current = false;
    }
  }, [queryClient, refetch]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Elevate connection</h1>
          <p className="text-muted-foreground text-sm">Monitor the Agent Connection and the Insights data stored by Support Bot.</p>
        </div>
        <Button variant="outline" size="sm" disabled={isFetching} onClick={() => refetch()}>
          <RefreshCw className={isFetching ? "animate-spin" : undefined} />
          Refresh status
        </Button>
      </div>

      {isLoading ? <ElevateLoading /> : null}

      {!isLoading && error && !data ? (
        <div className="bg-card rounded-xl border p-6 text-center" role="alert">
          <AlertCircle className="text-destructive mx-auto h-8 w-8" />
          <h2 className="text-foreground mt-3 text-base font-semibold">Unable to load Elevate status</h2>
          <p className="text-muted-foreground mt-1 text-sm">Support Bot could not load its locally stored connection data.</p>
          <Button className="mt-4" variant="outline" size="sm" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {data ? (
        <>
          {error ? (
            <div
              className="border-warning/30 bg-warning/10 text-foreground flex items-center gap-2 rounded-md border p-3 text-sm"
              role="alert"
            >
              <AlertCircle className="text-warning h-4 w-4 shrink-0" />
              Could not refresh Elevate status. Showing the most recently loaded local data.
            </div>
          ) : null}

          <ElevateStatusCards status={data} />

          {data.snapshotVersion ? (
            <ElevateRelationshipExplorer
              key={data.snapshotVersion}
              snapshotVersion={data.snapshotVersion}
              productCount={data.counts.products}
              integrity={data.integrity}
              onSnapshotChanged={handleSnapshotChanged}
            />
          ) : (
            <div className="bg-card rounded-xl border p-10 text-center" role="status">
              <p className="text-foreground text-sm font-medium">No snapshot available</p>
              <p className="text-muted-foreground mt-1 text-sm">A complete Elevate sync has not been stored yet.</p>
            </div>
          )}
        </>
      ) : null}
    </div>
  );
}
