package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.coreeng.supportbot.analysis.AnalysisPromptRepository;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.WindowAnalysisRunner;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.SummaryProps;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Boots the summary beans with async proxying switched on, the way the real application does.
 *
 * <p>This exists because of a startup failure that no unit test could see: {@code
 * SummaryRefreshService} is {@code @Async} <em>and</em> implements an interface, so Spring proxies it
 * with a JDK dynamic proxy, which exposes only interfaces — and every collaborator that asked for the
 * concrete class became unsatisfiable ("could not be injected because it is a JDK dynamic proxy").
 * Constructing the beans by hand, as the other tests do, bypasses proxying entirely and so proves
 * nothing about wiring. Anything that reintroduces a concrete-class dependency on a proxied bean
 * fails here instead of at {@code make run-local}.
 */
@SpringJUnitConfig(SummaryWiringTest.TestConfig.class)
class SummaryWiringTest {

    @Test
    void summaryBeansWireUpUnderAsyncProxying(ApplicationContext context) {
        // The guard itself: if this stops being a proxy the test no longer proves anything, and the
        // regression it was written for would slip through unnoticed.
        SummaryRefresher refresher = context.getBean(SummaryRefresher.class);
        assertThat(AopUtils.isAopProxy(refresher))
                .as("SummaryRefreshService must still be proxied for @Async to work")
                .isTrue();

        // Both halves of the proxied bean must be reachable by interface: SummaryService triggers
        // through one, AnalysisService resumes a restart-orphaned job through the other.
        assertThat(context.getBean(WindowAnalysisRunner.class)).isSameAs(refresher);
        assertThat(context.getBean(SummaryService.class)).isNotNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class TestConfig {

        /** Named to match the qualifier on {@code @Async}; synchronous so nothing escapes the test. */
        @Bean(name = "analysisTaskExecutor")
        Executor analysisTaskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        SummaryRefreshService summaryRefreshService(
                SummaryReadRepository summaryReadRepository, ApplicationContext applicationContext) {
            return new SummaryRefreshService(
                    mock(AsyncJobRepository.class),
                    mock(AnalysisService.class),
                    mock(AnalysisPromptRepository.class),
                    summaryReadRepository,
                    mock(SummarySnapshotRepository.class),
                    mock(LlmSummaryService.class),
                    channelRegistry(),
                    new SummaryProps(true, 400),
                    applicationContext);
        }

        @Bean
        SummaryService summaryService(SummaryReadRepository summaryReadRepository, SummaryRefresher summaryRefresher) {
            return new SummaryService(
                    mock(AnalysisService.class),
                    mock(AnalysisPromptRepository.class),
                    summaryReadRepository,
                    mock(SummarySnapshotRepository.class),
                    summaryRefresher,
                    channelRegistry());
        }

        @Bean
        SummaryReadRepository summaryReadRepository() {
            return mock(SummaryReadRepository.class);
        }

        private static SlackChannelRegistry channelRegistry() {
            return new SlackChannelRegistry(
                    new SlackTicketsProps("C123456", List.of(), "eyes", "ticket", "white_check_mark", "rocket"));
        }
    }
}
