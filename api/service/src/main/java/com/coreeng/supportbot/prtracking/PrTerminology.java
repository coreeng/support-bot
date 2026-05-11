package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;

/**
 * Per-provider wording used in default notification text. Centralises the {@code PR} vs {@code MR}
 * and {@code #} vs {@code !} differences so each default-message template doesn't need a ternary
 * inline. Custom CEL messages bypass this entirely — they're free to use either vocabulary.
 */
final class PrTerminology {

    private PrTerminology() {}

    /** Short noun: "PR" for GitHub, "MR" for GitLab. */
    static String noun(Provider provider) {
        return provider == Provider.GITLAB ? "MR" : "PR";
    }

    /** Plural noun: "PRs" for GitHub, "MRs" for GitLab. */
    static String plural(Provider provider) {
        return noun(provider) + "s";
    }

    /** Number separator: "#" for GitHub, "!" for GitLab — matches what each provider's UI shows. */
    static String separator(Provider provider) {
        return provider == Provider.GITLAB ? "!" : "#";
    }

    /** Long form used in narrative sentences: "Pull requests" / "Merge requests". */
    static String longForm(Provider provider) {
        return provider == Provider.GITLAB ? "Merge requests" : "Pull requests";
    }
}
