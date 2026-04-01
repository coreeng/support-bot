"use client";

import { useState, useRef, useMemo } from "react";
import * as Popover from "@radix-ui/react-popover";
import { Search, ChevronDown, X } from "lucide-react";

interface TeamComboboxProps {
  suggestedTeams: string[];
  otherTeams: string[];
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
}

export default function TeamCombobox({
  suggestedTeams,
  otherTeams,
  value,
  onChange,
  error,
  disabled = false,
}: TeamComboboxProps) {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const lowerFilter = filter.toLowerCase();

  const filteredSuggested = useMemo(
    () => suggestedTeams.filter((t) => t.toLowerCase().includes(lowerFilter)),
    [suggestedTeams, lowerFilter]
  );

  const filteredOther = useMemo(
    () => otherTeams.filter((t) => t.toLowerCase().includes(lowerFilter)),
    [otherTeams, lowerFilter]
  );

  const hasResults = filteredSuggested.length > 0 || filteredOther.length > 0;

  const handleSelect = (team: string) => {
    onChange(team);
    setFilter("");
    setOpen(false);
  };

  const handleClear = () => {
    onChange("");
    setFilter("");
    inputRef.current?.focus();
  };

  return (
    <div className="space-y-1">
      <Popover.Root open={open} onOpenChange={setOpen}>
        <Popover.Trigger asChild disabled={disabled}>
          <button
            type="button"
            className={`w-full flex items-center justify-between p-2.5 border rounded-md bg-white text-left focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:shadow-md transition-all ${
              error ? "border-red-500" : "border-gray-300"
            } ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
          >
            <span className={value ? "text-gray-900" : "text-gray-400"}>
              {value || "Select team..."}
            </span>
            <span className="flex items-center gap-1">
              {value && !disabled && (
                <X
                  className="w-4 h-4 text-gray-400 hover:text-gray-600"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleClear();
                  }}
                />
              )}
              <ChevronDown className="w-4 h-4 text-gray-400" />
            </span>
          </button>
        </Popover.Trigger>

        <Popover.Portal>
          <Popover.Content
            className="z-50 w-[var(--radix-popover-trigger-width)] bg-white border border-gray-200 rounded-md shadow-lg mt-1"
            sideOffset={4}
            align="start"
            onOpenAutoFocus={(e) => {
              e.preventDefault();
              inputRef.current?.focus();
            }}
          >
            <div className="p-2 border-b border-gray-100">
              <div className="flex items-center gap-2 px-2 py-1.5 bg-gray-50 rounded-md">
                <Search className="w-4 h-4 text-gray-400 shrink-0" />
                <input
                  ref={inputRef}
                  type="text"
                  value={filter}
                  onChange={(e) => setFilter(e.target.value)}
                  placeholder="Filter teams..."
                  className="w-full bg-transparent text-sm outline-none placeholder:text-gray-400"
                />
              </div>
            </div>

            <div className="max-h-60 overflow-y-auto">
              {!hasResults && (
                <p className="p-3 text-sm text-gray-500 text-center">
                  No teams available
                </p>
              )}

              {filteredSuggested.length > 0 && (
                <div>
                  <p className="px-3 py-1.5 text-xs font-semibold text-gray-500 uppercase tracking-wider bg-gray-50">
                    Suggested teams
                  </p>
                  {filteredSuggested.map((team) => (
                    <button
                      key={team}
                      type="button"
                      onClick={() => handleSelect(team)}
                      className={`w-full text-left px-3 py-2 text-sm hover:bg-blue-50 transition-colors ${
                        value === team
                          ? "bg-blue-50 text-blue-700 font-medium"
                          : "text-gray-700"
                      }`}
                    >
                      {team}
                    </button>
                  ))}
                </div>
              )}

              {filteredOther.length > 0 && (
                <div>
                  <p className="px-3 py-1.5 text-xs font-semibold text-gray-500 uppercase tracking-wider bg-gray-50">
                    Others
                  </p>
                  {filteredOther.map((team) => (
                    <button
                      key={team}
                      type="button"
                      onClick={() => handleSelect(team)}
                      className={`w-full text-left px-3 py-2 text-sm hover:bg-blue-50 transition-colors ${
                        value === team
                          ? "bg-blue-50 text-blue-700 font-medium"
                          : "text-gray-700"
                      }`}
                    >
                      {team}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </Popover.Content>
        </Popover.Portal>
      </Popover.Root>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}
