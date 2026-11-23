package com.coreeng.supportbot.docanalysis;

public interface DocumentationSource {

    /** Called once at the start of a run; this is where a source refreshes whatever it keeps. */
    OpenResult open();

    /**
     * Changes whenever the source would answer the same thread differently: its type, its prompts
     * and the settings that change its answers. Results made under another fingerprint are stale.
     */
    String fingerprint();
}
