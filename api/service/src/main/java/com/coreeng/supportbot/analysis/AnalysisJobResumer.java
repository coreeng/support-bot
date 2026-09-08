package com.coreeng.supportbot.analysis;

import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Resumes the analysis job a restart interrupted.
 *
 * <p>An {@code async_job} row that survives a pod restart is a run that never finished. On startup
 * the row is read back and handed to whoever owns that kind of run: a days-based run to {@link
 * AnalysisService}, a windowed one to the Support Summary feature through {@link
 * WindowAnalysisRunner}. This lives outside {@code AnalysisService} because the summary feature is
 * downstream of it — the runner is what the summary package implements — and a service that both
 * serves the summary and resolves its implementation would be a dependency cycle.
 */
@Component
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobResumer {

    private final AsyncJobRepository asyncJobRepository;
    private final AnalysisService analysisService;

    /**
     * Only present when the Support Summary feature is enabled. Resolved lazily through a provider so
     * this component exists whether or not the feature does.
     */
    private final ObjectProvider<WindowAnalysisRunner> windowAnalysisRunner;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeOnStartup() {
        try {
            AsyncJobRepository.AsyncJob existingJob = asyncJobRepository.findJob(AnalysisJobData.JOB_ID);
            if (existingJob == null) return;

            AnalysisJobData.Parsed job = AnalysisJobData.parse(existingJob.data());
            if (job == null) {
                log.error("Corrupt async job data '{}', deleting job", existingJob.data());
                asyncJobRepository.deleteJob(AnalysisJobData.JOB_ID);
                return;
            }

            log.info("Found pending async job on startup: {}, resuming...", AnalysisJobData.JOB_ID);
            switch (job) {
                case AnalysisJobData.DaysRun daysRun -> analysisService.resume(daysRun.days());
                case AnalysisJobData.WindowRun windowRun -> resumeWindowRun(windowRun);
            }
        } catch (Exception e) {
            log.error("Failed to resume analysis job on startup", e);
        }
    }

    /**
     * Hands a windowed job back to the Support Summary feature. With that feature off there is no
     * owner, and the row has to go: it holds the shared lock, so leaving it would block every future
     * analysis run.
     */
    private void resumeWindowRun(AnalysisJobData.WindowRun windowRun) {
        WindowAnalysisRunner runner = windowAnalysisRunner.getIfAvailable();
        if (runner == null) {
            log.warn(
                    "Pending windowed analysis job {}..{} has no runner (summary feature disabled), deleting job",
                    windowRun.from(),
                    windowRun.to());
            asyncJobRepository.deleteJob(AnalysisJobData.JOB_ID);
            return;
        }
        runner.runWindowRefresh(windowRun.from(), windowRun.to());
    }
}
