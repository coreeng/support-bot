package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Merges platform teams derived from Dex {@code groups} (or configured claim) into email-based team
 * resolution. LDAP-backed Dex logins expose groups; Google/Azure via Dex typically do not, so behavior
 * stays email + direct IdP group fetch only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGroupTeamMerger {
    private static final String DEX_REGISTRATION = "dex";

    private final JwtGroupsProperties properties;
    private final TeamService teamService;

    public ImmutableList<Team> mergeForProvider(
            String oauthRegistrationId, java.util.Map<String, Object> claims, ImmutableList<Team> emailTeams) {
        if (!properties.enabled() || oauthRegistrationId == null || !DEX_REGISTRATION.equalsIgnoreCase(oauthRegistrationId)) {
            return emailTeams;
        }
        var rawGroups = extractGroupStrings(claims.get(properties.claimName()));
        if (rawGroups.isEmpty()) {
            return emailTeams;
        }

        Set<String> seenCodes = new LinkedHashSet<>();
        for (Team t : emailTeams) {
            seenCodes.add(t.code());
        }

        ImmutableList.Builder<Team> out = ImmutableList.builder();
        out.addAll(emailTeams);

        for (String jwtGroup : rawGroups) {
            for (JwtGroupsProperties.Mapping mapping : properties.mappings()) {
                if (mapping.teamCode() == null || mapping.teamCode().isBlank()) {
                    continue;
                }
                if (!anyClaimValueMatches(mapping.claimValues(), jwtGroup)) {
                    continue;
                }
                Team team = teamService.findTeamByCode(mapping.teamCode());
                if (team == null) {
                    log.warn("jwt-groups mapping references unknown team-code {}", mapping.teamCode());
                    break;
                }
                if (seenCodes.add(team.code())) {
                    out.add(team);
                }
                break;
            }
        }

        return out.build();
    }

    private static boolean anyClaimValueMatches(List<String> claimValues, String jwtGroup) {
        for (String expected : claimValues) {
            if (expected == null || expected.isBlank()) {
                continue;
            }
            if (jwtGroup.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractGroupStrings(@Nullable Object claim) {
        if (claim == null) {
            return List.of();
        }
        if (claim instanceof String s) {
            return s.isBlank() ? List.of() : List.of(s);
        }
        if (claim instanceof Collection<?> c) {
            return c.stream()
                    .filter(o -> o != null && !o.toString().isBlank())
                    .map(Object::toString)
                    .toList();
        }
        if (claim instanceof String[] arr) {
            return java.util.Arrays.stream(arr)
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        }
        return List.of();
    }
}
