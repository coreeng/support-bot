package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableList;

import org.jspecify.annotations.Nullable;

public interface EscalationTeamsRegistry {
    ImmutableList<EscalationTeam> listAllEscalationTeams();
    @Nullable
    EscalationTeam findEscalationTeamByCode(String code);
}
