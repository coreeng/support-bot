"use client";

import { useExternalStoreRuntime, type AppendMessage, type ThreadMessageLike } from "@assistant-ui/react";
import { useMemo, useState } from "react";
import { createItemReuser, type SessionItem } from "./session-items";

const COMPLETE = { type: "complete", reason: "stop" } as const;

export function convertItem(item: SessionItem): ThreadMessageLike {
  const common = {
    id: String(item.seq),
    createdAt: new Date(item.createdAt),
    metadata: { custom: { item } },
  };
  switch (item.kind) {
    case "engineer-message":
      return { ...common, role: "user", content: [{ type: "text", text: item.text }] };
    case "router-message":
      return { ...common, role: "assistant", status: COMPLETE, content: [{ type: "text", text: item.text }] };
    case "slack-message":
      return { ...common, role: "assistant", status: COMPLETE, content: [{ type: "data-slack-message", data: item }] };
    case "proposal":
      return { ...common, role: "assistant", status: COMPLETE, content: [{ type: "data-proposal", data: item }] };
    case "run-update":
      return { ...common, role: "assistant", status: COMPLETE, content: [{ type: "data-run-update", data: item }] };
    case "router-failed":
      return { ...common, role: "assistant", status: COMPLETE, content: [{ type: "data-router-failed", data: item }] };
  }
}

function textOf(message: AppendMessage): string {
  return message.content
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("\n")
    .trim();
}

/**
 * The server owns the log, so the runtime only renders it. `isRunning` stays false on purpose:
 * while true, assistant-ui disables sending unless a queue adapter is wired, and engineers must be
 * able to write while the router works. The busy state is shown outside the runtime instead.
 */
export function useSessionRuntime(items: readonly SessionItem[], onSend: (text: string) => Promise<void>) {
  const [reuse] = useState(createItemReuser);
  const messages = useMemo(() => reuse(items), [reuse, items]);

  return useExternalStoreRuntime<SessionItem>({
    messages,
    isRunning: false,
    convertMessage: convertItem,
    onNew: async (message) => {
      const text = textOf(message);
      if (text) await onSend(text);
    },
  });
}
