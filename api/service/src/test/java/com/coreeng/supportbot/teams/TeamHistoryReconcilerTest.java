package com.coreeng.supportbot.teams;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.teams.fakes.FakeEscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class TeamHistoryReconcilerTest {

    @Mock
    private PlatformTeamsService platformTeamsService;

    @Mock
    private SupportTeamService supportTeamService;

    @Mock
    private TeamHistoryRepository teamHistoryRepository;

    @Test
    void run_persistsCodeAndLabelForEveryKnownTeamSource() {
        EscalationTeamsRegistry escalationTeamsRegistry =
                new FakeEscalationTeamsRegistry(List.of(new EscalationTeam("Escalation One", "esc1", "slack:S1")));
        when(platformTeamsService.listTeams())
                .thenReturn(ImmutableList.of(new PlatformTeam("Platform One", "plat1", Set.of(), Set.of())));
        when(supportTeamService.getTeam())
                .thenReturn(new Team("Support", "support", ImmutableList.of(TeamType.SUPPORT)));
        when(supportTeamService.getLeadershipTeam())
                .thenReturn(new Team("Leadership", "leadership", ImmutableList.of(TeamType.LEADERSHIP)));

        TeamHistoryReconciler reconciler = new TeamHistoryReconciler(
                platformTeamsService, escalationTeamsRegistry, supportTeamService, teamHistoryRepository);

        reconciler.run(new DefaultApplicationArguments());

        verify(teamHistoryRepository).deleteAllExcept(ImmutableList.of("plat1", "esc1", "support", "leadership"));
        verify(teamHistoryRepository)
                .insertOrActivate(ImmutableList.of(
                        new Team("Platform One", "plat1", ImmutableList.of(TeamType.TENANT)),
                        new Team("Escalation One", "esc1", ImmutableList.of(TeamType.ESCALATION)),
                        new Team("Support", "support", ImmutableList.of(TeamType.SUPPORT)),
                        new Team("Leadership", "leadership", ImmutableList.of(TeamType.LEADERSHIP))));
    }
}
