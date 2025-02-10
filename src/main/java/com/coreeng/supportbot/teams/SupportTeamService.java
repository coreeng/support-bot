package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTeamService {
    private final SlackClient slackClient;
    private final ExecutorService executor;
    private final SupportTeamProps supportTeamProps;

    private ImmutableMap<String, String> emailToUserSlackId;

    @PostConstruct
    void init() {
        ImmutableList<String> supportTeamMembers = slackClient.getGroupMembers(supportTeamProps.slackGroupId());
        emailToUserSlackId = reloadUsersInfo(supportTeamMembers);
    }

    public void handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        if (supportTeamProps.slackGroupId().equals(groupId)) {
            emailToUserSlackId = reloadUsersInfo(teamUsers);
        }
    }

    public Team getTeam() {
        return new Team(
            supportTeamProps.name(),
            ImmutableList.of(TeamType.support)
        );

    }

    public boolean isMemberBeUserEmail(String email) {
        return emailToUserSlackId.containsKey(email);
    }

    private ImmutableMap<String, String> reloadUsersInfo(ImmutableList<String> teamUserIds) {
        record EmailAndSlackId(String email, String slackId) {
        }
        ExecutorCompletionService<EmailAndSlackId> completionService = new ExecutorCompletionService<>(executor);
        long totalTasks = 0;
        for (String userId : teamUserIds) {
            completionService.submit(() -> new EmailAndSlackId(
                slackClient.getUserById(userId).getEmail(),
                userId
            ));
            totalTasks += 1;
        }
        ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
        for (int i = 0; i < totalTasks; i++) {
            try {
                EmailAndSlackId userInfo = completionService.take().get();
                result.put(userInfo.email(), userInfo.slackId());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return result.build();
    }
}
