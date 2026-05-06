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
 * <p>Each mapping declares either {@code group-ref} (typed, e.g. {@code "jwt:developers"}) or the
 * legacy {@code claim-values} list. Both forms are accepted for one release; setting both on the
 * same mapping is rejected at startup.
 */
@ConfigurationProperties(prefix = "platform-integration.jwt-groups")
public record JwtGroupsProperties(boolean enabled, String claimName, List<Mapping> mappings) {
    private static final Logger LOG = LoggerFactory.getLogger(JwtGroupsProperties.class);

    public JwtGroupsProperties {
        if (claimName == null || claimName.isBlank()) {
            claimName = "groups";
        }
        if (mappings == null) {
            mappings = List.of();
        }
        for (Mapping m : mappings) {
            if (m.usingDeprecatedClaimValues()) {
                LOG.warn(
                        "platform-integration.jwt-groups.mappings[team-code={}] uses deprecated 'claim-values'; "
                                + "switch to 'group-ref: \"jwt:<value>\"'",
                        m.teamCode());
            }
        }
    }

    public record Mapping(@Nullable GroupRef groupRef, String teamCode, List<String> claimValues) {
        @ConstructorBinding
        public Mapping {
            if (claimValues == null) {
                claimValues = List.of();
            }
            if (groupRef == null && claimValues.isEmpty()) {
                throw new IllegalArgumentException("jwt-groups mapping for team-code '" + teamCode
                        + "' must specify either 'group-ref' or 'claim-values'");
            }
            if (groupRef != null && !claimValues.isEmpty()) {
                throw new IllegalArgumentException("jwt-groups mapping for team-code '" + teamCode
                        + "' has both 'group-ref' and (deprecated) 'claim-values' set — choose one");
            }
            if (groupRef != null && !(groupRef instanceof GroupRef.Jwt)) {
                throw new IllegalArgumentException("jwt-groups mapping for team-code '" + teamCode
                        + "' must use a 'jwt:' prefixed group-ref; got " + groupRef.canonical());
            }
        }

        public Mapping(List<String> claimValues, String teamCode) {
            this(null, teamCode, claimValues);
        }

        /** Values to match against incoming JWT claim values (case-insensitive). */
        public List<String> matchValues() {
            if (groupRef != null) {
                return List.of(groupRef.value());
            }
            return claimValues;
        }

        public boolean usingDeprecatedClaimValues() {
            return groupRef == null;
        }
    }
}
