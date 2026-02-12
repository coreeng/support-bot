package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {
    private final SecretKey secretKey;
    private final SecurityProperties properties;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserPrincipal principal) {
        var now = Instant.now();
        var expiration = now.plus(properties.jwt().expiration());

        var teamsJson = principal.teams().stream()
                .map(team -> Map.of(
                        "code", team.code(),
                        "types", team.types().stream().map(Enum::name).toList()))
                .toList();

        var roles = principal.roles().stream().map(Enum::name).toList();

        return Jwts.builder()
                .subject(principal.email())
                .claim("email", principal.email())
                .claim("name", principal.name())
                .claim("teams", teamsJson)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Optional<UserPrincipal> validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            var email = claims.getSubject();
            var name = claims.get("name", String.class);
            var teams = parseTeamsClaim(claims.get("teams"));
            var parsedRoles = parseRoles(claims.get("roles"));

            return Optional.of(new UserPrincipal(email, name, teams, parsedRoles));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private ImmutableList<Team> parseTeamsClaim(@Nullable Object teamsClaim) {
        if (!(teamsClaim instanceof List<?> teamsRaw)) {
            return ImmutableList.of();
        }

        var teams = ImmutableList.<Team>builder();
        for (Object teamRaw : teamsRaw) {
            if (teamRaw instanceof Map<?, ?> teamMap) {
                teams.add(parseTeam(teamMap));
            }
        }
        return teams.build();
    }

    private ImmutableList<Role> parseRoles(@Nullable Object rolesClaim) {
        if (!(rolesClaim instanceof List<?> rolesRaw)) {
            return ImmutableList.of(Role.USER);
        }

        var builder = ImmutableList.<Role>builder();
        for (Object roleRaw : rolesRaw) {
            if (roleRaw != null) {
                try {
                    builder.add(Role.valueOf(roleRaw.toString()));
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping unknown role in JWT: {}", roleRaw);
                }
            }
        }

        var parsed = builder.build();
        return parsed.isEmpty() ? ImmutableList.of(Role.USER) : parsed;
    }

    private Team parseTeam(Map<?, ?> teamMap) {
        var code = parseStringValue(teamMap.get("code"), "unknown");
        var types = parseTeamTypes(teamMap.get("types"));
        return new Team(code, code, types);
    }

    private ImmutableList<TeamType> parseTeamTypes(@Nullable Object typesClaim) {
        if (!(typesClaim instanceof List<?> typesRaw)) {
            return ImmutableList.of(TeamType.TENANT);
        }

        var types = ImmutableList.<TeamType>builder();
        for (Object typeRaw : typesRaw) {
            types.add(parseTeamType(typeRaw));
        }

        var parsedTypes = types.build();
        return parsedTypes.isEmpty() ? ImmutableList.of(TeamType.TENANT) : parsedTypes;
    }

    private TeamType parseTeamType(@Nullable Object typeRaw) {
        if (typeRaw == null) {
            return TeamType.TENANT;
        }
        try {
            return TeamType.valueOf(typeRaw.toString());
        } catch (IllegalArgumentException e) {
            return TeamType.TENANT;
        }
    }

    private String parseStringValue(@Nullable Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String parsedValue = value.toString();
        return parsedValue.isBlank() ? fallback : parsedValue;
    }
}
