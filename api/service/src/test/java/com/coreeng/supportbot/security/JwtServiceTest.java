package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String testSecret = "test-jwt-secret-for-unit-tests-minimum-256-bits";

    private final JwtService service = createService(Duration.ofHours(24));

    private static JwtService createService(Duration expiration) {
        return new JwtService(new SecurityProperties(
            new SecurityProperties.JwtProperties(testSecret, expiration),
            new SecurityProperties.OAuth2Properties("http://localhost:3000/auth/callback"),
            new SecurityProperties.CorsProperties("http://localhost:3000"),
            new SecurityProperties.TestBypassProperties(false)
        ));
    }

    @Test
    void generateToken_validJwt() {
        // given
        var principal = new UserPrincipal(
            "user@example.com", "Test User",
            ImmutableList.of(new Team("Test Tenant", "test-tenant", ImmutableList.of(TeamType.tenant))),
            ImmutableList.of(Role.user)
        );

        // when
        var token = service.generateToken(principal);
        var result = service.validateToken(token);

        // then
        assertTrue(result.isPresent());
        assertEquals("user@example.com", result.get().email());
        assertEquals("Test User", result.get().name());
    }

    @Test
    void generateToken_tenantRole() {
        // given
        var principal = new UserPrincipal(
            "user@example.com", "Test User",
            ImmutableList.of(new Team("Test Tenant", "test-tenant", ImmutableList.of(TeamType.tenant))),
            ImmutableList.of(Role.user)
        );

        // when
        var result = service.validateToken(service.generateToken(principal));

        // then
        assertTrue(result.isPresent());
        assertFalse(result.get().isLeadership());
        assertFalse(result.get().isSupportEngineer());
        assertFalse(result.get().isEscalation());
        assertEquals("test-tenant", result.get().teams().get(0).code());
        assertEquals(ImmutableList.of(TeamType.tenant), result.get().teams().get(0).types());
    }

    @Test
    void generateToken_supportRole() {
        // given
        var principal = new UserPrincipal(
            "support@example.com", "Support User",
            ImmutableList.of(new Team("Core Support", "core-support", ImmutableList.of(TeamType.support))),
            ImmutableList.of(Role.user, Role.supportEngineer)
        );

        // when
        var result = service.validateToken(service.generateToken(principal));

        // then
        assertTrue(result.isPresent());
        assertTrue(result.get().isSupportEngineer());
        assertFalse(result.get().isLeadership());
    }

    @Test
    void generateToken_leadershipRole() {
        // given
        var principal = new UserPrincipal(
            "lead@example.com", "Lead User",
            ImmutableList.of(new Team("Leadership", "leadership", ImmutableList.of(TeamType.leadership))),
            ImmutableList.of(Role.user, Role.leadership, Role.supportEngineer)
        );

        // when
        var result = service.validateToken(service.generateToken(principal));

        // then
        assertTrue(result.isPresent());
        assertTrue(result.get().isLeadership());
        assertTrue(result.get().isSupportEngineer());
    }

    @Test
    void validateToken_invalidToken_returnsEmpty() {
        // when
        var result = service.validateToken("not-a-valid-jwt");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void validateToken_tamperedToken_returnsEmpty() {
        // given
        var principal = new UserPrincipal(
            "user@example.com", "Test User",
            ImmutableList.of(), ImmutableList.of(Role.user)
        );
        var token = service.generateToken(principal);

        // when
        var result = service.validateToken(token + "tampered");

        // then
        assertTrue(result.isEmpty());
    }
}
