package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
public class UserInfoController {
    private final PlatformTeamsService platformTeamsService;
    private final SupportLeadershipTeamProps leaderShipTeamProps;
    private final SupportTeamService supportTeamService;


    public UserInfoController(PlatformTeamsService platformTeamsService, SupportLeadershipTeamProps leaderShipTeamProps, SupportTeamService supportTeamService) {
        this.platformTeamsService = platformTeamsService;
        this.leaderShipTeamProps = leaderShipTeamProps;
        this.supportTeamService = supportTeamService;
    }

    @GetMapping
    public ResponseEntity<UserInfoResponse> getUserInfo(@RequestParam String email) {
        PlatformUser user = platformTeamsService.findUserByEmail(email);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isLeadership = false;
        if (leaderShipTeamProps.enabled() && leaderShipTeamProps.memberEmails() != null) {
            isLeadership = leaderShipTeamProps.memberEmails().stream().anyMatch(email::equalsIgnoreCase);
        }

        boolean isSupportEngineer = supportTeamService.members().stream().map(SupportMemberFetcher.SupportMember::email)
                .anyMatch(email::equalsIgnoreCase);

        List<TeamDto> teamDtos = user.teams().stream()
                .map(team -> new TeamDto(
                        team.name(),
                        team.groupRefs()
                ))
                .collect(Collectors.toList());

        UserInfoResponse response = new UserInfoResponse(user.email(), teamDtos, isLeadership, isSupportEngineer);
        return ResponseEntity.ok(response);
    }
}
