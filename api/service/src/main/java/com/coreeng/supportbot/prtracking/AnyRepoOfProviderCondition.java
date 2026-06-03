package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when at least one configured PR-tracking repository targets a given {@link Provider}. Used
 * to gate each provider's adapter beans so a deployment that uses only one provider doesn't have to
 * configure credentials for the other (and doesn't pay the cost of standing up unused clients).
 *
 * <p>Reads the indexed property list directly from {@link Environment} because conditions run at
 * bean-factory time, before {@link com.coreeng.supportbot.config.PrTrackingProps} is bound. An
 * omitted {@code provider} defaults to {@code github}, so a default-provider repo only ever matches
 * the GitHub condition, never GitLab.
 *
 * <p>Subclassed (rather than parameterized via constructor) because {@code @Conditional} instantiates
 * conditions reflectively with a no-arg constructor; each provider gets a thin subclass that pins the
 * target {@link Provider}.
 */
public abstract class AnyRepoOfProviderCondition implements Condition {

    // Upper bound on the index scan. The repository list is operator-authored and small in
    // practice; this exists only as a safety net so a malformed property source can't loop forever.
    private static final int MAX_REPO_INDEX = 1024;

    private final Provider target;

    protected AnyRepoOfProviderCondition(Provider target) {
        this.target = target;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        for (int i = 0; i < MAX_REPO_INDEX; i++) {
            String namePath = "pr-review-tracking.repositories[" + i + "].name";
            if (!env.containsProperty(namePath)) {
                return false;
            }
            String providerPath = "pr-review-tracking.repositories[" + i + "].provider";
            String provider = env.getProperty(providerPath, Provider.GITHUB.storageValue());
            if (target.storageValue().equalsIgnoreCase(provider)) {
                return true;
            }
        }
        return false;
    }
}
