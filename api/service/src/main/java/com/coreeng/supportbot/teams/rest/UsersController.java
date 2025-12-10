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

import java.util.HashSet;

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
        ImmutableList<Team> teams = teamService.listTeamsByUserEmail(email);

        PlatformUser user = platformTeamsService.findUserByEmail(email);
        if (user == null && teams.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // If the user is not a member of any platform teams, but is, for example, a support member,
        // return a minimal PlatformUser so the caller still gets their team memberships.
        if (user == null) {
            user = new PlatformUser(email.toLowerCase(), new HashSet<>());
        }
        return ResponseEntity.ok(mapper.mapToUI(user, teams));
    }
}

