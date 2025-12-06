package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

@Slf4j
@RequiredArgsConstructor
public class SlackSupportMemberFetcher implements SupportMemberFetcher {
    private final SlackClient slackClient;
    private final ExecutorService executor;

    @Override
    public ImmutableList<SupportMember> loadInitialSupportMembers(String groupId) {
        ImmutableList<String> supportTeamMembers = slackClient.getGroupMembers(groupId);
        return loadMembersFromUserIds(supportTeamMembers);
    }

    @Override
    public ImmutableList<SupportMember> handleSupportMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        return loadMembersFromUserIds(teamUsers);
    }

    @Override
    public ImmutableList<SupportMember> loadInitialLeadershipMembers(String groupId) {
        ImmutableList<String> leadershipMembers = slackClient.getGroupMembers(groupId);
        return loadMembersFromUserIds(leadershipMembers);
    }

    @Override
    public ImmutableList<SupportMember> handleLeadershipMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        return loadMembersFromUserIds(teamUsers);
    }

    private ImmutableList<SupportMember> loadMembersFromUserIds(ImmutableList<String> teamUserIds) {
        ExecutorCompletionService<SupportMember> completionService = new ExecutorCompletionService<>(executor);
        long totalTasks = 0;
        for (String userId : teamUserIds) {
            completionService.submit(() -> new SupportMember(
                    slackClient.getUserById(userId).getEmail(),
                    userId
            ));
            totalTasks += 1;
        }
        ImmutableList.Builder<SupportMember> result = ImmutableList.builder();
        for (int i = 0; i < totalTasks; i++) {
            try {
                SupportMember userInfo = completionService.take().get();
                result.add(userInfo);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return result.build();
    }
}