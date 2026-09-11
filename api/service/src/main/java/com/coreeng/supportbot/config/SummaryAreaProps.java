package com.coreeng.supportbot.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Support Summary area: the {@code /summary} page and the {@code /summary-data} thread export.
 *
 * <p>There is no feature flag here. The page exists whenever an LLM provider is selected ({@link
 * LlmProps#enabled()}), because it is the only consumer of the classification the provider is
 * needed for; the export is always on.
 *
 * @param sanitisation patterns scrubbed from thread text before it reaches either the page's
 *     classifier or the export file
 * @param page tuning for the page
 * @param offlineExport the offline analysis kit served next to the export, kept apart so it can be
 *     dropped wholesale when it is retired
 */
@ConfigurationProperties(prefix = "summary-area")
public record SummaryAreaProps(
        @DefaultValue Sanitisation sanitisation,
        @DefaultValue Page page,
        @DefaultValue OfflineExport offlineExport) {

    public record Sanitisation(
            @DefaultValue List<String> patterns,
            @DefaultValue List<String> exceptions) {}

    /**
     * @param maxReasons upper bound on the per-ticket reason lines handed to the LLM, so a very wide
     *     window cannot overflow the model's context; the default covers the widest allowed window
     *     (one quarter, {@code SummaryController.MAX_WINDOW_DAYS}) at roughly 80 tickets a week
     * @param failureRetryDelay how long a failed refresh is reported as an error before the next
     *     visit retries it, when neither the window's data nor the summary prompt has changed in
     *     between
     */
    public record Page(
            @DefaultValue("1000") int maxReasons,
            @DefaultValue("15m") Duration failureRetryDelay) {

        public Page {
            if (maxReasons < 1) {
                throw new IllegalArgumentException(
                        "summary-area.page.max-reasons must be at least 1, got: " + maxReasons);
            }
            if (failureRetryDelay.isZero() || failureRetryDelay.isNegative()) {
                throw new IllegalArgumentException(
                        "summary-area.page.failure-retry-delay must be positive, got: " + failureRetryDelay);
            }
        }
    }

    /**
     * @param analysisBundlePath the analysis bundle zip (or a directory to zip on the fly) served by
     *     the summary-data download endpoint
     */
    public record OfflineExport(
            @DefaultValue("classpath:placeholder-analysis-bundle.zip")
            String analysisBundlePath) {}
}
