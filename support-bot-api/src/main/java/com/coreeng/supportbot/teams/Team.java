package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;

public record Team(
    String name,
    ImmutableList<TeamType> types
) {
}
