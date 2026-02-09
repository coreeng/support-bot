package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.teams.rest.TeamUI;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class EscalationUI {
    private EscalationId id;
    private TicketId ticketId;
    @Nullable
    private String escalatingTeam;
    @Nullable
    private String threadLink;
    private Instant openedAt;
    @Nullable
    private Instant resolvedAt;
    @Nullable
    private TeamUI team;
    private ImmutableList<String> tags;
    @Nullable
    private String impact;
}
