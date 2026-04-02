package com.coreeng.supportbot.ticket;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Message;
import com.slack.api.model.User;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketTeamSuggestionsService {
    private final SlackClient slackClient;
    private final PlatformTeamsService platformTeamsService;
    private final TicketRepository ticketRepository;

    public TicketTeamsSuggestion getTeamSuggestions(String filterValue, SlackId entityId) {
        String normalisedFilterValue = filterValue.toLowerCase(Locale.ROOT);

        ImmutableList<String> allTeams = getAllTeamsFiltered(normalisedFilterValue);

        if (!(entityId instanceof SlackId.User userId)) {
            log.atInfo()
                    .addKeyValue("entityId", entityId.id())
                    .log("Team suggestions requested for a query posted by not a user. Returning all teams.");

            return new TicketTeamsSuggestion(ImmutableList.of(), allTeams);
        }

        User user = slackClient.getUserById(userId);
        String userEmail = user.getProfile().getEmail();

        ImmutableList<String> authorTeams = platformTeamsService.listTeamsByUserEmail(userEmail).stream()
                .map(PlatformTeam::name)
                .filter(t -> t.toLowerCase(Locale.ROOT).contains(normalisedFilterValue))
                .collect(toImmutableList());

        if (authorTeams.isEmpty()) {
            return new TicketTeamsSuggestion(ImmutableList.of(TicketTeam.NOT_A_TENANT_CODE), allTeams);
        }

        ImmutableList<String> otherTeams =
                allTeams.stream().filter(t -> !authorTeams.contains(t)).collect(toImmutableList());
        return new TicketTeamsSuggestion(authorTeams, otherTeams);
    }

    public TicketTeamsSuggestion getFallbackSuggestions(String filterValue) {
        String normalisedFilterValue = filterValue.toLowerCase(Locale.ROOT);
        ImmutableList<String> allTeams = getAllTeamsFiltered(normalisedFilterValue);
        return new TicketTeamsSuggestion(ImmutableList.of(), allTeams);
    }

    /**
     * Resolves team suggestions for a ticket by looking up the ticket's author from the original
     * Slack query message. Returns empty if the ticket is not found.
     */
    public Optional<TicketTeamsSuggestion> getTeamSuggestionsForTicket(TicketId ticketId) {
        Ticket ticket = ticketRepository.findTicketById(ticketId);
        if (ticket == null) {
            return Optional.empty();
        }

        try {
            Message queryMessage =
                    slackClient.getMessageByTs(new SlackGetMessageByTsRequest(ticket.channelId(), ticket.queryTs()));
            SlackId authorId = resolveAuthorId(queryMessage);

            if (authorId != null && !SlackId.SLACKBOT.equals(authorId)) {
                return Optional.of(getTeamSuggestions("", authorId));
            } else {
                return Optional.of(getFallbackSuggestions(""));
            }
        } catch (SlackException e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("ticketId", ticketId.id())
                    .log("Error resolving team suggestions from Slack, returning fallback");
            return Optional.of(getFallbackSuggestions(""));
        }
    }

    @Nullable private static SlackId resolveAuthorId(@Nullable Message message) {
        if (message == null) {
            return null;
        }
        if (message.getUser() != null) {
            return SlackId.user(message.getUser());
        }
        if (message.getBotId() != null) {
            return SlackId.bot(message.getBotId());
        }
        return null;
    }

    private ImmutableList<String> getAllTeamsFiltered(String normalisedFilterValue) {
        return platformTeamsService.listTeams().stream()
                .map(PlatformTeam::name)
                .filter(t -> t.toLowerCase(Locale.ROOT).contains(normalisedFilterValue))
                .collect(toImmutableList());
    }
}
