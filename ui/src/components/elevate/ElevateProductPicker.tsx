"use client";

import type { ProductRelationship } from "@/components/elevate/elevate-relationships";
import { Button } from "@/components/ui/button";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { Check, ChevronsUpDown } from "lucide-react";
import { useState } from "react";

function countLabel(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function ElevateProductPicker({
  relationships,
  selectedProductId,
  onSelect,
}: {
  relationships: ProductRelationship[];
  selectedProductId: string;
  onSelect: (productId: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const selected = relationships.find(({ product }) => product.id === selectedProductId) ?? relationships[0];

  if (!selected) return null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id="elevate-product-picker"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-label="Product"
          className="h-auto min-h-9 w-full justify-between px-3 py-2 text-left font-normal sm:max-w-xl"
        >
          <span className="min-w-0">
            <span className="text-foreground block truncate font-medium">{selected.product.name}</span>
            <span className="text-muted-foreground block truncate text-xs">
              {selected.product.slug} · {countLabel(selected.journeys.length, "journey")} ·{" "}
              {countLabel(selected.users.length, "product user")}
            </span>
          </span>
          <ChevronsUpDown className="text-muted-foreground ml-2 size-4 shrink-0" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[var(--radix-popover-trigger-width)] p-0" align="start">
        <Command>
          <CommandInput placeholder="Search products…" />
          <CommandList className="max-h-80">
            <CommandEmpty>No products found.</CommandEmpty>
            <CommandGroup>
              {relationships.map((relationship) => {
                const selectedItem = relationship.product.id === selected.product.id;
                return (
                  <CommandItem
                    key={relationship.product.id}
                    value={`${relationship.product.name} ${relationship.product.slug} ${relationship.product.customer ?? ""}`}
                    className="items-start py-2"
                    onSelect={() => {
                      onSelect(relationship.product.id);
                      setOpen(false);
                    }}
                  >
                    <Check className={cn("mt-0.5 size-4", selectedItem ? "opacity-100" : "opacity-0")} />
                    <span className="min-w-0">
                      <span className="text-foreground block truncate font-medium">{relationship.product.name}</span>
                      <span className="text-muted-foreground block truncate text-xs">
                        {relationship.product.slug} · {countLabel(relationship.journeys.length, "journey")} ·{" "}
                        {countLabel(relationship.users.length, "product user")}
                      </span>
                    </span>
                  </CommandItem>
                );
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
