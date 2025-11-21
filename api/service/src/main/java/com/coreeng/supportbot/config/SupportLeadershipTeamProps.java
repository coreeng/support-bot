package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("support-leadership-team")
public record SupportLeadershipTeamProps(
        String name,
        String code,
        Boolean enabled,
        List<String> memberEmails
) {}