package com.coreeng.supportbot.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps Dex (LDAP) ID-token group claims to Support Bot platform {@linkplain com.coreeng.supportbot.teams.Team}
 * codes. Only applied for the {@code dex} OAuth client — Google/Azure membership still comes from
 * {@code platform-integration} fetchers (static, Azure AD, GCP).
 */
@ConfigurationProperties(prefix = "platform-integration.jwt-groups")
public record JwtGroupsProperties(boolean enabled, String claimName, List<Mapping> mappings) {
    public JwtGroupsProperties {
        if (claimName == null || claimName.isBlank()) {
            claimName = "groups";
        }
        if (mappings == null) {
            mappings = List.of();
        }
    }

    public record Mapping(List<String> claimValues, String teamCode) {
        public Mapping {
            if (claimValues == null) {
                claimValues = List.of();
            }
        }
    }
}
