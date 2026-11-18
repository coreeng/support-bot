"use client";

import { troubleshootingSessionKey } from "@/lib/hooks";
import type { TroubleshootingEvent, TroubleshootingSession } from "@/lib/types/troubleshooting";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { mergeEvents } from "./session-items";

const REOPEN_DELAY_MS = 3000;

export type StreamState = "connecting" | "open" | "reconnecting";

/**
 * Follows a session's event stream and adds each event to the cached session. The browser retries a
 * dropped stream by itself (sending Last-Event-ID); when it gives up, the session is refetched, which
 * also surfaces a 401, and the stream is opened again after the last event held.
 */
export function useSessionStream(ticketId: number, sessionId: number | undefined): StreamState {
  const queryClient = useQueryClient();
  const [state, setState] = useState<StreamState>("connecting");
  const reopenTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (sessionId === undefined) return;
    const key = troubleshootingSessionKey(ticketId);
    let source: EventSource | null = null;
    let disposed = false;

    const open = () => {
      const after = queryClient.getQueryData<TroubleshootingSession | null>(key)?.lastSeq ?? 0;
      source = new EventSource(`/api/troubleshooting/tickets/${ticketId}/session/stream?after=${after}`);
      source.onopen = () => setState("open");
      source.onmessage = (message) => {
        const event = JSON.parse(message.data) as TroubleshootingEvent;
        queryClient.setQueryData<TroubleshootingSession | null>(key, (current) => (current ? mergeEvents(current, [event]) : current));
      };
      source.onerror = () => {
        setState("reconnecting");
        if (source?.readyState !== EventSource.CLOSED) return;
        source = null;
        void queryClient.invalidateQueries({ queryKey: key });
        reopenTimer.current = setTimeout(() => {
          if (!disposed) open();
        }, REOPEN_DELAY_MS);
      };
    };

    open();
    return () => {
      disposed = true;
      if (reopenTimer.current) clearTimeout(reopenTimer.current);
      source?.close();
    };
  }, [ticketId, sessionId, queryClient]);

  return state;
}
