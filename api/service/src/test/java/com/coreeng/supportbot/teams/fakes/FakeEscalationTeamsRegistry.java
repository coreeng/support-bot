package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class FakeEscalationTeamsRegistry implements EscalationTeamsRegistry {
    private final List<EscalationTeam> teams;

    @Override
    public ImmutableList<EscalationTeam> listAllEscalationTeams() {
        return ImmutableList.copyOf(teams);
    }

    @Override
    @Nullable public EscalationTeam findEscalationTeamByCode(String code) {
        return teams.stream().filter(t -> t.code().equals(code)).findFirst().orElse(null);
    }
}
