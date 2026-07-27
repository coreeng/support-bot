package com.coreeng.supportbot.elevate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.security.AllowListService;
import com.coreeng.supportbot.security.AuthCodeStore;
import com.coreeng.supportbot.security.JwtGroupTeamMerger;
import com.coreeng.supportbot.security.JwtService;
import com.coreeng.supportbot.security.OAuth2AvailabilityChecker;
import com.coreeng.supportbot.security.SecurityConfig;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Proves the {@code /elevate/enabled} carve-out in {@link SecurityConfig} actually works at the
 * request-matching level, not just that {@link ElevateEnabledController} itself has no {@code
 * @PreAuthorize}. Unlike a plain controller unit test, this boots the real {@code SecurityFilterChain}
 * bean, so a future reorder of the {@code requestMatchers} rules (which would silently 403 the sidebar's
 * feature-flag check, or silently over-widen access) fails this test.
 */
@WebMvcTest(controllers = {ElevateEnabledController.class, ElevateStatusController.class})
@Import(SecurityConfig.class)
@TestPropertySource(
        properties = {
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.oauth2.redirect-uri=http://localhost:3000/login",
            "security.test-bypass.enabled=false"
        })
class ElevateSecurityRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElevateQueryService elevateQueryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthCodeStore authCodeStore;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private SupportTeamService supportTeamService;

    @MockitoBean
    private OAuth2AvailabilityChecker oauth2AvailabilityChecker;

    @MockitoBean
    private AllowListService allowListService;

    @MockitoBean
    private JwtGroupTeamMerger jwtGroupTeamMerger;

    @TestConfiguration
    static class CorsTestConfig {
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }
    }

    @Test
    void elevateEnabledIsReachableByAnyAuthenticatedUserRegardlessOfRole() throws Exception {
        mockMvc.perform(get("/elevate/enabled").with(user("plain-user").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void elevateStatusStillRequiresLeadershipOrSupportEngineerRole() throws Exception {
        mockMvc.perform(get("/elevate/status").with(user("plain-user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elevateStatusIsReachableByLeadership() throws Exception {
        mockMvc.perform(get("/elevate/status").with(user("leader").roles("LEADERSHIP")))
                .andExpect(status().isOk());
    }

    @Test
    void elevateEnabledIsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/elevate/enabled")).andExpect(status().isUnauthorized());
    }
}
