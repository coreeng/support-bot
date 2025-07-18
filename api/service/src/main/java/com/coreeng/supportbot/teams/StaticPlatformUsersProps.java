package com.coreeng.supportbot.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties("platform-integration.static-user")
public record StaticPlatformUsersProps(
    boolean enabled,
    Map<String, List<String>> users
) {
}