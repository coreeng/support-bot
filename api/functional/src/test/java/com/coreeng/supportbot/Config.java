package com.coreeng.supportbot;

import java.util.List;

public record Config(
    Mocks mocks,
    List<Tenant> tenants,
    List<User> users,
    SupportBot supportBot
) {
    public record Mocks(
        SlackMock slack,
        KubernetesMock kubernetes,
        AzureMock azure,
        GCPMock gcp
    ) {}

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
    ) {}
    public record SlackSupportMember(
        String userId,
        String name,
        String email
    ) {}

    public record KubernetesMock(
        int port
    ) {}

    public record AzureMock(
        int port
    ) {}
    public record GCPMock(
        int port
    ) {}

    public record Tenant(
        String name,
        String groupRef,
        List<User> users
    ) {}
    public record User(
        String email,
        String slackUserId
    ) {}

    public record SupportBot(
        String baseUrl,
        String token
    ) {}

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
}
