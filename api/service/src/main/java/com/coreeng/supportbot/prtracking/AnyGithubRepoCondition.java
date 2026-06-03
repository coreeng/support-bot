package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;

/**
 * Matches when at least one configured PR-tracking repository uses {@code provider: github} (or
 * omits the provider, which defaults to github). Gates the GitHub adapter beans so pure-GitLab
 * deployments don't have to configure a GitHub token they never use. See
 * {@link AnyRepoOfProviderCondition} for the matching logic.
 */
public final class AnyGithubRepoCondition extends AnyRepoOfProviderCondition {

    public AnyGithubRepoCondition() {
        super(Provider.GITHUB);
    }
}
