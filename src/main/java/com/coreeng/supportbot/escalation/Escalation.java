package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.time.Instant;

@Getter
@Builder(toBuilder = true)
public class Escalation {
    @Nullable
    private EscalationId id;
    private String channelId;
    private MessageTs threadTs;
    private MessageTs createdMessageTs;
    private EscalationStatus status;
    private TicketId ticketId;
    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();

    private Instant openedAt;
    @Nullable
    private Instant resolvedAt;

    @Nullable
    private String team;

    public static Escalation createNew(
        TicketId ticketId,
        @Nullable MessageRef threadRef,
        @Nullable String team,
        ImmutableList<String> tags
    ) {
        return Escalation.builder()
            .ticketId(ticketId)
            .status(EscalationStatus.opened)
            .openedAt(Instant.now())
            .threadTs(threadRef != null ? threadRef.actualThreadTs() : null)
            .channelId(threadRef != null ? threadRef.channelId() : null)
            .team(team)
            .tags(tags)
            .build();
    }

    public Instant lastStatusChangedAt() {
        // assuming that we can't "unresolve" escalation
        return resolvedAt != null ? resolvedAt : openedAt;
    }
}
