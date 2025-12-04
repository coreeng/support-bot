package com.coreeng.supportbot.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("support-leadership-team")
public record SupportLeadershipTeamProps(
        Boolean enabled,
        List<String> memberEmails
) {}