"use client";

import { Badge } from "@/components/ui/badge";
import { useNow } from "@/hooks/useNow";
import type { ElevateStatus } from "@/lib/types";
import { AlertCircle, AlertTriangle, CheckCircle2, CircleDashed, Database, ExternalLink, PlugZap } from "lucide-react";
import { ReactNode, useEffect, useState } from "react";

const MAX_TIMEOUT_MILLISECONDS = 2_147_483_647;

function formatTimestamp(value: string | null): string {
  if (!value) return "Never";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Unknown";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function formatSchedule(value: string): string {
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?$/i.exec(value);
  if (!match) return value;
  const parts = [];
  if (match[1]) parts.push(`${match[1]}h`);
  if (match[2]) parts.push(`${match[2]}m`);
  return parts.join(" ") || value;
}

function durationMilliseconds(value: string): number | null {
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/i.exec(value);
  if (!match || !match.slice(1).some(Boolean)) return null;
  const duration = (Number(match[1] ?? 0) * 60 * 60 + Number(match[2] ?? 0) * 60 + Number(match[3] ?? 0)) * 1000;
  return Number.isFinite(duration) && duration > 0 ? duration : null;
}

function isSyncOverdue(lastSyncSuccessAt: string | null, syncInterval: string, now: number): boolean {
  if (!lastSyncSuccessAt) return false;
  const lastSuccess = Date.parse(lastSyncSuccessAt);
  const interval = durationMilliseconds(syncInterval);
  return Number.isFinite(lastSuccess) && interval !== null && now - lastSuccess > interval;
}

function useSyncOverdue(lastSyncSuccessAt: string | null, syncInterval: string): boolean {
  const now = useNow();
  const [timerRevision, setTimerRevision] = useState(0);

  useEffect(() => {
    if (!lastSyncSuccessAt) return;
    const lastSuccess = Date.parse(lastSyncSuccessAt);
    const interval = durationMilliseconds(syncInterval);
    if (!Number.isFinite(lastSuccess) || interval === null) return;

    const remaining = lastSuccess + interval - Date.now();
    if (remaining < 0) return;
    const timer = window.setTimeout(() => setTimerRevision((current) => current + 1), Math.min(remaining + 1, MAX_TIMEOUT_MILLISECONDS));
    return () => window.clearTimeout(timer);
  }, [lastSyncSuccessAt, syncInterval, timerRevision]);

  return isSyncOverdue(lastSyncSuccessAt, syncInterval, now);
}

function StatusRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr] sm:items-center">
      <dt className="text-muted-foreground text-sm">{label}</dt>
      <dd className="text-foreground min-w-0 text-sm">{children}</dd>
    </div>
  );
}

function StateBadge({
  configured,
  succeeded,
  successLabel,
  pendingLabel,
}: {
  configured: boolean;
  succeeded: boolean | null;
  successLabel: string;
  pendingLabel: string;
}) {
  if (!configured) return <Badge variant="outline">Not configured</Badge>;
  if (succeeded === true)
    return (
      <Badge variant="success">
        <CheckCircle2 /> {successLabel}
      </Badge>
    );
  if (succeeded === false)
    return (
      <Badge variant="destructive">
        <AlertCircle /> Failed
      </Badge>
    );
  return (
    <Badge variant="secondary">
      <CircleDashed /> {pendingLabel}
    </Badge>
  );
}

function FailureMessage({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="border-destructive/30 bg-destructive/10 text-destructive mt-4 rounded-md border p-3 text-sm" role="alert">
      {message}
    </div>
  );
}

export function ElevateStatusCards({ status }: { status: ElevateStatus }) {
  const syncOverdue = useSyncOverdue(status.lastSyncSuccessAt, status.syncInterval);

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="bg-card rounded-xl border p-6">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <PlugZap className="text-primary h-4 w-4" />
              <h2 className="text-foreground text-base font-semibold">Connection</h2>
            </div>
            <p className="text-muted-foreground text-sm">Authenticated status reports to Elevate.</p>
          </div>
          <StateBadge
            configured={status.configured}
            succeeded={status.lastPingSucceeded}
            successLabel="Connected"
            pendingLabel="Awaiting check"
          />
        </div>

        <dl className="divide-y">
          <StatusRow label="Elevate URL">
            {status.baseUrl ? (
              <a
                className="text-primary inline-flex max-w-full cursor-pointer items-center gap-1 hover:underline"
                href={status.baseUrl}
                target="_blank"
                rel="noreferrer"
              >
                <span className="truncate font-mono text-xs">{status.baseUrl}</span>
                <ExternalLink className="h-3.5 w-3.5 shrink-0" />
              </a>
            ) : (
              <span className="text-muted-foreground">Not set</span>
            )}
          </StatusRow>
          <StatusRow label="Last checked">
            <time className="font-mono tabular-nums" dateTime={status.lastPingAttemptAt ?? undefined}>
              {formatTimestamp(status.lastPingAttemptAt)}
            </time>
          </StatusRow>
          <StatusRow label="Last connected">
            <time className="font-mono tabular-nums" dateTime={status.lastPingSuccessAt ?? undefined}>
              {formatTimestamp(status.lastPingSuccessAt)}
            </time>
          </StatusRow>
          <StatusRow label="Check schedule">
            Every <span className="font-mono tabular-nums">{formatSchedule(status.statusInterval)}</span>
          </StatusRow>
        </dl>
        <FailureMessage message={status.lastPingError} />
      </div>

      <div className="bg-card rounded-xl border p-6">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <Database className="text-info h-4 w-4" />
              <h2 className="text-foreground text-base font-semibold">Local snapshot</h2>
            </div>
            <p className="text-muted-foreground text-sm">The last complete Insights refresh stored by Support Bot.</p>
          </div>
          <StateBadge
            configured={status.configured}
            succeeded={status.lastSyncSucceeded}
            successLabel="Synced"
            pendingLabel="Awaiting sync"
          />
        </div>

        <dl className="divide-y">
          <StatusRow label="Last attempted">
            <time className="font-mono tabular-nums" dateTime={status.lastSyncAttemptAt ?? undefined}>
              {formatTimestamp(status.lastSyncAttemptAt)}
            </time>
          </StatusRow>
          <StatusRow label="Last successful">
            <span className="inline-flex items-center gap-1.5">
              <time className="font-mono tabular-nums" dateTime={status.lastSyncSuccessAt ?? undefined}>
                {formatTimestamp(status.lastSyncSuccessAt)}
              </time>
              {syncOverdue ? (
                <span
                  className="text-warning inline-flex"
                  role="img"
                  aria-label="Last successful sync is overdue"
                  title="Last successful sync is overdue"
                >
                  <AlertTriangle className="size-4" aria-hidden="true" />
                </span>
              ) : null}
            </span>
          </StatusRow>
          <StatusRow label="Sync schedule">
            Every <span className="font-mono tabular-nums">{formatSchedule(status.syncInterval)}</span>
          </StatusRow>
        </dl>

        <div className="mt-4 grid grid-cols-3 divide-x border-t pt-4 text-center">
          {[
            ["Products", status.counts.products],
            ["Journeys", status.counts.journeys],
            ["Product users", status.counts.users],
          ].map(([label, count]) => (
            <div key={label} className="px-2">
              <p className="text-foreground font-mono text-2xl font-semibold tracking-tight tabular-nums">{count}</p>
              <p className="text-muted-foreground text-xs">{label}</p>
            </div>
          ))}
        </div>
        <FailureMessage message={status.lastSyncError} />
      </div>
    </div>
  );
}

export { formatSchedule, formatTimestamp, isSyncOverdue };
