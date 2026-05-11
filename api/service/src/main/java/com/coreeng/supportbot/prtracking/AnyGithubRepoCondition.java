package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when at least one configured PR-tracking repository uses {@code provider: github}
 * (or omits the provider, which defaults to github). Used to gate the GitHub adapter beans so
 * pure-GitLab deployments don't have to configure a GitHub token they never use.
 *
 * <p>Reads the indexed property list directly from {@link Environment} because this runs at
 * bean-factory time, before {@link com.coreeng.supportbot.config.PrTrackingProps} is bound.
 */
public class AnyGithubRepoCondition implements Condition {

    // Upper bound on the index scan. The repository list is operator-authored and small in
    // practice; this exists only as a safety net so a malformed property source can't loop forever.
    private static final int MAX_REPO_INDEX = 1024;

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
            if (Provider.GITHUB.storageValue().equalsIgnoreCase(provider)) {
                return true;
            }
        }
        return false;
    }
}
