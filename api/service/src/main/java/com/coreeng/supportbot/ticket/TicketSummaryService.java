package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

@Service
@RequiredArgsConstructor
public class TicketSummaryService {
    private final TicketRepository repository;
    private final SlackClient slackClient;
    private final EscalationQueryService escalationQueryService;
    private final TagsRegistry tagsRegistry;
    private final ImpactsRegistry impactsRegistry;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final SupportTeamService supportTeamService;
    private final TicketAssignmentProps assignmentProps;

    public TicketSummaryView summaryView(TicketId id) {
        Ticket ticket = repository.findTicketById(id);
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found: " + id);
        }
        Message queryMessage = slackClient
            .getMessageByTs(new SlackGetMessageByTsRequest(ticket.channelId(), ticket.queryTs()));
        TicketSummaryView.QuerySummaryView querySummary = getQuerySummaryView(ticket, queryMessage);
        ImmutableList<TicketSummaryView.EscalationView> escalations = getEscalationViews(ticket);
        ImmutableList<Tag> allTags = tagsRegistry.listAllTags();
        ImmutableList<TicketImpact> allImpacts = impactsRegistry.listAllImpacts();
        
        // Assignee fields (only if assignment is enabled)
        String currentAssignee = assignmentProps.enabled() && !ticket.assignedToOrphaned() 
            ? ticket.assignedTo() 
            : null;
        ImmutableList<TicketSummaryView.AssigneeOption> availableAssignees = assignmentProps.enabled()
            ? supportTeamService.members().stream()
                .map(member -> new TicketSummaryView.AssigneeOption(
                    member.slackId().id(),
                    member.email()
                ))
                .collect(toImmutableList())
            : ImmutableList.of();
        
        return TicketSummaryView.of(
            ticket,
            querySummary,
            escalations,
            allTags,
            allTags.stream()
                .filter(t -> ticket.tags().contains(t.code()))
                .collect(toImmutableList()),
            allImpacts,
            ticket.impact() != null
                ? allImpacts.stream()
                    .filter(i -> ticket.impact().contains(i.code()))
                    .findAny().orElse(null)
                : null,
            currentAssignee,
            availableAssignees
        );
    }

    private ImmutableList<TicketSummaryView.EscalationView> getEscalationViews(Ticket ticket) {
        return escalationQueryService
            .listByTicketId(ticket.id()).stream()
            .sorted(comparing(Escalation::openedAt))
            .map(e -> {
                String threadPermalink = slackClient.getPermalink(new SlackGetMessageByTsRequest(
                    e.channelId(), e.threadTs()
                ));
                return TicketSummaryView.EscalationView.of(
                    e,
                    threadPermalink,
                    checkNotNull(escalationTeamsRegistry.findEscalationTeamByCode(e.team())).slackGroupId()
                );
            })
            .collect(toImmutableList());
    }

    private TicketSummaryView.QuerySummaryView getQuerySummaryView(Ticket ticket, Message queryMessage) {
        SlackGetMessageByTsRequest messageRequest = new SlackGetMessageByTsRequest(
            ticket.channelId(),
            ticket.queryTs()
        );
        String permalink = slackClient.getPermalink(messageRequest);
        SlackId senderId = queryMessage.getUser() != null
            ? SlackId.user(queryMessage.getUser())
            : SlackId.bot(queryMessage.getBotId());
        return new TicketSummaryView.QuerySummaryView(
            ImmutableList.copyOf(queryMessage.getBlocks()),
            new MessageTs(queryMessage.getTs()),
            senderId,
            permalink
        );
    }
}
