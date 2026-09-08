# ADR: Real-Time Analysis Pipeline Triggered from UI

**Date:** 2026-02-23
**Status:** Proposed
**Amended:** 2026-09 — [PR #327](https://github.com/coreeng/support-bot/pull/327) retired the UI that triggered this pipeline (`/knowledge-gaps`, now redirecting to `/summary`); the backfill is triggered server-side while serving `GET /summary`, and `POST /analysis/run` remains as an API-only path. The Support Summary settings are documented in `api/service/docs/configuration.md`.

---

## Context

The support bot already supports a knowledge-gap analysis workflow, but it is entirely offline: a user manually exports Slack threads as a ZIP, runs external scripts to summarise them with an LLM, then uploads a JSONL file. This is error-prone, slow to run, and requires access to tooling outside the application.

The team wants to trigger the same end-to-end pipeline from a single button click in the existing web UI, with live progress feedback and no risk of two analyses running concurrently.

---

## Decision Drivers

- Reuse the existing analysis data model and summary-data export logic where possible.
- Avoid introducing a separate worker process or message queue — keep operational complexity low.
- Leverage the existing GCP platform-identity integration so no new secrets need managing.
- Configuration must be consistent with existing Spring `application.yaml` / env-var patterns.
- The application can be restarted by Kubernetes scheduler; analysis pipeline must resume on restart
- LLM Analysis is time-consuming and incurs cost; we want to eliminate re-analysis of the same thread with the same prompt

---

## Decision

### 1. LLM Integration

Use client libraries to be able to talk to LLM APIs.

### 2. Avoid re-analysis with the same prompt

Compare current analysis to current prompt, if the same prompt was used for the current analysis don't re-analyze.

### 3. Resume on pod restart

### 4. Use database for concurrency control


---

## Consequences

### Positive

- **No new infrastructure.** The pipeline runs inside the existing Spring Boot pod; no queues, workers, or caches are needed.
- **No new secrets.** Workload Identity Federation means Vertex AI credentials are handled by GKE, consistent with the existing GCP integration pattern.
- **Incremental — existing import/export flows unchanged.** The offline workflow continues to work; this is an additive change. *(Amended 2026-09, PR #327: true for direct API calls only — the UI export/import surface was removed, so the offline workflow is `curl`-driven via `/summary-data/*` as documented in `api/service/README.md`.)*
- **Progress is durable.** Counters in PostgreSQL survive pod restarts; the UI will resume polling the correct state after any disruption.
- **Incremental persistence.** Each thread is persisted immediately after LLM analysis, so partial progress is never lost.
- **Prompt versioning.** Skips re-analysis when the prompt hasn't changed, saving API costs and time.
- **Resumable batches.** If a batch is interrupted, the application can detect the in-progress batch on startup and resume or restart it.

### Negative / Trade-offs

- **Long-running async task in-process.** Potential infinite timeout on LLM calls
- **No retry logic in v1.** Individual thread failures will be logged but will not stop the batch;
- **Vertex API rate limits.** Large exports (many threads) may hit Gemini rate limits. A configurable delay between LLM calls will be added as an initial mitigation; proper backoff/retry is a follow-up.
- **Thread-level parallelism not in scope.** Threads are summarized sequentially to keep implementation simple and avoid hitting rate limits. Parallelism can be added later with a bounded executor.
- **Prompt hash computation.** The hash is computed from the prompt text string. If the prompt is changed (even whitespace), all threads will be re-analyzed. This is intentional but may cause unexpected re-analysis if prompts are edited frequently.
