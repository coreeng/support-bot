import { Button } from "@/components/ui/button";

export function ElevatePagination({
  page,
  totalPages,
  busy = false,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  busy?: boolean;
  onPageChange: (page: number) => void;
}) {
  const displayTotal = Math.max(totalPages, 1);
  const displayPage = Math.min(page + 1, displayTotal);

  return (
    <div
      className="flex flex-wrap items-center justify-end gap-x-2 gap-y-2 border-t px-3 py-3 sm:gap-x-4 sm:px-6"
      aria-busy={busy || undefined}
    >
      <span className="text-muted-foreground text-sm" aria-live="polite">
        {busy ? (
          "Updating…"
        ) : (
          <>
            Page <span className="font-mono tabular-nums">{displayPage}</span> of{" "}
            <span className="font-mono tabular-nums">{displayTotal}</span>
          </>
        )}
      </span>
      <Button variant="outline" size="sm" disabled={busy || page === 0} onClick={() => onPageChange(page - 1)}>
        Previous
      </Button>
      <Button variant="outline" size="sm" disabled={busy || page >= displayTotal - 1} onClick={() => onPageChange(page + 1)}>
        Next
      </Button>
    </div>
  );
}
