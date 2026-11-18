"use client";

import { useExternalStoreRuntime, type ThreadMessageLike } from "@assistant-ui/react";
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
    case "slack-message":
      return { ...common, role: "assistant", status: COMPLETE, content: [{ type: "data-slack-message", data: item }] };
  }
}

/** The server owns the log, so the runtime only renders it. */
export function useSessionRuntime(items: readonly SessionItem[]) {
  const [reuse] = useState(createItemReuser);
  const messages = useMemo(() => reuse(items), [reuse, items]);

  return useExternalStoreRuntime<SessionItem>({
    messages,
    convertMessage: convertItem,
    // No composer is rendered, so nothing can be sent.
    onNew: async () => {},
  });
}
