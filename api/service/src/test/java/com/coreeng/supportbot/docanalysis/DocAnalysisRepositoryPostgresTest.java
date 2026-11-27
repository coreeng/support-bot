package com.coreeng.supportbot.docanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.summary.SummaryTestFixtures;
import com.coreeng.supportbot.ticket.TicketId;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final String OTHER_CHANNEL = "C-DOC-ANALYSIS-OTHER";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final LocalDate FROM = LocalDate.parse("2026-03-09");
    private static final LocalDate TO = LocalDate.parse("2026-03-22");
    private static final Instant FAILED_BEFORE = NOW.minus(Duration.ofHours(24));
    private static final int MAX_ATTEMPTS = 3;

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
        SummaryTestFixtures.clear(jdbcTemplate, CHANNEL, OTHER_CHANNEL);
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

    @Test
    void aTicketWithNoRowIsDue() {
        TicketId ticket = closedTicket("ts-due-no-row", "2026-03-10T09:00:00");

        assertThat(due()).containsExactly(ticket);
    }

    @Test
    void aResultUnderTheCurrentPromptIsNotDueAndOneUnderAnotherIs() {
        TicketId current = closedTicket("ts-due-current", "2026-03-10T09:00:00");
        TicketId stale = closedTicket("ts-due-stale", "2026-03-11T09:00:00");
        repository.saveResult(result(current, DocClassification.CORRECT));
        repository.saveResult(withPromptId(result(stale, DocClassification.CORRECT), "older-prompt"));

        assertThat(due()).containsExactly(stale);
    }

    @Test
    void aFailureInsideTheRetryDelayIsNotDue() {
        TicketId ticket = closedTicket("ts-due-recent-failure", "2026-03-10T09:00:00");
        failed(ticket, PROMPT_ID, FAILED_BEFORE.plus(Duration.ofHours(1)), 1);

        assertThat(due()).isEmpty();
    }

    @Test
    void aFailurePastTheRetryDelayWithAttemptsLeftIsDue() {
        TicketId ticket = closedTicket("ts-due-old-failure", "2026-03-10T09:00:00");
        failed(ticket, PROMPT_ID, FAILED_BEFORE.minus(Duration.ofHours(1)), MAX_ATTEMPTS - 1);

        assertThat(due()).containsExactly(ticket);
    }

    @Test
    void aTicketOutOfAttemptsIsNotDueUntilThePromptIdChanges() {
        TicketId current = closedTicket("ts-due-out-current", "2026-03-10T09:00:00");
        TicketId older = closedTicket("ts-due-out-older", "2026-03-11T09:00:00");
        failed(current, PROMPT_ID, FAILED_BEFORE.minus(Duration.ofDays(3)), MAX_ATTEMPTS);
        failed(older, "older-prompt", FAILED_BEFORE.minus(Duration.ofDays(3)), MAX_ATTEMPTS);

        assertThat(due()).containsExactly(older);
    }

    @Test
    void onlyClosedTicketsRaisedInTheWindowInTheGivenChannelsAreDueOldestFirst() {
        TicketId last = closedTicket("ts-due-last-day", "2026-03-22T23:59:00");
        TicketId first = closedTicket("ts-due-first-day", "2026-03-09T00:00:00");
        closedTicket("ts-due-after", "2026-03-23T00:00:00");
        closedTicket("ts-due-before", "2026-03-08T23:59:00");
        SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, "ts-due-open", LocalDateTime.parse("2026-03-10T09:00:00"), "opened", null);
        SummaryTestFixtures.insertTicket(
                jdbcTemplate,
                OTHER_CHANNEL,
                "ts-due-other-channel",
                LocalDateTime.parse("2026-03-10T09:00:00"),
                "closed",
                null);

        assertThat(due()).containsExactly(first, last);
    }

    @Test
    void findsTheWindowsClosedTicketsWithTheirRowsWhateverThePromptIdOldestFirst() {
        TicketId last = closedTicket("ts-window-last-day", "2026-03-22T23:59:00");
        TicketId first = closedTicket("ts-window-first-day", "2026-03-09T00:00:00");
        TicketId stale = closedTicket("ts-window-stale", "2026-03-12T09:00:00");
        closedTicket("ts-window-after", "2026-03-23T00:00:00");
        SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, "ts-window-open", LocalDateTime.parse("2026-03-10T09:00:00"), "opened", null);
        SummaryTestFixtures.insertTicket(
                jdbcTemplate,
                OTHER_CHANNEL,
                "ts-window-other-channel",
                LocalDateTime.parse("2026-03-10T09:00:00"),
                "closed",
                null);
        DocAnalysis.Result result = result(first, DocClassification.MISSING);
        repository.saveResult(result);
        var attempt = new DocAnalysis.FailedAttempt(stale, "older-prompt", NOW, "undetermined", 1);
        repository.saveFailedAttempt(attempt);

        assertThat(repository.findWindow(FROM, TO, List.of(CHANNEL)))
                .containsExactly(
                        new WindowTicket(
                                first, CHANNEL, "ts-window-first-day", Instant.parse("2026-03-09T00:00:00Z"), result),
                        new WindowTicket(
                                stale, CHANNEL, "ts-window-stale", Instant.parse("2026-03-12T09:00:00Z"), attempt),
                        new WindowTicket(
                                last, CHANNEL, "ts-window-last-day", Instant.parse("2026-03-22T23:59:00Z"), null));
    }

    @Test
    void countsResultsAndFailuresSavedSinceTheGivenTime() {
        Instant later = NOW.plus(Duration.ofDays(30));
        Instant since = later.minus(Duration.ofHours(24));
        TicketId recentResult = closedTicket("ts-count-result", "2026-03-10T09:00:00");
        TicketId recentFailure = closedTicket("ts-count-failure", "2026-03-10T10:00:00");
        TicketId olderResult = closedTicket("ts-count-older", "2026-03-10T11:00:00");
        repository.saveResult(analysedAt(result(recentResult, DocClassification.MISSING), since));
        failed(recentFailure, PROMPT_ID, later, 1);
        repository.saveResult(analysedAt(result(olderResult, DocClassification.MISSING), since.minusSeconds(1)));

        assertThat(repository.countAttemptedSince(since)).isEqualTo(2);
    }

    @Test
    void removesOnlyTheGivenTicketsFailuresWithTheGivenReason() {
        TicketId providerError = closedTicket("ts-delete-provider", "2026-03-10T09:00:00");
        TicketId otherReason = closedTicket("ts-delete-other-reason", "2026-03-10T10:00:00");
        TicketId notGiven = closedTicket("ts-delete-not-given", "2026-03-10T11:00:00");
        TicketId withResult = closedTicket("ts-delete-result", "2026-03-10T12:00:00");
        repository.saveFailedAttempt(new DocAnalysis.FailedAttempt(providerError, PROMPT_ID, NOW, "provider_error", 1));
        repository.saveFailedAttempt(new DocAnalysis.FailedAttempt(otherReason, PROMPT_ID, NOW, "undetermined", 1));
        repository.saveFailedAttempt(new DocAnalysis.FailedAttempt(notGiven, PROMPT_ID, NOW, "provider_error", 1));
        repository.saveResult(result(withResult, DocClassification.CORRECT));

        repository.deleteFailedAttempts(List.of(providerError, otherReason, withResult), "provider_error");

        assertThat(repository.findByTicketId(providerError)).isEmpty();
        assertThat(repository.findByTicketId(otherReason)).isPresent();
        assertThat(repository.findByTicketId(notGiven)).isPresent();
        assertThat(repository.findByTicketId(withResult)).isPresent();
    }

    private List<TicketId> due() {
        return repository.findDue(FROM, TO, List.of(CHANNEL), PROMPT_ID, FAILED_BEFORE, MAX_ATTEMPTS).stream()
                .map(TicketToClassify::ticketId)
                .toList();
    }

    private void failed(TicketId ticket, String promptId, Instant failedAt, int attempts) {
        repository.saveFailedAttempt(
                new DocAnalysis.FailedAttempt(ticket, promptId, failedAt, "undetermined", attempts));
    }

    private static DocAnalysis.Result withPromptId(DocAnalysis.Result result, String promptId) {
        return new DocAnalysis.Result(
                result.ticketId(),
                promptId,
                result.classification(),
                result.source(),
                result.docsVersion(),
                result.question(),
                result.answerSummary(),
                result.tags(),
                result.productAreas(),
                result.docPaths(),
                result.evidenceQuote(),
                result.rationale(),
                result.model(),
                result.analysedAt());
    }

    private static DocAnalysis.Result analysedAt(DocAnalysis.Result result, Instant analysedAt) {
        return new DocAnalysis.Result(
                result.ticketId(),
                result.promptId(),
                result.classification(),
                result.source(),
                result.docsVersion(),
                result.question(),
                result.answerSummary(),
                result.tags(),
                result.productAreas(),
                result.docPaths(),
                result.evidenceQuote(),
                result.rationale(),
                result.model(),
                analysedAt);
    }

    private TicketId closedTicket(String ts, String raisedAt) {
        return new TicketId(SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, ts, LocalDateTime.parse(raisedAt), "closed", null));
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
