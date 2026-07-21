"use client";

import LoadingSkeleton from "@/components/LoadingSkeleton";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useTeamFilter } from "@/contexts/TeamFilterContext";
import { TEAM_SCOPE } from "@/lib/constants";
import { useAllTickets, useRegistry } from "@/lib/hooks";
import { enumValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import { normalizeTeamKey } from "@/lib/teamUtils";
import { PaginatedTickets, TicketTag, TicketWithLogs } from "@/lib/types";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { useMemo } from "react";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

const PRODUCT_TAG_PREFIX = "Product -";
const NO_PRODUCT_LABEL = "None";

const CHART_COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-9)",
  "var(--chart-10)",
  "var(--chart-8)",
];

type SortColumn = "product" | "count";

function SortableHeader({
  col,
  label,
  sortColumn,
  sortDirection,
  onSort,
}: {
  col: SortColumn;
  label: string;
  sortColumn: SortColumn;
  sortDirection: "asc" | "desc";
  onSort: (column: SortColumn) => void;
}) {
  return (
    <TableHead className="hover:bg-muted cursor-pointer transition-colors select-none" onClick={() => onSort(col)}>
      <span className="inline-flex items-center gap-1">
        {label}
        {sortColumn === col ? (
          sortDirection === "asc" ? (
            <ArrowUp className="h-3.5 w-3.5" />
          ) : (
            <ArrowDown className="h-3.5 w-3.5" />
          )
        ) : (
          <ArrowUpDown className="text-muted-foreground h-3.5 w-3.5" />
        )}
      </span>
    </TableHead>
  );
}

const stripProductPrefix = (label: string): string => label.slice(PRODUCT_TAG_PREFIX.length).trim();

const formatPercentage = (count: number, total: number): string => (total > 0 ? `${((count / total) * 100).toFixed(1)}%` : "0.0%");

const getTagLabel = (tag: unknown): string => {
  if (typeof tag === "string") return tag;
  if (tag && typeof tag === "object") {
    const obj = tag as { label?: string; code?: string };
    return obj.label || obj.code || "";
  }
  return "";
};

export default function ProductsPage({ dateRange }: { dateRange?: { from?: string; to?: string } }) {
  const { effectiveTeams, hasNoTeamScope: contextHasNoTeamScope } = useTeamFilter();
  const hasNoTeamScope = contextHasNoTeamScope ?? effectiveTeams.includes(TEAM_SCOPE.NO_TEAMS);

  const { data: registryData } = useRegistry();

  const [params, setParams] = useUrlParams(
    {
      sortBy: "count",
      sortDir: "desc",
      hideUntagged: "false",
    },
    {
      sortBy: enumValidator(["product", "count"] as const, "count"),
      sortDir: enumValidator(["asc", "desc"] as const, "desc"),
      hideUntagged: enumValidator(["true", "false"] as const, "false"),
    }
  );

  // Casts are safe: sortBy and sortDir are guarded by enumValidators above.
  const sortColumn = params.sortBy as SortColumn;
  const sortDirection = params.sortDir as "asc" | "desc";
  const hideUntagged = params.hideUntagged === "true";

  const handleSort = (column: SortColumn) => {
    if (sortColumn === column) {
      setParams({ sortDir: sortDirection === "asc" ? "desc" : "asc" });
    } else {
      setParams({ sortBy: column, sortDir: column === "count" ? "desc" : "asc" });
    }
  };

  const ticketsQuery = useAllTickets(200, dateRange?.from, dateRange?.to, !hasNoTeamScope);
  const ticketsData = ticketsQuery.data as PaginatedTickets | undefined;
  const ticketsContent = useMemo(() => (ticketsData?.content as TicketWithLogs[] | undefined) ?? [], [ticketsData]);

  // Scope tickets to the teams the user is viewing, mirroring the tickets page.
  const visibleTickets = useMemo(() => {
    if (hasNoTeamScope) return [];
    if (effectiveTeams.length === 0) return ticketsContent;
    return ticketsContent.filter((t: TicketWithLogs) => {
      if (!t.team?.name) return false;
      const ticketTeam = normalizeTeamKey(t.team.name);
      return effectiveTeams.some((team) => normalizeTeamKey(team) === ticketTeam);
    });
  }, [ticketsContent, hasNoTeamScope, effectiveTeams]);

  const totalTickets = visibleTickets.length;

  const productCounts = useMemo(() => {
    const labelByCode = new Map<string, string>();
    (registryData?.tags ?? []).forEach((tag: TicketTag) => labelByCode.set(tag.code, tag.label));

    const counts = new Map<string, number>();
    // Seed with active registry product tags so products with no tickets in range still appear.
    (registryData?.tags ?? []).forEach((tag: TicketTag) => {
      if (tag.active !== false && tag.label.startsWith(PRODUCT_TAG_PREFIX)) {
        counts.set(stripProductPrefix(tag.label), 0);
      }
    });
    counts.set(NO_PRODUCT_LABEL, 0);

    visibleTickets.forEach((t: TicketWithLogs) => {
      // A ticket counts once per distinct product, even if tagged twice.
      const products = new Set<string>();
      (t.tags ?? []).forEach((code) => {
        const label = labelByCode.get(code as string) || getTagLabel(code);
        if (label.startsWith(PRODUCT_TAG_PREFIX)) products.add(stripProductPrefix(label));
      });
      if (products.size === 0) products.add(NO_PRODUCT_LABEL);
      products.forEach((product) => counts.set(product, (counts.get(product) ?? 0) + 1));
    });

    return Array.from(counts, ([product, count]) => ({ product, count }));
  }, [visibleTickets, registryData]);

  // Hiding untagged tickets removes them entirely: no None row, and they leave
  // the Totals count and percentage denominator too.
  const noneCount = productCounts.find((p) => p.product === NO_PRODUCT_LABEL)?.count ?? 0;
  const displayedCounts = useMemo(
    () => (hideUntagged ? productCounts.filter((p) => p.product !== NO_PRODUCT_LABEL) : productCounts),
    [productCounts, hideUntagged]
  );
  const displayedTotal = hideUntagged ? totalTickets - noneCount : totalTickets;

  // The chart is a fixed magnitude ranking (largest first), independent of the table's sort.
  const chartData = useMemo(
    () => [...displayedCounts].sort((a, b) => b.count - a.count || a.product.localeCompare(b.product)),
    [displayedCounts]
  );

  // Hues are assigned by alphabetical position, not rank, so a product keeps its
  // color when counts shift between date ranges. "None" stays gray (catch-all).
  const colorByProduct = useMemo(() => {
    const products = productCounts
      .map((p) => p.product)
      .filter((p) => p !== NO_PRODUCT_LABEL)
      .sort((a, b) => a.localeCompare(b));
    return new Map(products.map((p, idx) => [p, CHART_COLORS[idx % CHART_COLORS.length]]));
  }, [productCounts]);

  const sortedProducts = useMemo(() => {
    return [...displayedCounts].sort((a, b) => {
      if (sortColumn === "count") {
        const cmp = a.count - b.count;
        // Ties stay alphabetical regardless of sort direction
        if (cmp === 0) return a.product.localeCompare(b.product);
        return sortDirection === "asc" ? cmp : -cmp;
      }
      const cmp = a.product.localeCompare(b.product);
      return sortDirection === "asc" ? cmp : -cmp;
    });
  }, [displayedCounts, sortColumn, sortDirection]);

  return (
    <div className="space-y-6">
      <p className="text-muted-foreground text-sm">Ticket counts per product tag</p>

      {hasNoTeamScope && (
        <div className="border-warning/30 bg-warning/10 text-warning rounded-lg border p-4">
          <p className="font-semibold">No Team Access</p>
          <p className="mt-1 text-sm">You are not assigned to any teams, so product ticket counts cannot be displayed.</p>
        </div>
      )}

      {!ticketsQuery.isLoading && !ticketsQuery.error && !hasNoTeamScope && (
        <div className="flex items-center gap-2">
          <input
            id="hide-untagged"
            type="checkbox"
            checked={hideUntagged}
            onChange={(e) => setParams({ hideUntagged: e.target.checked ? "true" : "false" })}
            className="accent-primary h-4 w-4 cursor-pointer"
          />
          <Label htmlFor="hide-untagged" className="cursor-pointer font-normal">
            Hide tickets without a product tag
          </Label>
        </div>
      )}

      {!ticketsQuery.isLoading && !ticketsQuery.error && !hasNoTeamScope && chartData.length > 0 && (
        <div className="bg-card rounded-lg border p-4">
          <h2 className="text-foreground mb-2 text-center font-semibold">Tickets by Product</h2>
          <div style={{ width: "100%", height: Math.max(200, chartData.length * 36 + 60) }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} layout="vertical" margin={{ left: 16, right: 24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
                <XAxis
                  type="number"
                  allowDecimals={false}
                  stroke="var(--border)"
                  tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                />
                <YAxis
                  type="category"
                  dataKey="product"
                  width={120}
                  stroke="var(--border)"
                  tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--popover)",
                    color: "var(--popover-foreground)",
                    border: "1px solid var(--border)",
                    borderRadius: "8px",
                    boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                  }}
                  labelStyle={{ color: "var(--popover-foreground)" }}
                  itemStyle={{ color: "var(--popover-foreground)" }}
                  cursor={{ fill: "var(--accent)" }}
                  formatter={(value: number) => [`${value} (${formatPercentage(value, displayedTotal)})`, "Tickets"]}
                />
                <Bar dataKey="count" fill="var(--chart-1)" barSize={20} radius={[0, 4, 4, 0]}>
                  {chartData.map((entry) => (
                    <Cell
                      key={entry.product}
                      fill={
                        entry.product === NO_PRODUCT_LABEL
                          ? "var(--muted-foreground)"
                          : (colorByProduct.get(entry.product) ?? "var(--chart-1)")
                      }
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border">
        {ticketsQuery.isLoading ? (
          <LoadingSkeleton />
        ) : ticketsQuery.error ? (
          <div className="border-destructive/30 bg-destructive/10 text-destructive m-6 rounded-lg border p-4">
            <p className="font-semibold">Error loading products</p>
            <p className="mt-1 text-sm">Unable to load ticket data. Please try refreshing the page.</p>
          </div>
        ) : (
          <Table>
            <TableHeader className="bg-muted z-10">
              <TableRow>
                <SortableHeader col="product" label="Product" sortColumn={sortColumn} sortDirection={sortDirection} onSort={handleSort} />
                <SortableHeader col="count" label="Tickets" sortColumn={sortColumn} sortDirection={sortDirection} onSort={handleSort} />
                <TableHead>% of Tickets</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedProducts.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={3} className="text-muted-foreground py-8 text-center">
                    No product tags found
                  </TableCell>
                </TableRow>
              ) : (
                <>
                  {sortedProducts.map(({ product, count }) => (
                    <TableRow key={product}>
                      <TableCell>{product}</TableCell>
                      <TableCell className="font-mono text-sm tabular-nums">{count}</TableCell>
                      <TableCell className="font-mono text-sm tabular-nums">{formatPercentage(count, displayedTotal)}</TableCell>
                    </TableRow>
                  ))}
                  {/* Distinct tickets in scope — can be below the column sum since a ticket may carry several product tags. */}
                  <TableRow className="bg-muted/50 border-t font-semibold">
                    <TableCell>Totals</TableCell>
                    <TableCell className="font-mono text-sm tabular-nums">{displayedTotal}</TableCell>
                    <TableCell className="font-mono text-sm tabular-nums">{formatPercentage(displayedTotal, displayedTotal)}</TableCell>
                  </TableRow>
                </>
              )}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  );
}
