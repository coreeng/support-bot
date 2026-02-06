package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
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

            @SuppressWarnings("unchecked")
            var teamsRaw = (List<Map<String, Object>>) claims.get("teams");
            var teams = teamsRaw.stream()
                .map(this::parseTeam)
                .collect(ImmutableList.toImmutableList());

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

    @SuppressWarnings("unchecked")
    private Team parseTeam(Map<String, Object> teamMap) {
        var label = (String) teamMap.get("label");
        var code = (String) teamMap.get("code");
        var typesRaw = (List<String>) teamMap.get("types");
        var types = typesRaw.stream()
            .map(t -> {
                try {
                    return TeamType.valueOf(t);
                } catch (IllegalArgumentException e) {
                    return TeamType.tenant;
                }
            })
            .collect(ImmutableList.toImmutableList());
        return new Team(label, code, types);
    }
}
