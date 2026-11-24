package com.coreeng.supportbot.prtracking;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * Matches when PR tracking watches a GitHub repository, or documentation analysis is enabled with
 * its GitHub source. Both read the credentials in {@code pr-review-tracking.github}.
 */
final class GitHubClientNeededCondition extends AnyNestedCondition {

    GitHubClientNeededCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
    @Conditional(AnyGithubRepoCondition.class)
    static final class PrTrackingWithGitHubRepository {}

    @ConditionalOnProperty(name = "documentation-analysis.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "documentation-analysis.source.type", havingValue = "github", matchIfMissing = true)
    static final class DocumentationAnalysisWithGitHubSource {}
}
