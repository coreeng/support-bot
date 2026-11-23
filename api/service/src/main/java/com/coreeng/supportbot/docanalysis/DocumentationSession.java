package com.coreeng.supportbot.docanalysis;

public interface DocumentationSession {

    /** Recorded with each result. Its meaning belongs to the source. */
    String version();

    /**
     * @param thread the role-labelled thread, and nothing else about the ticket
     * @param budget the most page text the answer may hold, in tokens as {@link TokenEstimator}
     *     counts them
     */
    ContextAnswer findContext(String thread, int budget);
}
