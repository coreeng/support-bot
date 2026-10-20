package com.coreeng.supportbot.erasure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(JdbcErasureRequestRepositoryPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class JdbcErasureRequestRepositoryPostgresTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-15T09:30:00.123456Z");

    private final ErasureRequestRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final UUID requestId = UUID.randomUUID();

    JdbcErasureRequestRepositoryPostgresTest(ErasureRequestRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    // Shares the database `make run-local` uses, so only this test's own row is removed.
    @AfterEach
    void deleteTestRequest() {
        jdbcTemplate.update("DELETE FROM erasure_requests WHERE id = ?", requestId);
    }

    @Test
    void storesANewRequestAndReadsItBack() {
        ErasureRequest request = request("leaver@example.com", REQUESTED_AT);

        assertThat(repository.insertIfAbsent(request)).isTrue();

        assertThat(repository.findById(requestId)).contains(request);
    }

    @Test
    void leavesAnExistingRequestUnchanged() {
        ErasureRequest first = request("leaver@example.com", REQUESTED_AT);
        repository.insertIfAbsent(first);

        boolean stored = repository.insertIfAbsent(request("someone-else@example.com", REQUESTED_AT.plusSeconds(60)));

        assertThat(stored).isFalse();
        assertThat(repository.findById(requestId)).contains(first);
    }

    @Test
    void findsNothingForAnUnknownId() {
        assertThat(repository.findById(requestId)).isEmpty();
    }

    private ErasureRequest request(String currentUserEmail, Instant requestedAt) {
        return new ErasureRequest(
                requestId, currentUserEmail, "erased@example.com", ErasureRequestStatus.IN_PROGRESS, requestedAt);
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
        DSLContext dslContext(DataSource dataSource) {
            return DSL.using(new TransactionAwareDataSourceProxy(dataSource), SQLDialect.POSTGRES);
        }

        @Bean
        ErasureRequestRepository erasureRequestRepository(DSLContext dslContext) {
            return new JdbcErasureRequestRepository(dslContext);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
