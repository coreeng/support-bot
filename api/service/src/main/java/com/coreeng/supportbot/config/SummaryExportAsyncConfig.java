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
        // Only one export can run at a time (enforced separately by the DB gate); no queue needed
        // since a concurrent request gets a 409 before it would ever reach one.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("summary-export-");
        executor.initialize();
        return executor;
    }
}
