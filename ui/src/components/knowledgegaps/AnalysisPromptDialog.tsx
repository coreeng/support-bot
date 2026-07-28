"use client";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { isApiError, useAnalysisPrompt } from "@/lib/hooks";

interface AnalysisPromptDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function promptErrorMessage(error: unknown): string {
  if (isApiError(error, 403)) {
    return "You do not have permission to view the analysis prompt.";
  }
  if (isApiError(error) && error.reason === "ANALYSIS_PROMPT_LOAD_FAILED") {
    return "The analysis prompt could not be loaded on the server.";
  }
  return "Failed to load analysis prompt. Please try again.";
}

export default function AnalysisPromptDialog({ open, onOpenChange }: AnalysisPromptDialogProps) {
  const { data, isFetching, error } = useAnalysisPrompt(open);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="analysis-prompt-dialog" className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle className="text-xl">Analysis Prompt</DialogTitle>
          <DialogDescription>The prompt template used by the backend when running analysis.</DialogDescription>
        </DialogHeader>
        {isFetching && <p className="text-muted-foreground text-sm">Loading prompt...</p>}
        {error && !isFetching && <p className="text-destructive text-sm">{promptErrorMessage(error)}</p>}
        {data && !isFetching && !error && (
          <pre
            tabIndex={0}
            aria-label="Analysis prompt"
            className="bg-muted max-h-[60vh] overflow-auto rounded-md p-4 font-mono text-xs whitespace-pre-wrap"
          >
            {data.prompt}
          </pre>
        )}
      </DialogContent>
    </Dialog>
  );
}
