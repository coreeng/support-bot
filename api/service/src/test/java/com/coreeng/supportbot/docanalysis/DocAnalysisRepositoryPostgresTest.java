package com.coreeng.supportbot.docanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.summary.SummaryTestFixtures;
import com.coreeng.supportbot.ticket.TicketId;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

@SpringJUnitConfig(DocAnalysisRepositoryPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class DocAnalysisRepositoryPostgresTest {

    private static final String CHANNEL = "C-DOC-ANALYSIS-TEST";
    private static final String PROMPT_ID = "prompt-doc-analysis-test";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    private final DocAnalysisRepository repository;
    private final JdbcTemplate jdbcTemplate;

    DocAnalysisRepositoryPostgresTest(DocAnalysisRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @BeforeEach
    @AfterEach
    void clearFixtures() {
        SummaryTestFixtures.clear(jdbcTemplate, CHANNEL);
    }

    @Test
    void readsBackASavedResult() {
        TicketId ticket = closedTicket("ts-result");
        DocAnalysis.Result result = result(ticket, DocClassification.INCORRECT);

        repository.saveResult(result);

        assertThat(repository.findByTicketId(ticket)).contains(result);
    }

    @Test
    void readsBackASavedFailedAttempt() {
        TicketId ticket = closedTicket("ts-failed");
        var attempt = new DocAnalysis.FailedAttempt(ticket, PROMPT_ID, NOW, "quote_not_found", 2);

        repository.saveFailedAttempt(attempt);

        assertThat(repository.findByTicketId(ticket)).contains(attempt);
    }

    @Test
    void aFailedAttemptReplacesAnEarlierResult() {
        TicketId ticket = closedTicket("ts-result-then-failed");
        repository.saveResult(result(ticket, DocClassification.CORRECT));
        var attempt = new DocAnalysis.FailedAttempt(ticket, "newer-prompt", NOW, "provider_error", 1);

        repository.saveFailedAttempt(attempt);

        assertThat(repository.findByTicketId(ticket)).contains(attempt);
    }

    @Test
    void aResultReplacesAnEarlierFailedAttempt() {
        TicketId ticket = closedTicket("ts-failed-then-result");
        repository.saveFailedAttempt(new DocAnalysis.FailedAttempt(ticket, PROMPT_ID, NOW, "undetermined", 1));
        DocAnalysis.Result result = result(ticket, DocClassification.MISSING);

        repository.saveResult(result);

        assertThat(repository.findByTicketId(ticket)).contains(result);
    }

    @Test
    void findsNothingForATicketWithNoRow() {
        assertThat(repository.findByTicketId(closedTicket("ts-none"))).isEmpty();
    }

    private TicketId closedTicket(String ts) {
        return new TicketId(SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, ts, LocalDateTime.parse("2026-03-11T09:00:00"), "closed", null));
    }

    private static DocAnalysis.Result result(TicketId ticket, DocClassification classification) {
        return new DocAnalysis.Result(
                ticket,
                PROMPT_ID,
                classification,
                "github",
                "3f2c1a9",
                "How do I cache build dependencies?",
                "Support said caching is not available.",
                List.of("build-cache", "pipelines"),
                List.of("pipelines"),
                List.of("pipelines/caching.md"),
                "Dependencies are cached between runs.",
                "The page documents caching, which contradicts support's answer.",
                "test-model",
                NOW);
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
        DocAnalysisRepository docAnalysisRepository(DSLContext dslContext) {
            return new JdbcDocAnalysisRepository(dslContext);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
