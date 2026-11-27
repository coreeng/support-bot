package com.coreeng.supportbot.docanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisService;
import com.coreeng.supportbot.config.AsyncConfig;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.SummaryAreaProps;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunService.StartResult;
import com.coreeng.supportbot.summary.LlmSummaryService;
import com.coreeng.supportbot.summary.SummaryReadRepository;
import com.coreeng.supportbot.summary.SummaryRefreshService;
import com.coreeng.supportbot.summary.SummaryScope;
import com.coreeng.supportbot.summary.SummarySnapshotRepository;
import com.coreeng.supportbot.summary.SummaryWindow;
import com.coreeng.supportbot.teamgroups.TeamGroupService;
import com.coreeng.supportbot.ticket.TicketId;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Runs both kinds of run on the executors the application builds, sharing one {@code async_job} table. */
class DocAnalysisRunOverlapTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 2);
    private static final LocalDate TO = LocalDate.of(2026, 11, 15);
    private static final String CHANNEL = "C123";
    private static final long TIMEOUT_SECONDS = 10;

    private final AsyncConfig asyncConfig = new AsyncConfig();
    private final Executor analysisExecutor = asyncConfig.analysisTaskExecutor();
    private final Executor docAnalysisExecutor = asyncConfig.docAnalysisTaskExecutor();
    private final InMemoryAsyncJobRepository asyncJobs = new InMemoryAsyncJobRepository();
    private final SlackChannelRegistry channels = new SlackChannelRegistry(
            new SlackTicketsProps(CHANNEL, List.of(), "eyes", "ticket", "white_check_mark", "rocket"));

    private final DocClassifier classifier = mock(DocClassifier.class);
    private final DocAnalysisRepository docRepository = mock(DocAnalysisRepository.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);

    private final CountDownLatch docRunClassifying = new CountDownLatch(1);
    private final CountDownLatch summaryRefreshClassifying = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    private DocAnalysisRunService docRuns;
    private SummaryRefreshService summaryRefreshes;

    @BeforeEach
    void setUp() {
        when(classifier.currentPromptId()).thenReturn("prompt-1");
        when(docRepository.findDue(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(List.of(new TicketToClassify(new TicketId(1), CHANNEL, "1700000000.000001")));
        docRuns = new DocAnalysisRunService(
                asyncJobs,
                docRepository,
                classifier,
                new StubDocumentationSource(),
                channels,
                DocAnalysisRunServiceTest.props(),
                DocAnalysisRunServiceTest.llmProps(),
                docAnalysisExecutor,
                Clock.systemUTC(),
                duration -> {});
        summaryRefreshes = new SummaryRefreshService(
                asyncJobs,
                analysisService,
                mock(ThreadsAwaitingAnalysisService.class),
                mock(SummaryReadRepository.class),
                mock(SummarySnapshotRepository.class),
                mock(LlmSummaryService.class),
                channels,
                mock(TeamGroupService.class),
                new SummaryAreaProps(
                        new SummaryAreaProps.Sanitisation(List.of(), List.of()),
                        new SummaryAreaProps.Page(400, Duration.ofMinutes(15)),
                        new SummaryAreaProps.OfflineExport("classpath:placeholder-analysis-bundle.zip")),
                analysisExecutor,
                Clock.systemUTC());
    }

    @AfterEach
    void shutDown() {
        release.countDown();
        ((ThreadPoolTaskExecutor) analysisExecutor).shutdown();
        ((ThreadPoolTaskExecutor) docAnalysisExecutor).shutdown();
    }

    @Test
    void aSupportSummaryRefreshStartsAndRunsWhileADocumentationRunIsActive() throws InterruptedException {
        when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            docRunClassifying.countDown();
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            throw new IllegalStateException("released");
        });
        when(analysisService.currentPromptId()).thenAnswer(invocation -> {
            summaryRefreshClassifying.countDown();
            return "classification-prompt";
        });

        assertThat(docRuns.start(FROM, TO)).isEqualTo(StartResult.STARTED);
        assertThat(docRunClassifying.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThat(summaryRefreshes.start(SummaryScope.all(new SummaryWindow(FROM, TO))))
                .isTrue();
        assertThat(summaryRefreshClassifying.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();
        assertThat(docRuns.state().running()).isTrue();
    }

    @Test
    void aDocumentationRunStartsAndRunsWhileASupportSummaryRefreshIsActive() throws InterruptedException {
        when(analysisService.currentPromptId()).thenAnswer(invocation -> {
            summaryRefreshClassifying.countDown();
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            throw new IllegalStateException("released");
        });
        when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            docRunClassifying.countDown();
            throw new IllegalStateException("done");
        });

        assertThat(summaryRefreshes.start(SummaryScope.all(new SummaryWindow(FROM, TO))))
                .isTrue();
        assertThat(summaryRefreshClassifying.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();

        assertThat(docRuns.start(FROM, TO)).isEqualTo(StartResult.STARTED);
        assertThat(docRunClassifying.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(summaryRefreshes.status().running()).isTrue();
    }
}
