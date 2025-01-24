package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.slack.MessageTs;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import javax.annotation.Nullable;

public record TicketSummaryView(
    TicketId ticketId,
    QuerySummaryView query,
    TicketStatus currentStatus,
    ImmutableList<EscalationView> escalations,
    ImmutableList<Ticket.StatusLog> statusLogs,
    TeamsInput teamsInput,
    ImmutableList<Tag> tags,
    ImmutableList<Tag> currentTags,
    ImmutableList<TicketImpact> impacts,
    @Nullable TicketImpact currentImpact
) {
    public static TicketSummaryView of(
        Ticket ticket,
        QuerySummaryView query,
        ImmutableList<EscalationView> escalationViews,
        TeamsInput teamsInput,
        ImmutableList<Tag> tags,
        ImmutableList<TicketImpact> impacts
    ) {
        return new TicketSummaryView(
            ticket.id(),
            query,
            ticket.status(),
            escalationViews,
            ticket.statusHistory(),
            teamsInput,
            tags,
            ticket.tags(),
            impacts,
            ticket.impact()
        );
    }

    public record QuerySummaryView(
        ImmutableList<LayoutBlock> blocks,
        MessageTs messageTs,
        String senderId,
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

    public record TeamsInput(
        @Nullable
        String currentTeam,
        ImmutableList<String> authorTeams,
        ImmutableList<String> otherTeams
    ) {}
}
