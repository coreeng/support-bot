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
    public ImmutableList<SupportMemberFetcher.SupportMember> loadInitialMembers(String groupId) {
        ImmutableList<String> supportTeamMembers = slackClient.getGroupMembers(groupId);
        return loadMembersFromUserIds(supportTeamMembers);
    }

    @Override
    public ImmutableList<SupportMemberFetcher.SupportMember> handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        return loadMembersFromUserIds(teamUsers);
    }

    private ImmutableList<SupportMemberFetcher.SupportMember> loadMembersFromUserIds(ImmutableList<String> teamUserIds) {
        ExecutorCompletionService<SupportMemberFetcher.SupportMember> completionService = new ExecutorCompletionService<>(executor);
        long totalTasks = 0;
        for (String userId : teamUserIds) {
            completionService.submit(() -> new SupportMemberFetcher.SupportMember(
                slackClient.getUserById(userId).getEmail(),
                userId
            ));
            totalTasks += 1;
        }
        ImmutableList.Builder<SupportMemberFetcher.SupportMember> result = ImmutableList.builder();
        for (int i = 0; i < totalTasks; i++) {
            try {
                SupportMemberFetcher.SupportMember userInfo = completionService.take().get();
                result.add(userInfo);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return result.build();
    }
}