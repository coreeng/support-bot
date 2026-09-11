package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.analysis.AnalysisJobData;
import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.ThreadsAwaitingAnalysisService;
import com.coreeng.supportbot.analysis.WindowAnalysisRunner;
import com.coreeng.supportbot.asyncjob.AsyncJobRepository;
import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.config.SummaryAreaProps;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Runs one window's refresh: backfill the classification gaps, then regenerate the prose summary.
 *
 * <p>Both halves happen inside a single {@code async_job} claim on the shared {@code "analysis"}
 * lock. Splitting them would let a days-based run slip in between and change the very data the
 * summary is about to be generated from, and would show the user two separate waits instead of one.
 *
 * <p>The lock also means concurrent visitors never duplicate work: the first claim wins, everyone
 * else sees {@code generating} and re-polls.
 *
 * <p>The run itself is handed to the same single-threaded {@code analysisTaskExecutor} the
 * days-based analysis uses, so the two kinds of run also never overlap on the LLM.
 */
@Service
@ConditionalOnLlmEnabled
@Slf4j
public class SummaryRefreshService implements WindowAnalysisRunner {

    private static final String ASYNC_ID = AnalysisJobData.JOB_ID;

    /** Upper bound on remembered failures; past it the oldest is dropped and simply retried. */
    static final int MAX_REMEMBERED_FAILURES = 64;

    private final AsyncJobRepository asyncJobRepository;
    private final AnalysisService analysisService;
    private final ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService;
    private final SummaryReadRepository summaryReadRepository;
    private final SummarySnapshotRepository summarySnapshotRepository;
    private final LlmSummaryService llmSummaryService;
    private final SlackChannelRegistry channelRegistry;
    private final SummaryAreaProps summaryAreaProps;
    private final Executor analysisExecutor;
    private final Clock clock;

    public SummaryRefreshService(
            AsyncJobRepository asyncJobRepository,
            AnalysisService analysisService,
            ThreadsAwaitingAnalysisService threadsAwaitingAnalysisService,
            SummaryReadRepository summaryReadRepository,
            SummarySnapshotRepository summarySnapshotRepository,
            LlmSummaryService llmSummaryService,
            SlackChannelRegistry channelRegistry,
            SummaryAreaProps summaryAreaProps,
            @Qualifier("analysisTaskExecutor") Executor analysisExecutor,
            Clock clock) {
        this.asyncJobRepository = asyncJobRepository;
        this.analysisService = analysisService;
        this.threadsAwaitingAnalysisService = threadsAwaitingAnalysisService;
        this.summaryReadRepository = summaryReadRepository;
        this.summarySnapshotRepository = summarySnapshotRepository;
        this.llmSummaryService = llmSummaryService;
        this.channelRegistry = channelRegistry;
        this.summaryAreaProps = summaryAreaProps;
        this.analysisExecutor = analysisExecutor;
        this.clock = clock;
    }

    private final AtomicReference<SummaryRefreshStatus> status = new AtomicReference<>(SummaryRefreshStatus.IDLE);

    /**
     * Failed attempts by window, remembered so the page reports the error instead of retrying on
     * every 3s poll. Insertion-ordered and capped: a service that has failed for more windows than
     * fit here has bigger problems than a retry. Guarded by its own monitor — touched from the async
     * refresh thread and from every request thread that polls.
     */
    private final Map<SummaryWindow, Failure> failures = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SummaryWindow, Failure> eldest) {
            return size() > MAX_REMEMBERED_FAILURES;
        }
    };

    /**
     * A failed attempt. It only applies while the input it failed on is unchanged — the window's
     * data fingerprint and the summary prompt version — and only for {@link
     * SummaryAreaProps.Page#failureRetryDelay()}: a window whose data never changes again (last month's)
     * would otherwise stay pinned to a transient error until a restart.
     */
    private record Failure(String summaryPromptId, String fingerprint, String error, Instant recordedAt) {

        boolean matches(String summaryPromptId, String fingerprint) {
            return this.summaryPromptId.equals(summaryPromptId) && this.fingerprint.equals(fingerprint);
        }

        boolean expiredAt(Instant now, SummaryAreaProps.Page props) {
            return !now.isBefore(recordedAt.plus(props.failureRetryDelay()));
        }
    }

    /**
     * Claims the shared {@code async_job} lock and kicks off a refresh for the window.
     *
     * @return false when another run already holds the lock, or the executor is saturated
     */
    public boolean start(SummaryWindow window) {
        if (!asyncJobRepository.tryStartJob(ASYNC_ID, AnalysisJobData.window(window.from(), window.to()))) {
            log.debug(
                    "Summary refresh for {}..{} not started: a job already holds the lock", window.from(), window.to());
            return false;
        }
        log.info("Started summary refresh for window {}..{}", window.from(), window.to());
        return runWindowRefresh(window.from(), window.to());
    }

    @Override
    public boolean runWindowRefresh(LocalDate from, LocalDate to) {
        SummaryWindow window = new SummaryWindow(from, to);
        try {
            analysisExecutor.execute(() -> refresh(window));
            return true;
        } catch (RejectedExecutionException e) {
            log.error("Executor rejected summary refresh for {}..{}, cleaning up DB record", from, to, e);
            asyncJobRepository.deleteJob(ASYNC_ID);
            return false;
        }
    }

    /**
     * The refresh itself, on the executor thread. Never throws: a failed refresh must leave the
     * breakdowns renderable, so the error is recorded for the page to show and the lock is always
     * released.
     */
    private void refresh(SummaryWindow window) {
        LocalDate from = window.from();
        LocalDate to = window.to();
        try {
            status.set(new SummaryRefreshStatus(window, SummaryState.Phase.CLASSIFYING, true));
            ImmutableSet<TicketId> attempted = backfill(window);
            if (attempted == null) {
                // The backfill stops early on interrupt (shutdown) and returns normally. Summarising now
                // would store a snapshot whose fingerprint marks the unclassified tickets as gaps, and
                // that snapshot would be served as ready after restart without ever retrying them.
                // Nothing is recorded, so the next visit starts a fresh refresh. The flag is left set
                // for the executor.
                log.warn("Summary refresh for window {}..{} interrupted during backfill; not summarising", from, to);
                return;
            }

            status.set(new SummaryRefreshStatus(window, SummaryState.Phase.SUMMARISING, true));
            generate(window, attempted);

            clearFailure(window);
            log.info("Summary refresh for window {}..{} completed", from, to);
        } catch (Exception e) {
            log.error("Summary refresh for window {}..{} failed: {}", from, to, e.getMessage(), e);
            recordFailure(window, e);
        } finally {
            status.set(SummaryRefreshStatus.IDLE);
            asyncJobRepository.deleteJob(ASYNC_ID);
        }
    }

    /** The current refresh state; {@link SummaryRefreshStatus#running()} is false when idle. */
    public SummaryRefreshStatus status() {
        return status.get();
    }

    /**
     * @return the recorded error for this window, summary prompt version and data fingerprint, or
     *     null when the last attempt did not fail, its input has moved on since, or the retry delay
     *     has passed
     */
    public @Nullable String failureFor(SummaryWindow window, String summaryPromptId, String fingerprint) {
        synchronized (failures) {
            Failure failure = failures.get(window);
            if (failure == null) {
                return null;
            }
            if (!failure.matches(summaryPromptId, fingerprint)
                    || failure.expiredAt(clock.instant(), summaryAreaProps.page())) {
                // Either the input moved on or the retry delay has passed: the next visit retries, and
                // whatever comes of that replaces this entry.
                failures.remove(window);
                return null;
            }
            return failure.error();
        }
    }

    /**
     * Classifies the window's gaps, in at most two passes.
     *
     * <p>A pass classifies serially with a per-thread delay, so a wide window can take a long time,
     * and a ticket closed meanwhile is not in the list the pass computed up front. Left alone it
     * would be baked into the snapshot's fingerprint as a gap and served as "ready" without ever
     * being classified — until the window's data happened to change again. So the gaps are read once
     * more afterwards, and if any ticket is awaiting classification that the first pass could not
     * have known about, one more pass runs. Only one: a gap the first pass already attempted is a
     * ticket the backfill gave up on, not a new arrival, and anything closed during the second pass
     * is left for the next refresh rather than chasing a moving target — which works because the
     * snapshot is stored under a fingerprint that only counts the gaps returned here (see {@link
     * #generate}), so such a ticket makes the next visit's fingerprint differ.
     *
     * @return the ids of the tickets the passes set out to classify (whether or not they succeeded),
     *     or null when a pass was interrupted (shutdown); the interrupt flag is then left set
     */
    private @Nullable ImmutableSet<TicketId> backfill(SummaryWindow window) {
        String classificationPromptId = analysisService.currentPromptId();
        ImmutableSet<TicketId> firstPassTargets = awaitingClassification(window, classificationPromptId);

        analysisService.backfillWindow(window.from(), window.to());
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }

        ImmutableSet<TicketId> stillAwaiting = awaitingClassification(window, classificationPromptId);
        if (firstPassTargets.containsAll(stillAwaiting)) {
            return firstPassTargets;
        }
        log.info(
                "{} ticket(s) closed in window {}..{} during the backfill; classifying them in a second pass",
                stillAwaiting.stream()
                        .filter(id -> !firstPassTargets.contains(id))
                        .count(),
                window.from(),
                window.to());
        analysisService.backfillWindow(window.from(), window.to());
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        return ImmutableSet.<TicketId>builder()
                .addAll(firstPassTargets)
                .addAll(stillAwaiting)
                .build();
    }

    /** The same lookup the backfill itself starts from: closed tickets in the window with no analysis for the prompt. */
    private ImmutableSet<TicketId> awaitingClassification(SummaryWindow window, String classificationPromptId) {
        return threadsAwaitingAnalysisService.find(window.from(), window.to(), classificationPromptId).stream()
                .map(thread -> new TicketId(thread.ticketId()))
                .collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Reads the window afresh — after the backfill, so the counts, the reasons and the fingerprint
     * stored with the summary all describe the same post-backfill state.
     *
     * <p>The stored fingerprint's gap component is narrowed to the gaps the backfill attempted. A gap
     * it attempted and could not fill is pinned into the fingerprint, so the page serves the snapshot
     * instead of retrying it on every poll. A gap it never attempted — a ticket closed during the
     * second pass — is left out, so the next visit computes a different fingerprint and starts the
     * refresh that will attempt it.
     *
     * @param attempted the ids of the tickets the backfill set out to classify
     */
    private void generate(SummaryWindow window, ImmutableSet<TicketId> attempted) {
        AnalysisPrompt summaryPrompt = analysisService.inUsePrompt(AnalysisPromptType.SUMMARY);
        if (summaryPrompt == null) {
            throw new AnalysisPromptLoadException("No summary prompt version is marked as in use");
        }
        String summaryPromptId = AnalysisService.computePromptId(summaryPrompt.content());
        String classificationPromptId = analysisService.currentPromptId();
        ImmutableList<String> channelIds = channelRegistry.monitoredChannelIds();

        SummaryBreakdowns breakdowns = summaryReadRepository.breakdowns(window, classificationPromptId, channelIds);
        SummaryFingerprint current = summaryReadRepository.fingerprint(window, classificationPromptId, channelIds);
        SummaryFingerprint fingerprint = current.withGapsAmong(attempted);
        if (fingerprint.gapCount() > 0) {
            // Not an error: the summary is generated from what could be classified, and stored under a
            // fingerprint that includes these gaps so the page does not retry them on every poll.
            log.warn(
                    "{} closed ticket(s) in window {}..{} could not be classified; summarising without them",
                    fingerprint.gapCount(),
                    window.from(),
                    window.to());
        }
        if (current.gapCount() > fingerprint.gapCount()) {
            log.info(
                    "{} ticket(s) closed in window {}..{} after the backfill computed its targets; left out of the"
                            + " snapshot's fingerprint so the next visit refreshes",
                    current.gapCount() - fingerprint.gapCount(),
                    window.from(),
                    window.to());
        }
        ImmutableList<SummaryReason> reasons = summaryReadRepository.reasons(
                window,
                classificationPromptId,
                channelIds,
                summaryAreaProps.page().maxReasons());

        String content = llmSummaryService.generate(summaryPrompt.content(), breakdowns, reasons);
        if (content.isBlank()) {
            throw new EmptySummaryException();
        }

        summarySnapshotRepository.upsert(new SummarySnapshot(
                window, summaryPromptId, fingerprint.value(), content.strip(), llmSummaryService.modelName(), null));
    }

    private void recordFailure(SummaryWindow window, Exception cause) {
        String summaryPromptId;
        String fingerprint;
        try {
            AnalysisPrompt summaryPrompt = analysisService.inUsePrompt(AnalysisPromptType.SUMMARY);
            if (summaryPrompt == null) {
                // The page reports the missing prompt itself without starting a refresh, so there is
                // nothing to pin.
                return;
            }
            summaryPromptId = AnalysisService.computePromptId(summaryPrompt.content());
            fingerprint = summaryReadRepository
                    .fingerprint(window, analysisService.currentPromptId(), channelRegistry.monitoredChannelIds())
                    .value();
        } catch (Exception e) {
            // Without the input's identity the failure cannot be tied to what it failed on, so let the
            // next visit retry rather than pinning an error we can no longer invalidate.
            log.warn(
                    "Could not identify the input of window {}..{} after a failed refresh",
                    window.from(),
                    window.to(),
                    e);
            return;
        }
        Failure failure = new Failure(summaryPromptId, fingerprint, describe(cause), clock.instant());
        synchronized (failures) {
            // Remove first so a repeat failure moves to the newest end instead of keeping its old slot.
            failures.remove(window);
            failures.put(window, failure);
        }
    }

    private void clearFailure(SummaryWindow window) {
        synchronized (failures) {
            failures.remove(window);
        }
    }

    /**
     * The message the page shows for a failed refresh. Deliberately not the exception's own: that
     * reaches every viewer of the page, and a jOOQ failure carries the full SQL while a model-client
     * failure can carry endpoint and project identifiers. The raw cause is in the server log, keyed by
     * window, so a fixed phrase per known failure is all the page needs. The cause chain is walked
     * because the interesting exception is often wrapped.
     */
    static String describe(Exception cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            switch (t) {
                case SlackException _ -> {
                    return ERROR_SLACK;
                }
                case AnalysisPromptLoadException _ -> {
                    return ERROR_PROMPT;
                }
                case EmptySummaryException _ -> {
                    return ERROR_EMPTY_SUMMARY;
                }
                case InterruptedException _, RejectedExecutionException _ -> {
                    return ERROR_INTERRUPTED;
                }
                default -> {}
            }
        }
        return ERROR_GENERIC;
    }

    static final String ERROR_SLACK = "Slack could not be reached while classifying tickets";
    static final String ERROR_PROMPT = "A prompt the summary needs could not be loaded";
    static final String ERROR_EMPTY_SUMMARY = "The model returned an empty summary";
    static final String ERROR_INTERRUPTED = "Summary generation was interrupted; it will be retried";
    static final String ERROR_GENERIC = "Summary generation failed";

    /** The model answered with nothing usable; caching a blank summary would serve an empty card as ready. */
    private static final class EmptySummaryException extends RuntimeException {
        EmptySummaryException() {
            super("The model returned an empty summary");
        }
    }
}
