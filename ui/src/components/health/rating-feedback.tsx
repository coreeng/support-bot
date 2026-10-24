"use client";

import { StarRating } from "@/components/health/star-rating";
import EditTicketModal from "@/components/tickets/EditTicketModal";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SingleSelectFilter, type SingleSelectFilterOption } from "@/components/ui/single-select-filter";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError, RatingFeedbackItem, useRatingFeedback } from "@/lib/hooks";
import { EyeOff, MessageSquareQuote, Search, Ticket } from "lucide-react";
import { useMemo, useState } from "react";

const PAGE_SIZE = 10;

type Sentiment = "negative" | "positive";
type Attribution = "named" | "anonymous";

const SENTIMENT_OPTIONS = [
  { label: "Needs attention (1–3★)", value: "negative" },
  { label: "Positive (4–5★)", value: "positive" },
] satisfies (SingleSelectFilterOption & { value: Sentiment })[];

const ATTRIBUTION_OPTIONS = [
  { label: "Named", value: "named", icon: Ticket },
  { label: "Anonymous", value: "anonymous", icon: EyeOff },
] satisfies (SingleSelectFilterOption & { value: Attribution })[];

// Same threshold as the backend's RatingFeedbackViewInput.isPositive(): 4 and 5 stars are asked
// what went well, 1 to 3 what went wrong. Keep the two in sync.
export const isPositiveRating = (rating: number) => rating >= 4;

function formatWeek(weekStart: string) {
  return new Date(`${weekStart}T00:00:00Z`).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}

export default function RatingFeedback({ from, to }: { from?: string; to?: string }) {
  const { data, isLoading, error } = useRatingFeedback(from, to);

  const [sentiment, setSentiment] = useState<Sentiment | undefined>();
  const [attribution, setAttribution] = useState<Attribution | undefined>();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);

  // Counts are only meaningful once the feedback has loaded, otherwise a failure would read as "no complaints"
  const ready = !isLoading && !error;
  const items = useMemo(() => data ?? [], [data]);

  const stats = useMemo(() => {
    const negative = items.filter((i) => !isPositiveRating(i.rating)).length;
    const named = items.filter((i) => i.ticketId !== null).length;
    return { total: items.length, negative, positive: items.length - negative, named };
  }, [items]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return items.filter((i) => {
      if (sentiment && (sentiment === "positive") !== isPositiveRating(i.rating)) return false;
      if (attribution && (attribution === "named") !== (i.ticketId !== null)) return false;
      return !term || i.feedback.toLowerCase().includes(term);
    });
  }, [items, sentiment, attribution, search]);

  // Per week across every page, so a week split over two pages still shows its full count
  const weekCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of filtered) counts.set(item.weekStart, (counts.get(item.weekStart) ?? 0) + 1);
    return counts;
  }, [filtered]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  // The backend sends the latest week first, then by rating and text within a week, never by time (ADR-009).
  // Grouping keeps that order, so don't re-sort.
  const weeks = new Map<string, RatingFeedbackItem[]>();
  for (const item of pageItems) {
    const week = weeks.get(item.weekStart);
    if (week) week.push(item);
    else weeks.set(item.weekStart, [item]);
  }

  const renderBody = () => {
    if (isLoading) {
      return (
        <div className="space-y-4 p-6" data-testid="feedback-loading">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </div>
      );
    }
    if (error) {
      return (
        <div className="text-muted-foreground p-16 text-center text-sm">
          {isApiError(error, 403) ? "Feedback requires Support Engineer or Leadership access." : "Couldn't load feedback. Try again later."}
        </div>
      );
    }
    if (filtered.length === 0) {
      return (
        <div className="text-muted-foreground flex flex-col items-center gap-2 p-16 text-center text-sm">
          <MessageSquareQuote className="text-muted-foreground/60 h-8 w-8" />
          {items.length === 0 ? "No written feedback in this period yet." : "No feedback matches these filters."}
        </div>
      );
    }
    return (
      <div>
        {[...weeks].map(([weekStart, weekItems]) => {
          const count = weekCounts.get(weekStart) ?? weekItems.length;
          return (
            <section key={weekStart} aria-label={`Week of ${formatWeek(weekStart)}`}>
              <div className="bg-muted flex items-center justify-between px-6 py-2">
                <h3 className="text-foreground text-xs font-bold tracking-wider uppercase">Week of {formatWeek(weekStart)}</h3>
                <span className="text-muted-foreground font-mono text-xs tabular-nums">
                  {count} {count === 1 ? "response" : "responses"}
                </span>
              </div>
              <ul className="divide-y">
                {weekItems.map((item, idx) => (
                  // Index key: the API deliberately exposes no rating id (ADR-009)
                  <FeedbackRow key={`${weekStart}-${idx}`} item={item} onOpenTicket={(id) => setSelectedTicketId(String(id))} />
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    );
  };

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-foreground text-base font-semibold">What people said</h2>
        <p className="text-muted-foreground text-sm">Written feedback left after rating a closed ticket.</p>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatTile label="Feedback received" value={ready ? stats.total : null} isLoading={isLoading} accent="bg-chart-4/15" />
        <StatTile
          label="Needs attention"
          value={ready ? stats.negative : null}
          isLoading={isLoading}
          hint="1–3 stars"
          accent="bg-warning/15"
          valueClassName={ready && stats.negative > 0 ? "text-warning" : undefined}
        />
        <StatTile
          label="Positive"
          value={ready ? stats.positive : null}
          isLoading={isLoading}
          hint="4–5 stars"
          accent="bg-success/15"
          valueClassName={ready ? "text-success" : undefined}
        />
        <StatTile
          label="Named"
          value={ready ? stats.named : null}
          isLoading={isLoading}
          hint={ready && stats.total > 0 ? `${Math.round((stats.named / stats.total) * 100)}% shared their name` : undefined}
          accent="bg-info/15"
        />
      </div>

      <div className="bg-card overflow-hidden rounded-xl border">
        <div className="flex flex-wrap items-center gap-2 border-b px-6 py-3">
          <div className="relative">
            <Search className="text-muted-foreground absolute top-1/2 left-2.5 h-4 w-4 -translate-y-1/2" />
            <Input
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(1);
              }}
              placeholder="Search feedback..."
              aria-label="Search feedback"
              className="h-8 w-[220px] pl-8"
            />
          </div>
          <SingleSelectFilter
            title="Sentiment"
            value={sentiment}
            onChange={(v) => {
              setSentiment(SENTIMENT_OPTIONS.find((o) => o.value === v)?.value);
              setPage(1);
            }}
            options={SENTIMENT_OPTIONS}
            showSearch={false}
          />
          <SingleSelectFilter
            title="Attribution"
            value={attribution}
            onChange={(v) => {
              setAttribution(ATTRIBUTION_OPTIONS.find((o) => o.value === v)?.value);
              setPage(1);
            }}
            options={ATTRIBUTION_OPTIONS}
            showSearch={false}
          />
          {ready && (
            <span className="text-muted-foreground ml-auto font-mono text-sm tabular-nums">
              {filtered.length} of {items.length}
            </span>
          )}
        </div>

        {renderBody()}

        {totalPages > 1 && (
          <div className="flex items-center justify-end gap-4 border-t px-6 py-3">
            <span className="text-muted-foreground text-sm">
              Page {currentPage} of {totalPages}
            </span>
            <Button variant="outline" size="sm" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>
              Previous
            </Button>
            <Button variant="outline" size="sm" disabled={currentPage === totalPages} onClick={() => setPage(currentPage + 1)}>
              Next
            </Button>
          </div>
        )}
      </div>

      <EditTicketModal
        ticketId={selectedTicketId}
        open={selectedTicketId !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedTicketId(null);
        }}
        onSuccess={() => setSelectedTicketId(null)}
      />
    </div>
  );
}

function FeedbackRow({ item, onOpenTicket }: { item: RatingFeedbackItem; onOpenTicket: (ticketId: number) => void }) {
  const { rating, feedback, ticketId } = item;
  const positive = isPositiveRating(rating);
  return (
    <li className="hover:bg-muted/50 flex gap-4 px-6 py-4 transition-all">
      <div className={`w-1 shrink-0 rounded-full ${positive ? "bg-success" : "bg-warning"}`} aria-hidden />
      <div className="min-w-0 flex-1 space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="sr-only">{rating} out of 5 stars</span>
          <StarRating rating={rating} hideValue />
          <Badge variant="outline" className={positive ? "text-success" : "text-warning"}>
            {positive ? "Positive" : "Needs attention"}
          </Badge>
          <div className="ml-auto">
            {ticketId !== null ? (
              <Button variant="outline" size="sm" onClick={() => onOpenTicket(ticketId)}>
                <Ticket className="h-4 w-4" />
                Ticket <span className="font-mono tabular-nums">#{ticketId}</span>
              </Button>
            ) : (
              <Badge variant="outline" className="text-muted-foreground gap-1">
                <EyeOff className="h-3 w-3" />
                Anonymous
              </Badge>
            )}
          </div>
        </div>
        <p className="text-foreground text-sm leading-relaxed break-words whitespace-pre-wrap">{feedback}</p>
      </div>
    </li>
  );
}

function StatTile({
  label,
  value,
  isLoading,
  hint,
  accent,
  valueClassName,
}: {
  label: string;
  /** Null when there is nothing trustworthy to show, e.g. the request failed. */
  value: number | null;
  isLoading: boolean;
  hint?: string;
  accent: string;
  valueClassName?: string;
}) {
  return (
    <div className="bg-card relative overflow-hidden rounded-xl border p-6">
      <div className={`${accent} absolute -top-4 -right-4 h-24 w-24 rounded-full`} />
      <div className={`${accent} absolute -right-6 -bottom-6 h-20 w-20 rounded-full`} />
      <div className="relative">
        <p className="text-muted-foreground mb-2 text-sm font-medium">{label}</p>
        {isLoading ? (
          <Skeleton className="h-9 w-16" data-testid="stat-loading" />
        ) : (
          <p className={`${valueClassName ?? "text-foreground"} font-mono text-3xl font-semibold tracking-tight tabular-nums`}>
            {value ?? "—"}
          </p>
        )}
        {hint && <p className="text-muted-foreground mt-1 text-xs">{hint}</p>}
      </div>
    </div>
  );
}
