package com.coreeng.supportbot.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unlike {@link AsyncConfig}'s {@code analysisTaskExecutor}, this executor must always be
 * available — the async thread export isn't gated behind {@code analysis.prompt.enabled}.
 */
@Configuration
public class SummaryExportAsyncConfig {

    @Bean(name = "summaryExportTaskExecutor")
    public Executor summaryExportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // DB gate (async_job unique constraint) allows only a single request to run @Async export
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // No need to queue - once @Async export is running, no other request will be allowed to run it
        // We return 409 Conflict before hitting the queue
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("summary-export-");
        executor.initialize();
        return executor;
    }
}
