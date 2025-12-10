package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.slack.SlackId;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SupportTeamService {
    private final SupportTeamProps supportTeamProps;
    private final SupportLeadershipTeamProps leadershipTeamProps;
    private final TeamMemberFetcher supportTeamFetcher;
    private final TeamMemberFetcher leadershipTeamFetcher;

    @Getter
    private ImmutableList<TeamMemberFetcher.TeamMember> members = ImmutableList.of();
    @Getter
    private ImmutableList<TeamMemberFetcher.TeamMember> leadershipMembers = ImmutableList.of();

    @PostConstruct
    void init() {
        this.members = supportTeamFetcher.loadInitialMembers(SlackId.group(supportTeamProps.slackGroupId()));
        this.leadershipMembers = leadershipTeamFetcher.loadInitialMembers(SlackId.group(leadershipTeamProps.slackGroupId()));
    }

    public Team getTeam() {
        return new Team(
                supportTeamProps.name(),
                supportTeamProps.code(),
                ImmutableList.of(TeamType.support)
        );
    }

    public Team getLeadershipTeam() {
        return new Team(
                leadershipTeamProps.name(),
                leadershipTeamProps.code(),
                ImmutableList.of(TeamType.leadership)
        );
    }

    public boolean isMemberByUserEmail(String email) {
        return members.stream().anyMatch(member -> member.email().equalsIgnoreCase(email));
    }

    public boolean isMemberByUserId(SlackId.User userId) {
        return members.stream().anyMatch(member -> member.slackId().equals(userId));
    }

    public boolean isLeadershipMemberByUserEmail(String email) {
        return leadershipMembers.stream().anyMatch(member -> member.email().equalsIgnoreCase(email));
    }

    public void handleMembershipUpdate(SlackId.Group groupId, ImmutableList<SlackId.User> teamUsers) {
        if (supportTeamProps.slackGroupId().equals(groupId.id())) {
            ImmutableList<TeamMemberFetcher.TeamMember> updatedMembers =
                    supportTeamFetcher.handleMembershipUpdate(groupId, teamUsers);
            if (!updatedMembers.isEmpty()) {
                this.members = updatedMembers;
                log.atInfo()
                        .addArgument(updatedMembers::size)
                        .log("Updated support team members to {} entries");
            }
        } else if (leadershipTeamProps.slackGroupId().equals(groupId.id())) {
            ImmutableList<TeamMemberFetcher.TeamMember> updatedMembers =
                    leadershipTeamFetcher.handleMembershipUpdate(groupId, teamUsers);
            if (!updatedMembers.isEmpty()) {
                this.leadershipMembers = updatedMembers;
                log.atInfo()
                        .addArgument(updatedMembers::size)
                        .log("Updated leadership team members to {} entries");
            }
        }
    }
}