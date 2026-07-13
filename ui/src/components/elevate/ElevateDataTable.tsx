"use client";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ReactNode, useMemo, useState } from "react";

export interface ElevateTableColumn<T> {
  key: string;
  header: string;
  className?: string;
  render: (item: T) => ReactNode;
}

interface ElevateDataTableProps<T> {
  title: string;
  description: string;
  caption: string;
  items: T[];
  columns: ElevateTableColumn<T>[];
  rowKey: (item: T) => string;
  emptyTitle: string;
  emptyDescription: string;
  pageSize?: number;
}

export function ElevateDataTable<T>({
  title,
  description,
  caption,
  items,
  columns,
  rowKey,
  emptyTitle,
  emptyDescription,
  pageSize = 10,
}: ElevateDataTableProps<T>) {
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const currentPage = Math.min(page, totalPages);

  const visibleItems = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return items.slice(start, start + pageSize);
  }, [currentPage, items, pageSize]);

  return (
    <div className="bg-card rounded-xl border p-6">
      <div className="mb-4">
        <h2 className="text-foreground text-base font-semibold">{title}</h2>
        <p className="text-muted-foreground text-sm">{description}</p>
      </div>

      {items.length === 0 ? (
        <div className="text-muted-foreground p-16 text-center text-sm" role="status">
          <p className="text-foreground font-medium">{emptyTitle}</p>
          <p className="mt-1">{emptyDescription}</p>
        </div>
      ) : (
        <>
          <Table>
            <TableCaption className="sr-only">{caption}</TableCaption>
            <TableHeader className="bg-muted">
              <TableRow>
                {columns.map((column) => (
                  <TableHead key={column.key} className={column.className} scope="col">
                    {column.header}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {visibleItems.map((item) => (
                <TableRow key={rowKey(item)}>
                  {columns.map((column) => (
                    <TableCell key={column.key} className={column.className}>
                      {column.render(item)}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {totalPages > 1 ? (
            <div className="flex flex-wrap items-center justify-end gap-4 border-t px-6 py-3">
              <span
                className="text-muted-foreground text-sm"
                aria-label="Pagination status"
                role="status"
                aria-live="polite"
                aria-atomic="true"
              >
                Page <span className="text-foreground font-mono tabular-nums">{currentPage}</span> of{" "}
                <span className="text-foreground font-mono tabular-nums">{totalPages}</span>
              </span>
              <Button variant="outline" size="sm" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>
                Previous
              </Button>
              <Button variant="outline" size="sm" disabled={currentPage === totalPages} onClick={() => setPage(currentPage + 1)}>
                Next
              </Button>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}
