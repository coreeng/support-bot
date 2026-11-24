package com.coreeng.supportbot.docanalysis;

import static com.coreeng.supportbot.docanalysis.ContextAnswer.Failed.PROVIDER_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunService.StartResult;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.Progress;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.StopReason;
import com.coreeng.supportbot.ticket.TicketId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class DocAnalysisRunServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 2);
    private static final LocalDate TO = LocalDate.of(2026, 11, 15);
    private static final String CHANNEL = "C123";
    private static final String PROMPT_ID = "prompt-1";
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final Duration REQUEST_DELAY = Duration.ofMillis(500);
    private static final Duration FAILURE_RETRY_DELAY = Duration.ofHours(24);
    private static final Duration RUN_RETRY_DELAY = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_TICKETS_PER_DAY = 500;

    private final InMemoryAsyncJobRepository asyncJobs = new InMemoryAsyncJobRepository();
    private final DocAnalysisRepository repository = mock(DocAnalysisRepository.class);
    private final DocClassifier classifier = mock(DocClassifier.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final List<Duration> waits = new ArrayList<>();
    private final Map<TicketId, List<ClassificationOutcome>> outcomes = new HashMap<>();
    private final List<TicketId> classified = new ArrayList<>();
    private StubDocumentationSource source = new StubDocumentationSource();
    private List<TicketToClassify> due = List.of();

    @BeforeEach
    void setUp() {
        when(classifier.currentPromptId()).thenReturn(PROMPT_ID);
        when(repository.findDue(eq(FROM), eq(TO), eq(List.of(CHANNEL)), eq(PROMPT_ID), any(), eq(MAX_ATTEMPTS)))
                .thenAnswer(invocation -> due);
        when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            TicketToClassify ticket = invocation.getArgument(1);
            classified.add(ticket.ticketId());
            List<ClassificationOutcome> queued = outcomes.get(ticket.ticketId());
            return queued == null || queued.isEmpty() ? result(ticket.ticketId()) : queued.removeFirst();
        });
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void classifiesTheWindowsDueTicketsInTurnWithTheRequestDelayBetween() {
        due = tickets(1, 2, 3);

        assertThat(service().start(FROM, TO)).isEqualTo(StartResult.STARTED);

        verify(repository, times(2))
                .findDue(FROM, TO, List.of(CHANNEL), PROMPT_ID, NOW.minus(FAILURE_RETRY_DELAY), MAX_ATTEMPTS);
        assertThat(classified).containsExactly(id(1), id(2), id(3));
        assertThat(waits).containsExactly(REQUEST_DELAY, REQUEST_DELAY);
        assertThat(service().state()).isEqualTo(DocAnalysisRunState.IDLE);
        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNull();
    }

    @Test
    void reportsProgressWhileARunIsActiveAndStartsNoSecond() {
        due = tickets(1, 2);
        List<Runnable> dispatched = new ArrayList<>();
        DocAnalysisRunService service = service(dispatched::add);

        assertThat(service.start(FROM, TO)).isEqualTo(StartResult.STARTED);
        assertThat(service.start(FROM, TO)).isEqualTo(StartResult.ALREADY_ACTIVE);

        assertThat(dispatched).hasSize(1);
        assertThat(service.state().active()).isEqualTo(new Progress(FROM, TO, 0, 0));

        dispatched.getFirst().run();

        assertThat(service.state().running()).isFalse();
        assertThat(service.start(FROM, TO)).isEqualTo(StartResult.STARTED);
    }

    @Test
    void startsNoRunWhenNoTicketIsDue() {
        assertThat(service().start(FROM, TO)).isEqualTo(StartResult.NOTHING_DUE);

        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNull();
        verify(classifier, never()).classify(any(), any());
    }

    @Test
    void failsWithoutClassifyingAnythingWhenTheSourceIsUnavailable() {
        source = StubDocumentationSource.unavailable("The documentation repository could not be read.");
        due = tickets(1);
        DocAnalysisRunService service = service();

        service.start(FROM, TO);

        verify(classifier, never()).classify(any(), any());
        assertThat(service.state().stopped())
                .isEqualTo(new StopReason.SourceUnavailable("The documentation repository could not be read."));
        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNull();
    }

    @Test
    void carriesOnPastATicketThatFailsToClassify() {
        due = tickets(1, 2);
        outcomes(1, attemptFailed(1, false));
        DocAnalysisRunService service = service();

        service.start(FROM, TO);

        assertThat(classified).containsExactly(id(1), id(2));
        assertThat(service.state()).isEqualTo(DocAnalysisRunState.IDLE);
        verify(classifier, never()).recordFailedAttempt(any(), anyString(), anyString());
    }

    @Test
    void retriesAProviderFailureWithLongerWaitsAndRecordsNothingWhenARetryPasses() {
        due = tickets(1);
        outcomes(1, providerFailed(1), providerFailed(1));

        service().start(FROM, TO);

        assertThat(classified).containsExactly(id(1), id(1), id(1));
        assertThat(waits).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(20));
        verify(classifier, never()).recordFailedAttempt(any(), anyString(), anyString());
    }

    @Test
    void recordsAProviderErrorAfterThreeRetriesAndCarriesOn() {
        due = tickets(1, 2);
        outcomes(1, providerFailed(1), providerFailed(1), providerFailed(1), providerFailed(1));
        DocAnalysisRunService service = service();

        service.start(FROM, TO);

        assertThat(classified).containsExactly(id(1), id(1), id(1), id(1), id(2));
        verify(classifier).recordFailedAttempt(id(1), PROMPT_ID, PROVIDER_ERROR);
        assertThat(service.state()).isEqualTo(DocAnalysisRunState.IDLE);
    }

    @Test
    void stopsAfterFiveProviderErrorsInARowRemovesThemAndHoldsOffNewRuns() {
        due = tickets(1, 2, 3, 4, 5, 6);
        IntStream.rangeClosed(1, 6).forEach(this::alwaysProviderFailed);
        DocAnalysisRunService service = service();

        service.start(FROM, TO);

        assertThat(classified).doesNotContain(id(6));
        verify(repository).deleteFailedAttempts(List.of(id(1), id(2), id(3), id(4), id(5)), PROVIDER_ERROR);
        Instant retryAt = NOW.plus(RUN_RETRY_DELAY);
        assertThat(service.state().stopped()).isEqualTo(new StopReason.ProviderOutage(retryAt));
        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNull();

        clock.set(NOW.plus(Duration.ofMinutes(1)));
        assertThat(service.start(FROM, TO)).isEqualTo(StartResult.HELD_OFF);

        clock.set(retryAt);
        outcomes.clear();
        assertThat(service.start(FROM, TO)).isEqualTo(StartResult.STARTED);
    }

    @Test
    void aTicketThatClassifiesBreaksTheProviderErrorStreak() {
        due = tickets(1, 2, 3, 4, 5, 6, 7, 8, 9);
        IntStream.of(1, 2, 3, 4, 6, 7, 8, 9).forEach(this::alwaysProviderFailed);
        DocAnalysisRunService service = service();

        service.start(FROM, TO);

        verify(repository, never()).deleteFailedAttempts(any(), anyString());
        assertThat(service.state()).isEqualTo(DocAnalysisRunState.IDLE);
    }

    @Test
    void stopsAtTheDailyLimitAndStartsNoRunWhileItIsReached() {
        due = tickets(1, 2, 3);
        when(repository.countAttemptedSince(NOW.minus(Duration.ofHours(24)))).thenReturn(MAX_TICKETS_PER_DAY - 1);
        DocAnalysisRunService service = service();

        assertThat(service.start(FROM, TO)).isEqualTo(StartResult.STARTED);

        assertThat(classified).containsExactly(id(1));
        assertThat(service.state().stopped()).isEqualTo(new StopReason.DailyLimitReached());

        when(repository.countAttemptedSince(NOW.minus(Duration.ofHours(24)))).thenReturn(MAX_TICKETS_PER_DAY);
        assertThat(service.start(FROM, TO)).isEqualTo(StartResult.DAILY_LIMIT_REACHED);
        assertThat(classified).containsExactly(id(1));
        assertThat(service.state().stopped()).isEqualTo(new StopReason.DailyLimitReached());
    }

    @Test
    void resumesOnStartupTheRunARestartInterrupted() {
        due = tickets(1, 2);
        service(task -> {}).start(FROM, TO);
        assertThat(classified).isEmpty();

        DocAnalysisRunService afterRestart = service();
        afterRestart.resumeOnStartup();

        assertThat(classified).containsExactly(id(1), id(2));
        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNull();
    }

    @Test
    void deletesAJobItCannotReadOnStartup() {
        asyncJobs.tryStartJob(DocAnalysisRunService.JOB_ID, "not a window");

        service().resumeOnStartup();

        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNull();
        verify(classifier, never()).classify(any(), any());
    }

    @Test
    void keepsTheJobForTheNextStartupWhenInterruptedAndRecordsNoProviderError() {
        due = tickets(1, 2);
        doAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    return providerFailed(1);
                })
                .when(classifier)
                .classify(any(), any());
        DocAnalysisRunService service = service();

        service.start(FROM, TO);

        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNotNull();
        verify(classifier, never()).recordFailedAttempt(any(), anyString(), anyString());
        assertThat(service.state().running()).isFalse();
    }

    @Test
    void failsTheRunWhenThePromptCannotBeLoaded() {
        due = tickets(1, 2);
        doThrow(new AnalysisPromptLoadException("no prompt")).when(classifier).classify(any(), any());
        DocAnalysisRunService service = service();

        service.start(FROM, TO);

        assertThat(service.state().stopped()).isEqualTo(new StopReason.Failed());
        assertThat(asyncJobs.findJob(DocAnalysisRunService.JOB_ID)).isNull();
        verify(classifier, times(1)).classify(any(), any());
    }

    private DocAnalysisRunService service() {
        return service(Runnable::run);
    }

    private DocAnalysisRunService service(Executor executor) {
        return new DocAnalysisRunService(
                asyncJobs,
                repository,
                classifier,
                source,
                new SlackChannelRegistry(
                        new SlackTicketsProps(CHANNEL, List.of(), "eyes", "ticket", "white_check_mark", "rocket")),
                props(),
                llmProps(),
                executor,
                clock,
                duration -> {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    waits.add(duration);
                });
    }

    private void outcomes(int ticket, ClassificationOutcome... queued) {
        outcomes.put(id(ticket), new ArrayList<>(List.of(queued)));
    }

    private void alwaysProviderFailed(int ticket) {
        outcomes(
                ticket, providerFailed(ticket), providerFailed(ticket), providerFailed(ticket), providerFailed(ticket));
    }

    private static TicketId id(int ticket) {
        return new TicketId(ticket);
    }

    private static List<TicketToClassify> tickets(int... ids) {
        return IntStream.of(ids)
                .mapToObj(id -> new TicketToClassify(id(id), CHANNEL, "1700000000.00000" + id))
                .toList();
    }

    private static ClassificationOutcome result(TicketId ticketId) {
        return new ClassificationOutcome.Classified(new DocAnalysis.Result(
                ticketId,
                PROMPT_ID,
                DocClassification.MISSING,
                "github",
                "v1",
                "How do I rotate a deploy key?",
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                "No page covers it.",
                "test-model",
                NOW));
    }

    private static ClassificationOutcome attemptFailed(int ticket, boolean retryable) {
        return new ClassificationOutcome.AttemptFailed(
                new DocAnalysis.FailedAttempt(id(ticket), PROMPT_ID, NOW, "quote_not_found", 1), retryable);
    }

    private static ClassificationOutcome providerFailed(int ticket) {
        return new ClassificationOutcome.ProviderFailed(id(ticket), PROMPT_ID);
    }

    static DocumentationAnalysisProps props() {
        return new DocumentationAnalysisProps(
                false,
                10_000,
                MAX_TICKETS_PER_DAY,
                FAILURE_RETRY_DELAY,
                MAX_ATTEMPTS,
                RUN_RETRY_DELAY,
                new DocumentationAnalysisProps.Source(
                        "github",
                        new DocumentationAnalysisProps.GitHub(
                                "",
                                "main",
                                "docs",
                                List.of(".md"),
                                "/tmp/documentation-analysis",
                                Duration.ofHours(24),
                                DataSize.ofMegabytes(50),
                                5000,
                                "whole",
                                2)));
    }

    static LlmProps llmProps() {
        return new LlmProps(
                LlmProvider.NONE,
                "configured-model",
                REQUEST_DELAY,
                new LlmProps.Vertex("", ""),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new LlmProps.Anthropic("", "", Duration.ofSeconds(60), 16000),
                new LlmProps.Stub(false));
    }

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
