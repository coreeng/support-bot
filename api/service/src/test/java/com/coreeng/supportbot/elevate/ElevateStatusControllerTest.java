package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.ElevateProps;
import com.coreeng.supportbot.util.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(ElevateStatusControllerTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ElevateStatusControllerTest {
    private static final Instant PING_ATTEMPT = Instant.parse("2026-07-13T09:00:00Z");
    private static final Instant PING_SUCCESS = Instant.parse("2026-07-13T09:00:01Z");

    private final ElevateStatusController controller;
    private final ElevateRepository repository;

    ElevateStatusControllerTest(ElevateStatusController controller, ElevateRepository repository) {
        this.controller = controller;
        this.repository = repository;
    }

    @BeforeEach
    void setUp() {
        when(repository.getStoredStatus())
                .thenReturn(new ElevateStoredStatus(
                        new ElevateSyncState(PING_ATTEMPT, PING_SUCCESS, true, null, null, null, null, null),
                        new ElevateSnapshot(
                                List.of(new ElevateProduct(
                                        "product-1",
                                        "product-one",
                                        "Product One",
                                        null,
                                        LocalDateTime.parse("2026-01-02T03:04:05"),
                                        LocalDateTime.parse("2026-02-03T04:05:06"))),
                                List.of(),
                                List.of())));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryAuthenticatedUserCannotReadElevateStatus() {
        authenticateAs("USER");

        assertThatThrownBy(controller::status).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEADERSHIP", "SUPPORT_ENGINEER"})
    void leadershipAndSupportEngineersCanReadAggregateStatus(String role) {
        authenticateAs(role);

        ElevateStatusResponse response = controller.status();

        assertThat(response.configured()).isTrue();
        assertThat(response.baseUrl()).isEqualTo("https://elevate.example.test");
        assertThat(response.statusInterval()).isEqualTo("PT1H");
        assertThat(response.syncInterval()).isEqualTo("PT12H");
        assertThat(response.lastPingAttemptAt()).isEqualTo(PING_ATTEMPT);
        assertThat(response.lastPingSuccessAt()).isEqualTo(PING_SUCCESS);
        assertThat(response.products()).extracting(ElevateProduct::id).containsExactly("product-1");
    }

    @Test
    void responseSerializationNeverContainsCredentials() {
        authenticateAs("SUPPORT_ENGINEER");

        String json = new JsonMapper().toJsonString(controller.status());

        assertThat(json)
                .doesNotContain("esc_client", "secret-value", "clientId", "clientSecret")
                .contains("\"configured\":true", "\"products\"");
    }

    private static void authenticateAs(String role) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "test-user", "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        ElevateProps elevateProps() {
            return new ElevateProps(
                    "https://elevate.example.test",
                    "esc_client",
                    "secret-value",
                    Duration.ofHours(1),
                    Duration.ofHours(12),
                    "Support Bot",
                    "https://support.example.test",
                    "1.2.3");
        }

        @Bean
        ElevateRepository elevateRepository() {
            return mock(ElevateRepository.class);
        }

        @Bean
        ElevateStatusController elevateStatusController(ElevateProps props, ElevateRepository repository) {
            return new ElevateStatusController(props, repository);
        }
    }
}
