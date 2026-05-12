"use client";

import { Check, ChevronDown, X } from "lucide-react";
import * as React from "react";

import { Badge } from "@/components/ui/badge";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

export interface MultiSelectOption {
  label: string;
  value: string;
}

interface MultiSelectProps {
  options: MultiSelectOption[];
  selected: string[];
  onChange: (next: string[]) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  className?: string;
  triggerId?: string;
  error?: boolean;
}

export function MultiSelect({
  options,
  selected,
  onChange,
  placeholder = "Select...",
  searchPlaceholder = "Search...",
  className,
  triggerId,
  error,
}: MultiSelectProps) {
  const [open, setOpen] = React.useState(false);

  const toggle = (value: string) => {
    onChange(selected.includes(value) ? selected.filter((v) => v !== value) : [...selected, value]);
  };

  const remove = (value: string, e: React.MouseEvent | React.KeyboardEvent) => {
    e.stopPropagation();
    e.preventDefault();
    onChange(selected.filter((v) => v !== value));
  };

  return (
    <Popover open={open} onOpenChange={setOpen} modal>
      <PopoverTrigger asChild>
        <button
          type="button"
          id={triggerId}
          className={cn(
            "dark:bg-input/30 dark:hover:bg-input/50 flex min-h-9 w-full items-center justify-between gap-2 rounded-md border bg-transparent px-3 py-1.5 text-sm shadow-xs transition-colors",
            "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
            error ? "border-destructive" : "border-input",
            className
          )}
        >
          <div className="flex flex-1 flex-wrap items-center gap-1">
            {selected.length === 0 ? (
              <span className="text-muted-foreground">{placeholder}</span>
            ) : (
              selected.map((value) => {
                const opt = options.find((o) => o.value === value);
                const label = opt?.label ?? value;
                return (
                  <Badge key={value} variant="outline" className="gap-1 font-normal">
                    {label}
                    <span
                      role="button"
                      tabIndex={0}
                      aria-label={`Remove ${label}`}
                      onClick={(e) => remove(value, e)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          remove(value, e);
                        }
                      }}
                      className="hover:bg-muted ml-0.5 rounded-full transition-colors"
                    >
                      <X className="h-3 w-3" />
                    </span>
                  </Badge>
                );
              })
            )}
          </div>
          <ChevronDown className="h-4 w-4 shrink-0 opacity-50" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-[var(--radix-popover-trigger-width)] p-0" align="start">
        <Command>
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            <CommandEmpty>No results.</CommandEmpty>
            <CommandGroup>
              {options.map((option) => {
                const isSelected = selected.includes(option.value);
                return (
                  <CommandItem key={option.value} onSelect={() => toggle(option.value)}>
                    <div
                      className={cn(
                        "flex size-4 items-center justify-center rounded-[4px] border",
                        isSelected ? "border-primary bg-primary text-primary-foreground" : "border-input [&_svg]:invisible"
                      )}
                    >
                      <Check className="text-primary-foreground size-3.5" />
                    </div>
                    <span>{option.label}</span>
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
