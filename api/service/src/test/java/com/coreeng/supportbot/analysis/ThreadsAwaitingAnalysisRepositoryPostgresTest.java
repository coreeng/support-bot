package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.summary.SummaryTestFixtures;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/**
 * Covers the windowed gap query added for the Support Summary page. The window is on ticket
 * <em>creation</em> ({@code query.date}), not last interaction, so the gaps found here line up
 * exactly with the tickets that page's breakdowns count.
 */
@SpringJUnitConfig(ThreadsAwaitingAnalysisRepositoryPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class ThreadsAwaitingAnalysisRepositoryPostgresTest {

    private static final String CHANNEL = "C-TAAR-TEST";
    private static final String OTHER_CHANNEL = "C-TAAR-OTHER";
    private static final String PROMPT_ID = "prompt-taar-test";
    private static final LocalDate FROM = LocalDate.of(2026, 3, 10);
    private static final LocalDate TO = LocalDate.of(2026, 3, 12);

    private final ThreadsAwaitingAnalysisRepository repository;
    private final JdbcTemplate jdbcTemplate;

    ThreadsAwaitingAnalysisRepositoryPostgresTest(
            ThreadsAwaitingAnalysisRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @BeforeEach
    @AfterEach
    void clearFixtures() {
        SummaryTestFixtures.clear(jdbcTemplate, CHANNEL, OTHER_CHANNEL);
    }

    @Test
    void returnsOnlyClosedUnanalysedTicketsRaisedInsideTheWindow() {
        long inside = closedTicket("2026-03-11T09:00:00", "ts-inside");
        long firstDay = closedTicket("2026-03-10T00:00:00", "ts-first-day");
        long lastDay = closedTicket("2026-03-12T23:59:59", "ts-last-day");
        closedTicket("2026-03-09T23:59:59", "ts-before");
        closedTicket("2026-03-13T00:00:00", "ts-after");

        long open = SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, "ts-open", LocalDateTime.parse("2026-03-11T09:00:00"), "opened", null);
        assertThat(open).isPositive();

        long analysed = closedTicket("2026-03-11T10:00:00", "ts-analysed");
        SummaryTestFixtures.insertAnalysis(
                jdbcTemplate, analysed, "Knowledge Gap", "Build & CI", "pipelines", "already done", PROMPT_ID);

        long stalePrompt = closedTicket("2026-03-11T11:00:00", "ts-stale");
        SummaryTestFixtures.insertAnalysis(
                jdbcTemplate, stalePrompt, "Knowledge Gap", "Build & CI", "pipelines", "stale", "some-older-prompt");

        ImmutableList<ThreadToAnalyze> threads =
                repository.findThreadsAwaitingAnalysis(FROM, TO, PROMPT_ID, List.of(CHANNEL));

        assertThat(threads)
                .extracting(ThreadToAnalyze::ticketId)
                .containsExactlyInAnyOrder(inside, firstDay, lastDay, stalePrompt);
        assertThat(threads).allSatisfy(thread -> assertThat(thread.channelId()).isEqualTo(CHANNEL));
    }

    @Test
    void ignoresChannelsThatAreNotMonitored() {
        closedTicket("2026-03-11T09:00:00", "ts-monitored");
        SummaryTestFixtures.insertTicket(
                jdbcTemplate,
                OTHER_CHANNEL,
                "ts-unmonitored",
                LocalDateTime.parse("2026-03-11T09:00:00"),
                "closed",
                null);

        ImmutableList<ThreadToAnalyze> threads =
                repository.findThreadsAwaitingAnalysis(FROM, TO, PROMPT_ID, List.of(CHANNEL));

        assertThat(threads).extracting(ThreadToAnalyze::threadTs).containsExactly("ts-monitored");
    }

    @Test
    void returnsNothingWhenNoChannelsAreMonitored() {
        closedTicket("2026-03-11T09:00:00", "ts-monitored");

        assertThat(repository.findThreadsAwaitingAnalysis(FROM, TO, PROMPT_ID, List.of()))
                .isEmpty();
    }

    private long closedTicket(String raisedAt, String ts) {
        return SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, ts, LocalDateTime.parse(raisedAt), "closed", null);
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
        ThreadsAwaitingAnalysisRepository threadsAwaitingAnalysisRepository(DSLContext dslContext) {
            return new JdbcThreadsAwaitingAnalysisRepository(dslContext);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
