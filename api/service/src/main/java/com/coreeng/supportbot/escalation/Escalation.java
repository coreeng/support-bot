package com.coreeng.supportbot.escalation;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
@Builder(toBuilder = true)
public class Escalation {
    @Nullable private EscalationId id;

    private String channelId;

    @Nullable private MessageTs threadTs;

    @Nullable private MessageTs createdMessageTs;

    private EscalationStatus status;
    private TicketId ticketId;

    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();

    private Instant openedAt;

    @Nullable private Instant resolvedAt;

    @Nullable private String team;

    @Builder.Default
    private EscalationSource source = EscalationSource.manual;

    public static Escalation createNew(
            TicketId ticketId,
            @Nullable String team,
            ImmutableList<String> tags,
            MessageRef queryRef,
            EscalationSource source) {
        checkNotNull(source, "escalation source must not be null");
        return Escalation.builder()
                .ticketId(ticketId)
                .status(EscalationStatus.opened)
                .openedAt(Instant.now())
                .team(team)
                .tags(tags)
                .channelId(queryRef.channelId())
                .threadTs(queryRef.ts())
                .source(source)
                .build();
    }

    public Instant lastStatusChangedAt() {
        // assuming that we can't "unresolve" escalation
        return resolvedAt != null ? resolvedAt : openedAt;
    }
}
