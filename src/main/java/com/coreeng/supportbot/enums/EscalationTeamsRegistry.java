package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

public interface EscalationTeamsRegistry {
    ImmutableList<EscalationTeam> listAllEscalationTeams();
    @Nullable
    EscalationTeam findEscalationTeamByCode(String code);
    @Nullable
    EscalationTeam findEscalationTeamByName(String team);
}
