package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.ticket.Ticket;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

@Getter
@Builder(toBuilder = true)
public class CreateEscalationRequest {
    private Ticket ticket;
    private String team;
    @Nullable
    private String threadPermalink;
    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();
}
