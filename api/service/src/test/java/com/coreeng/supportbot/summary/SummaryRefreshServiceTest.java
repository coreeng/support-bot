package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptRepository;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.WindowAnalysisRunner;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.SummaryProps;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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

    @Mock
    private AsyncJobRepository asyncJobRepository;

    @Mock
    private AnalysisService analysisService;

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

    private SummaryRefreshService service;

    @BeforeEach
    void setUp() {
        SlackChannelRegistry channelRegistry = new SlackChannelRegistry(
                new SlackTicketsProps(CHANNEL, List.of(), "eyes", "ticket", "white_check_mark", "rocket"));
        service = new SummaryRefreshService(
                asyncJobRepository,
                analysisService,
                analysisPromptRepository,
                summaryReadRepository,
                summarySnapshotRepository,
                llmSummaryService,
                channelRegistry,
                new SummaryProps(true, 400),
                applicationContext);

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
                        WINDOW, SUMMARY_PROMPT_ID, "2@2026-03-23T10:00", "the prose", "model-a", null));
        assertThat(service.status().running()).isFalse();
        verify(asyncJobRepository).deleteJob("analysis");
        assertThat(service.failureFor(WINDOW, "2@2026-03-23T10:00")).isNull();
    }

    @Test
    void releasesTheLockAndRecordsTheErrorWhenGenerationFails() {
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new IllegalStateException("model exploded"));

        service.runWindowRefresh(FROM, TO);

        // The lock must go back even on failure, or the page would report `generating` forever.
        verify(asyncJobRepository).deleteJob("analysis");
        verify(summarySnapshotRepository, never()).upsert(any());
        assertThat(service.status().running()).isFalse();
        assertThat(service.failureFor(WINDOW, "2@2026-03-23T10:00")).isEqualTo("model exploded");
    }

    @Test
    void aRecordedFailureOnlyAppliesToTheDataThatProducedIt() {
        when(llmSummaryService.generate(any(), any(), any())).thenThrow(new IllegalStateException("model exploded"));

        service.runWindowRefresh(FROM, TO);

        // A different fingerprint means the window's data moved on: the failure is stale, so a retry
        // is warranted rather than a sticky error.
        assertThat(service.failureFor(WINDOW, "9@2026-04-01T10:00")).isNull();
        assertThat(service.failureFor(new SummaryWindow(FROM, TO.plusDays(1)), "2@2026-03-23T10:00"))
                .isNull();
    }

    @Test
    void aFailedBackfillStopsTheRefreshBeforeItAsksTheModel() {
        doThrow(new IllegalStateException("slack is down"))
                .when(analysisService)
                .backfillWindow(FROM, TO);

        service.runWindowRefresh(FROM, TO);

        verifyNoInteractions(llmSummaryService);
        verify(asyncJobRepository).deleteJob("analysis");
        assertThat(service.failureFor(WINDOW, "2@2026-03-23T10:00")).isEqualTo("slack is down");
    }

    @Test
    void rejectsAnEmptySummaryRatherThanCachingIt() {
        when(llmSummaryService.generate(any(), any(), any())).thenReturn("   ");

        service.runWindowRefresh(FROM, TO);

        verify(summarySnapshotRepository, never()).upsert(any());
        assertThat(service.failureFor(WINDOW, "2@2026-03-23T10:00")).isNotNull();
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
