package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SupportTeamService {
    private final SupportTeamProps supportTeamProps;
    private final SupportMemberFetcher memberUpdater;
    @Getter
    private ImmutableList<SupportMemberFetcher.SupportMember> members = ImmutableList.of();

    @PostConstruct
    void init() {
        this.members = memberUpdater.loadInitialMembers(supportTeamProps.slackGroupId());
    }

    public Team getTeam() {
        return new Team(
            supportTeamProps.name(),
            supportTeamProps.code(),
            ImmutableList.of(TeamType.support)
        );
    }

    public String getSlackGroupId() {
        return supportTeamProps.slackGroupId();
    }

    public boolean isMemberByUserEmail(String email) {
        return members.stream().anyMatch(member -> member.email().equals(email));
    }

    public boolean isMemberByUserId(String userId) {
        return members.stream().anyMatch(member -> member.slackId().equals(userId));
    }

    public void handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        if (!supportTeamProps.slackGroupId().equals(groupId)) {
            return;
        }
        ImmutableList<SupportMemberFetcher.SupportMember> updatedMembers = memberUpdater.handleMembershipUpdate(groupId, teamUsers);
        if (!updatedMembers.isEmpty()) {
            this.members = updatedMembers;
            log.atInfo()
                .addArgument(updatedMembers::size)
                .log("Updated support team members to {} entries");
        }
    }
}