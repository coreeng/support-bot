package com.coreeng.supportbot.teams;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StaticPlatformTeamsFetcher implements PlatformTeamsFetcher {

    private final StaticPlatformTeamsProps props;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        List<String> teamsUsingNameAsCode = new ArrayList<>();
        List<TeamAndGroupTuple> teams = props.teams().stream()
                .map(team -> {
                    String explicitCode = team.code();
                    String code = (explicitCode != null && !explicitCode.isBlank()) ? explicitCode : team.name();
                    if (explicitCode == null || explicitCode.isBlank()) {
                        teamsUsingNameAsCode.add(team.name());
                    }
                    return new TeamAndGroupTuple(team.name(), code, team.groupRef());
                })
                .toList();
        if (!teamsUsingNameAsCode.isEmpty()) {
            log.atWarn()
                    .addKeyValue("teams", teamsUsingNameAsCode)
                    .log("Static teams without an explicit 'code' use their 'name' as the immutable identity; "
                            + "renaming such a team's 'name' orphans existing references. Add a stable 'code' to "
                            + "decouple the displayed name from identity.");
        }
        return teams;
    }
}
