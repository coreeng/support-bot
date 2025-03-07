package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;

public record TeamUI(
    String name,
    ImmutableList<TeamType> types
) {
}
