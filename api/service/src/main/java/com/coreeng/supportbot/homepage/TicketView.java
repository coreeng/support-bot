package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
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

    @Nullable
    private EnumerationValue impact;

    private Instant lastOpenedAt;
    @Nullable
    private Instant closedAt;
}
