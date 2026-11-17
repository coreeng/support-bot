"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import SlackMessageRenderer from "@/components/ui/SlackMessageRenderer";
import { Textarea } from "@/components/ui/textarea";
import type { RouterUsage } from "@/lib/types/troubleshooting";
import { AlertCircle, Check, Pencil, Search, Send, Slack, Terminal, X, XCircle } from "lucide-react";
import { createContext, useContext, useState } from "react";
import { actorLabel, type ProposalItem, type RouterFailedItem, type RunUpdateItem, type SlackMessageItem } from "./session-items";

export interface SessionActions {
  decide: (proposalSeq: number, decision: "APPROVED" | "REJECTED", text?: string) => Promise<void>;
}

export const SessionActionsContext = createContext<SessionActions | null>(null);

function useSessionActions(): SessionActions {
  const actions = useContext(SessionActionsContext);
  if (!actions) throw new Error("SessionActionsContext is missing");
  return actions;
}

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
        <span className="text-foreground font-medium">{item.available ? (item.userId ?? "unknown") : "Slack thread"}</span>
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

const ACTION_LABEL: Record<ProposalItem["action"], string> = {
  REPLY: "Reply to the author",
  INVESTIGATE: "Investigate",
  CLOSE: "Close the ticket",
};

const ACTION_ICON: Record<ProposalItem["action"], typeof Send> = {
  REPLY: Send,
  INVESTIGATE: Search,
  CLOSE: XCircle,
};

function ProposalStateBadge({ item }: { item: ProposalItem }) {
  switch (item.state) {
    case "PENDING":
      return <Badge variant="outline">Waiting for approval</Badge>;
    case "APPROVED":
      return <Badge variant="success">Approved by {actorLabel(item.decidedBy ?? "")}</Badge>;
    case "REJECTED":
      return <Badge variant="destructive">Rejected by {actorLabel(item.decidedBy ?? "")}</Badge>;
    case "SUPERSEDED":
      return <Badge variant="secondary">Replaced by a newer proposal</Badge>;
  }
}

export function ProposalCard({ item }: { item: ProposalItem }) {
  const { decide } = useSessionActions();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(item.text);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const Icon = ACTION_ICON[item.action];
  const pending = item.state === "PENDING";
  const shownText = item.state === "APPROVED" && item.decidedText ? item.decidedText : item.text;

  const submit = async (decision: "APPROVED" | "REJECTED", text?: string) => {
    setBusy(true);
    setError(null);
    try {
      await decide(item.seq, decision, text);
      setEditing(false);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The decision was not saved.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-card text-card-foreground space-y-3 rounded-xl border p-4">
      <div className="flex items-center justify-between gap-2">
        <div className="text-foreground flex items-center gap-2 text-sm font-semibold">
          <Icon className="h-4 w-4" />
          Proposal: {ACTION_LABEL[item.action]}
        </div>
        <ProposalStateBadge item={item} />
      </div>
      {editing ? (
        <Textarea value={draft} onChange={(event) => setDraft(event.target.value)} aria-label="Proposal text" />
      ) : (
        <p className="bg-muted text-foreground rounded-md p-3 text-sm whitespace-pre-wrap">{shownText}</p>
      )}
      <p className="text-muted-foreground text-sm">Why: {item.reason}</p>
      <UsageLine usage={item.usage} />
      {pending && (
        <div className="flex flex-wrap gap-2">
          {editing ? (
            <>
              <Button size="sm" variant="default" disabled={busy || draft.trim() === ""} onClick={() => submit("APPROVED", draft)}>
                <Check className="h-4 w-4" />
                Approve edited
              </Button>
              <Button size="sm" variant="outline" disabled={busy} onClick={() => setEditing(false)}>
                Cancel
              </Button>
            </>
          ) : (
            <>
              <Button size="sm" variant="default" disabled={busy} onClick={() => submit("APPROVED")}>
                <Check className="h-4 w-4" />
                Approve
              </Button>
              {item.action === "REPLY" && (
                <Button size="sm" variant="outline" disabled={busy} onClick={() => setEditing(true)}>
                  <Pencil className="h-4 w-4" />
                  Edit
                </Button>
              )}
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => submit("REJECTED")}>
                <X className="h-4 w-4" />
                Reject
              </Button>
            </>
          )}
        </div>
      )}
      {error && <p className="text-destructive text-sm">{error}</p>}
    </div>
  );
}

export function RunUpdateCard({ item }: { item: RunUpdateItem }) {
  const isOutput = item.runKind === "OUTPUT";
  return (
    <div className="bg-muted/50 space-y-1 rounded-lg border px-4 py-3">
      <div className="text-muted-foreground flex items-center gap-2 text-xs">
        <Terminal className="h-3.5 w-3.5" />
        <span className="font-medium">Run · {item.runKind.toLowerCase().replace("_", " ")}</span>
      </div>
      {isOutput ? (
        <pre className="bg-muted text-foreground overflow-x-auto rounded-md p-3 font-mono text-xs">{item.text}</pre>
      ) : (
        <p className="text-foreground text-sm whitespace-pre-wrap">{item.text}</p>
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
