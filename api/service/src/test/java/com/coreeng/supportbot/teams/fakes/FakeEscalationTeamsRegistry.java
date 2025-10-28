package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class FakeEscalationTeamsRegistry implements EscalationTeamsRegistry {
    private final List<EscalationTeam> teams;

    @Override
    public ImmutableList<EscalationTeam> listAllEscalationTeams() {
        return ImmutableList.copyOf(teams);
    }

    @Override
    public EscalationTeam findEscalationTeamByCode(String code) {
        return teams.stream().filter(t -> t.code().equals(code)).findFirst().orElse(null);
    }

    @Override
    public EscalationTeam findEscalationTeamByName(String teamName) {
        return teams.stream().filter(t -> t.label().equals(teamName)).findFirst().orElse(null);
    }
}

