package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Message;
import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static java.util.Comparator.comparing;

@Service
@RequiredArgsConstructor
@Slf4j
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

        TicketSummaryView.QuerySummaryView querySummary;
        try {
            Message queryMessage = slackClient
                .getMessageByTs(new SlackGetMessageByTsRequest(ticket.channelId(), ticket.queryTs()));
            querySummary = getQuerySummaryView(ticket, queryMessage);
        } catch (SlackException ex) {
            log.atError()
                .setCause(ex)
                .addKeyValue("ticketId", ticket.id())
                .addKeyValue("channelId", ticket.channelId())
                .addKeyValue("queryTs", ticket.queryTs())
                .log("Failed to fetch query message from Slack for ticket summary view");
            querySummary = buildFallbackQuerySummary(ticket);
        }
        ImmutableList<TicketSummaryView.EscalationView> escalations = getEscalationViews(ticket);
        ImmutableList<Tag> allTags = tagsRegistry.listAllTags();
        ImmutableList<TicketImpact> allImpacts = impactsRegistry.listAllImpacts();
        
        // Assignee fields (only if assignment is enabled)
        String currentAssignee = assignmentProps.enabled() && ticket.assignedTo() != null
            ? ticket.assignedTo().id()
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
	        TicketId ticketId = checkNotNull(ticket.id());
	        return escalationQueryService
	            .listByTicketId(ticketId).stream()
	            .sorted(comparing(Escalation::openedAt))
	            .map(e -> TicketSummaryView.EscalationView.of(
	                e,
	                checkNotNull(escalationTeamsRegistry.findEscalationTeamByCode(checkNotNull(e.team()))).slackGroupId()
	            ))
	            .collect(toImmutableList());
	    }

    private TicketSummaryView.QuerySummaryView getQuerySummaryView(Ticket ticket, Message queryMessage) {
        ImmutableList<LayoutBlock> blocks = buildBlocksForMessage(queryMessage);
        SlackId senderId = resolveSenderId(queryMessage);
        String permalink = resolveQueryPermalink(ticket);
        return new TicketSummaryView.QuerySummaryView(
            blocks,
            ticket.queryTs(),
            senderId,
            permalink
        );
    }

    private TicketSummaryView.QuerySummaryView buildFallbackQuerySummary(Ticket ticket) {
        ImmutableList<LayoutBlock> blocks = ImmutableList.of(
            section(s -> s.text(markdownText(t -> t.text("Couldn't fetch the message"))))
        );
        String permalink = resolveQueryPermalink(ticket);
        return new TicketSummaryView.QuerySummaryView(
            blocks,
            ticket.queryTs(),
            null,
            permalink
        );
    }

    private ImmutableList<LayoutBlock> buildBlocksForMessage(Message queryMessage) {
        String subtype = queryMessage.getSubtype();
        boolean isTombstone = "tombstone".equalsIgnoreCase(subtype);
        String text = queryMessage.getText();

        if (isTombstone) {
	            String tombstoneText = StringUtils.isBlank(text)
	                ? "This message is unavailable."
	                : text;
            return ImmutableList.of(
                section(s -> s.text(markdownText(t -> t.text(tombstoneText))))
            );
        }

        if (queryMessage.getBlocks() != null && !queryMessage.getBlocks().isEmpty()) {
            return ImmutableList.copyOf(queryMessage.getBlocks());
        }

        if (text != null && !text.isBlank()) {
            return ImmutableList.of(
                section(s -> s.text(markdownText(t -> t.text(text))))
            );
        }

        return ImmutableList.of(
            section(s -> s.text(markdownText(t -> t.text("No message content available."))))
        );
    }

    @Nullable
    private SlackId resolveSenderId(Message queryMessage) {
        if (queryMessage.getUser() != null) {
            return SlackId.user(queryMessage.getUser());
        }
        if (queryMessage.getBotId() != null) {
            return SlackId.bot(queryMessage.getBotId());
        }
        return null;
    }

    @Nullable
    private String resolveQueryPermalink(Ticket ticket) {
        try {
            return slackClient.getPermalink(new SlackGetMessageByTsRequest(
                ticket.channelId(),
                ticket.queryTs()
            ));
        } catch (SlackException ex) {
            log.atError()
                .setCause(ex)
                .addKeyValue("ticketId", ticket.id())
                .addKeyValue("channelId", ticket.channelId())
                .addKeyValue("queryTs", ticket.queryTs())
                .log("Failed to resolve query permalink from Slack for ticket summary view");
            return null;
        }
    }
}
