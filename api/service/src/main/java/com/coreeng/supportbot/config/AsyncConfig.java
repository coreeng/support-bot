package com.coreeng.supportbot.config;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// @EnableAsync lives on SupportBotApplication (must be unconditional — SummaryExportAsyncConfig's
// executor needs it regardless of whether an LLM provider is selected).
@Configuration
@ConditionalOnLlmEnabled
public class AsyncConfig {

    @Bean(name = "analysisTaskExecutor")
    public Executor analysisTaskExecutor() {
        return singleThreadWithoutQueue("analysis-");
    }

    @Bean(name = "docAnalysisTaskExecutor")
    @ConditionalOnProperty(name = "documentation-analysis.enabled", havingValue = "true")
    public Executor docAnalysisTaskExecutor() {
        return singleThreadWithoutQueue("doc-analysis-");
    }

    private static Executor singleThreadWithoutQueue(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // DB gate allows only a single request to run @Async analysis
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // No need to queue - once @Async analysis is running, no other request will be allowed to run it
        // We return 409 Conflict before hitting the queue
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
