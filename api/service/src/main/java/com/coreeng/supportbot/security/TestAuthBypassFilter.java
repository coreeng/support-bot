package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class TestAuthBypassFilter extends OncePerRequestFilter {
    private static final String TEST_USER_HEADER = "X-Test-User";
    private static final String TEST_ROLE_HEADER = "X-Test-Role";

    private final SecurityProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.testBypass().enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        var testUser = request.getHeader(TEST_USER_HEADER);
        if (testUser != null && !testUser.isBlank()) {
            var testRole = request.getHeader(TEST_ROLE_HEADER);
            var principal = createTestPrincipal(testUser, testRole);
            var authentication = new JwtAuthenticationToken(principal, "test-token");
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Test auth bypass: authenticated as {}", testUser);
        }

        filterChain.doFilter(request, response);
    }

    private UserPrincipal createTestPrincipal(String email, String role) {
        var isLeadership = "leadership".equalsIgnoreCase(role);
        var isSupportEngineer = "support".equalsIgnoreCase(role) || isLeadership;
        var isEscalation = "escalation".equalsIgnoreCase(role);

        ImmutableList<Team> teams;
        if (isLeadership) {
            teams = ImmutableList.of(
                    new Team("Support Leadership", "support-leadership", ImmutableList.of(TeamType.LEADERSHIP)),
                    new Team("Core Support", "support", ImmutableList.of(TeamType.SUPPORT)));
        } else if (isSupportEngineer) {
            teams = ImmutableList.of(new Team("Core Support", "support", ImmutableList.of(TeamType.SUPPORT)));
        } else if (isEscalation) {
            teams = ImmutableList.of(new Team("Escalation Team", "escalation", ImmutableList.of(TeamType.ESCALATION)));
        } else {
            teams = ImmutableList.of(new Team("Test Tenant", "test-tenant", ImmutableList.of(TeamType.TENANT)));
        }

        var roles = ImmutableList.<Role>builder();
        roles.add(Role.USER);

        if (isLeadership) {
            roles.add(Role.LEADERSHIP);
        }
        if (isSupportEngineer) {
            roles.add(Role.SUPPORT_ENGINEER);
        }
        if (isEscalation) {
            roles.add(Role.ESCALATION);
        }

        return new UserPrincipal(email, "Test User", teams, roles.build());
    }
}
