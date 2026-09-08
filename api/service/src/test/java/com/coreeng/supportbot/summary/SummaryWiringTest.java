package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisService;
import com.coreeng.supportbot.analysis.WindowAnalysisRunner;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.SummaryProps;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Boots the summary beans through Spring, with async proxying switched on the way the real
 * application has it, and lets Spring wire their constructors.
 *
 * <p>This exists because of a startup failure no unit test could see: the refresh service used to be
 * {@code @Async} and implement an interface, so Spring proxied it with a JDK dynamic proxy and every
 * collaborator asking for the concrete class became unsatisfiable. The run is now handed to the
 * {@code analysisTaskExecutor} explicitly, so there is no proxy — but the two things that failure
 * was about still need guarding: the beans must be injectable as Spring builds them (the executor is
 * picked by qualifier from among several {@link Executor} beans), and a refresh must land on that
 * executor rather than run inline on the request thread.
 */
@SpringJUnitConfig(SummaryWiringTest.TestConfig.class)
@TestPropertySource(properties = "summary.enabled=true")
class SummaryWiringTest {

    @Test
    void summaryBeansWireUpAndDispatchOntoTheAnalysisExecutor(ApplicationContext context) {
        SummaryRefreshService refresher = context.getBean(SummaryRefreshService.class);
        RecordingExecutor analysisExecutor = context.getBean("analysisTaskExecutor", RecordingExecutor.class);

        // Both consumers of the refresh service resolve to the one bean: SummaryService triggers
        // through the class, the startup resume through the runner interface.
        assertThat(context.getBean(SummaryService.class)).isNotNull();
        assertThat(context.getBean(WindowAnalysisRunner.class)).isSameAs(refresher);

        AsyncJobRepository asyncJobRepository = context.getBean(AsyncJobRepository.class);
        when(asyncJobRepository.tryStartJob(any(), any())).thenReturn(true);

        assertThat(refresher.start(new SummaryWindow(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23))))
                .isTrue();

        // Dispatched to the analysis executor, and to that one only — not the other Executor bean,
        // and not inline.
        assertThat(analysisExecutor.tasks).hasSize(1);
        assertThat(context.getBean("otherTaskExecutor", RecordingExecutor.class).tasks)
                .isEmpty();
        verifyNoInteractions(context.getBean(AnalysisService.class));
    }

    /** Collects what is submitted instead of running it. */
    static final class RecordingExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @Import({SummaryRefreshService.class, SummaryService.class})
    static class TestConfig {

        @Bean(name = "analysisTaskExecutor")
        RecordingExecutor analysisTaskExecutor() {
            return new RecordingExecutor();
        }

        /** A second Executor bean, so the qualifier has something to get wrong. */
        @Bean(name = "otherTaskExecutor")
        RecordingExecutor otherTaskExecutor() {
            return new RecordingExecutor();
        }

        @Bean
        AsyncJobRepository asyncJobRepository() {
            return mock(AsyncJobRepository.class);
        }

        @Bean
        AnalysisService analysisService() {
            return mock(AnalysisService.class);
        }

        @Bean
        ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService() {
            return mock(ThreadsAwaitingAnalysisService.class);
        }

        @Bean
        SummaryReadRepository summaryReadRepository() {
            return mock(SummaryReadRepository.class);
        }

        @Bean
        SummarySnapshotRepository summarySnapshotRepository() {
            return mock(SummarySnapshotRepository.class);
        }

        @Bean
        LlmSummaryService llmSummaryService() {
            return mock(LlmSummaryService.class);
        }

        @Bean
        SlackChannelRegistry channelRegistry() {
            return new SlackChannelRegistry(
                    new SlackTicketsProps("C123456", List.of(), "eyes", "ticket", "white_check_mark", "rocket"));
        }

        @Bean
        SummaryProps summaryProps() {
            return new SummaryProps(true, 400, Duration.ofMinutes(15));
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
