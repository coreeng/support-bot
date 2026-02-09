package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class JwtService {
    private final SecretKey secretKey;
    private final SecurityProperties properties;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        var jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.warn("JWT_SECRET environment variable is not set. Using insecure default. Set JWT_SECRET in production.");
        }
        this.secretKey = Keys.hmacShaKeyFor(
            properties.jwt().secret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(UserPrincipal principal) {
        var now = Instant.now();
        var expiration = now.plus(properties.jwt().expiration());

        var teamsJson = principal.teams().stream()
            .map(team -> Map.of(
                "label", team.label(),
                "code", team.code(),
                "types", team.types().stream().map(Enum::name).toList()
            ))
            .toList();

        return Jwts.builder()
            .subject(principal.email())
            .claim("email", principal.email())
            .claim("name", principal.name())
            .claim("teams", teamsJson)
            .claim("isLeadership", principal.isLeadership())
            .claim("isSupportEngineer", principal.isSupportEngineer())
            .claim("isEscalation", principal.isEscalation())
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
            var isLeadership = claims.get("isLeadership", Boolean.class);
            var isSupportEngineer = claims.get("isSupportEngineer", Boolean.class);
            var isEscalation = claims.get("isEscalation", Boolean.class);

            var teams = parseTeamsClaim(claims.get("teams"));

            return Optional.of(new UserPrincipal(
                email,
                name,
                teams,
                Boolean.TRUE.equals(isLeadership),
                Boolean.TRUE.equals(isSupportEngineer),
                Boolean.TRUE.equals(isEscalation)
            ));
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

    private Team parseTeam(Map<?, ?> teamMap) {
        var label = parseStringValue(teamMap.get("label"), "unknown");
        var code = parseStringValue(teamMap.get("code"), label);
        var types = parseTeamTypes(teamMap.get("types"));
        return new Team(label, code, types);
    }

    private ImmutableList<TeamType> parseTeamTypes(@Nullable Object typesClaim) {
        if (!(typesClaim instanceof List<?> typesRaw)) {
            return ImmutableList.of(TeamType.tenant);
        }

        var types = ImmutableList.<TeamType>builder();
        for (Object typeRaw : typesRaw) {
            types.add(parseTeamType(typeRaw));
        }

        var parsedTypes = types.build();
        return parsedTypes.isEmpty() ? ImmutableList.of(TeamType.tenant) : parsedTypes;
    }

    private TeamType parseTeamType(@Nullable Object typeRaw) {
        if (typeRaw == null) {
            return TeamType.tenant;
        }
        try {
            return TeamType.valueOf(typeRaw.toString());
        } catch (IllegalArgumentException e) {
            return TeamType.tenant;
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
