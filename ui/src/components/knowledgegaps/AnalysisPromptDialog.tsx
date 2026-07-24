"use client";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useAnalysisPrompt } from "@/lib/hooks";

interface AnalysisPromptDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export default function AnalysisPromptDialog({ open, onOpenChange }: AnalysisPromptDialogProps) {
  const { data, isFetching, error } = useAnalysisPrompt(open);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="analysis-prompt-dialog" className="max-h-[90vh] sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Analysis Prompt</DialogTitle>
          <DialogDescription>The prompt template used by the backend when running analysis.</DialogDescription>
        </DialogHeader>
        {isFetching && <p className="text-muted-foreground text-sm">Loading prompt...</p>}
        {error && !isFetching && <p className="text-destructive text-sm">Failed to load analysis prompt. Please try again.</p>}
        {data && !isFetching && !error && (
          <pre className="bg-muted max-h-[60vh] overflow-auto rounded-md p-4 font-mono text-xs whitespace-pre-wrap">{data.prompt}</pre>
        )}
      </DialogContent>
    </Dialog>
  );
}
