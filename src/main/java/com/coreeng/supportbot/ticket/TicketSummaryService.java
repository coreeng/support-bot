package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Message;
import com.slack.api.model.User;
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
    private final PlatformTeamsService platformTeamsService;

    public TicketSummaryView summaryView(TicketId id) {
        Ticket ticket = repository.findTicketById(id);
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found: " + id);
        }
        TicketSummaryView.QuerySummaryView querySummary = getQuerySummaryView(ticket);
        ImmutableList<TicketSummaryView.EscalationView> escalations = getEscalationViews(ticket);
        TicketSummaryView.TeamsInput teamsInput = getTeamsInputView(ticket);
        ImmutableList<Tag> allTags = tagsRegistry.listAllTags();
        ImmutableList<TicketImpact> allImpacts = impactsRegistry.listAllImpacts();
        return TicketSummaryView.of(
            ticket,
            querySummary,
            escalations,
            teamsInput,
            allTags,
            allTags.stream()
                .filter(t -> ticket.tags().contains(t.code()))
                .collect(toImmutableList()),
            allImpacts,
            ticket.impact() != null
                ? allImpacts.stream()
                    .filter(i -> ticket.impact().contains(i.code()))
                    .findAny().orElse(null)
                : null
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
                    checkNotNull(escalationTeamsRegistry.findEscalationTeamByName(e.team())).slackGroupId()
                );
            })
            .collect(toImmutableList());
    }

    private TicketSummaryView.QuerySummaryView getQuerySummaryView(Ticket ticket) {
        SlackGetMessageByTsRequest messageRequest = new SlackGetMessageByTsRequest(
            ticket.channelId(),
            ticket.queryTs()
        );
        Message queryMessage = slackClient.getMessageByTs(messageRequest);
        String permalink = slackClient.getPermalink(messageRequest);
        return new TicketSummaryView.QuerySummaryView(
            ImmutableList.copyOf(queryMessage.getBlocks()),
            new MessageTs(queryMessage.getTs()),
            queryMessage.getUser(),
            permalink
        );
    }

    private TicketSummaryView.TeamsInput getTeamsInputView(Ticket ticket) {
        Message queryMessage = slackClient
            .getMessageByTs(new SlackGetMessageByTsRequest(ticket.channelId(), ticket.queryTs()));
        User.Profile userProfile = slackClient.getUserById(queryMessage.getUser());
        String userEmail = userProfile.getEmail();

        ImmutableList<String> allTeams = platformTeamsService.listTeams().stream()
            .map(PlatformTeam::name)
            .collect(toImmutableList());
        ImmutableList<String> authorTeams = platformTeamsService.listTeamsByUserEmail(userEmail).stream()
            .map(PlatformTeam::name)
            .collect(toImmutableList());
        ImmutableList<String> otherTeams = allTeams.stream()
            .filter(t -> !authorTeams.contains(t))
            .collect(toImmutableList());
        return new TicketSummaryView.TeamsInput(
            ticket.team(),
            authorTeams,
            otherTeams
        );
    }
}
