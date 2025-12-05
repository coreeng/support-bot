package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;
import org.jspecify.annotations.Nullable;

public record TicketSummaryView(
    TicketId ticketId,
    QuerySummaryView query,
    TicketStatus currentStatus,
    @Nullable String currentTeam,
    ImmutableList<EscalationView> escalations,
    ImmutableList<Ticket.StatusLog> statusLogs,
    ImmutableList<Tag> tags,
    ImmutableList<Tag> currentTags,
    ImmutableList<TicketImpact> impacts,
    @Nullable TicketImpact currentImpact
) {
    public static TicketSummaryView of(
        Ticket ticket,
        QuerySummaryView query,
        ImmutableList<EscalationView> escalationViews,
        ImmutableList<Tag> tags,
        ImmutableList<Tag> currentTags,
        ImmutableList<TicketImpact> impacts,
        @Nullable TicketImpact currentImpact
    ) {
        return new TicketSummaryView(
            ticket.id(),
            query,
            ticket.status(),
            ticket.team(),
            escalationViews,
            ticket.statusLog(),
            tags,
            currentTags,
            impacts,
            currentImpact
        );
    }

    public Metadata metadata() {
        return new Metadata(
            ticketId().id(),
            query().senderId()
        );
    }

    public record QuerySummaryView(
        ImmutableList<LayoutBlock> blocks,
        MessageTs messageTs,
        SlackId senderId,
        String permalink
    ) {
    }

    public record EscalationView(
        EscalationId id,
        String threadPermalink,
        String teamSlackGroupId,
        EscalationStatus status
    ) {
        public static EscalationView of(
            Escalation escalation,
            String threadPermalink,
            String teamSlackGroupId
        ) {
            return new EscalationView(
                escalation.id(),
                threadPermalink,
                teamSlackGroupId,
                escalation.status()
            );
        }
    }

    public record Metadata(
        long ticketId,
        SlackId authorId
    ) {
    }
}
