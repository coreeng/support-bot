"use client";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import type { SummaryEvidenceTicket, SummarySegment } from "@/lib/types/summary";
import { formatUtcDateTime, slackDeepLink } from "@/lib/utils";
import { Slack } from "lucide-react";
import { Fragment, useEffect, useRef, useState, type PointerEvent } from "react";

/** Hover intent: long enough that sweeping the pointer across the prose does not flash popovers. */
const OPEN_DELAY_MS = 150;
/** Long enough to cross the gap from the phrase into its popover without it closing. */
const CLOSE_DELAY_MS = 200;

/**
 * The summary prose. Claims the model cited are underlined, and show the tickets behind them on
 * hover or click; prose without citations renders exactly as plain text.
 *
 * Every string here comes from model output, so it is only ever rendered as React text.
 */
export default function SummaryProse({
  content,
  segments,
  onOpenTicket,
}: {
  content?: string;
  segments?: SummarySegment[];
  onOpenTicket: (ticketId: string) => void;
}) {
  return (
    <p className="text-foreground text-sm whitespace-pre-wrap" data-testid="summary-prose">
      {segments?.length
        ? segments.map((segment, index) =>
            segment.tickets?.length ? (
              <CitedClaim key={index} text={segment.text} tickets={segment.tickets} onOpenTicket={onOpenTicket} />
            ) : (
              <Fragment key={index}>{segment.text}</Fragment>
            )
          )
        : content}
    </p>
  );
}

/**
 * One cited claim. Opens on mouse hover after a short delay and closes when the pointer leaves both
 * the phrase and the popover. A click (or Enter/Space) pins it open until Escape or a click
 * elsewhere, which is also how touch and keyboard users reach it.
 */
function CitedClaim({
  text,
  tickets,
  onOpenTicket,
}: {
  text: string;
  tickets: SummaryEvidenceTicket[];
  onOpenTicket: (ticketId: string) => void;
}) {
  const [open, setOpen] = useState(false);
  // Pinned by a click: hovering away must not close it, and it takes focus for keyboard users.
  const pinned = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cancelTimer = () => {
    if (timer.current !== null) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  };
  useEffect(() => cancelTimer, []);

  const hoverTo = (next: boolean) => (event: PointerEvent) => {
    if (event.pointerType !== "mouse" || pinned.current) return;
    cancelTimer();
    timer.current = setTimeout(() => setOpen(next), next ? OPEN_DELAY_MS : CLOSE_DELAY_MS);
  };

  const close = () => {
    cancelTimer();
    pinned.current = false;
    setOpen(false);
  };

  return (
    <Popover open={open} onOpenChange={(next) => (next ? setOpen(true) : close())}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onPointerEnter={hoverTo(true)}
          onPointerLeave={hoverTo(false)}
          onClick={(event) => {
            // Handled here rather than by the trigger's own toggle, so a click on a popover that
            // hovering opened pins it instead of closing it.
            event.preventDefault();
            cancelTimer();
            if (open && pinned.current) {
              close();
            } else {
              pinned.current = true;
              setOpen(true);
            }
          }}
          className="text-foreground decoration-muted-foreground hover:decoration-foreground data-[state=open]:bg-accent inline h-auto cursor-pointer rounded-sm p-0 align-baseline text-sm font-normal whitespace-pre-wrap underline decoration-dotted underline-offset-4"
          data-testid="summary-cited-claim"
        >
          {text}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        className="w-96 max-w-[calc(100vw-2rem)] p-0"
        onPointerEnter={(event) => event.pointerType === "mouse" && cancelTimer()}
        onPointerLeave={hoverTo(false)}
        // Opening on hover must not pull focus away from wherever the reader is.
        onOpenAutoFocus={(event) => !pinned.current && event.preventDefault()}
        data-testid="summary-evidence"
      >
        <div className="border-b px-4 py-3">
          {/* Examples, not the full set: a claim may cover more tickets than the five it can cite, or
              than the sample of reasons the model was shown. */}
          <p className="text-foreground text-sm font-medium">{tickets.length === 1 ? "Example ticket" : "Example tickets"}</p>
          <p className="text-muted-foreground text-xs">
            Cited by the model · <span className="font-mono tabular-nums">{tickets.length}</span> shown
          </p>
        </div>
        <ul className="max-h-80 divide-y overflow-y-auto">
          {tickets.map((ticket) => (
            <EvidenceRow
              key={ticket.ticketId}
              ticket={ticket}
              onOpenTicket={(ticketId) => {
                close();
                onOpenTicket(ticketId);
              }}
            />
          ))}
        </ul>
      </PopoverContent>
    </Popover>
  );
}

function EvidenceRow({ ticket, onOpenTicket }: { ticket: SummaryEvidenceTicket; onOpenTicket: (ticketId: string) => void }) {
  return (
    <li className="space-y-2 px-4 py-3">
      <p className="text-foreground text-sm">{ticket.text || `Ticket ${ticket.ticketId}`}</p>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-muted-foreground text-xs">
          Ticket <span className="font-mono tabular-nums">#{ticket.ticketId}</span> · {formatUtcDateTime(ticket.timestamp)}
        </span>
        <div className="flex gap-1">
          <Button asChild variant="outline" size="sm">
            <a
              href={slackDeepLink(ticket.channelId, ticket.queryTs)}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Open ticket ${ticket.ticketId} in Slack`}
            >
              <Slack className="h-4 w-4" />
              Open in Slack
            </a>
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onOpenTicket(ticket.ticketId)}
            aria-label={`View ticket ${ticket.ticketId}`}
            className="cursor-pointer"
          >
            View ticket
          </Button>
        </div>
      </div>
    </li>
  );
}
