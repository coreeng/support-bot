package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.collect.ImmutableList;
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

    private ImmutableList<SupportMember> members;

    @PostConstruct
    void init() {
        ImmutableList<String> supportTeamMembers = slackClient.getGroupMembers(supportTeamProps.slackGroupId());
        members = reloadUsersInfo(supportTeamMembers);
    }

    public void handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        if (supportTeamProps.slackGroupId().equals(groupId)) {
            members = reloadUsersInfo(teamUsers);
            log.atInfo()
                .addArgument(members::size)
                .log("Updated support team members to {} entries");
        }
    }

    public Team getTeam() {
        return new Team(
            supportTeamProps.name(),
            ImmutableList.of(TeamType.support)
        );

    }

    public String getSlackGroupId() {
        return  supportTeamProps.slackGroupId();
    }

    public boolean isMemberByUserEmail(String email) {
        return members.stream().anyMatch(member -> member.email().equals(email));
    }

    public boolean isMemberByUserId(String userId) {
        return members.stream().anyMatch(member -> member.slackId().equals(userId));
    }

    private ImmutableList<SupportMember> reloadUsersInfo(ImmutableList<String> teamUserIds) {
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

    private record SupportMember(String email, String slackId) {
    }
}
