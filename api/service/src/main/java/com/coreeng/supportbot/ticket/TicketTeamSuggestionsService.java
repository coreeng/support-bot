package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketTeamSuggestionsService {
    private final SlackClient slackClient;
    private final PlatformTeamsService platformTeamsService;

    public TicketTeamsSuggestion getTeamSuggestions(String filterValue, SlackId entityId) {
        String normalisedFilterValue = filterValue.toLowerCase(Locale.ROOT);

        ImmutableList<String> allTeams = getAllTeamsFiltered(normalisedFilterValue);

        if (!(entityId instanceof SlackId.User userId)) {
            log.atInfo()
                .addKeyValue("entityId", entityId.id())
                .log("Team suggestions requested for a query posted by not a user. Returning all teams.");

            return new TicketTeamsSuggestion(
                ImmutableList.of(),
                allTeams
            );
        }

        User user = slackClient.getUserById(userId);
        String userEmail = user.getProfile().getEmail();

        ImmutableList<String> authorTeams = platformTeamsService.listTeamsByUserEmail(userEmail).stream()
            .map(PlatformTeam::name)
            .filter(t -> t.toLowerCase(Locale.ROOT).contains(normalisedFilterValue))
            .collect(toImmutableList());

        if (authorTeams.isEmpty()) {
            return new TicketTeamsSuggestion(
                ImmutableList.of(TicketTeam.notATenantCode),
                allTeams
            );
        }

        ImmutableList<String> otherTeams = allTeams.stream()
            .filter(t -> !authorTeams.contains(t))
            .collect(toImmutableList());
        return new TicketTeamsSuggestion(
            authorTeams,
            otherTeams
        );
    }

    public TicketTeamsSuggestion getFallbackSuggestions(String filterValue) {
        String normalisedFilterValue = filterValue.toLowerCase(Locale.ROOT);
        ImmutableList<String> allTeams = getAllTeamsFiltered(normalisedFilterValue);
        return new TicketTeamsSuggestion(ImmutableList.of(), allTeams);
    }

    private ImmutableList<String> getAllTeamsFiltered(String normalisedFilterValue) {
        return platformTeamsService.listTeams().stream()
            .map(PlatformTeam::name)
            .filter(t -> t.toLowerCase(Locale.ROOT).contains(normalisedFilterValue))
            .collect(toImmutableList());
    }
}
