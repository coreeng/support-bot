-- Support Summary (EL-264).
--
-- Two changes:
--   1. `analysis_prompt` gains a `type` column so it can hold more than one kind of prompt. The
--      per-ticket classifier prompt is `classification`; the new windowed prose summary prompt is
--      `summary`. Uniqueness (version, and the at-most-one-in-use rule) becomes per type.
--   2. `summary_snapshot` caches the generated prose summary per (window, prompt version). Validity
--      is decided by `fingerprint` — a cheap digest of the window's `analysis` rows — not by a timer.
--
-- The V37 classification seed is deliberately left untouched: its SHA-256
-- (a306429c1eea579c033108c4c70ff859e9fc02e91fb1dffd643d1f209b16dde5) is analysis.prompt_id for every
-- existing row, and any drift would silently re-analyse every thread.

ALTER TABLE analysis_prompt
    ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'classification';

-- Version numbers restart per type, so uniqueness has to be scoped the same way.
ALTER TABLE analysis_prompt
    DROP CONSTRAINT IF EXISTS analysis_prompt_version_unique;
ALTER TABLE analysis_prompt
    ADD CONSTRAINT analysis_prompt_type_version_unique UNIQUE (type, version);

-- One in-use prompt per type, rather than one across the whole table.
DROP INDEX IF EXISTS analysis_prompt_in_use_idx;
CREATE UNIQUE INDEX IF NOT EXISTS analysis_prompt_in_use_idx ON analysis_prompt (type) WHERE is_in_use;

CREATE TABLE IF NOT EXISTS summary_snapshot
(
    id           BIGSERIAL PRIMARY KEY,
    window_from  DATE        NOT NULL,
    window_to    DATE        NOT NULL,
    prompt_id    TEXT        NOT NULL,
    fingerprint  TEXT        NOT NULL,
    content      TEXT        NOT NULL,
    model        TEXT        NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT summary_snapshot_window_prompt_unique UNIQUE (window_from, window_to, prompt_id)
);

-- First draft of the summary prompt. Unlike the classification prompt it is not hash-pinned by
-- anything historical, so it can be iterated on in later migrations; every new version invalidates
-- the cached snapshots that referenced the old one (they key on prompt_id).
INSERT INTO analysis_prompt (type, version, content, is_in_use)
VALUES ('summary', 1, $prompt$Platform Support Demand Summary Prompt

You are an expert Platform Enablement analyst writing a short briefing for platform leadership.

You are given a report covering a single time window. It contains:
- The window's dates and the total number of support tickets raised in it.
- Aggregated counts per Primary Support Driver (why tenants raised the ticket).
- Aggregated counts per Category (the support topic).
- Aggregated counts per Platform Feature (the capability referenced).
- Aggregated counts per tenant team (who raised the tickets).
- The per-ticket Reason lines: one sentence per classified ticket explaining why it was raised.

Some tickets may be unclassified (still open, or not yet analysed). Those are reported explicitly as
a count. Treat them as unknown — never assume what they contain.

---

Your task

Write a concise prose summary that answers, for this window:

1. What tenants were asking about most, and what the shape of demand was.
2. Why they were asking — the underlying drivers, grounded in the Reason lines rather than in the
   category labels alone.
3. Any notable trends, clusters, or repeated themes: several tickets that share a root cause, a
   feature that generated disproportionate traffic, or a single team dominating a topic.
4. What, if anything, this suggests is worth acting on (documentation, product, or enablement).

---

Rules

- Base every statement on the data provided. Do not invent tickets, teams, features, or numbers.
- Quote counts only when they are in the report, and make sure they are accurate.
- Prefer explaining a cluster of related tickets over listing individual ones.
- Do not restate the tables — the reader can already see the breakdowns. Add the interpretation the
  tables cannot give.
- Be honest about weak evidence: with few tickets, say the window is too small to show a trend.
- If a large share of the window is unclassified, say so and caveat the conclusions.
- No headings, no bullet lists, no markdown. Plain prose only.
- 3 to 5 short paragraphs, at most roughly 350 words.
- Neutral, factual, British English. No preamble such as "Here is the summary".
$prompt$, TRUE)
ON CONFLICT (type, version) DO NOTHING;
