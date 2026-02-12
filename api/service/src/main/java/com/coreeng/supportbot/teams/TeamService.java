package com.coreeng.supportbot.teams;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final PlatformTeamsService platformTeamsService;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final SupportTeamService supportTeamService;

    public ImmutableList<Team> listTeamsByUserEmail(String email) {
        ImmutableList<PlatformTeam> platformTeams = platformTeamsService.listTeamsByUserEmail(email);
        ImmutableList.Builder<Team> teamsBuilder =
                ImmutableList.<Team>builder().addAll(mapPlatformTeams(platformTeams));

        if (supportTeamService.isMemberByUserEmail(email)) {
            teamsBuilder.add(supportTeamService.getTeam());
        }

        if (supportTeamService.isLeadershipMemberByUserEmail(email)) {
            teamsBuilder.add(supportTeamService.getLeadershipTeam());
        }

        return teamsBuilder.build();
    }

    public ImmutableList<Team> listTeams() {
        return Stream.concat(
                        mapPlatformTeams(platformTeamsService.listTeams()).stream(),
                        Stream.of(supportTeamService.getTeam()))
                .collect(toImmutableList());
    }

    public ImmutableList<Team> listTeamsByType(TeamType type) {
        return switch (type) {
            case TENANT -> mapPlatformTeams(platformTeamsService.listTeams());
            case ESCALATION ->
                escalationTeamsRegistry.listAllEscalationTeams().stream()
                        .map(t -> {
                            PlatformTeam platformTeam = platformTeamsService.findTeamByName(t.code());
                            if (platformTeam != null) {
                                return new Team(
                                        t.label(), t.code(), ImmutableList.of(TeamType.TENANT, TeamType.ESCALATION));
                            }
                            return new Team(t.label(), t.code(), ImmutableList.of(TeamType.ESCALATION));
                        })
                        .collect(toImmutableList());
            case SUPPORT -> ImmutableList.of(supportTeamService.getTeam());
            case LEADERSHIP -> ImmutableList.of(supportTeamService.getLeadershipTeam());
        };
    }

    @Nullable public Team findTeamByCode(String code) {
        Team supportTeam = supportTeamService.getTeam();
        if (supportTeam.code().equals(code)) {
            return supportTeam;
        }

        PlatformTeam platformTeam = platformTeamsService.findTeamByName(code);
        if (platformTeam != null) {
            return mapPlatformTeam(platformTeam);
        }

        EscalationTeam escalationTeam = escalationTeamsRegistry.findEscalationTeamByCode(code);
        if (escalationTeam != null) {
            return new Team(escalationTeam.label(), escalationTeam.code(), ImmutableList.of(TeamType.ESCALATION));
        }

        return null;
    }

    private ImmutableList<Team> mapPlatformTeams(ImmutableList<PlatformTeam> platformTeams) {
        return platformTeams.stream().map(this::mapPlatformTeam).collect(toImmutableList());
    }

    @NonNull private Team mapPlatformTeam(PlatformTeam t) {
        EscalationTeam escalationTeam = escalationTeamsRegistry.findEscalationTeamByCode(t.name());
        if (escalationTeam != null) {
            return new Team(
                    escalationTeam.label(),
                    escalationTeam.code(),
                    ImmutableList.of(TeamType.TENANT, TeamType.ESCALATION));
        }
        return new Team(t.name(), t.name(), ImmutableList.of(TeamType.TENANT));
    }
}
