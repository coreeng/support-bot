package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@RequiredArgsConstructor
public class TicketTeamSuggestionsService {
    private final SlackClient slackClient;
    private final PlatformTeamsService platformTeamsService;

    public TicketTeamsSuggestion getTeamSuggestions(String filterValue, String slackUserId) {
        String normalisedFilterValue = filterValue.toLowerCase();
        User.Profile userProfile = slackClient.getUserById(slackUserId);
        String userEmail = userProfile.getEmail();

        ImmutableList<String> allTeams = platformTeamsService.listTeams().stream()
            .map(PlatformTeam::name)
            .filter(t -> t.toLowerCase().contains(normalisedFilterValue))
            .collect(toImmutableList());
        ImmutableList<String> authorTeams = platformTeamsService.listTeamsByUserEmail(userEmail).stream()
            .map(PlatformTeam::name)
            .filter(t -> t.toLowerCase().contains(normalisedFilterValue))
            .collect(toImmutableList());
        ImmutableList<String> otherTeams = allTeams.stream()
            .filter(t -> !authorTeams.contains(t))
            .collect(toImmutableList());
        return new TicketTeamsSuggestion(
            authorTeams,
            otherTeams
        );
    }
}
