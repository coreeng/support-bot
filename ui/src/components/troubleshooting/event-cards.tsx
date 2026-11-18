"use client";

import SlackMessageRenderer from "@/components/ui/SlackMessageRenderer";
import type { RouterUsage } from "@/lib/types/troubleshooting";
import { AlertCircle, Slack } from "lucide-react";
import { actorLabel, type RouterFailedItem, type SlackMessageItem } from "./session-items";

const numberFormat = new Intl.NumberFormat("en-GB");

export function UsageLine({ usage }: { usage: RouterUsage }) {
  return (
    <p className="text-muted-foreground font-mono text-xs tabular-nums">
      {usage.model} · {numberFormat.format(usage.inputTokens)} in · {numberFormat.format(usage.outputTokens)} out ·{" "}
      {(usage.durationMs / 1000).toFixed(1)} s
    </p>
  );
}

export function SlackMessageCard({ item }: { item: SlackMessageItem }) {
  return (
    <div className="bg-card text-card-foreground space-y-2 rounded-xl border p-4">
      <div className="text-muted-foreground flex items-center gap-2 text-xs">
        <Slack className="h-3.5 w-3.5" />
        <span className="text-foreground font-medium">{item.available ? actorLabel(item.actor) : "Slack thread"}</span>
        <span>in the query thread</span>
      </div>
      {item.available ? (
        <SlackMessageRenderer text={item.text} className="text-sm" />
      ) : (
        <p className="text-muted-foreground text-sm">{item.text}</p>
      )}
    </div>
  );
}

export function RouterFailedNote({ item }: { item: RouterFailedItem }) {
  return (
    <div className="bg-destructive/10 text-destructive flex items-start gap-2 rounded-lg px-4 py-3 text-sm">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <span>The router could not answer: {item.error}. Nothing was done; send a message to try again.</span>
    </div>
  );
}
