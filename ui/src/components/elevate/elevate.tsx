"use client";

import { ElevateDataTable } from "@/components/elevate/ElevateDataTable";
import { ElevateStatusCards } from "@/components/elevate/ElevateStatusCards";
import { journeyColumns, productColumns, userColumns } from "@/components/elevate/elevate-columns";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useElevateStatus } from "@/lib/hooks";
import { AlertCircle, Package, RefreshCw, Route, Users } from "lucide-react";

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

          <Tabs defaultValue="products" className="space-y-4">
            <TabsList className="grid h-auto w-full grid-cols-3 sm:inline-flex sm:h-9 sm:w-fit">
              <TabsTrigger value="products" className="min-w-0 cursor-pointer px-1 text-xs sm:px-2 sm:text-sm">
                <Package className="hidden sm:block" /> Products{" "}
                <Badge variant="secondary" className="hidden font-mono tabular-nums sm:inline-flex">
                  {data.products.length}
                </Badge>
              </TabsTrigger>
              <TabsTrigger value="journeys" className="min-w-0 cursor-pointer px-1 text-xs sm:px-2 sm:text-sm">
                <Route className="hidden sm:block" /> Journeys{" "}
                <Badge variant="secondary" className="hidden font-mono tabular-nums sm:inline-flex">
                  {data.journeys.length}
                </Badge>
              </TabsTrigger>
              <TabsTrigger value="users" className="min-w-0 cursor-pointer px-1 text-xs sm:px-2 sm:text-sm">
                <Users className="hidden sm:block" /> Users{" "}
                <Badge variant="secondary" className="hidden font-mono tabular-nums sm:inline-flex">
                  {data.users.length}
                </Badge>
              </TabsTrigger>
            </TabsList>

            <TabsContent value="products" className="space-y-6">
              <ElevateDataTable
                title="Products"
                description="Products available to this Elevate Agent Connection."
                caption="Products synchronized from Elevate"
                items={data.products}
                columns={productColumns}
                rowKey={(product) => product.id}
                emptyTitle="No products synced"
                emptyDescription="Elevate has not returned any products for this Agent Connection."
              />
            </TabsContent>
            <TabsContent value="journeys" className="space-y-6">
              <ElevateDataTable
                title="Journeys"
                description="Journeys associated with the synchronized products."
                caption="Journeys synchronized from Elevate"
                items={data.journeys}
                columns={journeyColumns}
                rowKey={(journey) => journey.id}
                emptyTitle="No journeys synced"
                emptyDescription="Elevate has not returned any journeys for this Agent Connection."
              />
            </TabsContent>
            <TabsContent value="users" className="space-y-6">
              <ElevateDataTable
                title="Users"
                description="Product users associated with the synchronized products."
                caption="Users synchronized from Elevate"
                items={data.users}
                columns={userColumns}
                rowKey={(user) => user.id}
                emptyTitle="No users synced"
                emptyDescription="Elevate has not returned any users for this Agent Connection."
              />
            </TabsContent>
          </Tabs>
        </>
      ) : null}
    </div>
  );
}
