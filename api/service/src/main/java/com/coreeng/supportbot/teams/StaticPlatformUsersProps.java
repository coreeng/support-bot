package com.coreeng.supportbot.teams;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform-integration.static-user")
public record StaticPlatformUsersProps(boolean enabled, Map<String, List<String>> users) {}
