package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.escalation.rest.EscalationUI;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.teams.rest.TeamUI;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.annotation.Nullable;
import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class TicketUI {
    private TicketId id;
    private Query query;
    private FormMessage formMessage;
    private String channelId;
    private TicketStatus status;
    private boolean escalated;
    @Nullable
    private TeamUI team;
    private String impact;
    private ImmutableList<String> tags;
    private ImmutableList<Log> logs;
    private ImmutableList<EscalationUI> escalations;
    private boolean ratingSubmitted;


    public record Query(
        String link,
        Instant date,
        MessageTs ts,
        @Nullable String text
    ) {}
    public record FormMessage(
        MessageTs ts
    ) {}
    public record Log(
        Instant date,
        LogEvent event
    ) {}
    public enum LogEvent {
        opened, stale, closed
    }
}
