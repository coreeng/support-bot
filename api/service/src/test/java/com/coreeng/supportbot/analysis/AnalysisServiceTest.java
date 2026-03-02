package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.analysis.AnalysisService.AnalysisStatus;
import com.coreeng.supportbot.analysis.llm.LlmAnalysisService;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository.AsyncJob;
import com.coreeng.supportbot.config.AnalysisProps;
import com.coreeng.supportbot.config.AnalysisProps.Prompt;
import com.coreeng.supportbot.config.AnalysisProps.Vertex;
import com.coreeng.supportbot.config.SlackTicketsProps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

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
    private ApplicationContext applicationContext;

    private AnalysisProps analysisProps;
    private SlackTicketsProps slackTicketsProps;
    private AnalysisService service;
    private Path tempPromptFile;
    private Path tempPromptIdFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create temporary files for testing
        tempPromptFile = Files.createTempFile("test-prompt", ".md");
        tempPromptIdFile = Files.createTempFile("test-prompt-id", ".yaml");

        Files.writeString(tempPromptFile, "Test prompt content");
        Files.writeString(tempPromptIdFile, "id: test-prompt-v1");

        Vertex vertex = new Vertex("test-project", "europe-west2", "gemini-2.5-flash", Duration.ofMillis(100));
        Prompt prompt =
                new Prompt(tempPromptFile.toString(), tempPromptIdFile.toString(), ""); // prompt is loaded from file
        analysisProps = new AnalysisProps(vertex, prompt);
        slackTicketsProps = new SlackTicketsProps("C123456", "eyes", "ticket", "white_check_mark", "rocket");

        service = new AnalysisService(
                asyncJobRepository,
                threadsAwaitingAnalysisService,
                llmAnalysisService,
                analysisRepository,
                analysisProps,
                slackTicketsProps,
                applicationContext);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temporary files
        if (tempPromptFile != null) {
            Files.deleteIfExists(tempPromptFile);
        }
        if (tempPromptIdFile != null) {
            Files.deleteIfExists(tempPromptIdFile);
        }
    }

    @Test
    void start_shouldStartJobWhenNotRunning() {
        // given
        int days = 7;
        when(asyncJobRepository.tryStartJob("analysis", "7")).thenReturn(true);
        when(applicationContext.getBean(AnalysisService.class)).thenReturn(service);

        // when
        boolean result = service.start(days);

        // then
        assertThat(result).isTrue();
        verify(asyncJobRepository).tryStartJob("analysis", "7");
        verify(applicationContext).getBean(AnalysisService.class);
    }

    @Test
    void start_shouldReturnFalseWhenJobAlreadyRunning() {
        // given
        int days = 7;
        when(asyncJobRepository.tryStartJob("analysis", "7")).thenReturn(false);

        // when
        boolean result = service.start(days);

        // then
        assertThat(result).isFalse();
        verify(asyncJobRepository).tryStartJob("analysis", "7");
        verifyNoInteractions(applicationContext);
    }

    @Test
    void resumeAnalysisOnStartup_shouldResumeWhenJobExists() {
        // given
        AsyncJob existingJob = new AsyncJob("analysis", "14", Instant.now());
        when(asyncJobRepository.findJob("analysis")).thenReturn(existingJob);
        when(applicationContext.getBean(AnalysisService.class)).thenReturn(service);

        // when
        service.resumeAnalysisOnStartup();

        // then
        verify(asyncJobRepository).findJob("analysis");
        verify(applicationContext).getBean(AnalysisService.class);
    }

    @Test
    void resumeAnalysisOnStartup_shouldDoNothingWhenNoJobExists() {
        // given
        when(asyncJobRepository.findJob("analysis")).thenReturn(null);

        // when
        service.resumeAnalysisOnStartup();

        // then
        verify(asyncJobRepository).findJob("analysis");
        verifyNoInteractions(applicationContext);
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
        when(asyncJobRepository.tryStartJob("analysis", "30")).thenReturn(true);
        when(applicationContext.getBean(AnalysisService.class)).thenReturn(service);

        // when
        service.start(30);

        // then
        verify(asyncJobRepository).tryStartJob("analysis", "30");
    }

    @Test
    void start_shouldHandleSingleDayParameter() {
        // given
        when(asyncJobRepository.tryStartJob("analysis", "1")).thenReturn(true);
        when(applicationContext.getBean(AnalysisService.class)).thenReturn(service);

        // when
        service.start(1);

        // then
        verify(asyncJobRepository).tryStartJob("analysis", "1");
    }

    @Test
    void resumeAnalysisOnStartup_shouldParseJobDataCorrectly() {
        // given
        AsyncJob existingJob = new AsyncJob("analysis", "365", Instant.now());
        when(asyncJobRepository.findJob("analysis")).thenReturn(existingJob);
        when(applicationContext.getBean(AnalysisService.class)).thenReturn(service);

        // when
        service.resumeAnalysisOnStartup();

        // then
        verify(asyncJobRepository).findJob("analysis");
        verify(applicationContext).getBean(AnalysisService.class);
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
        when(asyncJobRepository.tryStartJob("analysis", "7")).thenReturn(false);

        // when
        service.start(7);

        // then
        verify(asyncJobRepository).tryStartJob("analysis", "7");
        verifyNoInteractions(threadsAwaitingAnalysisService);
        verifyNoInteractions(llmAnalysisService);
        verifyNoInteractions(analysisRepository);
    }
}
