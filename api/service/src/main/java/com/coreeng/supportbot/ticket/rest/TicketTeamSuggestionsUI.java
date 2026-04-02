package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketTeamsSuggestion;
import com.google.common.collect.ImmutableList;

public record TicketTeamSuggestionsUI(ImmutableList<String> suggestedTeams, ImmutableList<String> otherTeams) {

    public static TicketTeamSuggestionsUI from(TicketTeamsSuggestion suggestion) {
        return new TicketTeamSuggestionsUI(suggestion.userTeams(), suggestion.otherTeams());
    }
}
