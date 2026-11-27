-- Documentation gap analysis: one row per ticket, holding either its result or its last failed
-- attempt. A row counts only while its prompt_id is the one in use, and the next attempt replaces it.
CREATE TABLE doc_analysis
(
    ticket_id      BIGINT PRIMARY KEY REFERENCES ticket (id) ON DELETE CASCADE,
    prompt_id      TEXT      NOT NULL,
    classification TEXT,
    source         TEXT,
    docs_version   TEXT,
    question       TEXT,
    answer_summary TEXT,
    tags           VARCHAR[] NOT NULL DEFAULT '{}',
    product_areas  VARCHAR[] NOT NULL DEFAULT '{}',
    doc_paths      VARCHAR[] NOT NULL DEFAULT '{}',
    evidence_quote TEXT,
    rationale      TEXT,
    model          TEXT,
    analysed_at    TIMESTAMPTZ,
    failed_at      TIMESTAMPTZ,
    failure_reason TEXT,
    attempts       INTEGER   NOT NULL DEFAULT 0,

    CONSTRAINT doc_analysis_classification_check
        CHECK (classification IN ('correct', 'missing', 'incorrect', 'not_applicable')),
    CONSTRAINT doc_analysis_result_or_failure_check
        CHECK ((classification IS NOT NULL AND analysed_at IS NOT NULL AND failed_at IS NULL AND failure_reason IS NULL)
            OR (classification IS NULL AND analysed_at IS NULL AND failed_at IS NOT NULL AND failure_reason IS NOT NULL))
);
