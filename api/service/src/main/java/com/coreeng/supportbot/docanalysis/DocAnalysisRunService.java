package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.Progress;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.StopReason;
import com.coreeng.supportbot.ticket.TicketId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Classifies a window's due tickets in the background, one at a time.
 *
 * <p>The run holds an {@code async_job} row of its own and runs on its own single-thread executor, so
 * it never waits for, or holds up, the analysis job and Support Summary refresh, which share theirs.
 */
@Service
@ConditionalOnLlmEnabled
@ConditionalOnProperty(name = "documentation-analysis.enabled", havingValue = "true")
@Slf4j
public class DocAnalysisRunService {

    static final String JOB_ID = "documentation-analysis";
    static final int PROVIDER_FAILURE_STREAK = 5;
    static final List<Duration> PROVIDER_RETRY_WAITS =
            List.of(Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(60));

    private static final Duration DAY = Duration.ofHours(24);
    private static final ObjectMapper JOB_DATA = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    private final AsyncJobRepository asyncJobRepository;
    private final DocAnalysisRepository repository;
    private final DocClassifier classifier;
    private final DocumentationSource source;
    private final SlackChannelRegistry channelRegistry;
    private final DocumentationAnalysisProps props;
    private final LlmProps llmProps;
    private final Executor executor;
    private final Clock clock;
    private final Sleeper sleeper;

    private final AtomicReference<DocAnalysisRunState> state = new AtomicReference<>(DocAnalysisRunState.IDLE);

    public enum StartResult {
        STARTED,
        ALREADY_ACTIVE,
        NOTHING_DUE,
        HELD_OFF,
        DAILY_LIMIT_REACHED
    }

    @Autowired
    public DocAnalysisRunService(
            AsyncJobRepository asyncJobRepository,
            DocAnalysisRepository repository,
            DocClassifier classifier,
            DocumentationSource source,
            SlackChannelRegistry channelRegistry,
            DocumentationAnalysisProps props,
            LlmProps llmProps,
            @Qualifier("docAnalysisTaskExecutor") Executor executor,
            Clock clock) {
        this(
                asyncJobRepository,
                repository,
                classifier,
                source,
                channelRegistry,
                props,
                llmProps,
                executor,
                clock,
                Sleeper.THREAD);
    }

    DocAnalysisRunService(
            AsyncJobRepository asyncJobRepository,
            DocAnalysisRepository repository,
            DocClassifier classifier,
            DocumentationSource source,
            SlackChannelRegistry channelRegistry,
            DocumentationAnalysisProps props,
            LlmProps llmProps,
            Executor executor,
            Clock clock,
            Sleeper sleeper) {
        this.asyncJobRepository = asyncJobRepository;
        this.repository = repository;
        this.classifier = classifier;
        this.source = source;
        this.channelRegistry = channelRegistry;
        this.props = props;
        this.llmProps = llmProps;
        this.executor = executor;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Starts a run over the window when none is active, one is allowed and a ticket is due, and
     * returns without waiting for it.
     *
     * @param from first day of the window, inclusive, in UTC
     * @param to last day of the window, inclusive, in UTC
     * @throws AnalysisPromptLoadException when no documentation classification prompt is in use
     */
    public StartResult start(LocalDate from, LocalDate to) {
        if (asyncJobRepository.findJob(JOB_ID) != null) {
            return StartResult.ALREADY_ACTIVE;
        }
        if (state.get().stopped() instanceof StopReason.ProviderOutage outage
                && clock.instant().isBefore(outage.retryAt())) {
            return StartResult.HELD_OFF;
        }
        if (due(from, to, classifier.currentPromptId()).isEmpty()) {
            return StartResult.NOTHING_DUE;
        }
        if (attemptsLeftToday() <= 0) {
            state.set(new DocAnalysisRunState(null, new StopReason.DailyLimitReached()));
            return StartResult.DAILY_LIMIT_REACHED;
        }
        if (!asyncJobRepository.tryStartJob(JOB_ID, encode(new Window(from, to)))) {
            return StartResult.ALREADY_ACTIVE;
        }
        log.info("Started documentation analysis for {}..{}", from, to);
        return dispatch(from, to) ? StartResult.STARTED : StartResult.ALREADY_ACTIVE;
    }

    /** Never touches the documentation source. */
    public DocAnalysisRunState state() {
        return state.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeOnStartup() {
        try {
            AsyncJobRepository.AsyncJob job = asyncJobRepository.findJob(JOB_ID);
            if (job == null) {
                return;
            }
            Window window = decode(job.data());
            if (window == null) {
                log.error("Corrupt documentation analysis job data '{}', deleting job", job.data());
                asyncJobRepository.deleteJob(JOB_ID);
                return;
            }
            log.info("Resuming documentation analysis for {}..{}", window.from(), window.to());
            dispatch(window.from(), window.to());
        } catch (RuntimeException e) {
            log.error("Failed to resume documentation analysis on startup", e);
        }
    }

    private boolean dispatch(LocalDate from, LocalDate to) {
        state.set(new DocAnalysisRunState(new Progress(from, to, 0, 0), null));
        try {
            executor.execute(() -> run(from, to));
            return true;
        } catch (RejectedExecutionException e) {
            log.error("Executor rejected documentation analysis, cleaning up DB record", e);
            state.set(DocAnalysisRunState.IDLE);
            asyncJobRepository.deleteJob(JOB_ID);
            return false;
        }
    }

    private void run(LocalDate from, LocalDate to) {
        StopReason stopped = null;
        boolean interrupted = false;
        try {
            stopped = classifyWindow(from, to);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interrupted = true;
        } catch (RuntimeException e) {
            interrupted = Thread.currentThread().isInterrupted();
            if (!interrupted) {
                log.error("Documentation analysis for {}..{} failed", from, to, e);
                stopped = new StopReason.Failed();
            }
        } finally {
            state.set(new DocAnalysisRunState(null, stopped));
            // An interrupt here is a shutdown: the row stays so the next startup resumes the run.
            if (interrupted) {
                log.warn("Documentation analysis for {}..{} interrupted; it resumes on startup", from, to);
            } else {
                asyncJobRepository.deleteJob(JOB_ID);
            }
        }
    }

    private @Nullable StopReason classifyWindow(LocalDate from, LocalDate to) throws InterruptedException {
        int allowed = attemptsLeftToday();
        if (allowed <= 0) {
            log.info("Documentation analysis for {}..{} not run: the daily limit is reached", from, to);
            return new StopReason.DailyLimitReached();
        }
        OpenResult opened = source.open();
        if (opened instanceof OpenResult.Unavailable unavailable) {
            log.warn("Documentation analysis for {}..{} not run: {}", from, to, unavailable.reason());
            return new StopReason.SourceUnavailable(unavailable.reason());
        }
        DocumentationSession session = ((OpenResult.Opened) opened).session();

        List<TicketToClassify> due = due(from, to, classifier.currentPromptId());
        log.info("Documentation analysis for {}..{}: {} ticket(s) due", from, to, due.size());
        List<TicketId> providerFailures = new ArrayList<>();
        int done = 0;
        for (TicketToClassify ticket : due) {
            if (done >= allowed) {
                log.info("Documentation analysis stopped at the daily limit after {}/{}", done, due.size());
                return new StopReason.DailyLimitReached();
            }
            if (done > 0) {
                sleeper.sleep(llmProps.requestDelay());
            }
            if (classifyRetrying(session, ticket) instanceof ClassificationOutcome.ProviderFailed failed) {
                classifier.recordFailedAttempt(
                        failed.ticketId(), failed.promptId(), ContextAnswer.Failed.PROVIDER_ERROR);
                providerFailures.add(failed.ticketId());
                if (providerFailures.size() == PROVIDER_FAILURE_STREAK) {
                    repository.deleteFailedAttempts(providerFailures, ContextAnswer.Failed.PROVIDER_ERROR);
                    Instant retryAt = clock.instant().plus(props.runRetryDelay());
                    log.warn(
                            "Documentation analysis stopped: the provider failed {} tickets in a row; next run from {}",
                            PROVIDER_FAILURE_STREAK,
                            retryAt);
                    return new StopReason.ProviderOutage(retryAt);
                }
            } else {
                providerFailures.clear();
            }
            done++;
            state.set(new DocAnalysisRunState(new Progress(from, to, done, due.size()), null));
        }
        log.info("Documentation analysis for {}..{} completed: {} ticket(s)", from, to, done);
        return null;
    }

    private ClassificationOutcome classifyRetrying(DocumentationSession session, TicketToClassify ticket)
            throws InterruptedException {
        ClassificationOutcome outcome = classifier.classify(session, ticket);
        for (Duration wait : PROVIDER_RETRY_WAITS) {
            if (!(outcome instanceof ClassificationOutcome.ProviderFailed)) {
                return outcome;
            }
            log.info("The provider failed on ticket {}; retrying in {}", ticket.ticketId(), wait);
            sleeper.sleep(wait);
            outcome = classifier.classify(session, ticket);
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return outcome;
    }

    private List<TicketToClassify> due(LocalDate from, LocalDate to, String promptId) {
        return repository.findDue(
                from,
                to,
                channelRegistry.monitoredChannelIds(),
                promptId,
                clock.instant().minus(props.failureRetryDelay()),
                props.maxAttempts());
    }

    private int attemptsLeftToday() {
        return props.maxTicketsPerDay()
                - repository.countAttemptedSince(clock.instant().minus(DAY));
    }

    record Window(LocalDate from, LocalDate to) {
        Window {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    private static String encode(Window window) {
        try {
            return JOB_DATA.writeValueAsString(window);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode documentation analysis job " + window, e);
        }
    }

    private static @Nullable Window decode(String data) {
        try {
            return JOB_DATA.readValue(data, Window.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }
}
