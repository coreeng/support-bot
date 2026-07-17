"use client";

import { ElevatePagination } from "@/components/elevate/ElevatePagination";
import { Button } from "@/components/ui/button";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { isApiError, useElevateProducts } from "@/lib/hooks";
import type { ElevateProduct } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Check, ChevronsUpDown, LoaderCircle } from "lucide-react";
import { useEffect, useState } from "react";

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function ElevateProductPicker({
  snapshotVersion,
  selectedProduct,
  onSelect,
  onSnapshotChanged,
}: {
  snapshotVersion: string;
  selectedProduct: ElevateProduct;
  onSelect: (productId: string) => void;
  onSnapshotChanged: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const deferredQuery = useDebouncedValue(query);
  const [page, setPage] = useState(0);
  const products = useElevateProducts({ snapshotVersion, page, query: deferredQuery, sort: "name", direction: "asc" }, open);
  const waitingForSearch = query !== deferredQuery;
  const showingPlaceholder = waitingForSearch || products.isPlaceholderData;
  const busy = waitingForSearch || products.isFetching;

  function closePicker() {
    setOpen(false);
    setQuery("");
    setPage(0);
  }

  useEffect(() => {
    if (isApiError(products.error, 409)) onSnapshotChanged();
  }, [onSnapshotChanged, products.error]);

  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen) => {
        if (nextOpen) setOpen(true);
        else closePicker();
      }}
    >
      <PopoverTrigger asChild>
        <Button
          id="elevate-product-picker"
          variant="outline"
          size="default"
          role="combobox"
          aria-expanded={open}
          aria-label="Product"
          className="h-auto min-h-9 w-full justify-between px-3 py-2 text-left font-normal sm:max-w-xl"
        >
          <span className="min-w-0">
            <span className="text-foreground block truncate font-medium">{selectedProduct.name}</span>
            <span className="text-muted-foreground block truncate text-xs">
              <span className="font-mono">{selectedProduct.slug}</span> ·{" "}
              <span className="font-mono tabular-nums">{countLabel(selectedProduct.journeyCount, "journey")}</span> ·{" "}
              <span className="font-mono tabular-nums">{countLabel(selectedProduct.userCount, "product user")}</span>
            </span>
          </span>
          <ChevronsUpDown className="text-muted-foreground ml-2 size-4 shrink-0" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[var(--radix-popover-trigger-width)] p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput
            placeholder="Search products…"
            aria-label="Search products"
            value={query}
            onValueChange={(value) => {
              setQuery(value);
              setPage(0);
            }}
          />
          <CommandList className="max-h-80" aria-busy={busy || undefined}>
            {products.isLoading || showingPlaceholder ? (
              <div className="text-muted-foreground flex items-center justify-center gap-2 p-6 text-sm" role="status">
                <LoaderCircle className="size-4 animate-spin" /> {query ? "Searching products" : "Loading products"}
              </div>
            ) : null}
            {!products.isLoading && !showingPlaceholder && products.error && !products.data ? (
              <p className="text-destructive p-6 text-center text-sm" role="alert">
                Unable to load products.
              </p>
            ) : null}
            {!products.isLoading && !showingPlaceholder && products.data?.content.length === 0 ? (
              <CommandEmpty>No products found.</CommandEmpty>
            ) : null}
            {!showingPlaceholder && products.data?.content.length ? (
              <CommandGroup>
                {products.data.content.map((product) => {
                  const selectedItem = product.id === selectedProduct.id;
                  return (
                    <CommandItem
                      key={product.id}
                      value={product.id}
                      className="cursor-pointer items-start py-2"
                      onSelect={() => {
                        onSelect(product.id);
                        closePicker();
                      }}
                    >
                      <Check className={cn("mt-0.5 size-4", selectedItem ? "opacity-100" : "opacity-0")} />
                      <span className="min-w-0">
                        <span className="text-foreground block truncate font-medium">{product.name}</span>
                        <span className="text-muted-foreground block truncate text-xs">
                          <span className="font-mono">{product.slug}</span> ·{" "}
                          <span className="font-mono tabular-nums">{countLabel(product.journeyCount, "journey")}</span> ·{" "}
                          <span className="font-mono tabular-nums">{countLabel(product.userCount, "product user")}</span>
                        </span>
                      </span>
                    </CommandItem>
                  );
                })}
              </CommandGroup>
            ) : null}
          </CommandList>
        </Command>
        {products.data ? (
          <ElevatePagination page={products.data.page} totalPages={products.data.totalPages} busy={busy} onPageChange={setPage} />
        ) : null}
      </PopoverContent>
    </Popover>
  );
}
