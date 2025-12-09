package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StaticSupportMemberFetcher implements SupportMemberFetcher {
    private final StaticSupportTeamProps supportTeamProps;
    private final StaticLeadershipTeamProps leadershipTeamProps;

    @Override
    public ImmutableList<SupportMember> loadInitialSupportMembers(String groupId) {
        if (supportTeamProps.members() == null) {
            return ImmutableList.of();
        }
        return supportTeamProps.members().stream()
                .map(member -> new SupportMember(member.email(), member.slackId()))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableList<SupportMember> handleSupportMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        log.info("Ignoring membership update for static support team");
        return ImmutableList.of();
    }

    @Override
    public ImmutableList<SupportMember> loadInitialLeadershipMembers(String groupId) {
        if (leadershipTeamProps.members() == null) {
            return ImmutableList.of();
        }
        return leadershipTeamProps.members().stream()
                .map(member -> new SupportMember(member.email(), member.slackId()))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableList<SupportMember> handleLeadershipMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        log.info("Ignoring membership update for static leadership team");
        return ImmutableList.of();
    }
}