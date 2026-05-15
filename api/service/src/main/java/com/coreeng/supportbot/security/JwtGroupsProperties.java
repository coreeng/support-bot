package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Maps Dex (LDAP) ID-token group claims to Support Bot platform {@linkplain com.coreeng.supportbot.teams.Team}
 * codes. Only applied for the {@code dex} OAuth client — Google/Azure membership still comes from
 * {@code platform-integration} fetchers (static, Azure AD, GCP).
 *
 * <p>Each mapping declares {@code group-ref} as a typed JWT reference, e.g. {@code "jwt:developers"}.
 */
@ConfigurationProperties(prefix = "platform-integration.jwt-groups")
public record JwtGroupsProperties(boolean enabled, String claimName, List<Mapping> mappings) {

    public JwtGroupsProperties(boolean enabled, @Nullable String claimName, @Nullable List<Mapping> mappings) {
        this.enabled = enabled;
        this.claimName = (claimName == null || claimName.isBlank()) ? "groups" : claimName;
        this.mappings = mappings == null ? List.of() : mappings;
    }

    public record Mapping(
            GroupRef groupRef,
            String teamCode,
            @Nullable @Deprecated List<String> claimValues) {

        private static final Logger LOGGER = LoggerFactory.getLogger(Mapping.class);

        @ConstructorBinding
        public Mapping(@Nullable GroupRef groupRef, String teamCode, @Nullable List<String> claimValues) {
            if (groupRef == null && claimValues != null && !claimValues.isEmpty()) {
                String first = claimValues.get(0);
                if (first == null || first.isBlank()) {
                    throw new IllegalArgumentException(
                            "jwt-groups mapping for team-code '" + teamCode
                                    + "' has blank legacy 'claim-values' entry; specify 'group-ref' instead (PT-351 migration).");
                }
                LOGGER.warn(
                        "'platform-integration.jwt-groups.mappings[team-code={}].claim-values' is deprecated;"
                                + " use 'group-ref: jwt:{}'. Legacy key will be removed in a future release"
                                + " (PT-351 migration).",
                        teamCode,
                        first);
                if (claimValues.size() > 1) {
                    LOGGER.warn(
                            "jwt-groups mapping for team-code '{}' has {} legacy 'claim-values' entries"
                                    + " — only the first ('{}') is honoured. Split into one mapping per value"
                                    + " using typed 'group-ref' (PT-351 migration).",
                            teamCode,
                            claimValues.size(),
                            first);
                }
                groupRef = new GroupRef.Jwt(first);
            } else if (groupRef != null && claimValues != null && !claimValues.isEmpty()) {
                LOGGER.warn(
                        "jwt-groups mapping for team-code '{}' has both 'group-ref' and deprecated 'claim-values'"
                                + " set; 'group-ref' takes precedence. Remove 'claim-values' (PT-351 migration).",
                        teamCode);
            }
            if (groupRef == null) {
                throw new IllegalArgumentException(
                        "jwt-groups mapping for team-code '" + teamCode + "' must specify 'group-ref'");
            }
            if (!(groupRef instanceof GroupRef.Jwt)) {
                throw new IllegalArgumentException("jwt-groups mapping for team-code '" + teamCode
                        + "' must use a 'jwt:' prefixed group-ref; got " + groupRef.canonical());
            }
            this.groupRef = groupRef;
            this.teamCode = teamCode;
            this.claimValues = claimValues;
        }

        public Mapping(GroupRef groupRef, String teamCode) {
            this(groupRef, teamCode, null);
        }
    }
}
