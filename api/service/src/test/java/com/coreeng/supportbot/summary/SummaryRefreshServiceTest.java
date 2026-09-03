package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptRepository;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisService;
import com.coreeng.supportbot.analysis.WindowAnalysisRunner;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.SummaryProps;
import com.coreeng.supportbot.slack.SlackException;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class SummaryRefreshServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 10);
    private static final LocalDate TO = LocalDate.of(2026, 3, 23);
    private static final SummaryWindow WINDOW = new SummaryWindow(FROM, TO);
    private static final String CHANNEL = "C123456";
    private static final String CLASSIFICATION_PROMPT = "classify this";
    private static final String SUMMARY_PROMPT = "summarise this";
    private static final String CLASSIFICATION_PROMPT_ID = AnalysisService.computePromptId(CLASSIFICATION_PROMPT);
    private static final String SUMMARY_PROMPT_ID = AnalysisService.computePromptId(SUMMARY_PROMPT);
    private static final String FINGERPRINT = "3/2@2026-03-23T10:00";
    private static final Duration RETRY_DELAY = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-03-23T12:00:00Z");

    @Mock
    private AsyncJobRepository asyncJobRepository;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService;

    @Mock
    private AnalysisPromptRepository analysisPromptRepository;

    @Mock
    private SummaryReadRepository summaryReadRepository;

    @Mock
    private SummarySnapshotRepository summarySnapshotRepository;

    @Mock
    private LlmSummaryService llmSummaryService;

    @Mock
    private ApplicationContext applicationContext;

    private final SteppingClock clock = new SteppingClock(NOW);
    private SummaryRefreshService service;

    @BeforeEach
    void setUp() {
        SlackChannelRegistry channelRegistry = new SlackChannelRegistry(
                new SlackTicketsProps(CHANNEL, List.of(), "eyes", "ticket", "white_check_mark", "rocket"));
        service = new SummaryRefreshService(
                asyncJobRepository,
                analysisService,
                threadsAwaitingAnalysisService,
                analysisPromptRepository,
                summaryReadRepository,
                summarySnapshotRepository,
                llmSummaryService,
                channelRegistry,
                new SummaryProps(true, 400, RETRY_DELAY),
                applicationContext,
                clock);

        lenient().when(analysisService.loadPrompt()).thenReturn(CLASSIFICATION_PROMPT);
        lenient()
                .when(threadsAwaitingAnalysisService.find(FROM, TO, CLASSIFICATION_PROMPT_ID))
                .thenReturn(ImmutableList.of());
        lenient()
                .when(analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY))
                .thenReturn(new AnalysisPrompt(1, SUMMARY_PROMPT));
        lenient()
                .when(summaryReadRepository.breakdowns(WINDOW, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(breakdowns());
        lenient()
                .when(summaryReadRepository.fingerprint(WINDOW, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(
                        new SummaryFingerprint(3, 2, LocalDate.of(2026, 3, 23).atTime(10, 0)));
        lenient()
                .when(summaryReadRepository.reasons(WINDOW, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL), 400))
                .thenReturn(ImmutableList.of("Because."));
        lenient().when(llmSummaryService.modelName()).thenReturn("model-a");
    }

    @Test
    void start_claimsTheSharedLockWithAWindowPayload() {
        when(asyncJobRepository.tryStartJob("analysis", "window:2026-03-10:2026-03-23"))
                .thenReturn(true);
        when(applicationContext.getBean(WindowAnalysisRunner.class)).thenReturn(service);
        when(llmSummaryService.generate(any(), any(), any())).thenReturn("the prose");

        assertThat(service.start(WINDOW)).isTrue();
    }

    @Test
    void start_doesNothingWhenAnotherJobHoldsTheLock() {
        when(asyncJobRepository.tryStartJob("analysis", "window:2026-03-10:2026-03-23"))
                .thenReturn(false);

        assertThat(service.start(WINDOW)).isFalse();
        verifyNoInteractions(applicationContext, analysisService);
    }

    @Test
    void backfillsBeforeGeneratingAndStoresTheResultingSnapshot() {
        when(llmSummaryService.generate(SUMMARY_PROMPT, breakdowns(), ImmutableList.of("Because.")))
                .thenReturn("  the prose  ");

        service.runWindowRefresh(FROM, TO);

        verify(analysisService).backfillWindow(FROM, TO);
        ArgumentCaptor<SummarySnapshot> stored = ArgumentCaptor.forClass(SummarySnapshot.class);
        verify(summarySnapshotRepository).upsert(stored.capture());
        assertThat(stored.getValue())
                .isEqualTo(new SummarySnapshot(
                        WINDOW, SUMMARY_PROMPT_ID, "3/2@2026-03-23T10:00", "the prose", "model-a", null));
        assertThat(service.status().running()).isFalse();
        verify(asyncJobRepository).deleteJob("analysis");
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT)).isNull();
    }

    @Test
    void releasesTheLockAndRecordsTheErrorWhenGenerationFails() {
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new IllegalStateException("model exploded"));

        service.runWindowRefresh(FROM, TO);

        // The lock must go back even on failure, or the page would report `generating` forever.
        verify(asyncJobRepository).deleteJob("analysis");
        verify(summarySnapshotRepository, never()).upsert(any());
        assertThat(service.status().running()).isFalse();
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
    }

    @Test
    void aRecordedFailureOnlyAppliesToTheDataThatProducedIt() {
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new IllegalStateException("model exploded"));

        service.runWindowRefresh(FROM, TO);

        // A different fingerprint means the window's data moved on: the failure is stale, so a retry
        // is warranted rather than a sticky error.
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, "9/9@2026-04-01T10:00"))
                .isNull();
        assertThat(service.failureFor(new SummaryWindow(FROM, TO.plusDays(1)), SUMMARY_PROMPT_ID, FINGERPRINT))
                .isNull();
    }

    @Test
    void aRecordedFailureIsRetriedOnceTheDelayHasPassedEvenIfTheDataIsUnchanged() {
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new IllegalStateException("model exploded"));

        service.runWindowRefresh(FROM, TO);

        // Polls inside the delay keep reporting the error rather than hammering the model.
        clock.advance(RETRY_DELAY.minusSeconds(1));
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
        // A window whose data never changes again (last month's) must not stay pinned to a transient
        // error until a restart: once the delay has passed the next visit gets to retry.
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT)).isNull();
        // And the expiry is not a one-off: the entry is gone, not merely hidden.
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT)).isNull();
    }

    @Test
    void aNewSummaryPromptVersionReleasesARecordedFailure() {
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new IllegalStateException("model exploded"));

        service.runWindowRefresh(FROM, TO);

        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
        // The failure was about a particular prompt; a newly published version is a different input
        // and deserves a fresh attempt even though the window's data is identical.
        String newPromptId = AnalysisService.computePromptId("summarise this, but better");
        assertThat(service.failureFor(WINDOW, newPromptId, FINGERPRINT)).isNull();
    }

    @Test
    void failuresForDifferentWindowsAreRememberedIndependently() {
        SummaryWindow otherWindow = new SummaryWindow(FROM.minusMonths(1), TO.minusMonths(1));
        String otherFingerprint = "9/7@2026-02-20T09:00";
        when(summaryReadRepository.fingerprint(otherWindow, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(
                        new SummaryFingerprint(9, 7, LocalDate.of(2026, 2, 20).atTime(9, 0)));
        when(summaryReadRepository.breakdowns(otherWindow, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(breakdowns());
        when(summaryReadRepository.reasons(otherWindow, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL), 400))
                .thenReturn(ImmutableList.of("Because."));
        when(threadsAwaitingAnalysisService.find(otherWindow.from(), otherWindow.to(), CLASSIFICATION_PROMPT_ID))
                .thenReturn(ImmutableList.of());
        // Distinguishable failures, so the test can tell whose memo is whose.
        when(llmSummaryService.generate(any(), any(), any()))
                .thenThrow(new IllegalStateException("first exploded"))
                .thenThrow(new SlackException("second exploded"));

        service.runWindowRefresh(FROM, TO);
        service.runWindowRefresh(otherWindow.from(), otherWindow.to());

        // The second window's failure must not evict the first's, or the first would be retried on
        // the very next poll of a tab that is still looking at it.
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
        assertThat(service.failureFor(otherWindow, SUMMARY_PROMPT_ID, otherFingerprint))
                .isEqualTo(SummaryRefreshService.ERROR_SLACK);
    }

    @Test
    void theFailureMemoIsBoundedAndDropsTheOldestWindowFirst() {
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new IllegalStateException("model exploded"));
        when(summaryReadRepository.fingerprint(any(), any(), any()))
                .thenReturn(
                        new SummaryFingerprint(3, 2, LocalDate.of(2026, 3, 23).atTime(10, 0)));
        when(summaryReadRepository.breakdowns(any(), any(), any())).thenReturn(breakdowns());
        when(summaryReadRepository.reasons(any(), any(), any(), anyInt())).thenReturn(ImmutableList.of("Because."));
        when(threadsAwaitingAnalysisService.find(any(), any(), any())).thenReturn(ImmutableList.of());

        service.runWindowRefresh(FROM, TO);
        for (int i = 1; i <= SummaryRefreshService.MAX_REMEMBERED_FAILURES; i++) {
            service.runWindowRefresh(FROM.plusDays(i), TO.plusDays(i));
        }

        // The first window was pushed out; the most recent ones are still remembered.
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT)).isNull();
        assertThat(service.failureFor(
                        new SummaryWindow(FROM.plusDays(1), TO.plusDays(1)), SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
        SummaryWindow newest = new SummaryWindow(
                FROM.plusDays(SummaryRefreshService.MAX_REMEMBERED_FAILURES),
                TO.plusDays(SummaryRefreshService.MAX_REMEMBERED_FAILURES));
        assertThat(service.failureFor(newest, SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
    }

    @Test
    void aFailedBackfillStopsTheRefreshBeforeItAsksTheModel() {
        doThrow(new SlackException("slack is down")).when(analysisService).backfillWindow(FROM, TO);

        service.runWindowRefresh(FROM, TO);

        verifyNoInteractions(llmSummaryService);
        verify(asyncJobRepository).deleteJob("analysis");
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_SLACK);
    }

    @Test
    void theRecordedErrorNeverCarriesTheRawExceptionMessage() {
        // The memo is rendered verbatim to every viewer of the page, so a jOOQ failure (full SQL) or a
        // model-client failure (endpoint, project) must be reduced to a fixed phrase.
        String sql = "select secret_column from secret_table where project = 'acme-prod'";
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new DataAccessException(sql));

        service.runWindowRefresh(FROM, TO);

        String error = service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT);
        assertThat(error).isEqualTo(SummaryRefreshService.ERROR_GENERIC).doesNotContain("secret", "acme");
    }

    @Test
    void knownFailuresMapToFixedUserFacingMessages() {
        assertThat(SummaryRefreshService.describe(new SlackException("ratelimited")))
                .isEqualTo(SummaryRefreshService.ERROR_SLACK);
        // Wrapped causes are still recognised.
        assertThat(SummaryRefreshService.describe(
                        new RuntimeException("wrapper", new SlackException(new RuntimeException("socket")))))
                .isEqualTo(SummaryRefreshService.ERROR_SLACK);
        assertThat(SummaryRefreshService.describe(new AnalysisPromptLoadException("no prompt in use")))
                .isEqualTo(SummaryRefreshService.ERROR_PROMPT);
        assertThat(SummaryRefreshService.describe(new RuntimeException("io", new InterruptedException())))
                .isEqualTo(SummaryRefreshService.ERROR_INTERRUPTED);
        assertThat(SummaryRefreshService.describe(new DataAccessException("select * from ticket")))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
        assertThat(SummaryRefreshService.describe(new RuntimeException()))
                .isEqualTo(SummaryRefreshService.ERROR_GENERIC);
    }

    @Test
    void aMissingSummaryPromptIsReportedAsAPromptProblem() {
        // The summary prompt vanishes between the page's check and the refresh: the page shows the
        // prompt message, and nothing is pinned (there is no prompt id to pin it to).
        when(analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY)).thenReturn(null);

        service.runWindowRefresh(FROM, TO);

        verify(summarySnapshotRepository, never()).upsert(any());
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT)).isNull();
    }

    @Test
    void aTicketClosedDuringTheFirstPassIsClassifiedByASecondPass() {
        ThreadToAnalyze lateCloser = new ThreadToAnalyze(99L, "ts-99", CHANNEL);
        // Nothing awaits before the first pass; by the time it finishes, ticket 99 has closed.
        when(threadsAwaitingAnalysisService.find(FROM, TO, CLASSIFICATION_PROMPT_ID))
                .thenReturn(ImmutableList.of())
                .thenReturn(ImmutableList.of(lateCloser));
        when(llmSummaryService.generate(any(), any(), any())).thenReturn("the prose");

        service.runWindowRefresh(FROM, TO);

        // Two passes, both before the summary is generated from the post-backfill state.
        InOrder inOrder = Mockito.inOrder(analysisService, llmSummaryService);
        inOrder.verify(analysisService, times(2)).backfillWindow(FROM, TO);
        inOrder.verify(llmSummaryService).generate(any(), any(), any());
        verify(summarySnapshotRepository).upsert(any());
    }

    @Test
    void noThirdPassRunsEvenIfTheSecondLeavesGapsBehind() {
        ThreadToAnalyze lateCloser = new ThreadToAnalyze(99L, "ts-99", CHANNEL);
        when(threadsAwaitingAnalysisService.find(FROM, TO, CLASSIFICATION_PROMPT_ID))
                .thenReturn(ImmutableList.of())
                .thenReturn(ImmutableList.of(lateCloser))
                // Were a third pass to be considered, this is what it would see: another new arrival.
                .thenReturn(ImmutableList.of(lateCloser, new ThreadToAnalyze(100L, "ts-100", CHANNEL)));
        when(llmSummaryService.generate(any(), any(), any())).thenReturn("the prose");

        service.runWindowRefresh(FROM, TO);

        verify(analysisService, times(2)).backfillWindow(FROM, TO);
        // The gaps are only re-read once, between the two passes.
        verify(threadsAwaitingAnalysisService, times(2)).find(FROM, TO, CLASSIFICATION_PROMPT_ID);
        verify(summarySnapshotRepository).upsert(any());
    }

    @Test
    void gapsTheFirstPassAlreadyGaveUpOnDoNotTriggerASecondPass() {
        // Ticket 42's thread is gone: it awaits classification before and after the pass. Retrying it
        // would fail the same way, so a second pass is only for tickets the first could not have seen.
        ThreadToAnalyze unclassifiable = new ThreadToAnalyze(42L, "ts-42", CHANNEL);
        when(threadsAwaitingAnalysisService.find(FROM, TO, CLASSIFICATION_PROMPT_ID))
                .thenReturn(ImmutableList.of(unclassifiable));
        when(llmSummaryService.generate(any(), any(), any())).thenReturn("the prose");

        service.runWindowRefresh(FROM, TO);

        verify(analysisService, times(1)).backfillWindow(FROM, TO);
        verify(summarySnapshotRepository).upsert(any());
    }

    @Test
    void anInterruptedSecondPassAbortsTheRefreshWithoutSummarising() {
        when(threadsAwaitingAnalysisService.find(FROM, TO, CLASSIFICATION_PROMPT_ID))
                .thenReturn(ImmutableList.of())
                .thenReturn(ImmutableList.of(new ThreadToAnalyze(99L, "ts-99", CHANNEL)));
        doAnswer(_ -> null)
                .doAnswer(_ -> {
                    Thread.currentThread().interrupt();
                    return null;
                })
                .when(analysisService)
                .backfillWindow(FROM, TO);

        try {
            service.runWindowRefresh(FROM, TO);

            verifyNoInteractions(llmSummaryService, summarySnapshotRepository);
            verify(asyncJobRepository).deleteJob("analysis");
            assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                    .isNull();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void anInterruptedBackfillAbortsTheRefreshWithoutSummarising() {
        doAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    return null;
                })
                .when(analysisService)
                .backfillWindow(FROM, TO);

        try {
            service.runWindowRefresh(FROM, TO);

            // A partial backfill must not be summarised: the snapshot's fingerprint would mark the
            // unclassified tickets as gaps and be served as ready after restart.
            verifyNoInteractions(llmSummaryService, summarySnapshotRepository);
            verify(asyncJobRepository).deleteJob("analysis");
            assertThat(service.status().running()).isFalse();
            // Nothing is pinned, so the next visit starts a fresh refresh.
            assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                    .isNull();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsAnEmptySummaryRatherThanCachingIt() {
        when(llmSummaryService.generate(any(), any(), any())).thenReturn("   ");

        service.runWindowRefresh(FROM, TO);

        verify(summarySnapshotRepository, never()).upsert(any());
        assertThat(service.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                .isEqualTo(SummaryRefreshService.ERROR_EMPTY_SUMMARY);
    }

    /** A clock the test moves by hand; the service only ever asks it for the instant. */
    private static final class SteppingClock extends Clock {
        private Instant now;

        SteppingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static SummaryBreakdowns breakdowns() {
        return new SummaryBreakdowns(
                WINDOW,
                3,
                2,
                ImmutableList.of(new SummaryCount("Knowledge Gap", 2)),
                ImmutableList.of(new SummaryCount("Build & CI", 2)),
                ImmutableList.of(new SummaryCount("Build & CI", 2)),
                ImmutableList.of(new SummaryCount("pipelines", 2)),
                ImmutableList.of(new SummaryCount("team-a", 3)),
                ImmutableList.of(new SummaryCount("Alpha", 1)));
    }
}
