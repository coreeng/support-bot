package com.coreeng.supportbot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Feature flag and tuning for the Support Summary page.
 *
 * <p>The cross-feature rule — this requires {@code analysis.prompt.enabled} — is enforced by
 * {@link SummaryValidationConfig}, because a {@code @ConfigurationProperties} record can only see
 * its own prefix.
 *
 * @param enabled whether the page and its endpoint exist at all; there is no degraded mode
 * @param maxReasons upper bound on the per-ticket reason lines handed to the LLM, so a very wide
 *     window cannot overflow the model's context
 * @param failureRetryDelay how long a failed refresh is reported as an error before the next visit
 *     retries it, when neither the window's data nor the summary prompt has changed in between
 */
@ConfigurationProperties(prefix = "summary")
public record SummaryProps(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("400") int maxReasons,
        @DefaultValue("15m") Duration failureRetryDelay) {

    public SummaryProps {
        if (enabled && maxReasons < 1) {
            throw new IllegalArgumentException("summary.max-reasons must be at least 1, got: " + maxReasons);
        }
        if (enabled && (failureRetryDelay.isZero() || failureRetryDelay.isNegative())) {
            throw new IllegalArgumentException(
                    "summary.failure-retry-delay must be positive, got: " + failureRetryDelay);
        }
    }
}
