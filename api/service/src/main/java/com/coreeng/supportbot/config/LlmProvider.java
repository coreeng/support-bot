package com.coreeng.supportbot.config;

/**
 * The one switch for every LLM-backed feature: the knowledge-gap analysis run and the Support
 * Summary page. Selecting a provider turns those features on; {@link #NONE} turns them off and
 * means no LLM client is built and no provider setting is validated.
 */
public enum LlmProvider {
    /** Feature off. The default. */
    NONE,
    /** Hosted Vertex AI via Application Default Credentials. */
    VERTEX,
    /** An internal LLM proxy speaking the native Gemini REST API, authenticated with Basic auth. */
    PROXY,
    /**
     * Local development only: canned deterministic responses that write synthetic data. Also needs
     * {@code llm.stub.acknowledge-synthetic-data=true}; see {@link LlmProps.Stub}.
     */
    STUB
}
