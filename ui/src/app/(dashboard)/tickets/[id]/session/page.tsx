"use client";

import { AccessDenied } from "@/components/AccessDenied";
import SessionView from "@/components/troubleshooting/session-view";
import { useAuth } from "@/hooks/useAuth";
import { useTroubleshootingEnabled } from "@/lib/hooks";
import { useParams } from "next/navigation";

export default function TroubleshootingSessionPage() {
  const { id } = useParams<{ id: string }>();
  const { isSupportEngineer, isLoading } = useAuth();
  const { data: enabled, isLoading: isEnabledLoading } = useTroubleshootingEnabled();

  if (isLoading || isEnabledLoading) return null;
  if (!isSupportEngineer) return <AccessDenied />;
  if (!enabled) return <div className="text-muted-foreground p-16 text-center text-sm">Troubleshooting sessions are not enabled.</div>;
  if (!/^\d+$/.test(id)) return <div className="text-muted-foreground p-16 text-center text-sm">Unknown ticket.</div>;

  return <SessionView ticketId={Number(id)} />;
}
