package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.util.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(ElevateRepositoryPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIfSystemProperty(named = "docker", matches = "true")
class ElevateRepositoryPostgresTest {
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-13T10:00:05Z");

    private final ElevateRepository repository;
    private final JdbcTemplate jdbcTemplate;

    ElevateRepositoryPostgresTest(ElevateRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearSnapshot() {
        jdbcTemplate.update("DELETE FROM elevate_journeys");
        jdbcTemplate.update("DELETE FROM elevate_users");
        jdbcTemplate.update("DELETE FROM elevate_products");
        jdbcTemplate.update("""
                UPDATE elevate_sync_state
                   SET last_ping_attempt_at = NULL,
                       last_ping_success_at = NULL,
                       last_ping_succeeded = NULL,
                       last_ping_error = NULL,
                       last_sync_attempt_at = NULL,
                       last_sync_success_at = NULL,
                       last_sync_succeeded = NULL,
                       last_sync_error = NULL
                 WHERE singleton = TRUE
                """);
    }

    @Test
    void replacesExistingSnapshotIncludingWithAuthoritativeEmptyCollections() {
        repository.replaceSnapshot(snapshot(product("old")), ATTEMPTED_AT, COMPLETED_AT);

        repository.replaceSnapshot(new ElevateSnapshot(List.of(), List.of(), List.of()), ATTEMPTED_AT, COMPLETED_AT);

        ElevateStoredStatus storedStatus = repository.getStoredStatus();
        assertThat(storedStatus.snapshot()).isEqualTo(new ElevateSnapshot(List.of(), List.of(), List.of()));
        ElevateSyncState state = storedStatus.state();
        assertThat(state.lastSyncSucceeded()).isTrue();
        assertThat(state.lastSyncAttemptAt()).isEqualTo(ATTEMPTED_AT);
        assertThat(state.lastSyncSuccessAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void rollsBackWholeReplacementAndRetainsLastGoodSnapshotOnInsertFailure() {
        ElevateProduct oldProduct = product("old");
        repository.replaceSnapshot(snapshot(oldProduct), ATTEMPTED_AT, COMPLETED_AT);
        ElevateProduct duplicate = product("duplicate");
        ElevateSnapshot invalidSnapshot = new ElevateSnapshot(List.of(duplicate, duplicate), List.of(), List.of());

        assertThatThrownBy(() -> repository.replaceSnapshot(invalidSnapshot, ATTEMPTED_AT, COMPLETED_AT))
                .isInstanceOf(RuntimeException.class);

        assertThat(repository.getSnapshot().products()).containsExactly(oldProduct);
    }

    private static ElevateSnapshot snapshot(ElevateProduct product) {
        return new ElevateSnapshot(List.of(product), List.of(), List.of());
    }

    private static ElevateProduct product(String id) {
        return new ElevateProduct(
                id,
                id + "-slug",
                id + " name",
                null,
                LocalDateTime.parse("2026-01-02T03:04:05"),
                LocalDateTime.parse("2026-02-03T04:05:06"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            String url = System.getProperty("supportbot.localDb.url", "jdbc:postgresql://localhost:5432/postgres");
            String username = System.getProperty("supportbot.localDb.user", "postgres");
            String password = System.getProperty("supportbot.localDb.password", "postgres");
            return new DriverManagerDataSource(url, username, password);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new JsonMapper().getObjectMapper();
        }

        @Bean
        ElevateRepository elevateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new ElevateRepository(jdbcTemplate, objectMapper);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
