package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class TicketSubmission {
    private TicketId ticketId;
    private TicketStatus status;
    private TicketTeam authorsTeam;
    private ImmutableList<String> tags;
    private String impact;
    private boolean confirmed;
}
