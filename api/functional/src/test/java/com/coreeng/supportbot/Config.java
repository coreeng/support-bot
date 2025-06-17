package com.coreeng.supportbot;

import java.util.List;

public record Config(
    Mocks mocks,
    List<Tenant> tenants
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
        String groupRef
    ) {}
}
