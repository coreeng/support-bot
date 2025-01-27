package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final PlatformTeamsService platformTeamsService;
    private final SupportTeamService supportTeamService;

    public ImmutableList<Team> listTeamsByUserEmail(String email) {
        ImmutableList<PlatformTeam> platformTeams = platformTeamsService.listTeamsByUserEmail(email);
        Stream<Team> tenantTeams = platformTeams.stream()
            .map(t -> new Team(t.name(), TeamType.tenant));
        if (supportTeamService.isMemberBeUserEmail(email)) {
            return ImmutableList.<Team>builder()
                .addAll(tenantTeams.iterator())
                .add(supportTeamService.getTeam())
                .build();

        }
        return tenantTeams.collect(toImmutableList());
    }

    public ImmutableList<Team> listTeams() {
        return Stream.concat(
            platformTeamsService.listTeams().stream()
                .map(t -> new Team(t.name(), TeamType.tenant)),
            Stream.of(supportTeamService.getTeam())
        ).collect(toImmutableList());
    }

    public ImmutableList<Team> listTeamsByType(TeamType type) {
        return switch (type) {
            case tenant -> platformTeamsService.listTeams().stream()
                .map(t -> new Team(t.name(), TeamType.tenant))
                .collect(toImmutableList());
            case support -> ImmutableList.of(supportTeamService.getTeam());
        };
    }
}
