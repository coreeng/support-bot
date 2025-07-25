package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;

public interface SupportMemberFetcher {
    ImmutableList<SupportMember> loadInitialMembers(String groupId);
    ImmutableList<SupportMember> handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers);

    record SupportMember(String email, String slackId) {}
}