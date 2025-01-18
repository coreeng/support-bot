package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

public interface SlackTeamsRegistry {
    ImmutableList<SlackTeam> listAllSlackTeams();
    @Nullable
    SlackTeam findSlackTeamByCode(String code);
    @Nullable
    SlackTeam findSlackTeamById(String id);
}
