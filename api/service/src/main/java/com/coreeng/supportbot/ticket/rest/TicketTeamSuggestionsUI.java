package com.coreeng.supportbot.ticket.rest;

import com.google.common.collect.ImmutableList;

public record TicketTeamSuggestionsUI(ImmutableList<String> suggestedTeams, ImmutableList<String> otherTeams) {}
