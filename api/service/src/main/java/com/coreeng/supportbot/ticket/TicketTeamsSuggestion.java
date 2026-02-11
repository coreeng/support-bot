package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;

public record TicketTeamsSuggestion(ImmutableList<String> userTeams, ImmutableList<String> otherTeams) {}
