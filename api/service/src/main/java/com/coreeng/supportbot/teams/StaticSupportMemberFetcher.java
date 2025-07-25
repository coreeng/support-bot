package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StaticSupportMemberFetcher implements SupportMemberFetcher {
    private final StaticSupportTeamProps staticProps;

    @Override
    public ImmutableList<SupportMemberFetcher.SupportMember> loadInitialMembers(String groupId) {
        return staticProps.members().stream()
            .map(staticMember -> new SupportMemberFetcher.SupportMember(staticMember.email(), staticMember.slackId()))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableList<SupportMemberFetcher.SupportMember> handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        log.info("Ignoring membership update for static support team service");
        return ImmutableList.of(); // Empty list means ignore this update
    }
}