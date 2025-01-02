package com.coreeng.supportbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class HandlerExecutorConfig {
    /**
     * There is a problem in slack sdk socket mode that it executes things single threaded
     * This executor is used to run handlers concurrently at the application level.
     * See the issue for more details: <a href="https://github.com/slackapi/java-slack-sdk/issues/1409">...</a>
     */
    @Bean
    public ExecutorService slackHandlerExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
