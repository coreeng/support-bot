package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import org.jspecify.annotations.Nullable;
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

    public record Mapping(GroupRef groupRef, String teamCode) {
        @ConstructorBinding
        public Mapping(@Nullable GroupRef groupRef, String teamCode) {
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
        }
    }
}
