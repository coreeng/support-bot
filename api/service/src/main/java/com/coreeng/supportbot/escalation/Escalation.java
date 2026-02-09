package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class Escalation {
    @Nullable
    private EscalationId id;
    private String channelId;
    @Nullable
    private MessageTs threadTs;
    @Nullable
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
        @Nullable String team,
        ImmutableList<String> tags,
        MessageRef queryRef
    ) {
        return Escalation.builder()
            .ticketId(ticketId)
            .status(EscalationStatus.opened)
            .openedAt(Instant.now())
            .team(team)
            .tags(tags)
            .channelId(queryRef.channelId())
            .threadTs(queryRef.ts())
            .build();
    }

    public Instant lastStatusChangedAt() {
        // assuming that we can't "unresolve" escalation
        return resolvedAt != null ? resolvedAt : openedAt;
    }
}
