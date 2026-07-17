package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.ElevateProps;
import com.coreeng.supportbot.util.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;

@SpringJUnitConfig(ElevateStatusControllerTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ElevateStatusControllerTest {
    private static final Instant PING_ATTEMPT = Instant.parse("2026-07-13T09:00:00Z");
    private static final Instant PING_SUCCESS = Instant.parse("2026-07-13T09:00:01Z");
    private static final UUID SNAPSHOT_VERSION = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
                        SNAPSHOT_VERSION,
                        new ElevateCounts(1, 2, 3, 4),
                        new ElevateIntegrityCounts(5, 6, 7, 8)));
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
        assertThat(response.snapshotVersion()).isEqualTo(SNAPSHOT_VERSION);
        assertThat(response.counts()).isEqualTo(new ElevateCounts(1, 2, 3, 4));
        assertThat(response.integrity()).isEqualTo(new ElevateIntegrityCounts(5, 6, 7, 8));
    }

    @Test
    void responseSerializationNeverContainsCredentials() {
        authenticateAs("SUPPORT_ENGINEER");

        String json = new JsonMapper().toJsonString(controller.status());

        assertThat(json)
                .doesNotContain("esc_client", "secret-value", "clientId", "clientSecret")
                .doesNotContain("\"products\":[", "\"journeys\":[", "\"users\":[")
                .contains("\"configured\":true", "\"snapshotVersion\"", "\"counts\"");
    }

    @Test
    void parsesTheLowerCamelWireQueryUsedByTheUi() {
        authenticateAs("SUPPORT_ENGINEER");

        controller.products(SNAPSHOT_VERSION, 2, 50, "  Product  ", "linked", "relationships", "desc");
        controller.integrity(SNAPSHOT_VERSION, "crossProductAssignment", 0, 20, "", "name", "asc");

        ArgumentCaptor<ElevateReadQuery> query = ArgumentCaptor.forClass(ElevateReadQuery.class);
        verify(repository).findProducts(org.mockito.ArgumentMatchers.eq(SNAPSHOT_VERSION), query.capture());
        assertThat(query.getValue())
                .isEqualTo(new ElevateReadQuery(
                        2,
                        50,
                        "Product",
                        ElevateRelationshipFilter.LINKED,
                        ElevateSort.RELATIONSHIPS,
                        ElevateDirection.DESC));
        verify(repository)
                .findIntegrity(
                        SNAPSHOT_VERSION,
                        ElevateIntegrityType.CROSS_PRODUCT_ASSIGNMENT,
                        new ElevateReadQuery(
                                0, 20, "", ElevateRelationshipFilter.ALL, ElevateSort.NAME, ElevateDirection.ASC));
    }

    @Test
    void rejectsInvalidPaginationBeforeQueryingTheRepository() {
        authenticateAs("SUPPORT_ENGINEER");

        assertThatThrownBy(() -> controller.products(SNAPSHOT_VERSION, 0, 101, "", "all", "name", "asc"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pageSize must be between 1 and 100");
    }

    @Test
    void integrityTypesHaveStableLowerCamelJsonValues() {
        assertThat(new JsonMapper().toJsonString(ElevateIntegrityItem.Type.CROSS_PRODUCT_ASSIGNMENT))
                .isEqualTo("\"crossProductAssignment\"");
    }

    @Test
    void snapshotRolloverHasAStableConflictCode() {
        var problem = new ElevateExceptionHandler().handleSnapshotChanged(new ElevateSnapshotChangedException());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "SNAPSHOT_CHANGED");
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
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30),
                    16_777_216,
                    Duration.ofMinutes(1),
                    Duration.ofHours(1),
                    Duration.ofHours(12),
                    100,
                    20_000,
                    100_000,
                    3,
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(5),
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
