"use client";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { isApiError, useAnalysisPrompt, useSummaryPrompt } from "@/lib/hooks";
import { useState } from "react";

/** The two backend prompts the dialog can show; classification is the default. */
const PROMPT_KINDS = {
  classification: {
    label: "Ticket classification",
    description: "Used when classifying each support thread into driver, category and feature.",
  },
  summary: {
    label: "Summary generation",
    description: "Used when generating the summary prose at the top of this page.",
  },
} as const;

type PromptKind = keyof typeof PROMPT_KINDS;

interface PromptDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function promptErrorMessage(kind: PromptKind, error: unknown): string {
  const name = PROMPT_KINDS[kind].label.toLowerCase();
  if (isApiError(error, 403)) {
    return `You do not have permission to view the ${name} prompt.`;
  }
  if (isApiError(error) && error.reason === "ANALYSIS_PROMPT_LOAD_FAILED") {
    return `The ${name} prompt could not be loaded on the server.`;
  }
  return `Failed to load the ${name} prompt. Please try again.`;
}

/**
 * The summary page's View Prompt dialog: a dropdown picks which of the two backend prompts to
 * show — the ticket classification prompt (default) or the summary generation prompt.
 */
export default function PromptDialog({ open, onOpenChange }: PromptDialogProps) {
  const [kind, setKind] = useState<PromptKind>("classification");
  const classification = useAnalysisPrompt(open && kind === "classification");
  const summary = useSummaryPrompt(open && kind === "summary");
  const { data, isFetching, error } = kind === "classification" ? classification : summary;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="prompt-dialog" className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle className="text-xl">Prompts</DialogTitle>
          <DialogDescription>{PROMPT_KINDS[kind].description}</DialogDescription>
        </DialogHeader>
        <Select value={kind} onValueChange={(value) => setKind(value as PromptKind)}>
          <SelectTrigger className="w-[220px]" aria-label="Prompt" data-testid="prompt-dialog-kind">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(PROMPT_KINDS).map(([value, { label }]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {isFetching && <p className="text-muted-foreground text-sm">Loading prompt...</p>}
        {error && !isFetching && <p className="text-destructive text-sm">{promptErrorMessage(kind, error)}</p>}
        {data && !isFetching && !error && (
          <pre
            tabIndex={0}
            aria-label={`${PROMPT_KINDS[kind].label} prompt`}
            className="bg-muted max-h-[60vh] overflow-auto rounded-md p-4 font-mono text-xs whitespace-pre-wrap"
          >
            {data.prompt}
          </pre>
        )}
      </DialogContent>
    </Dialog>
  );
}
