"use client";

import LoadingSkeleton from "@/components/LoadingSkeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAllTickets, useRegistry } from "@/lib/hooks";
import { enumValidator, useUrlParams } from "@/lib/hooks/useUrlParams";
import { PaginatedTickets, TicketTag, TicketWithLogs } from "@/lib/types";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { useMemo } from "react";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

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

// Product tags are detected by their label prefix ("Product - <name>") — a UI
// convention until the registry can declare a tag category. Matching tolerates
// case, spacing, and hyphen/en/em dash variants so a slightly different label
// doesn't silently drop a product; a label that is only the prefix (empty
// product name) is not a product tag.
const PRODUCT_TAG_PREFIX_RE = /^\s*product\s*[-–—]\s*/i;

const stripProductPrefix = (label: string): string => label.replace(PRODUCT_TAG_PREFIX_RE, "").trim();

const isProductLabel = (label: string): boolean => PRODUCT_TAG_PREFIX_RE.test(label) && stripProductPrefix(label) !== "";

// Used by the Analytics & Operations page to hide the Products View tab
// entirely when the registry has no active product tags.
export const hasActiveProductTags = (registryData?: { tags?: TicketTag[] }): boolean =>
  (registryData?.tags ?? []).some((tag) => tag.active !== false && isProductLabel(tag.label));

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
  const registryQuery = useRegistry();
  const registryData = registryQuery.data;

  const [params, setParams] = useUrlParams(
    {
      sortBy: "count",
      sortDir: "desc",
    },
    {
      sortBy: enumValidator(["product", "count"] as const, "count"),
      sortDir: enumValidator(["asc", "desc"] as const, "desc"),
    }
  );

  // Casts are safe: sortBy and sortDir are guarded by enumValidators above.
  const sortColumn = params.sortBy as SortColumn;
  const sortDirection = params.sortDir as "asc" | "desc";

  const handleSort = (column: SortColumn) => {
    if (sortColumn === column) {
      setParams({ sortDir: sortDirection === "asc" ? "desc" : "asc" });
    } else {
      setParams({ sortBy: column, sortDir: column === "count" ? "desc" : "asc" });
    }
  };

  // Deliberately unscoped by the sidebar team filter: like the other analytics
  // tabs on this page, Products View reports across all teams.
  const ticketsQuery = useAllTickets(200, dateRange?.from, dateRange?.to);
  const ticketsData = ticketsQuery.data as PaginatedTickets | undefined;
  const visibleTickets = useMemo(() => (ticketsData?.content as TicketWithLogs[] | undefined) ?? [], [ticketsData]);

  // Product bucketing needs the registry's code→label map, so the view is not
  // ready until BOTH queries resolve — rendering on tickets alone would bucket
  // every ticket under "None" and present it as final data.
  const isLoading = ticketsQuery.isLoading || registryQuery.isLoading;
  const loadError = ticketsQuery.error || registryQuery.error;

  // All tickets in range (untagged included) — the percentage denominator, so
  // each row reads as "share of all tickets in the selected period".
  const totalTickets = visibleTickets.length;

  // Tickets without a product tag get no row and don't count toward Total
  // Product Tickets, but they stay in the percentage denominator above — with
  // mostly-untagged data the percentages are deliberately small and don't sum
  // to 100%.
  const { productCounts, taggedTicketCount } = useMemo(() => {
    const labelByCode = new Map<string, string>();
    (registryData?.tags ?? []).forEach((tag: TicketTag) => labelByCode.set(tag.code, tag.label));

    const counts = new Map<string, number>();
    // Seed with active registry product tags so products with no tickets in
    // range still appear. Retired (inactive) product tags are deliberately not
    // seeded, but tickets carrying them still count below — a retired product
    // surfaces only for date ranges that contain its tickets.
    (registryData?.tags ?? []).forEach((tag: TicketTag) => {
      if (tag.active !== false && isProductLabel(tag.label)) {
        counts.set(stripProductPrefix(tag.label), 0);
      }
    });

    let taggedTicketCount = 0;
    visibleTickets.forEach((t: TicketWithLogs) => {
      // A ticket counts once per distinct product, even if tagged twice.
      const products = new Set<string>();
      (t.tags ?? []).forEach((code) => {
        const label = labelByCode.get(code as string) || getTagLabel(code);
        if (isProductLabel(label)) products.add(stripProductPrefix(label));
      });
      if (products.size === 0) return;
      taggedTicketCount++;
      products.forEach((product) => counts.set(product, (counts.get(product) ?? 0) + 1));
    });

    return { productCounts: Array.from(counts, ([product, count]) => ({ product, count })), taggedTicketCount };
  }, [visibleTickets, registryData]);

  // The chart is a fixed magnitude ranking (largest first), independent of the table's sort.
  const chartData = useMemo(
    () => [...productCounts].sort((a, b) => b.count - a.count || a.product.localeCompare(b.product)),
    [productCounts]
  );

  // Hues are assigned by alphabetical position over every registry product tag
  // (retired included), not by rank or by which rows currently have tickets —
  // so a product keeps its color when counts shift between date ranges, even
  // when a retired product's row only exists for some ranges.
  const colorByProduct = useMemo(() => {
    const products = Array.from(
      new Set(
        (registryData?.tags ?? [])
          .filter((tag: TicketTag) => isProductLabel(tag.label))
          .map((tag: TicketTag) => stripProductPrefix(tag.label))
      )
    ).sort((a, b) => a.localeCompare(b));
    return new Map(products.map((p, idx) => [p, CHART_COLORS[idx % CHART_COLORS.length]]));
  }, [registryData]);

  const sortedProducts = useMemo(() => {
    return [...productCounts].sort((a, b) => {
      if (sortColumn === "count") {
        const cmp = a.count - b.count;
        // Ties stay alphabetical regardless of sort direction
        if (cmp === 0) return a.product.localeCompare(b.product);
        return sortDirection === "asc" ? cmp : -cmp;
      }
      const cmp = a.product.localeCompare(b.product);
      return sortDirection === "asc" ? cmp : -cmp;
    });
  }, [productCounts, sortColumn, sortDirection]);

  return (
    <div className="space-y-6">
      <p className="text-muted-foreground text-sm">Ticket counts per product tag</p>

      {!isLoading && !loadError && chartData.length > 0 && (
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
                  formatter={(value: number) => [`${value} (${formatPercentage(value, totalTickets)})`, "Tickets"]}
                />
                <Bar dataKey="count" fill="var(--chart-1)" barSize={20} radius={[0, 4, 4, 0]}>
                  {chartData.map((entry) => (
                    <Cell key={entry.product} fill={colorByProduct.get(entry.product) ?? "var(--chart-1)"} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border">
        {isLoading ? (
          <LoadingSkeleton />
        ) : loadError ? (
          <div className="border-destructive/30 bg-destructive/10 text-destructive m-6 rounded-lg border p-4">
            <p className="font-semibold">Error loading products</p>
            <p className="mt-1 text-sm">Unable to load data. Please try refreshing the page.</p>
          </div>
        ) : (
          <Table>
            <TableHeader className="bg-muted z-10">
              <TableRow>
                <SortableHeader col="product" label="Product" sortColumn={sortColumn} sortDirection={sortDirection} onSort={handleSort} />
                <SortableHeader col="count" label="Tickets" sortColumn={sortColumn} sortDirection={sortDirection} onSort={handleSort} />
                <TableHead>% of total Tickets</TableHead>
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
                      <TableCell className="font-mono text-sm tabular-nums">{formatPercentage(count, totalTickets)}</TableCell>
                    </TableRow>
                  ))}
                  {/* Distinct product-tagged tickets in scope — can be below the column sum since a ticket may carry several product tags. */}
                  <TableRow className="bg-muted/50 border-t font-semibold">
                    <TableCell>Total Product Tickets</TableCell>
                    <TableCell className="font-mono text-sm tabular-nums">{taggedTicketCount}</TableCell>
                    <TableCell className="font-mono text-sm tabular-nums">{formatPercentage(taggedTicketCount, totalTickets)}</TableCell>
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
