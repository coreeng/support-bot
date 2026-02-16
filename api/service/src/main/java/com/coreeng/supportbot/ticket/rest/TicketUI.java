package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.escalation.rest.EscalationUI;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.teams.rest.TeamUI;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class TicketUI {
    private TicketId id;
    private Query query;

    @Nullable private FormMessage formMessage;

    private String channelId;
    private TicketStatus status;
    private boolean escalated;

    @Nullable private TeamUI team;

    @Nullable private String impact;

    private ImmutableList<String> tags;
    private ImmutableList<Log> logs;
    private ImmutableList<EscalationUI> escalations;
    private boolean ratingSubmitted;

    @Nullable private String assignedTo;

    public record Query(
            @Nullable String link,
            Instant date,
            MessageTs ts,
            @Nullable String text) {}

    public record FormMessage(@Nullable MessageTs ts) {}

    public record Log(Instant date, LogEvent event) {}

    public enum LogEvent {
        opened,
        stale,
        closed
    }
}
