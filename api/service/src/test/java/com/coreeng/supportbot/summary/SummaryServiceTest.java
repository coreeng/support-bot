package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptRepository;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 10);
    private static final LocalDate TO = LocalDate.of(2026, 3, 23);
    private static final SummaryWindow WINDOW = new SummaryWindow(FROM, TO);
    private static final String CHANNEL = "C123456";
    private static final String CLASSIFICATION_PROMPT = "classify this";
    private static final String SUMMARY_PROMPT = "summarise this";
    private static final String FINGERPRINT = "2@2026-03-23T10:00";

    private static final String CLASSIFICATION_PROMPT_ID = AnalysisService.computePromptId(CLASSIFICATION_PROMPT);
    private static final String SUMMARY_PROMPT_ID = AnalysisService.computePromptId(SUMMARY_PROMPT);

    @Mock
    private AnalysisService analysisService;

    @Mock
    private AnalysisPromptRepository analysisPromptRepository;

    @Mock
    private SummaryReadRepository summaryReadRepository;

    @Mock
    private SummarySnapshotRepository summarySnapshotRepository;

    @Mock
    private SummaryRefresher summaryRefresher;

    private SummaryService service;

    @BeforeEach
    void setUp() {
        SlackChannelRegistry channelRegistry = new SlackChannelRegistry(
                new SlackTicketsProps(CHANNEL, List.of(), "eyes", "ticket", "white_check_mark", "rocket"));
        service = new SummaryService(
                analysisService,
                analysisPromptRepository,
                summaryReadRepository,
                summarySnapshotRepository,
                summaryRefresher,
                channelRegistry);

        lenient().when(analysisService.loadPrompt()).thenReturn(CLASSIFICATION_PROMPT);
        lenient()
                .when(analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY))
                .thenReturn(new AnalysisPrompt(1, SUMMARY_PROMPT));
        lenient()
                .when(summaryReadRepository.breakdowns(WINDOW, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(breakdowns());
        lenient()
                .when(summaryReadRepository.fingerprint(WINDOW, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(new SummaryFingerprint(2, LocalDate.of(2026, 3, 23).atTime(10, 0)));
        lenient().when(summaryRefresher.status()).thenReturn(idle());
        lenient()
                .when(analysisService.getStatus())
                .thenReturn(new AnalysisService.AnalysisStatus(null, null, null, false, null));
    }

    @Test
    void servesTheCachedSummaryWhenTheFingerprintMatches() {
        when(summarySnapshotRepository.find(WINDOW, SUMMARY_PROMPT_ID))
                .thenReturn(new SummarySnapshot(
                        WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT, "the prose", "model-a", Instant.EPOCH));

        SummaryService.SummaryResult result = service.get(FROM, TO);

        assertThat(result.summary()).isEqualTo(new SummaryState.Ready("the prose", "model-a", Instant.EPOCH));
        verify(summaryRefresher, never()).start(any());
    }

    @Test
    void regeneratesWhenTheFingerprintNoLongerMatches() {
        when(summarySnapshotRepository.find(WINDOW, SUMMARY_PROMPT_ID))
                .thenReturn(new SummarySnapshot(WINDOW, SUMMARY_PROMPT_ID, "1@older", "stale", "model-a", null));
        when(summaryRefresher.start(WINDOW)).thenReturn(true);

        assertThat(service.get(FROM, TO).summary())
                .isEqualTo(new SummaryState.Generating(SummaryState.Phase.CLASSIFYING, null, null));
    }

    @Test
    void backfillsWhenAClosedTicketAwaitsClassification() {
        // The analysis rows are unchanged since the snapshot, but a ticket has since closed unclassified:
        // the gap is part of the fingerprint, so the cached prose describes an incomplete window.
        when(summaryReadRepository.fingerprint(WINDOW, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(new SummaryFingerprint(2, LocalDate.of(2026, 3, 23).atTime(10, 0), 1, 74));
        when(summarySnapshotRepository.find(WINDOW, SUMMARY_PROMPT_ID))
                .thenReturn(new SummarySnapshot(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT, "prose", "model-a", null));
        when(summaryRefresher.start(WINDOW)).thenReturn(true);
        when(analysisService.getStatus()).thenReturn(new AnalysisService.AnalysisStatus("analysis", 5, 2, true, null));

        assertThat(service.get(FROM, TO).summary())
                .isEqualTo(new SummaryState.Generating(SummaryState.Phase.CLASSIFYING, 2, 5));
    }

    @Test
    void ignoresThePreviousRunsCountsUntilTheNewClassificationStarts() {
        // The refresh is dispatched asynchronously, so the first poll can land before classify() has
        // reset the analysis status: it still holds the last run's final counts with running=false.
        when(summarySnapshotRepository.find(WINDOW, SUMMARY_PROMPT_ID)).thenReturn(null);
        when(summaryRefresher.start(WINDOW)).thenReturn(true);
        when(analysisService.getStatus())
                .thenReturn(new AnalysisService.AnalysisStatus("analysis", 40, 40, false, null));

        assertThat(service.get(FROM, TO).summary())
                .isEqualTo(new SummaryState.Generating(SummaryState.Phase.CLASSIFYING, null, null));
    }

    @Test
    void servesTheCachedSummaryWhenTheOnlyGapsAreOnesTheBackfillAlreadyGaveUpOn() {
        // A ticket whose thread is gone can never be classified. The snapshot was generated after
        // attempting it, so its fingerprint already carries the gap — regenerating would loop forever.
        SummaryFingerprint withGap =
                new SummaryFingerprint(2, LocalDate.of(2026, 3, 23).atTime(10, 0), 1, 74);
        when(summaryReadRepository.fingerprint(WINDOW, CLASSIFICATION_PROMPT_ID, List.of(CHANNEL)))
                .thenReturn(withGap);
        when(summarySnapshotRepository.find(WINDOW, SUMMARY_PROMPT_ID))
                .thenReturn(new SummarySnapshot(
                        WINDOW, SUMMARY_PROMPT_ID, withGap.value(), "the prose", "model-a", Instant.EPOCH));

        assertThat(service.get(FROM, TO).summary())
                .isEqualTo(new SummaryState.Ready("the prose", "model-a", Instant.EPOCH));
        verify(summaryRefresher, never()).start(any());
    }

    @Test
    void reportsTheRunningRefreshWithoutStartingASecondOne() {
        when(summaryRefresher.status())
                .thenReturn(new SummaryRefreshStatus(WINDOW, SummaryState.Phase.SUMMARISING, true));

        assertThat(service.get(FROM, TO).summary())
                .isEqualTo(new SummaryState.Generating(SummaryState.Phase.SUMMARISING, null, null));
        verify(summaryRefresher, never()).start(any());
        // A poll during a run is the hot path: it must not pay for the summary prompt or the fingerprint.
        verify(analysisPromptRepository, never()).findInUse(any());
        verify(summaryReadRepository, never()).fingerprint(any(), any(), any());
    }

    @Test
    void reportsAFailedAttemptInsteadOfRetryingItEveryPoll() {
        when(summaryRefresher.failureFor(WINDOW, SUMMARY_PROMPT_ID, FINGERPRINT))
                .thenReturn("the model timed out");

        assertThat(service.get(FROM, TO).summary()).isEqualTo(new SummaryState.Unavailable("the model timed out"));
        verify(summaryRefresher, never()).start(any());
    }

    @Test
    void reportsUnavailableWhenNoSummaryPromptIsInUse() {
        when(analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY)).thenReturn(null);

        SummaryService.SummaryResult result = service.get(FROM, TO);

        assertThat(result.summary()).isInstanceOf(SummaryState.Unavailable.class);
        // The breakdowns are unaffected: a broken summary must never cost the caller the rest of the page.
        assertThat(result.breakdowns().totalTickets()).isEqualTo(3);
    }

    @Test
    void reportsGeneratingWhenAnotherVisitorWinsTheLockRace() {
        when(summarySnapshotRepository.find(WINDOW, SUMMARY_PROMPT_ID)).thenReturn(null);
        when(summaryRefresher.start(WINDOW)).thenReturn(false);
        when(summaryRefresher.status())
                .thenReturn(idle(), new SummaryRefreshStatus(WINDOW, SummaryState.Phase.SUMMARISING, true));

        assertThat(service.get(FROM, TO).summary())
                .isEqualTo(new SummaryState.Generating(SummaryState.Phase.SUMMARISING, null, null));
    }

    @Test
    void alwaysReturnsTheBreakdownsForTheRequestedWindow() {
        when(summarySnapshotRepository.find(WINDOW, SUMMARY_PROMPT_ID)).thenReturn(null);
        when(summaryRefresher.start(WINDOW)).thenReturn(true);

        SummaryBreakdowns result = service.get(FROM, TO).breakdowns();

        assertThat(result.window()).isEqualTo(WINDOW);
        assertThat(result.unclassifiedTickets()).isEqualTo(1);
    }

    private static SummaryRefreshStatus idle() {
        return SummaryRefreshStatus.IDLE;
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
