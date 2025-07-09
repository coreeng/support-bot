package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.time.Instant;

@Getter
@Builder
public class TicketView {
    private TicketId id;
    private TicketStatus status;
    private String queryPermalink;
    private Instant lastOpenedAt;

    @Nullable
    private EnumerationValue impact;
    @Nullable
    private ImmutableList<Escalation> escalation;
    @Nullable
    private Instant closedAt;
}
