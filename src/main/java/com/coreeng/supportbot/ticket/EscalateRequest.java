package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

@Getter
@Builder
public class EscalateRequest {
    private TicketId ticketId;
    private ImmutableList<String> tags;
    private String team;
    @Nullable
    private String threadPermalink;
}
