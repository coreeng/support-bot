package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.config.SupportLeadershipTeamProps;
import com.coreeng.supportbot.teams.*;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

@RestController
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamsController {
    private final TeamService teamService;
    private final TeamUIMapper mapper;
    private final SupportLeadershipTeamProps leadershipTeamProps;
    private final SupportTeamService supportTeamService;

    @GetMapping
    public ImmutableList<TeamUI> listTeams(
            @RequestParam(name = "type", required = false) TeamType type
    ) {
        ImmutableList<Team> teams;
        if (type == null) {
            teams = teamService.listTeams();
        } else {
            teams = teamService.listTeamsByType(type);
        }
        return teams.stream()
                .map(mapper::mapToUI)
                .collect(toImmutableList());
    }

    @GetMapping("/leadership/members")
    public List<String> getLeadershipMembers() {
        return leadershipTeamProps.memberEmails();
    }

    @GetMapping("/support/members")
    public List<String> getSupportMembers() {
        return supportTeamService.members().stream().map(SupportMemberFetcher.SupportMember::email).collect(toImmutableList());
    }
}
