package com.coreeng.supportbot.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository.AsyncJob;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AnalysisJobResumerTest {

    @Mock
    private AsyncJobRepository asyncJobRepository;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private ObjectProvider<WindowAnalysisRunner> windowAnalysisRunner;

    @InjectMocks
    private AnalysisJobResumer resumer;

    @Test
    void resumesADaysRunWhenItsJobRowSurvivedTheRestart() {
        when(asyncJobRepository.findJob("analysis"))
                .thenReturn(new AsyncJob("analysis", AnalysisJobData.days(14), Instant.now()));

        resumer.resumeOnStartup();

        verify(analysisService).resume(14);
        verify(asyncJobRepository, never()).deleteJob(any());
    }

    @Test
    void stillResumesTheBareIntegerPayloadWrittenBeforeTheJsonForm() {
        // A row written by the previous release must survive the upgrade rather than be deleted as
        // corrupt.
        when(asyncJobRepository.findJob("analysis")).thenReturn(new AsyncJob("analysis", "365", Instant.now()));

        resumer.resumeOnStartup();

        verify(analysisService).resume(365);
    }

    @Test
    void doesNothingWhenNoJobExists() {
        when(asyncJobRepository.findJob("analysis")).thenReturn(null);

        resumer.resumeOnStartup();

        verify(asyncJobRepository).findJob("analysis");
        verifyNoInteractions(analysisService, windowAnalysisRunner);
    }

    @Test
    void handsAWindowJobToItsRunner() {
        // A window job belongs to the Support Summary feature; it must not be resumed as a days-based
        // run.
        String payload = AnalysisJobData.window(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));
        when(asyncJobRepository.findJob("analysis")).thenReturn(new AsyncJob("analysis", payload, Instant.now()));
        WindowAnalysisRunner runner = mock(WindowAnalysisRunner.class);
        when(windowAnalysisRunner.getIfAvailable()).thenReturn(runner);

        resumer.resumeOnStartup();

        verify(runner).runWindowRefresh(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));
        verify(analysisService, never()).resume(anyInt());
        verify(asyncJobRepository, never()).deleteJob(any());
    }

    @Test
    void deletesAWindowJobWhenTheSummaryFeatureIsOff() {
        // Nothing can run it, and leaving the row behind would hold the shared lock forever.
        String payload = AnalysisJobData.window(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));
        when(asyncJobRepository.findJob("analysis")).thenReturn(new AsyncJob("analysis", payload, Instant.now()));
        when(windowAnalysisRunner.getIfAvailable()).thenReturn(null);

        resumer.resumeOnStartup();

        verify(asyncJobRepository).deleteJob("analysis");
        verifyNoInteractions(analysisService);
    }

    @Test
    void deletesTheJobWhenItsDataIsCorrupt() {
        when(asyncJobRepository.findJob("analysis"))
                .thenReturn(new AsyncJob("analysis", "not-a-number", Instant.now()));

        resumer.resumeOnStartup();

        verify(asyncJobRepository).deleteJob("analysis");
        verifyNoInteractions(analysisService, windowAnalysisRunner);
    }

    @Test
    void survivesAFailingRepositoryRatherThanFailingTheBoot() {
        when(asyncJobRepository.findJob("analysis")).thenThrow(new IllegalStateException("db down"));

        resumer.resumeOnStartup();

        verifyNoInteractions(analysisService, windowAnalysisRunner);
    }
}
