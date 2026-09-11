package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.analysis.AnalysisService.AnalysisStatus;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.analysis.llm.LlmAnalysisService;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private AsyncJobRepository asyncJobRepository;

    @Mock
    private ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService;

    @Mock
    private LlmAnalysisService llmAnalysisService;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private AnalysisPromptRepository analysisPromptRepository;

    /** Stands in for the {@code analysisTaskExecutor}; nothing runs unless a test runs it. */
    @Mock
    private Executor analysisExecutor;

    private static final String PROMPT_TEXT = "Test prompt content";

    private LlmProps llmProps;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        llmProps = new LlmProps(
                LlmProvider.VERTEX,
                "gemini-2.5-flash",
                Duration.ofMillis(100),
                new LlmProps.Vertex("test-project", "europe-west2"),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new LlmProps.Stub(false));

        service = new AnalysisService(
                asyncJobRepository,
                threadsAwaitingAnalysisService,
                llmAnalysisService,
                analysisRepository,
                analysisPromptRepository,
                llmProps,
                analysisExecutor);
    }

    private static String daysPayload(int days) {
        return AnalysisJobData.days(days);
    }

    private void givenPromptInUse() {
        when(analysisPromptRepository.findInUse(AnalysisPromptType.CLASSIFICATION))
                .thenReturn(new AnalysisPrompt(1, PROMPT_TEXT));
    }

    @Test
    void start_shouldStartJobWhenNotRunning() {
        // given
        int days = 7;
        when(asyncJobRepository.tryStartJob("analysis", daysPayload(7))).thenReturn(true);

        // when
        boolean result = service.start(days);

        // then
        assertThat(result).isTrue();
        verify(asyncJobRepository).tryStartJob("analysis", daysPayload(7));
        verify(analysisExecutor).execute(any());
    }

    @Test
    void start_shouldRunTheAnalysisOnTheExecutorNotInline() {
        // The request thread must return as soon as the run is handed over; the analysis itself
        // happens when the executor gets round to the task.
        givenPromptInUse();
        when(asyncJobRepository.tryStartJob("analysis", daysPayload(7))).thenReturn(true);
        when(threadsAwaitingAnalysisService.find(eq(7), anyString())).thenReturn(ImmutableList.of());

        service.start(7);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(analysisExecutor).execute(task.capture());
        verifyNoInteractions(threadsAwaitingAnalysisService, analysisRepository);

        task.getValue().run();

        verify(threadsAwaitingAnalysisService).find(eq(7), anyString());
        verify(asyncJobRepository).deleteJob("analysis");
    }

    @Test
    void start_shouldReturnFalseWhenJobAlreadyRunning() {
        // given
        int days = 7;
        when(asyncJobRepository.tryStartJob("analysis", daysPayload(7))).thenReturn(false);

        // when
        boolean result = service.start(days);

        // then
        assertThat(result).isFalse();
        verify(asyncJobRepository).tryStartJob("analysis", daysPayload(7));
        verifyNoInteractions(analysisExecutor);
    }

    @Test
    void resume_shouldRunOnTheExecutorWithoutClaimingTheLock() {
        // The startup resume already found the lock row; claiming it again would fail on the unique
        // constraint and never run the job.
        assertThat(service.resume(14)).isTrue();

        verify(analysisExecutor).execute(any());
        verify(asyncJobRepository, never()).tryStartJob(any(), any());
    }

    @Test
    void resume_shouldDeleteJobAndReturnFalseWhenExecutorRejects() {
        doThrow(new TaskRejectedException("Executor queue full"))
                .when(analysisExecutor)
                .execute(any());

        assertThat(service.resume(7)).isFalse();

        verify(asyncJobRepository).deleteJob("analysis");
    }

    @Test
    void getStatus_shouldReturnCurrentStatus() {
        // when
        AnalysisStatus status = service.getStatus();

        // then
        assertThat(status).isNotNull();
        assertThat(status.running()).isFalse();
        assertThat(status.jobId()).isNull();
        assertThat(status.exportedCount()).isNull();
        assertThat(status.analyzedCount()).isNull();
        assertThat(status.error()).isNull();
    }

    @Test
    void analysisStatus_shouldHaveCorrectFields() {
        // given
        AnalysisStatus status = new AnalysisStatus("job-1", 10, 5, true, null);

        // then
        assertThat(status.jobId()).isEqualTo("job-1");
        assertThat(status.exportedCount()).isEqualTo(10);
        assertThat(status.analyzedCount()).isEqualTo(5);
        assertThat(status.running()).isTrue();
        assertThat(status.error()).isNull();
    }

    @Test
    void analysisStatus_shouldHandleErrorState() {
        // given
        AnalysisStatus status = new AnalysisStatus("job-1", 0, 0, false, "LLM API error");

        // then
        assertThat(status.jobId()).isEqualTo("job-1");
        assertThat(status.exportedCount()).isEqualTo(0);
        assertThat(status.analyzedCount()).isEqualTo(0);
        assertThat(status.running()).isFalse();
        assertThat(status.error()).isEqualTo("LLM API error");
    }

    @Test
    void start_shouldUseCorrectDaysParameter() {
        // given
        when(asyncJobRepository.tryStartJob("analysis", daysPayload(30))).thenReturn(true);

        // when
        service.start(30);

        // then
        verify(asyncJobRepository).tryStartJob("analysis", daysPayload(30));
    }

    @Test
    void start_shouldHandleSingleDayParameter() {
        // given
        when(asyncJobRepository.tryStartJob("analysis", daysPayload(1))).thenReturn(true);

        // when
        service.start(1);

        // then
        verify(asyncJobRepository).tryStartJob("analysis", daysPayload(1));
    }

    @Test
    void getStatus_shouldReturnNonNullStatus() {
        // when
        AnalysisStatus status = service.getStatus();

        // then
        assertThat(status).isNotNull();
    }

    @Test
    void start_shouldNotInteractWithRepositoryWhenJobStartFails() {
        // given
        when(asyncJobRepository.tryStartJob("analysis", daysPayload(7))).thenReturn(false);

        // when
        service.start(7);

        // then
        verify(asyncJobRepository).tryStartJob("analysis", daysPayload(7));
        verifyNoInteractions(threadsAwaitingAnalysisService);
        verifyNoInteractions(llmAnalysisService);
        verifyNoInteractions(analysisRepository);
    }

    @Test
    void computePromptId_returnsSameHashForSameContent() {
        // given
        String prompt = "Analyse the support thread and classify the issue.";

        // when
        String hash1 = AnalysisService.computePromptId(prompt);
        String hash2 = AnalysisService.computePromptId(prompt);

        // then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    void computePromptId_returnsDifferentHashForDifferentContent() {
        String hash1 = AnalysisService.computePromptId("Version 1 of the prompt");
        String hash2 = AnalysisService.computePromptId("Version 2 of the prompt");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    // --- runAnalysis tests (the body the executor runs) ---

    @Test
    void runAnalysis_analyzesThreadsAndPersists() {
        // given
        givenPromptInUse();
        when(threadsAwaitingAnalysisService.find(eq(7), anyString()))
                .thenReturn(ImmutableList.of(
                        new ThreadToAnalyze(1L, "ts1", "C123456"), new ThreadToAnalyze(2L, "ts2", "C123456")));
        when(llmAnalysisService.analyzeThread(eq("C123456"), eq("ts1"), eq(1L), anyString()))
                .thenReturn(new AnalysisRecord(1, "Bug", "Config", "networking", "Issue 1", null));
        when(llmAnalysisService.analyzeThread(eq("C123456"), eq("ts2"), eq(2L), anyString()))
                .thenReturn(new AnalysisRecord(2, "Knowledge Gap", "Monitoring", "compute", "Issue 2", null));

        // when
        service.runAnalysis(7);

        // then — both records upserted with promptId stamped (SHA-256 = 64-char hex)
        ArgumentCaptor<AnalysisRecord> captor = ArgumentCaptor.forClass(AnalysisRecord.class);
        verify(analysisRepository, times(2)).upsert(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(r -> assertThat(r.promptId()).matches("[0-9a-f]{64}"));
        assertThat(captor.getAllValues().get(0).ticketId()).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).ticketId()).isEqualTo(2);

        // job cleaned up
        verify(asyncJobRepository).deleteJob("analysis");

        // final status
        AnalysisStatus status = service.getStatus();
        assertThat(status.running()).isFalse();
        assertThat(status.analyzedCount()).isEqualTo(2);
        assertThat(status.exportedCount()).isEqualTo(2);
        assertThat(status.error()).isNull();
    }

    @Test
    void runAnalysis_skipsInvalidRecords() {
        // given — first thread returns null (LLM failure), second returns valid record
        givenPromptInUse();
        when(threadsAwaitingAnalysisService.find(eq(7), anyString()))
                .thenReturn(ImmutableList.of(
                        new ThreadToAnalyze(1L, "ts1", "C123456"), new ThreadToAnalyze(2L, "ts2", "C123456")));
        when(llmAnalysisService.analyzeThread(eq("C123456"), eq("ts1"), eq(1L), anyString()))
                .thenReturn(null);
        when(llmAnalysisService.analyzeThread(eq("C123456"), eq("ts2"), eq(2L), anyString()))
                .thenReturn(new AnalysisRecord(2, "Bug", "Config", "networking", "Issue", null));

        // when
        service.runAnalysis(7);

        // then — only 1 upsert (the valid record)
        verify(analysisRepository, times(1)).upsert(any(AnalysisRecord.class));
        verify(asyncJobRepository).deleteJob("analysis");

        AnalysisStatus status = service.getStatus();
        assertThat(status.analyzedCount()).isEqualTo(1);
        assertThat(status.exportedCount()).isEqualTo(2);
    }

    @Test
    void start_shouldDeleteJobAndReturnFalse_whenExecutorRejectsTask() {
        // given
        when(asyncJobRepository.tryStartJob("analysis", daysPayload(7))).thenReturn(true);
        doThrow(new TaskRejectedException("Executor queue full"))
                .when(analysisExecutor)
                .execute(any());

        // when
        boolean result = service.start(7);

        // then
        assertThat(result).isFalse();
        verify(asyncJobRepository).deleteJob("analysis");
    }

    @Test
    void runAnalysis_continuesAfterPerThreadException() {
        // given — first thread throws, second and third return valid records
        givenPromptInUse();
        when(threadsAwaitingAnalysisService.find(eq(7), anyString()))
                .thenReturn(ImmutableList.of(
                        new ThreadToAnalyze(1L, "ts1", "C123456"),
                        new ThreadToAnalyze(2L, "ts2", "C123456"),
                        new ThreadToAnalyze(3L, "ts3", "C123456")));
        when(llmAnalysisService.analyzeThread(eq("C123456"), eq("ts1"), eq(1L), anyString()))
                .thenThrow(new RuntimeException("Slack timeout for thread ts1"));
        when(llmAnalysisService.analyzeThread(eq("C123456"), eq("ts2"), eq(2L), anyString()))
                .thenReturn(new AnalysisRecord(2, "Bug", "Config", "networking", "Issue 2", null));
        when(llmAnalysisService.analyzeThread(eq("C123456"), eq("ts3"), eq(3L), anyString()))
                .thenReturn(new AnalysisRecord(3, "Knowledge Gap", "Monitoring", "compute", "Issue 3", null));

        // when
        service.runAnalysis(7);

        // then — 2 records persisted (skipping the failed one)
        verify(analysisRepository, times(2)).upsert(any(AnalysisRecord.class));
        verify(asyncJobRepository).deleteJob("analysis");

        AnalysisStatus status = service.getStatus();
        assertThat(status.analyzedCount()).isEqualTo(2);
        assertThat(status.exportedCount()).isEqualTo(3);
        assertThat(status.error()).isNull();
    }

    @Test
    void runAnalysis_setsErrorOnPromptLoadFailure() {
        // given — no prompt version is marked as in use
        when(analysisPromptRepository.findInUse(AnalysisPromptType.CLASSIFICATION))
                .thenReturn(null);

        // when
        service.runAnalysis(7);

        // then — error status set, job still cleaned up
        AnalysisStatus status = service.getStatus();
        assertThat(status.running()).isFalse();
        assertThat(status.error()).isNotNull();
        verify(asyncJobRepository).deleteJob("analysis");
        verifyNoInteractions(llmAnalysisService);
    }

    @Test
    void loadPrompt_returnsContentOfVersionInUse() {
        givenPromptInUse();

        assertThat(service.loadPrompt()).isEqualTo(PROMPT_TEXT);
    }

    @Test
    void loadPrompt_throwsWhenNoVersionIsInUse() {
        when(analysisPromptRepository.findInUse(AnalysisPromptType.CLASSIFICATION))
                .thenReturn(null);

        assertThatThrownBy(service::loadPrompt)
                .isInstanceOf(AnalysisPromptLoadException.class)
                .hasMessageContaining("marked as in use");
    }

    @Test
    void loadPrompt_wrapsDatabaseFailure() {
        // Keeps the ANALYSIS_PROMPT_LOAD_FAILED contract the UI relies on when the DB is unreachable.
        when(analysisPromptRepository.findInUse(AnalysisPromptType.CLASSIFICATION))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(service::loadPrompt)
                .isInstanceOf(AnalysisPromptLoadException.class)
                .hasRootCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void currentPromptId_isTheHashOfThePromptInUse() {
        givenPromptInUse();

        assertThat(service.currentPromptId()).isEqualTo(AnalysisService.computePromptId(PROMPT_TEXT));
    }

    @Test
    void currentPromptId_throwsWhenNoVersionIsInUse() {
        when(analysisPromptRepository.findInUse(AnalysisPromptType.CLASSIFICATION))
                .thenReturn(null);

        assertThatThrownBy(service::currentPromptId).isInstanceOf(AnalysisPromptLoadException.class);
    }

    @Test
    void inUsePrompt_returnsNullWhenNoVersionOfThatTypeIsInUse() {
        when(analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY)).thenReturn(null);

        assertThat(service.inUsePrompt(AnalysisPromptType.SUMMARY)).isNull();
    }

    @Test
    void inUsePrompt_wrapsDatabaseFailure() {
        when(analysisPromptRepository.findInUse(AnalysisPromptType.SUMMARY))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.inUsePrompt(AnalysisPromptType.SUMMARY))
                .isInstanceOf(AnalysisPromptLoadException.class)
                .hasMessageContaining("summary")
                .hasRootCauseInstanceOf(DataAccessResourceFailureException.class);
    }
}
