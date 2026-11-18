"use client";

import SlackMessageRenderer from "@/components/ui/SlackMessageRenderer";
import { Slack } from "lucide-react";
import { actorLabel, type SlackMessageItem } from "./session-items";

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
