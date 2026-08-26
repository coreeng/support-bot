package com.coreeng.supportbot.config;

import org.springframework.context.annotation.Configuration;

/**
 * Fails startup when the Support Summary page is switched on without the analysis feature it is
 * built on.
 *
 * <p>The page has no degraded mode: without {@code analysis.prompt.enabled} there is no classifier,
 * no prompt to load and no LLM wiring, so every request would 500. Refusing to boot makes that a
 * deployment-time error with a clear message instead of a runtime mystery.
 *
 * <p>This lives in a configuration class rather than in {@link SummaryProps} itself because a
 * {@code @ConfigurationProperties} record is only bound its own prefix and cannot see
 * {@code analysis.*}.
 */
@Configuration(proxyBeanMethods = false)
public class SummaryValidationConfig {

    public SummaryValidationConfig(SummaryProps summaryProps, AnalysisProps analysisProps) {
        if (summaryProps.enabled() && !analysisProps.prompt().enabled()) {
            throw new IllegalArgumentException(
                    "summary.enabled=true requires analysis.prompt.enabled=true (the Support Summary page has no"
                            + " degraded mode: it needs the classifier and the LLM configuration)");
        }
    }
}
