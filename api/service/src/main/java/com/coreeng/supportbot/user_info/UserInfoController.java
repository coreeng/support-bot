package com.coreeng.supportbot.user_info;

import com.coreeng.supportbot.config.SupportLeadershipTeamProps;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.teams.PlatformUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user-info")
public class UserInfoController {
    private final PlatformTeamsService platformTeamsService;

    public UserInfoController(PlatformTeamsService platformTeamsService, SupportLeadershipTeamProps supportLeadershipTeamProps) {
        this.platformTeamsService = platformTeamsService;
    }

    @GetMapping
    public ResponseEntity<UserInfoResponse> getUserInfo(@RequestParam String email) {
        PlatformUser user = platformTeamsService.findUserByEmail(email);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        List<TeamDto> teamDtos = user.teams().stream()
                .map(team -> new TeamDto(
                        team.name(),
                        team.groupRefs()
                ))
                .collect(Collectors.toList());

        UserInfoResponse response = new UserInfoResponse(user.email(), teamDtos);
        return ResponseEntity.ok(response);

    }
}
