import type { ElevateTableColumn } from "@/components/elevate/ElevateDataTable";
import { formatTimestamp } from "@/components/elevate/ElevateStatusCards";
import type { ElevateJourney, ElevateProduct, ElevateUser } from "@/lib/types";

export const productColumns: ElevateTableColumn<ElevateProduct>[] = [
  {
    key: "product",
    header: "Product",
    render: (product) => (
      <div>
        <p className="text-foreground font-medium">{product.name}</p>
        <p className="text-muted-foreground font-mono text-xs">{product.slug}</p>
      </div>
    ),
  },
  { key: "customer", header: "Customer", render: (product) => product.customer || <span className="text-muted-foreground">—</span> },
  {
    key: "created",
    header: "Created",
    render: (product) => <span className="font-mono text-xs tabular-nums">{formatTimestamp(product.createdAt)}</span>,
  },
  {
    key: "updated",
    header: "Updated",
    render: (product) => <span className="font-mono text-xs tabular-nums">{formatTimestamp(product.lastUpdatedAt)}</span>,
  },
];

export const journeyColumns: ElevateTableColumn<ElevateJourney>[] = [
  {
    key: "journey",
    header: "Journey",
    className: "min-w-56 whitespace-normal",
    render: (journey) => (
      <div>
        <p className="text-foreground font-medium">{journey.name}</p>
        <p className="text-muted-foreground font-mono text-xs">{journey.slug}</p>
        {journey.userDescription ? <p className="text-muted-foreground mt-1 text-sm">{journey.userDescription}</p> : null}
      </div>
    ),
  },
  {
    key: "product",
    header: "Product",
    render: (journey) => <span className="font-mono text-xs">{journey.productSlug}</span>,
  },
  {
    key: "problems",
    header: "Primary problems",
    className: "min-w-64 whitespace-normal",
    render: (journey) => journey.primaryProblems || <span className="text-muted-foreground">—</span>,
  },
  {
    key: "users",
    header: "Users",
    render: (journey) => <span className="font-mono tabular-nums">{journey.userIds.length}</span>,
  },
  {
    key: "updated",
    header: "Updated",
    render: (journey) => <span className="font-mono text-xs tabular-nums">{formatTimestamp(journey.lastUpdatedAt)}</span>,
  },
];

export const userColumns: ElevateTableColumn<ElevateUser>[] = [
  {
    key: "user",
    header: "User",
    render: (user) => <span className="text-foreground font-medium">{user.name}</span>,
  },
  {
    key: "product",
    header: "Product ID",
    render: (user) => <span className="font-mono text-xs">{user.productId}</span>,
  },
  {
    key: "description",
    header: "Description",
    className: "min-w-72 whitespace-normal",
    render: (user) => user.description || <span className="text-muted-foreground">—</span>,
  },
  {
    key: "updated",
    header: "Updated",
    render: (user) => <span className="font-mono text-xs tabular-nums">{formatTimestamp(user.lastUpdatedAt)}</span>,
  },
];
