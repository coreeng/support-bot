# ADR: Real-Time Analysis Pipeline Triggered from UI

**Date:** 2026-02-23
**Status:** Proposed

---

## Context

The support bot already supports a knowledge-gap analysis workflow, but it is entirely offline: a user manually exports Slack threads as a ZIP, runs external scripts to summarise them with an LLM, then uploads a JSONL file via `POST /summary-data/import`. This is error-prone, slow to run, and requires access to tooling outside the application.

The team wants to trigger the same end-to-end pipeline from a single button click in the existing web UI, with live progress feedback and no risk of two analyses running concurrently.

The application is a stateless Spring Boot 3 service running on Kubernetes, backed by PostgreSQL, with an existing GCP/Azure platform-identity integration layer.

---

## Decision Drivers

- Reuse the existing analysis data model (`analysis` table, `AnalysisRepository`) and summary-data export logic where possible.
- Avoid introducing a separate worker process or message queue — keep operational complexity low.
- Leverage the existing GCP platform-identity integration so no new secrets need managing.
- Configuration must be consistent with existing Spring `application.yaml` / env-var patterns.
- The application can be restarted by Kubernetes descheduler; analysis pipeline must resume on restart
- LLM Analysis is time consuming and incurs cost; we want to eliminate re-analysis of the same thread with the same prompt

---

## Decision

### 1. LLM Integration — LangChain4j + Vertex AI (Gemini)

Add `dev.langchain4j:langchain4j-vertex-ai-gemini` as a Gradle dependency.
Wire a `ChatLanguageModel` bean (Google Gemini) via LangChain4j's Spring Boot auto-configuration.
Spring will pick up the Vertex AI configuration from `application.yaml`:

```yaml
analysis:
  vertex:
    project-id: ${VERTEX_PROJECT_ID}
    location: ${VERTEX_LOCATION:us-central1}
    model-name: ${VERTEX_MODEL_NAME:gemini-1.5-pro}
```

The `ChatLanguageModel` bean is annotated `@ConditionalOnProperty(name = "analysis.vertex.project-id")` so it is only created when Vertex is configured, keeping local/test environments unaffected.

### 2. GCP Identity — Workload Identity Federation (no new secrets)

The existing `platform-integration.gcp.enabled` integration already configures the Kubernetes `ServiceAccount` to carry a GCP Service Account annotation:

```
iam.gke.io/gcp-service-account: <GSA>@<project>.iam.gserviceaccount.com
```

The GCP SA is granted `roles/aiplatform.user` on the Vertex AI project.
LangChain4j's Vertex AI client uses Application Default Credentials (ADC), which on GKE automatically uses the projected service account token — no explicit credential configuration is needed in application code.

Enabling this for analysis:

```yaml
platform-integration:
  gcp:
    enabled: true                           # already exists; set to true for analysis
    vertex-service-account: ${GCP_VERTEX_SA:}   # new optional override
```

### 3. Analysis Pipeline — Async, Database-Tracked

A new `AnalysisBatchService` orchestrates the pipeline as a `@Async` Spring task:

1. **Export** — reuse existing `SummaryDataService` to fetch open Slack threads from the configured channel for the last N days. Filter out threads that already have an analysis record with a matching `prompt` to avoid re-analyzing with the same prompt. See the following section for more details.
2. **Summarise & Persist** — for each thread:
   - Build a prompt and call `ChatLanguageModel.generate()` via LangChain4j
   - Parse the model's response into an `AnalysisRecord` (one JSON record per thread matching the existing schema)
   - **Immediately persist** the record to the database via `AnalysisRepository.upsert()`
   - Update in-memory progress counters after each thread is analyzed

This approach ensures that analysis results are persisted incrementally, so if the batch process is interrupted, already-analyzed threads are not lost and will not be re-analyzed (unless the prompt changes).

### 4. Concurrency Control — Database Lock via `analysis_batch` Table

```sql
CREATE TABLE analysis_batch (
    id             TEXT PRIMARY KEY,
    days           INTEGER NOT NULL,       -- Number of days the batch is configured to analyze
    started_at     TIMESTAMP NOT NULL DEFAULT NOW(),
);

CREATE UNIQUE INDEX analysis_batch_id_idx
    ON analysis_batch (id)
```

The `analysis_batch` table holds **at most one record** indicating a batch is in progress. The unique index on `id` ensures only one row can exist at a time at the database level. `knowledge-gap-analysis` is the only supported batch id for now.

The `days` field records how many days of threads the batch was configured to analyze, allowing the application to resume or restart the batch with the same configuration if interrupted.

`POST /analysis/run` attempts to insert a row inside a transaction. If the unique index is violated, the endpoint returns `HTTP 409 Conflict`. On success the `@Async` task is started.

When the batch completes or fails, the row is deleted. In memory status is updated accordingly and exposed via status endpoint.


### 5. Avoid re-analysis with teh same prompt

In order to avoid re-analyzing the same thread with the same prompt, we will add a new column to the `analysis` table called `prompt`.
This column will store the hash of the prompt text. Before starting the analysis, we will compute the hash of the prompt text and check if there are any analysis records with a matching `prompt`. If there are, we will skip those threads.
Each analysis record with be updated with the hash of the prompt used to generate it.

#### Analysis Table Schema Update

The existing `analysis` table is extended with a `prompt` column to track which version of the prompt was used:

```sql
ALTER TABLE analysis ADD COLUMN prompt TEXT;
```

#### Computing the hash
```java
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

String hash = Hashing.murmur3_32_fixed()
        .hashString(input, StandardCharsets.UTF_8)
        .toString();   // e.g. "a1b2c3d4"
```

### Query to find threads that need to be analyzed

```sql
SELECT q.ts
    FROM query q
    JOIN ticket t on q.id = t.query_id
    JOIN analysis a on t.id = a.ticket_id
    WHERE t.status = 'closed' and t.last_interacted_at >= '2026-01-01' and a.prompt = 'a1b2c3d4'
```

### 6. Resume on pod restart

**Startup Hook:** On application startup, a `@PostConstruct` method in `AnalysisBatchService` checks for a running batch with id `knowledge-gap-analysis`. If found,
resumes the batch (re-fetches threads and continue analysis, skipping already-analyzed threads via `prompt` column)


### 7. New API Endpoints

| Method | Path              | Description                                                           |
|--------|-------------------|-----------------------------------------------------------------------|
| POST   | `/analysis/run`   | Start a new batch. Returns `202 Accepted` or `409 Conflict`.          |
| GET    | `/analysis/status`| Returns the imn-memory state of current @Async task (or 204 if none). |

Both endpoints are protected by the existing JWT security filter and require an authenticated user with `SUPPORT_ENGINEER` role.

### 8. Progress Panel — UI Polling

The UI adds a small panel (new `AnalysisProgressPanel` client component) that:

- Shows a **"Run Analysis"** button when no batch is active (`/analysis/status` returns 204 No Content)
- On click, calls `POST /api/analysis/run`.
- POST returns 202, polls `GET /api/analysis/status` every **3 seconds** using React Query's `refetchInterval`.
- Displays a progress bar derived from `(analysed_count / exported_count) * 100` with counts: `Exported: N | Analysed: N`.
- Disables the button while a batch is in progress.
- On 204 from the status endpoint, stops polling and shows a success/error state, then re-enables the button after a short delay.

---

## Consequences

### Positive

- **No new infrastructure.** The pipeline runs inside the existing Spring Boot pod; no queues, workers, or caches are needed.
- **No new secrets.** Workload Identity Federation means Vertex AI credentials are handled by GKE, consistent with the existing GCP integration pattern.
- **Safe concurrency.** The database unique index prevents double-runs durably, regardless of replica count.
- **Incremental — existing import/export flows unchanged.** The offline workflow continues to work; this is an additive change.
- **Progress is durable.** Counters in PostgreSQL survive pod restarts; the UI will resume polling the correct state after any disruption.
- **Incremental persistence.** Each thread is persisted immediately after LLM analysis, so partial progress is never lost.
- **Prompt versioning.** The `prompt` column enables skipping re-analysis when the prompt hasn't changed, saving API costs and time.
- **Resumable batches.** If a batch is interrupted, the application can detect the in-progress batch on startup and resume or restart it.

### Negative / Trade-offs

- **Long-running async task in-process.** Potential infinite timeout on LLM calls
- **No retry logic in v1.** Individual thread failures will be logged but will not stop the batch;
- **Vertex API rate limits.** Large exports (many threads) may hit Gemini rate limits. A configurable delay between LLM calls (`analysis.vertex.request-delay-ms`) will be added as an initial mitigation; proper backoff/retry is a follow-up.
- **Thread-level parallelism not in scope.** Threads are summarised sequentially to keep implementation simple and avoid hitting rate limits. Parallelism can be added later with a bounded executor.
- **Prompt hash computation.** The hash is computed from the prompt text string. If the prompt is changed (even whitespace), all threads will be re-analyzed. This is intentional but may cause unexpected re-analysis if prompts are edited frequently.

### Neutral

- The `analysis_batch` table holds only the current/most recent batch state. Historical batch runs are not tracked (this could be added later if audit history is needed).
- The existing `POST /summary-data/import` and `GET /summary-data/export` endpoints are unchanged and continue to support the manual offline workflow.
- The `prompt` column in the `analysis` table allows tracking which records were generated with which prompt version, enabling future prompt evolution and A/B testing scenarios.

