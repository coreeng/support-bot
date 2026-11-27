package com.coreeng.supportbot.docanalysis.source.github;

import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DocumentationSession;

/** Gives every thread the same answer: every page, read once when the session opened. */
final class WholeDocumentationSession implements DocumentationSession {

    private final String commit;
    private final ContextAnswer.Pages whole;
    private final int estimatedTokens;

    WholeDocumentationSession(String commit, ContextAnswer.Pages whole) {
        this.commit = commit;
        this.whole = whole;
        this.estimatedTokens = whole.estimatedTokens();
    }

    @Override
    public String version() {
        return commit;
    }

    @Override
    public ContextAnswer findContext(String thread, int budget) {
        if (estimatedTokens > budget) {
            return new ContextAnswer.Failed(ContextAnswer.Failed.CONTEXT_TOO_LARGE, false);
        }
        return whole;
    }
}
