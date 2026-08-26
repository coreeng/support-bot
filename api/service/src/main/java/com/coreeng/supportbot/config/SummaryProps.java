package com.coreeng.supportbot.config;

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
 */
@ConfigurationProperties(prefix = "summary")
public record SummaryProps(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("400") int maxReasons) {

    public SummaryProps {
        if (enabled && maxReasons < 1) {
            throw new IllegalArgumentException("summary.max-reasons must be at least 1, got: " + maxReasons);
        }
    }
}
