package com.coreeng.supportbot.teams;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(150)
@RequiredArgsConstructor
public class TeamHistoryReconciler implements ApplicationRunner {
    private final PlatformTeamsService platformTeamsService;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final SupportTeamService supportTeamService;
    private final TeamHistoryRepository teamHistoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Team> byCode = new LinkedHashMap<>();
        for (PlatformTeam t : platformTeamsService.listTeams()) {
            byCode.put(t.code(), new Team(t.name(), t.code(), ImmutableList.of(TeamType.TENANT)));
        }
        for (EscalationTeam t : escalationTeamsRegistry.listAllEscalationTeams()) {
            byCode.put(t.code(), new Team(t.label(), t.code(), ImmutableList.of(TeamType.ESCALATION)));
        }
        Team support = supportTeamService.getTeam();
        byCode.put(support.code(), support);
        Team leadership = supportTeamService.getLeadershipTeam();
        byCode.put(leadership.code(), leadership);

        ImmutableList<Team> teams = ImmutableList.copyOf(byCode.values());
        teamHistoryRepository.deleteAllExcept(teams.stream().map(Team::code).collect(toImmutableList()));
        teamHistoryRepository.insertOrActivate(teams);
    }
}
