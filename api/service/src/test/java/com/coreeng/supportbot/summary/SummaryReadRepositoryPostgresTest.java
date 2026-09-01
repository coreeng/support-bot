package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("Knowledge Gap", 1L), tuple("Product Usability Problem", 1L));
        assertThat(breakdowns.categories())
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("Build & CI", 2L));
        assertThat(breakdowns.features())
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("pipelines", 2L));
        // Knowledge gaps are the categories of Knowledge Gap tickets only, so the usability ticket is absent.
        assertThat(breakdowns.knowledgeGaps())
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("Build & CI", 1L));
        assertThat(recentFor(breakdowns.knowledgeGaps(), "Build & CI"))
                .extracting(SummaryTicketExample::ticketId)
                .containsExactly(knowledge);
        // Teams cover every ticket raised, classified or not, so they sum to the window total.
        assertThat(breakdowns.teams())
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("team-a", 2L), tuple("team-b", 1L));
        assertThat(sum(breakdowns.drivers())).isEqualTo(breakdowns.classifiedTickets());
        assertThat(sum(breakdowns.teams())).isEqualTo(breakdowns.totalTickets());
    }

    @Test
    void bucketsBlankValuesInsteadOfDroppingThem() {
        classify(ticket("2026-03-11T09:00:00", "ts-blank", null), "Knowledge Gap", "  ", "", "Reason.");

        SummaryBreakdowns breakdowns = repository.breakdowns(WINDOW, PROMPT_ID, List.of(CHANNEL));

        assertThat(breakdowns.categories())
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("Unclassified", 1L));
        assertThat(breakdowns.features())
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("None", 1L));
        assertThat(breakdowns.teams())
                .extracting(SummaryCount::label, SummaryCount::count)
                .containsExactly(tuple("Unknown", 1L));
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
    void recentTicketsPerRowAreNewestFirstAndCapped() {
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
        // Raised but never classified: absent from the analysis breakdowns, still a team example.
        long open = ticket("2026-03-12T11:00:00", "ts-open", "team-c");

        SummaryBreakdowns breakdowns = repository.breakdowns(WINDOW, PROMPT_ID, List.of(CHANNEL));

        assertThat(recentFor(breakdowns.drivers(), "Knowledge Gap"))
                .hasSize(JdbcSummaryReadRepository.RECENT_PER_ROW)
                .extracting(SummaryTicketExample::text)
                .containsExactly("Reason 6", "Reason 5", "Reason 4", "Reason 3", "Reason 2");
        assertThat(recentFor(breakdowns.drivers(), "Product Usability Problem"))
                .extracting(SummaryTicketExample::ticketId)
                .containsExactly(usability);
        assertThat(recentFor(breakdowns.drivers(), "Unclassified"))
                .extracting(SummaryTicketExample::ticketId)
                .containsExactly(blank);

        // The same tickets hang off the category and feature rows.
        assertThat(recentFor(breakdowns.categories(), "Build & CI"))
                .hasSize(JdbcSummaryReadRepository.RECENT_PER_ROW)
                .extracting(SummaryTicketExample::ticketId)
                .startsWith(blank, usability);
        assertThat(recentFor(breakdowns.features(), "ci")).hasSize(JdbcSummaryReadRepository.RECENT_PER_ROW);

        // Teams include not-yet-classified tickets, whose reason is blank.
        assertThat(recentFor(breakdowns.teams(), "team-c"))
                .extracting(SummaryTicketExample::ticketId, SummaryTicketExample::text)
                .containsExactly(tuple(open, ""));
        assertThat(recentFor(breakdowns.teams(), "team-b"))
                .extracting(SummaryTicketExample::ticketId)
                .containsExactly(blank, usability);

        // Every row of every breakdown has at least one example.
        for (ImmutableList<SummaryCount> counts : List.of(
                breakdowns.drivers(),
                breakdowns.categories(),
                breakdowns.knowledgeGaps(),
                breakdowns.features(),
                breakdowns.teams())) {
            assertThat(counts).allSatisfy(count -> assertThat(count.recent()).isNotEmpty());
        }
    }

    private static ImmutableList<SummaryTicketExample> recentFor(ImmutableList<SummaryCount> counts, String label) {
        return counts.stream()
                .filter(count -> count.label().equals(label))
                .findFirst()
                .map(SummaryCount::recent)
                .orElse(ImmutableList.of());
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
    void fingerprintTracksClosedTicketsAwaitingClassification() {
        long classified = ticket("2026-03-11T09:00:00", "ts-done", "team-a");
        classify(classified, "Knowledge Gap", "Build & CI", "ci", "Done.", LocalDateTime.parse("2026-03-11T12:00:00"));
        SummaryFingerprint complete = repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL));
        assertThat(complete.gapCount()).isZero();
        assertThat(complete.value()).isEqualTo("1@2026-03-11T12:00");

        // An open ticket is not a gap — it will be classified once it closes.
        SummaryTestFixtures.insertTicket(
                jdbcTemplate, CHANNEL, "ts-open", LocalDateTime.parse("2026-03-11T10:00:00"), "opened", "team-a");
        assertThat(repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL)).value())
                .isEqualTo(complete.value());

        // A closed, unclassified ticket is: the fingerprint moves even though no analysis row changed.
        long gap = ticket("2026-03-12T09:00:00", "ts-gap", "team-a");
        SummaryFingerprint withGap = repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL));
        assertThat(withGap.analysisCount()).isEqualTo(1);
        assertThat(withGap.gapCount()).isEqualTo(1);
        assertThat(withGap.gapIdSum()).isEqualTo(gap);
        assertThat(withGap.value()).isEqualTo("1@2026-03-11T12:00#1:" + gap);

        // Classifying it closes the gap and moves the analysis half instead.
        classify(gap, "Task Request", "Build & CI", "ci", "Later.", LocalDateTime.parse("2026-03-12T12:00:00"));
        SummaryFingerprint after = repository.fingerprint(WINDOW, PROMPT_ID, List.of(CHANNEL));
        assertThat(after.gapCount()).isZero();
        assertThat(after.value()).isEqualTo("2@2026-03-12T12:00");
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
