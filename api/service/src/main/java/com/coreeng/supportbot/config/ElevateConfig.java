package com.coreeng.supportbot.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ElevateConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean("elevateStatusScheduler")
    public ThreadPoolTaskScheduler elevateStatusScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return elevateScheduler(builder, "elevate-status-");
    }

    @Bean("elevateSyncScheduler")
    public ThreadPoolTaskScheduler elevateSyncScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return elevateScheduler(builder, "elevate-sync-");
    }

    private static ThreadPoolTaskScheduler elevateScheduler(
            ThreadPoolTaskSchedulerBuilder builder, String threadNamePrefix) {
        return builder.poolSize(1)
                .threadNamePrefix(threadNamePrefix)
                .awaitTermination(true)
                .awaitTerminationPeriod(Duration.ofSeconds(30))
                .build();
    }
}
