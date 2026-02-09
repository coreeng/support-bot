package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

@Getter
@Builder
public class TicketView {
    private TicketId id;
    private TicketStatus status;
    @Nullable
    private String queryPermalink;
    private Instant lastOpenedAt;

    @Nullable
    private EnumerationValue impact;
    @Nullable
    private ImmutableList<Escalation> escalations;
    @Nullable
    private Instant closedAt;
    @Nullable
    private String inquiringTeam;
    @Nullable
    private String assignedTo;
}
