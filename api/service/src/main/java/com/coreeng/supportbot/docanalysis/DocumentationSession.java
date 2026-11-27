package com.coreeng.supportbot.docanalysis;

public interface DocumentationSession {

    /** Recorded with each result. Its meaning belongs to the source. */
    String version();

    /**
     * Returns {@link ContextAnswer.Failed} for every failure the source expects, a failed model call
     * included. An exception thrown from here is a defect; the analysis records the ticket as a
     * failed attempt and carries on.
     *
     * @param thread the role-labelled thread, and nothing else about the ticket
     * @param budget the most page text the answer may hold, in tokens as {@link TokenEstimator}
     *     counts them
     */
    ContextAnswer findContext(String thread, int budget);
}
