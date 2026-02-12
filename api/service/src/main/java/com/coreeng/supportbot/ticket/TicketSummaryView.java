package com.coreeng.supportbot.ticket;

import static com.google.common.base.Preconditions.checkNotNull;

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
        @Nullable TicketImpact currentImpact,
        @Nullable String currentAssignee,
        ImmutableList<AssigneeOption> availableAssignees) {
    public static TicketSummaryView of(
            Ticket ticket,
            QuerySummaryView query,
            ImmutableList<EscalationView> escalationViews,
            ImmutableList<Tag> tags,
            ImmutableList<Tag> currentTags,
            ImmutableList<TicketImpact> impacts,
            @Nullable TicketImpact currentImpact,
            @Nullable String currentAssignee,
            ImmutableList<AssigneeOption> availableAssignees) {
        return new TicketSummaryView(
                checkNotNull(ticket.id()),
                query,
                ticket.status(),
                ticket.team() != null ? ticket.team().toCode() : null,
                escalationViews,
                ticket.statusLog(),
                tags,
                currentTags,
                impacts,
                currentImpact,
                currentAssignee,
                availableAssignees);
    }

    public Metadata metadata() {
        return new Metadata(ticketId().id(), query().senderId());
    }

    public record QuerySummaryView(
            ImmutableList<LayoutBlock> blocks,
            MessageTs messageTs,
            @Nullable SlackId senderId,
            @Nullable String permalink) {}

    public record EscalationView(EscalationId id, String teamSlackGroupId, EscalationStatus status) {
        public static EscalationView of(Escalation escalation, String teamSlackGroupId) {
            return new EscalationView(checkNotNull(escalation.id()), teamSlackGroupId, escalation.status());
        }
    }

    public record Metadata(long ticketId, @Nullable SlackId authorId) {}

    public record AssigneeOption(String userId, String displayName) {}
}
