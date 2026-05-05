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
 * Merges platform teams derived from JWT group claims (default claim: {@code groups}) into
 * email-based team resolution. Only active when {@code jwt-groups.enabled} is true and the
 * configured claim is present in the ID token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGroupTeamMerger {
    private final JwtGroupsProperties properties;
    private final TeamService teamService;

    public ImmutableList<Team> merge(java.util.Map<String, Object> claims, ImmutableList<Team> emailTeams) {
        if (!properties.enabled()) {
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
                if (!anyClaimValueMatches(mapping.matchValues(), jwtGroup)) {
                    continue;
                }
                Team team = teamService.findTeamByCode(mapping.teamCode());
                if (team == null) {
                    log.warn("jwt-groups mapping references unknown team-code {}", mapping.teamCode());
                    continue;
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
