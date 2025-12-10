package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

@Slf4j
@RequiredArgsConstructor
public class GenericSlackMemberFetcher implements TeamMemberFetcher {
    private final SlackClient slackClient;
    private final ExecutorService executor;

    @Override
    public ImmutableList<TeamMember> loadInitialMembers(SlackId.Group groupId) {
        ImmutableList<String> teamMembers = slackClient.getGroupMembers(groupId);
        return loadMembersFromUserIds(teamMembers.stream()
                .map(SlackId::user)
                .collect(ImmutableList.toImmutableList()));
    }

    @Override
    public ImmutableList<TeamMember> handleMembershipUpdate(SlackId.Group groupId, ImmutableList<SlackId.User> teamUsers) {
        return loadMembersFromUserIds(teamUsers);
    }

    private ImmutableList<TeamMember> loadMembersFromUserIds(ImmutableList<SlackId.User> teamUserIds) {
        ExecutorCompletionService<TeamMember> completionService = new ExecutorCompletionService<>(executor);
        long totalTasks = 0;
        for (SlackId.User userId : teamUserIds) {
            completionService.submit(() -> new TeamMember(
                    slackClient.getUserById(userId).getProfile().getEmail(),
                    userId
            ));
            totalTasks += 1;
        }
        ImmutableList.Builder<TeamMember> result = ImmutableList.builder();
        for (int i = 0; i < totalTasks; i++) {
            try {
                TeamMember userInfo = completionService.take().get();
                result.add(userInfo);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return result.build();
    }
}


