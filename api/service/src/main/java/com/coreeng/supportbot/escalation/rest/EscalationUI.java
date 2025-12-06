package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.teams.rest.TeamUI;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class EscalationUI {
    private EscalationId id;
    private TicketId ticketId;
    private String escalatingTeam;
    private String threadLink;
    private Instant openedAt;
    private Instant resolvedAt;
    private TeamUI team;
    private ImmutableList<String> tags;
    private String impact;
}
