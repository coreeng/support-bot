package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.ticket.Ticket;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class CreateEscalationRequest {
    private Ticket ticket;
    private String team;
    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();
}
