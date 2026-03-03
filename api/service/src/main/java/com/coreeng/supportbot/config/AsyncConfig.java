package com.coreeng.supportbot.config;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
public class AsyncConfig {

    @Bean(name = "analysisTaskExecutor")
    public Executor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // DB gate allows only a single request to run @Async analysis
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // No need to queue - once @Async analysis is running, no other request will be allowed to run it
        // We return 409 Conflict before hitting the queue
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("analysis-");
        executor.initialize();
        return executor;
    }
}
