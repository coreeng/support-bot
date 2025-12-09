package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;

public interface SupportMemberFetcher {
    ImmutableList<SupportMember> loadInitialSupportMembers(String groupId);
    ImmutableList<SupportMember> handleSupportMembershipUpdate(String groupId, ImmutableList<String> teamUsers);
    ImmutableList<SupportMember> loadInitialLeadershipMembers(String groupId);
    ImmutableList<SupportMember> handleLeadershipMembershipUpdate(String groupId, ImmutableList<String> teamUsers);
    record SupportMember(String email, String slackId) {}
}