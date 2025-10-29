package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final PlatformTeamsService platformTeamsService;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final SupportTeamService supportTeamService;

    public ImmutableList<Team> listTeamsByUserEmail(String email) {
        ImmutableList<PlatformTeam> platformTeams = platformTeamsService.listTeamsByUserEmail(email);
        ImmutableList<Team> teams = mapPlatformTeams(platformTeams);
        if (supportTeamService.isMemberByUserEmail(email)) {
            return ImmutableList.<Team>builder()
                .addAll(teams)
                .add(supportTeamService.getTeam())
                .build();

        }
        return teams;
    }

    public ImmutableList<Team> listTeams() {
        return Stream.concat(
            mapPlatformTeams(platformTeamsService.listTeams()).stream(),
            Stream.of(supportTeamService.getTeam())
        ).collect(toImmutableList());
    }

    public ImmutableList<Team> listTeamsByType(TeamType type) {
        return switch (type) {
            case tenant -> mapPlatformTeams(platformTeamsService.listTeams());
            case l2Support -> escalationTeamsRegistry.listAllEscalationTeams().stream()
                .map(t -> new Team(t.label(), t.code(), ImmutableList.of(TeamType.l2Support)))
                .collect(toImmutableList());
            case support -> ImmutableList.of(supportTeamService.getTeam());
        };
    }

    @Nullable
    public Team findTeamByCode(String code) {
        Team supportTeam = supportTeamService.getTeam();
        if (supportTeam.code().equals(code)) {
            return supportTeam;
        }
        PlatformTeam team = platformTeamsService.findTeamByName(code);
        return team != null
            ? mapPlatformTeam(team)
            : null;
    }

    private ImmutableList<Team> mapPlatformTeams(ImmutableList<PlatformTeam> platformTeams) {
        return platformTeams.stream()
            .map(this::mapPlatformTeam)
            .collect(toImmutableList());
    }

    @NotNull
    private Team mapPlatformTeam(PlatformTeam t) {
        EscalationTeam escalationTeam = escalationTeamsRegistry.findEscalationTeamByCode(t.name());
        if (escalationTeam != null) {
            return new Team(
                escalationTeam.label(),
                escalationTeam.code(),
                ImmutableList.of(TeamType.tenant, TeamType.l2Support)
            );
        }
        return new Team(
            t.name(),
            t.name(),
            ImmutableList.of(TeamType.tenant)
        );
    }
}
