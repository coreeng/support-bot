"use client";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/hooks/useAuth";
import {
  sendTroubleshootingMessage,
  startTroubleshootingSession,
  troubleshootingSessionKey,
  useTicket,
  useTroubleshootingSession,
} from "@/lib/hooks";
import type { TroubleshootingSession } from "@/lib/types/troubleshooting";
import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useAuiState,
  type DataMessagePartProps,
  type TextMessagePartProps,
} from "@assistant-ui/react";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Bot, Loader2, SendHorizontal, Slack, User } from "lucide-react";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { toast } from "sonner";
import { RouterFailedNote, SlackMessageCard, UsageLine } from "./event-cards";
import { ModeBadge } from "./mode-badge";
import { actorLabel, buildSessionView, type RouterFailedItem, type SessionItem, type SlackMessageItem } from "./session-items";
import { useSessionRuntime } from "./session-runtime";
import { useSessionStream, type StreamState } from "./use-session-stream";

function RouterText({ text }: TextMessagePartProps) {
  return <p className="text-foreground text-sm whitespace-pre-wrap">{text}</p>;
}

const PART_COMPONENTS = {
  Text: RouterText,
  data: {
    by_name: {
      "slack-message": ({ data }: DataMessagePartProps<SlackMessageItem>) => <SlackMessageCard item={data} />,
      "router-failed": ({ data }: DataMessagePartProps<RouterFailedItem>) => <RouterFailedNote item={data} />,
    },
  },
};

function AuthorIcon({ item }: { item: SessionItem }) {
  switch (item.kind) {
    case "engineer-message":
      return <User className="h-3.5 w-3.5" />;
    case "slack-message":
      return <Slack className="h-3.5 w-3.5" />;
    default:
      return <Bot className="h-3.5 w-3.5" />;
  }
}

function SessionMessage({ currentUser }: { currentUser: string | undefined }) {
  const item = useAuiState((s) => (s.message.metadata.custom as { item: SessionItem }).item);
  const isMe = item.kind === "engineer-message" && item.actor === currentUser;
  const time = new Date(item.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });

  return (
    <MessagePrimitive.Root className="space-y-1.5" data-seq={item.seq} data-kind={item.kind}>
      <div className="text-muted-foreground flex items-center gap-2 text-xs">
        <AuthorIcon item={item} />
        <span className="text-foreground font-medium" title={item.actor}>
          {isMe ? "You" : actorLabel(item.actor)}
        </span>
        <span className="font-mono tabular-nums">#{item.seq}</span>
        <span className="font-mono tabular-nums">{time}</span>
      </div>
      {item.kind === "engineer-message" ? (
        <div className={isMe ? "bg-primary/10 rounded-xl px-4 py-3" : "bg-info/10 rounded-xl px-4 py-3"}>
          <MessagePrimitive.Parts components={PART_COMPONENTS} />
        </div>
      ) : item.kind === "router-message" ? (
        <div className="bg-card space-y-2 rounded-xl border px-4 py-3">
          <MessagePrimitive.Parts components={PART_COMPONENTS} />
          <UsageLine usage={item.usage} />
        </div>
      ) : (
        <MessagePrimitive.Parts components={PART_COMPONENTS} />
      )}
    </MessagePrimitive.Root>
  );
}

function StreamNote({ state }: { state: StreamState }) {
  if (state === "open") return null;
  return <span className="text-muted-foreground text-xs">{state === "connecting" ? "Connecting…" : "Reconnecting…"}</span>;
}

function SessionThread({ session, ticketId }: { session: TroubleshootingSession; ticketId: number }) {
  const { user } = useAuth();
  const streamState = useSessionStream(ticketId, session.id);
  const view = useMemo(() => buildSessionView(session.events), [session.events]);

  const onSend = useCallback(
    async (text: string) => {
      try {
        await sendTroubleshootingMessage(ticketId, text);
      } catch {
        toast.error("The message was not sent.");
        throw new Error("send failed");
      }
    },
    [ticketId]
  );
  const runtime = useSessionRuntime(view.items, onSend);

  const renderMessage = useCallback(() => <SessionMessage currentUser={user?.email} />, [user?.email]);

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <ThreadPrimitive.Root className="bg-card flex h-[calc(100vh-14rem)] min-h-[480px] flex-col rounded-xl border">
        <ThreadPrimitive.Viewport className="flex-1 space-y-4 overflow-y-auto p-6">
          <ThreadPrimitive.Messages>{renderMessage}</ThreadPrimitive.Messages>
          {view.routerBusy && (
            <div className="text-muted-foreground flex items-center gap-2 text-sm" data-testid="router-busy">
              <Loader2 className="h-4 w-4 animate-spin" />
              Router is thinking…
            </div>
          )}
        </ThreadPrimitive.Viewport>
        <ComposerPrimitive.Root className="flex items-end gap-2 border-t p-4">
          <ComposerPrimitive.Input
            placeholder={view.routerBusy ? "The router is busy; your message will be answered next…" : "Ask the router or add context…"}
            className="placeholder:text-muted-foreground dark:bg-input/30 border-input focus-visible:border-ring focus-visible:ring-ring/50 max-h-40 min-h-9 flex-1 resize-none rounded-md border bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-[3px]"
          />
          <ComposerPrimitive.Send asChild>
            <Button size="default" variant="default">
              <SendHorizontal className="h-4 w-4" />
              Send
            </Button>
          </ComposerPrimitive.Send>
        </ComposerPrimitive.Root>
        <div className="flex justify-end px-4 pb-2">
          <StreamNote state={streamState} />
        </div>
      </ThreadPrimitive.Root>
    </AssistantRuntimeProvider>
  );
}

function StartSession({ ticketId }: { ticketId: number }) {
  const queryClient = useQueryClient();
  const [starting, setStarting] = useState(false);

  const start = async () => {
    setStarting(true);
    try {
      const session = await startTroubleshootingSession(ticketId);
      queryClient.setQueryData(troubleshootingSessionKey(ticketId), session);
    } catch {
      toast.error("The session could not be started.");
    } finally {
      setStarting(false);
    }
  };

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground mb-4 text-base font-semibold">No troubleshooting session yet</h2>
      <p className="text-muted-foreground mb-4 text-sm">
        Starting a session reads the query&apos;s Slack thread and asks the router for a first answer. Nothing is posted to Slack.
      </p>
      <Button onClick={start} disabled={starting}>
        {starting && <Loader2 className="h-4 w-4 animate-spin" />}
        Start troubleshooting
      </Button>
    </div>
  );
}

export default function SessionView({ ticketId }: { ticketId: number }) {
  const { data: session, isLoading, error } = useTroubleshootingSession(ticketId);
  const { data: ticket } = useTicket(String(ticketId));

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Troubleshooting ID-{ticketId}</h1>
          <p className="text-muted-foreground text-sm">
            {ticket
              ? `Ticket ${ticket.status}${ticket.team?.label || ticket.team?.name ? ` · ${ticket.team?.label ?? ticket.team?.name}` : ""}`
              : "Ticket details unavailable"}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {session && <ModeBadge mode={session.mode} />}
          <Button asChild variant="outline" size="sm">
            <Link href="/">
              <ArrowLeft className="h-4 w-4" />
              Tickets
            </Link>
          </Button>
        </div>
      </div>

      {isLoading && <Skeleton className="h-96 w-full rounded-xl" />}
      {error && <div className="text-muted-foreground p-16 text-center text-sm">The session could not be loaded.</div>}
      {!isLoading && !error && (session ? <SessionThread session={session} ticketId={ticketId} /> : <StartSession ticketId={ticketId} />)}
    </div>
  );
}
