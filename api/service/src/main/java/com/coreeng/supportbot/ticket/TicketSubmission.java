package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class TicketSubmission {
    private TicketId ticketId;
    private TicketStatus status;
    @Nullable
    private TicketTeam authorsTeam;
    private ImmutableList<String> tags;
    @Nullable
    private String impact;
    @Nullable
    private String assignedTo;
    private boolean confirmed;
}
