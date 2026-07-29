# ADR: Database-Backed Analysis Prompt

**Date:** 2026-07-29
**Status:** Proposed

---

## Context

The analysis prompt shipped as a file inside the container image: `api/service/analysis/prompt.md`, copied in by the Dockerfile, located at runtime through `ANALYSIS_PROMPT_FILE`, and read on every use by `AnalysisService.loadPrompt()`.

That makes the prompt release-coupled. Changing a single line means a code change, a build, and a deploy. EL-141 asks a Support Manager to edit, preview and publish the prompt from the UI, keep a version history, and roll back — none of which is possible against a read-only file baked into an image. Mounting a ConfigMap or a volume would remove the release coupling but still gives no version history, no atomic publish, and no shared state between replicas.

The prompt is also load-bearing for cost control. [ADR-002](adr-002-automated-analysis-workflow.md) stamps every `analysis` row with `prompt_id`, a SHA-256 of the prompt text, and skips threads that already have a row matching the current hash. Any change to the prompt bytes re-analyses every thread at LLM cost.

---

## Decision

Store the prompt in PostgreSQL, in a new `analysis_prompt` table, and delete the file.

```sql
CREATE TABLE IF NOT EXISTS analysis_prompt
(
    id         BIGSERIAL PRIMARY KEY,
    version    INTEGER     NOT NULL,
    content    TEXT        NOT NULL,
    is_in_use  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT analysis_prompt_version_unique UNIQUE (version)
);

CREATE UNIQUE INDEX analysis_prompt_in_use_idx ON analysis_prompt (is_in_use) WHERE is_in_use;
```

- **Edits are new rows, never updates.** A new version is inserted with a higher `version`; older rows stay for history and rollback.
- **`is_in_use` marks the live version.** The partial unique index makes "at most one live version" a database invariant rather than an application convention, so two concurrent publishes cannot both win.
- **A draft is simply a row with `is_in_use = false`.** Publishing is flipping the flag; rolling back is flipping it to an older row. Neither needs a schema change.
- **`AnalysisService.loadPrompt()` reads the in-use row** through `AnalysisPromptRepository`, and throws `AnalysisPromptLoadException` when no row is flagged or the database is unreachable — the same exception, and the same `ANALYSIS_PROMPT_LOAD_FAILED` response code, that a failed file read produced before.

### Seeding

`V37__analysis_prompt.sql` creates the table and inserts version 1 containing the text of `prompt.md` **verbatim**, dollar-quoted, trailing newline included. Its SHA-256 is `a306429c1eea579c033108c4c70ff859e9fc02e91fb1dffd643d1f209b16dde5`, which is the `prompt_id` already stamped on every existing `analysis` row, so no environment re-analyses anything on deploy. `AnalysisPromptRepositoryPostgresTest` pins that hash; if the seed ever drifts, the build fails rather than the LLM bill rising.

The file is deleted along with the `COPY service/analysis analysis` Dockerfile line, the `analysis.prompt.file` property, and `ANALYSIS_PROMPT_FILE`. `analysis.prompt.enabled` is unchanged — it gates the analysis beans and is unrelated to where the text lives.

### What this ADR does not decide

The write path. There is no endpoint to create a draft or publish a version yet; the table is populated only by the seed migration. The schema is shaped so that path is additive.

---

## Consequences

### Positive

- The prompt can change without a release, which is the prerequisite for the rest of EL-141.
- Version history and rollback come from the row model rather than from git archaeology on a deleted file.
- "Exactly one live prompt" is enforced by the database, not by application code that could race.
- All replicas read the same prompt, and the existing `GET /analysis/prompt` endpoint keeps serving it with no contract change.

### Negative

- The prompt is no longer visible in the repository. Reading the current prompt now means querying the database or calling the endpoint, and reviewing a prompt change becomes a UI diff rather than a git diff.
- The seed text lives inside an immutable migration, so the migration file and the live row diverge as soon as anyone publishes a new version. The migration is a bootstrap, not a source of truth.
- A database outage now breaks prompt loading, where previously a local file read could not fail that way. The failure is surfaced as the existing `ANALYSIS_PROMPT_LOAD_FAILED` 500 rather than something new.
- Publishing a new version will invalidate the entire `prompt_id` cache described in [ADR-002](adr-002-automated-analysis-workflow.md) and trigger full re-analysis at LLM cost. This ADR does not address that; whoever builds the publish step must.
