package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;

/**
 * Matches when at least one configured PR-tracking repository uses {@code provider: gitlab}. Gates
 * the GitLab adapter beans so pure-GitHub deployments don't have to configure a GitLab token (and
 * don't pay the cost of standing up an unused RestClient + caches). See
 * {@link AnyRepoOfProviderCondition} for the matching logic.
 */
public final class AnyGitLabRepoCondition extends AnyRepoOfProviderCondition {

    public AnyGitLabRepoCondition() {
        super(Provider.GITLAB);
    }
}
