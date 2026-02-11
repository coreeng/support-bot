package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.SlackId;
import com.google.common.collect.ImmutableList;

public interface TeamMemberFetcher {
    ImmutableList<TeamMember> loadInitialMembers(SlackId.Group groupId);

    ImmutableList<TeamMember> handleMembershipUpdate(SlackId.Group groupId, ImmutableList<SlackId.User> teamUsers);

    record TeamMember(String email, SlackId.User slackId) {}
}
