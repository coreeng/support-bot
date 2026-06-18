package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisRepository.ThreadToAnalyze;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThreadsAwaitingAnalysisServiceTest {

    @Mock
    private ThreadsAwaitingAnalysisRepository repository;

    private ThreadsAwaitingAnalysisService service;

    @BeforeEach
    void setUp() {
        SlackTicketsProps slackTicketsProps =
                new SlackTicketsProps("C123456", List.of(), "eyes", "ticket", "white_check_mark", "rocket");
        SlackChannelRegistry channelRegistry = new SlackChannelRegistry(slackTicketsProps);
        service = new ThreadsAwaitingAnalysisService(repository, channelRegistry);
    }

    @Test
    void find_shouldCallRepositoryWithCorrectParameters() {
        // given
        int days = 7;
        String promptId = "prompt-v1.0";
        ImmutableList<ThreadToAnalyze> expectedThreads = ImmutableList.of(
                new ThreadToAnalyze(1L, "1234.5678", "C123456"), new ThreadToAnalyze(2L, "2345.6789", "C123456"));

        when(repository.findThreadsAwaitingAnalysis(days, promptId, List.of("C123456")))
                .thenReturn(expectedThreads);

        // when
        ImmutableList<ThreadToAnalyze> result = service.find(days, promptId);

        // then
        assertThat(result).isEqualTo(expectedThreads);
        verify(repository).findThreadsAwaitingAnalysis(days, promptId, List.of("C123456"));
    }

    @Test
    void find_shouldReturnEmptyListWhenNoThreadsFound() {
        // given
        int days = 30;
        String promptId = "prompt-v2.0";
        when(repository.findThreadsAwaitingAnalysis(days, promptId, List.of("C123456")))
                .thenReturn(ImmutableList.of());

        // when
        ImmutableList<ThreadToAnalyze> result = service.find(days, promptId);

        // then
        assertThat(result).isEmpty();
        verify(repository).findThreadsAwaitingAnalysis(days, promptId, List.of("C123456"));
    }

    @Test
    void find_shouldUseChannelIdFromProps() {
        // given
        int days = 14;
        String promptId = "prompt-v3.0";
        when(repository.findThreadsAwaitingAnalysis(days, promptId, List.of("C123456")))
                .thenReturn(ImmutableList.of());

        // when
        service.find(days, promptId);

        // then
        verify(repository).findThreadsAwaitingAnalysis(days, promptId, List.of("C123456"));
    }

    @Test
    void find_shouldHandleLargeNumberOfThreads() {
        // given
        int days = 1;
        String promptId = "prompt-v1.0";
        ImmutableList.Builder<ThreadToAnalyze> builder = ImmutableList.builder();
        for (long i = 1; i <= 100; i++) {
            builder.add(new ThreadToAnalyze(i, "thread-" + i, "C123456"));
        }
        ImmutableList<ThreadToAnalyze> manyThreads = builder.build();

        when(repository.findThreadsAwaitingAnalysis(days, promptId, List.of("C123456")))
                .thenReturn(manyThreads);

        // when
        ImmutableList<ThreadToAnalyze> result = service.find(days, promptId);

        // then
        assertThat(result).hasSize(100);
        assertThat(result).isEqualTo(manyThreads);
    }
}
