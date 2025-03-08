package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.teams.PlatformUser;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UsersController {
    private final PlatformTeamsService platformTeamsService;
    private final TeamService teamService;
    private final TeamUIMapper mapper;

    @GetMapping("/user")
    public ResponseEntity<UserUI> findByEmail(
        @RequestParam String email
    ) {
        PlatformUser user = platformTeamsService.findUserByEmail(email);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        ImmutableList<Team> teams = teamService.listTeamsByUserEmail(email);
        return ResponseEntity.ok(mapper.mapToUI(user, teams));
    }
}
