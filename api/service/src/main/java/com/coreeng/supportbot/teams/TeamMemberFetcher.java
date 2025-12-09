package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;

public interface TeamMemberFetcher {
    ImmutableList<TeamMember> loadInitialMembers(String groupId);
    ImmutableList<TeamMember> handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers);
    
    record TeamMember(String email, String slackId) {}
}


