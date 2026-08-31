package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SummaryPostgresTestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class SummaryReadRepositoryPostgresTest {

    private static final String CHANNEL = "C-SUMMARY-TEST";
    private static final String OTHER_CHANNEL = "C-SUMMARY-OTHER";
    private static final String PROMPT_ID = "prompt-summary-test";
    private static final SummaryWindow WINDOW = new SummaryWindow(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

    private final SummaryReadRepository repository;
    private final JdbcTemplate jdbcTemplate;

    SummaryReadRepositoryPostgresTest(SummaryReadRepository repository, JdbcTemplate jdbcTemplate) {
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
    void countsReconcileAgainstTheWindowTotal() {
        long gap = ticket("2026-03-11T09:00:00", "ts-gap", "team-a");
        long knowledge = ticket("2026-03-11T10:00:00", "ts-kg", "team-a");
        long usability = ticket("2026-03-12T10:00:00", "ts-up", "team-b");
        classify(knowledge, "Knowledge Gap", "Build & CI", "pipelines", "Did not know pipelines existed.");
        classify(usability, "Product Usability Problem", "Build & CI", "pipelines", "Confused by pipeline naming.");
        assertThat(gap).isPositive();

        // Outside the window and outside the monitored channels: neither may reach any figure.
        classify(ticket("2026-03-09T23:59:59", "ts-before", "team-a"), "Knowledge Gap", "Deployment & CD", "cd", "x");
        long unmonitored = SummaryTestFixtures.insertTicket(
                jdbcTemplate,
                OTHER_CHANNEL,
                "ts-other",
                LocalDateTime.parse("2026-03-11T09:00:00"),
                "closed",
                "team-z");
        classify(unmonitored, "Knowledge Gap", "Deployment & CD", "cd", "x");

        SummaryBreakdowns breakdowns = repository.breakdowns(WINDOW, PROMPT_ID, List.of(CHANNEL));

        assertThat(breakdowns.totalTickets()).isEqualTo(3);
        assertThat(breakdowns.classifiedTickets()).isEqualTo(2);
        assertThat(breakdowns.unclassifiedTickets()).isEqualTo(1);
        assertThat(breakdowns.drivers())
                .containsExactly(
                        new SummaryCount("Knowledge Gap", 1), new SummaryCount("Product Usability Problem", 1));
        assertThat(breakdowns.categories()).containsExactly(new SummaryCount("Build & CI", 2));
        assertThat(breakdowns.features()).containsExactly(new SummaryCount("pipelines", 2));
        // Teams cover every ticket raised, classified or not, so they sum to the window total.
        assertThat(breakdowns.teams()).containsExactly(new SummaryCount("team-a", 2), new SummaryCount("team-b", 1));
        assertThat(sum(breakdowns.drivers())).isEqualTo(breakdowns.classifiedTickets());
        assertThat(sum(breakdowns.teams())).isEqualTo(breakdowns.totalTickets());
    }

    @Test
    void bucketsBlankValuesInsteadOfDroppingThem() {
        classify(ticket("2026-03-11T09:00:00", "ts-blank", null), "Knowledge Gap", "  ", "", "Reason.");

        SummaryBreakdowns breakdowns = repository.breakdowns(WINDOW, PROMPT_ID, List.of(CHANNEL));

        assertThat(breakdowns.categories()).containsExactly(new SummaryCount("Unclassified", 1));
        assertThat(breakdowns.features()).containsExactly(new SummaryCount("None", 1));
        assertThat(breakdowns.teams()).containsExactly(new SummaryCount("Unknown", 1));
    }

    @Test
    void ignoresAnalysisWrittenByAnOlderPrompt() {
        long stale = ticket("2026-03-11T09:00:00", "ts-stale", "team-a");
        classifyWithPrompt(stale, "Knowledge Gap", "Build & CI", "pipelines", "Old.", "an-older-prompt");

        SummaryBreakdowns breakdowns = repository.breakdowns(WINDOW, PROMPT_ID, List.of(CHANNEL));

        assertThat(breakdowns.totalTickets()).isEqualTo(1);
        assertThat(breakdowns.classifiedTickets()).isZero();
        assertThat(breakdowns.drivers()).isEmpty();
        assertThat(repository.reasons(WINDOW, PROMPT_ID, List.of(CHANNEL), 100)).isEmpty();
    }

    @Test
    void recentTicketsPerDriverAreNewestFirstAndCapped() {
        // Six Knowledge Gap tickets: only the newest five come back, newest first.
        for (int hour = 1; hour <= 6; hour++) {
            classify(
                    ticket("2026-03-11T0" + hour + ":00:00", "ts-kg-" + hour, "team-a"),
                    "Knowledge Gap",
                    "Build & CI",
                    "ci",
                    "Reason " + hour);
        }
        long usability = ticket("2026-03-12T09:00:00", "ts-up", "team-b");
        classify(usability, "Product Usability Problem", "Build & CI", "ci", "Confusing.");
        // Blank driver lands in the explicit bucket and still gets its example.
        long blank = ticket("2026-03-12T10:00:00", "ts-blank", "team-b");
        classify(blank, " ", "Build & CI", "ci", "No driver.");

        SummaryBreakdowns breakdowns = repository.breakdowns(WINDOW, PROMPT_ID, List.of(CHANNEL));

        assertThat(breakdowns.recentFor("Knowledge Gap"))
                .hasSize(JdbcSummaryReadRepository.RECENT_PER_DRIVER)
                .extracting(SummaryTicketExample::text)
                .containsExactly("Reason 6", "Reason 5", "Reason 4", "Reason 3", "Reason 2");
        assertThat(breakdowns.recentFor("Product Usability Problem"))
                .extracting(SummaryTicketExample::ticketId)
                .containsExactly(usability);
        assertThat(breakdowns.recentFor("Unclassified"))
                .extracting(SummaryTicketExample::ticketId)
                .containsExactly(blank);
        assertThat(breakdowns.recentFor("Feature Request")).isEmpty();
        // Every driver row has a matching example list.
        assertThat(breakdowns.drivers()).allSatisfy(count -> assertThat(breakdowns.recentFor(count.label()))
                .isNotEmpty());
    }

    @Test
    void fingerprintChangesWhenTheWindowsAnalysisChanges() {
        long first = ticket("2026-03-11T09:00:00", "ts-1", "team-a");
        classify(first, "Knowledge Gap", "Build & CI", "pipelines", "One.", LocalDateTime.parse("2026-03-11T12:00:00"));

        SummaryFingerprint initial = repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL));
        assertThat(initial.analysisCount()).isEqualTo(1);
        assertThat(initial.maxUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-03-11T12:00:00"));
        assertThat(repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL)).value())
                .isEqualTo(initial.value());

        long second = ticket("2026-03-12T09:00:00", "ts-2", "team-a");
        classify(second, "Task Request", "Build & CI", "pipelines", "Two.", LocalDateTime.parse("2026-03-12T12:00:00"));

        assertThat(repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL)).value())
                .isNotEqualTo(initial.value());
    }

    @Test
    void fingerprintOfAnEmptyWindowIsStable() {
        SummaryFingerprint fingerprint = repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL));

        assertThat(fingerprint.analysisCount()).isZero();
        assertThat(fingerprint.maxUpdatedAt()).isNull();
        assertThat(fingerprint.value()).isEqualTo("0@-");
    }

    @Test
    void reasonsComeBackNewestFirstAndSkipBlanks() {
        classify(ticket("2026-03-10T09:00:00", "ts-old", "team-a"), "Knowledge Gap", "Build & CI", "ci", "Oldest.");
        classify(ticket("2026-03-12T09:00:00", "ts-new", "team-a"), "Knowledge Gap", "Build & CI", "ci", "Newest.");
        classify(ticket("2026-03-11T09:00:00", "ts-empty", "team-a"), "Knowledge Gap", "Build & CI", "ci", "   ");

        assertThat(repository.reasons(WINDOW, PROMPT_ID, List.of(CHANNEL), 100)).containsExactly("Newest.", "Oldest.");
        assertThat(repository.reasons(WINDOW, PROMPT_ID, List.of(CHANNEL), 1)).containsExactly("Newest.");
        assertThat(repository.reasons(WINDOW, PROMPT_ID, List.of(CHANNEL), 0)).isEmpty();
    }

    @Test
    void unmonitoredChannelSetYieldsNothingRatherThanEverything() {
        classify(ticket("2026-03-11T09:00:00", "ts-any", "team-a"), "Knowledge Gap", "Build & CI", "ci", "Reason.");

        SummaryBreakdowns breakdowns = repository.breakdowns(WINDOW, PROMPT_ID, List.of());

        assertThat(breakdowns.totalTickets()).isZero();
        assertThat(breakdowns.teams()).isEmpty();
        assertThat(repository.fingerprint(WINDOW, PROMPT_ID, List.of()).analysisCount())
                .isZero();
        assertThat(repository.reasons(WINDOW, PROMPT_ID, List.of(), 10)).isEmpty();
    }

    private static long sum(ImmutableList<SummaryCount> counts) {
        return counts.stream().mapToLong(SummaryCount::count).sum();
    }

    private long ticket(String raisedAt, String ts, @Nullable String team) {
        return SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, ts, LocalDateTime.parse(raisedAt), "closed", team);
    }

    private void classify(long ticketId, String driver, String category, String feature, String reason) {
        classifyWithPrompt(ticketId, driver, category, feature, reason, PROMPT_ID);
    }

    private void classify(
            long ticketId, String driver, String category, String feature, String reason, LocalDateTime updatedAt) {
        SummaryTestFixtures.insertAnalysis(
                jdbcTemplate, ticketId, driver, category, feature, reason, PROMPT_ID, updatedAt);
    }

    private void classifyWithPrompt(
            long ticketId, String driver, String category, String feature, String reason, String promptId) {
        SummaryTestFixtures.insertAnalysis(jdbcTemplate, ticketId, driver, category, feature, reason, promptId);
    }
}
