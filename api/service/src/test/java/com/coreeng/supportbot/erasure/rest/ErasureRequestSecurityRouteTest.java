package com.coreeng.supportbot.erasure.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.erasure.ErasureRequest;
import com.coreeng.supportbot.erasure.ErasureRequestService;
import com.coreeng.supportbot.erasure.ErasureRequestStatus;
import com.coreeng.supportbot.security.AllowListService;
import com.coreeng.supportbot.security.AuthCodeStore;
import com.coreeng.supportbot.security.InternalPortSecurityConfig;
import com.coreeng.supportbot.security.JwtGroupTeamMerger;
import com.coreeng.supportbot.security.JwtService;
import com.coreeng.supportbot.security.OAuth2AvailabilityChecker;
import com.coreeng.supportbot.security.SecurityConfig;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Boots the real security chains to prove the erasure endpoints are reachable only on the internal port and
 * that the internal port reaches nothing else (ADR-010).
 */
@WebMvcTest(controllers = ErasureRequestController.class)
@Import({SecurityConfig.class, InternalPortSecurityConfig.class})
@TestPropertySource(
        properties = {
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.oauth2.redirect-uri=http://localhost:3000/login",
            "security.test-bypass.enabled=false",
            "internal-port.enabled=true",
            "internal-port.port=" + ErasureRequestSecurityRouteTest.INTERNAL_PORT
        })
class ErasureRequestSecurityRouteTest {

    static final int INTERNAL_PORT = 8082;
    private static final UUID REQUEST_ID = UUID.fromString("5f0c6a2e-2a51-4b8e-9d0a-3b7c1e4f8a10");
    private static final String BODY = "{\"requestId\":\"" + REQUEST_ID
            + "\",\"currentUserEmail\":\"leaver@example.com\",\"newUserEmail\":\"erased@example.com\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ErasureRequestService erasureRequestService;

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
    void internalPortCallerCanSubmitAnErasureRequest() throws Exception {
        when(erasureRequestService.submit(any(), any(), any())).thenReturn(stored());

        mockMvc.perform(post("/api/erasure-requests")
                        .with(onPort(INTERNAL_PORT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());
    }

    @Test
    void internalPortCallerCanPollAnErasureRequest() throws Exception {
        when(erasureRequestService.get(REQUEST_ID)).thenReturn(stored());

        mockMvc.perform(get("/api/erasure-requests/" + REQUEST_ID).with(onPort(INTERNAL_PORT)))
                .andExpect(status().isOk());
    }

    @Test
    void internalPortRefusesEveryOtherPath() throws Exception {
        mockMvc.perform(get("/ticket").with(onPort(INTERNAL_PORT))).andExpect(status().isForbidden());
        mockMvc.perform(get("/health").with(onPort(INTERNAL_PORT))).andExpect(status().isForbidden());
    }

    @Test
    void publicPortRefusesAnUnauthenticatedCaller() throws Exception {
        mockMvc.perform(post("/api/erasure-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/erasure-requests/" + REQUEST_ID)).andExpect(status().isUnauthorized());

        verifyNoInteractions(erasureRequestService);
    }

    // The body proves the dedicated handler answered. The default one forwards to /error, where the running app
    // no longer sees the person's authentication and answers 401 instead.
    @Test
    void publicPortRefusesAPersonWhateverTheirRole() throws Exception {
        mockMvc.perform(post("/api/erasure-requests")
                        .with(user("leader").roles("LEADERSHIP", "SUPPORT_ENGINEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Forbidden\"}"));
        mockMvc.perform(get("/api/erasure-requests/" + REQUEST_ID)
                        .with(user("leader").roles("LEADERSHIP")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Forbidden\"}"));

        verifyNoInteractions(erasureRequestService);
    }

    @Test
    void publicPortRefusesAPersonHoldingTheServiceAuthority() throws Exception {
        mockMvc.perform(get("/api/erasure-requests/" + REQUEST_ID)
                        .with(user("impostor").authorities(() -> "elevate:principal:service")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(erasureRequestService);
    }

    private static RequestPostProcessor onPort(int port) {
        return request -> {
            request.setLocalPort(port);
            return request;
        };
    }

    private static ErasureRequest stored() {
        return new ErasureRequest(
                REQUEST_ID,
                "leaver@example.com",
                "erased@example.com",
                ErasureRequestStatus.IN_PROGRESS,
                Instant.parse("2026-10-15T09:30:00Z"));
    }
}
