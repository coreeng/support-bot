package com.coreeng.supportbot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record Config(
        Mocks mocks,
        List<Tenant> tenants,
        List<User> users,
        List<EscalationTeam> escalationTeams,
        List<Tag> tags,
        List<Impact> impacts,
        SupportBot supportBot
) {
    public List<User> nonSupportUsers() {
        return users.stream()
                .filter(u -> mocks.slack.supportMembers.stream()
                        .noneMatch(sm -> sm.email.equals(u.email)))
                .toList();
    }

    public List<User> supportUsers() {
        return users.stream()
                .filter(u -> mocks.slack.supportMembers.stream()
                        .anyMatch(sm -> sm.email.equals(u.email)))
                .toList();
    }

    @Nullable
    public User userById(@NonNull String userId) {
        return users.stream()
                .filter(u -> u.slackUserId.equals(userId))
                .findAny()
                .orElse(null);
    }

    public record Mocks(
            SlackMock slack,
            KubernetesMock kubernetes,
            AzureMock azure,
            GCPMock gcp
    ) {
    }

    public record SlackMock(
            int port,
            String serverUrl,
            String team,
            String teamId,
            String userId,
            String botId,
            String supportGroupId,
            String supportChannelId,
            List<SlackSupportMember> supportMembers
    ) {
    }

    public record SlackSupportMember(
            String userId,
            String name,
            String email
    ) {
    }

    public record KubernetesMock(
            int port
    ) {
    }

    public record AzureMock(
            int port
    ) {
    }

    public record GCPMock(
            int port
    ) {
    }

    public record Tenant(
            String name,
            String groupRef,
            List<User> users
    ) {
    }

    public record User(
            String email,
            String slackUserId
    ) {
    }

    public record SupportBot(
            String baseUrl,
            String token
    ) {
    }

    public record EscalationTeam(
        String name,
        String slackGroupId
    ) {}
    public record Tag(
        String code,
        String label
    ) {}
    public record Impact(
        String code,
        String label
    ) {}
}
