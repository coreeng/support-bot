## Why

Support engineers answer the same questions repeatedly because the platform's tenant-facing
documentation does not cover them, or covers them wrongly. The bot classifies why each ticket
was raised, but nothing compares a ticket with the documentation, so nobody can say which pages
are missing or wrong.

## What Changes

- A new **Documentation Analysis** page, listed under Support Summary in the menu. For a chosen
  window it shows, for every closed ticket, whether the documentation:
  - answers the question correctly (`correct`);
  - does not cover it (`missing`);
  - conflicts with the answer support gave (`incorrect`);
  - is not relevant, because the ticket is not a documentation question (`not_applicable`).
- Opening the page classifies the window's closed tickets that have no current result, in the
  background, with progress shown, as the Support Summary page does, up to a daily limit. There
  is no scheduled job.
- Classification extends the bot's existing LLM ticket analysis: the same provider, prompt
  versioning, and run-and-resume behaviour.
- Documentation reaches the analysis through a **documentation source**, a layer of its own
  that is asked one thing: which documentation is relevant to this question. The first source
  reads one configured GitHub repository. It keeps a local copy, fetched again only when the
  copy is absent or was fetched more than 24 hours ago, and chooses which product areas to
  read. Another source, such as an MCP server, can replace it later without changing how
  tickets are fetched, classified, checked or shown.

### Behaviour details

| Choice | Proposal | Main tradeoff |
|---|---|---|
| Evidence | Each result shows the pages consulted, a quoted passage and a short rationale. A `correct` or `incorrect` result whose quote is not in the page it cites is recorded as a failed attempt, not as a result. | A person can check each verdict; some tickets stay unclassified, and only these two classes can be lost this way. |
| When the bot cannot tell | A question the documentation source says nothing covers is `missing`. A question the consulted pages cannot settle, because it belongs elsewhere or the thread is unclear, is a failed attempt, not `missing`. | `missing` stays trustworthy; some tickets stay unclassified. |
| Reliability | Verdicts are compared with support engineers' labels on at least 150 real tickets, before building and again before release, against targets in `design.md`. | A missed target delays the build or the release. |
| Meaning of `incorrect` | The documentation contradicts the answer support gave, so it applies only to threads that contain a support answer. The page calls it "Conflicts with support's answer" and shows both, because either may be the one that is wrong. | Gives the model something to compare against; an error nobody corrected in the thread is not detected, and a person decides which side to fix. |
| Who said what | The bot labels each thread message as tenant, support or bot, from the support team's current membership, and sends the label instead of the author. | The model does not guess which message is support's answer; a message from someone who has since left the team counts as a tenant's. |
| Staleness | A result is redone only when the prompts, or the settings that change results, change, not when the documentation changes. Each result records the documentation version it was made against. After such a change, old results are hidden until redone. | Cheap and simple; a ticket stays `missing` after its page is written, and the page shows fewer results while a window is being redone. |
| Fetch failure | When a refresh fails and a copy exists, the run uses the old copy. | The page keeps working; results may use documentation fetched more than 24 hours ago. |
| Access | The Support Summary page's roles: leadership and support engineers. | A reader's visit starts LLM work, as it does there. |
| Cost limit | At most a configured number of tickets, 500 by default, are attempted in any 24 hours. | Bounds what visits can spend; a large window fills in over several days. |
| Window | The Support Summary page's default and 92-day limit. | Bounds the work one visit can start. |
| Enabling | Off by default. On when `documentation-analysis.enabled` is set and an LLM provider, a documentation repository and GitHub credentials are configured; startup fails if it is enabled without a repository or credentials. | An explicit switch for a feature that spends tokens; one more setting. |
| Failed tickets | A provider error (rate limit, timeout) is retried within the run, waiting longer each time. Five tickets in a row failing that way stop the run without marking them failed. Any other failed ticket is attempted again after a delay, 24 hours by default, three times at most; then only when the prompts or settings change. | An outage does not park a whole window, and a ticket that cannot be classified is not retried for ever; a ticket that failed three times stays failed until something changes. |

The architectural decisions (the documentation source boundary, a separate results table, a
separate job lock and worker, and for the GitHub source routing by product area and the local
copy under `/tmp`) are in `design.md`.

### Out of scope

- A scheduled run. It can be added later on the same entry point.
- Suggesting or raising documentation fixes.
- Comparing documentation with code changes.
- Answering tenants from the documentation.
- Classifying tickets that are still open.
- Any documentation source other than the GitHub repository one, and more than one source or
  repository at a time. The boundary is built so that a source can be added later.
- A second, wider check of `missing` verdicts.
- A button to start or repeat a run. Visits start runs, bounded by the daily limit.
- Showing that the documentation improves over time. A result is not redone when the
  documentation changes, so a ticket stays `missing` after its page is written.
- Skipping tickets based on the existing ticket classification. Every closed ticket is analysed.
- GitHub settings of their own. This feature uses the credentials configured for PR tracking.

## Capabilities

### New Capabilities

- `documentation-gap-analysis`: classifies closed tickets against documentation supplied by a
  documentation source and shows the results, with evidence, for a chosen window. It does not
  depend on which source is configured.
- `documentation-source-github`: the first documentation source. It reads a GitHub repository
  and finds the documentation relevant to a question by routing to product areas.

### Modified Capabilities

None. The Support Summary page and the existing ticket classification behave as before.

## Impact

- `api`: a new package next to `analysis` and `summary` for the analysis and the documentation
  source boundary, and a package of its own for the GitHub source; a `doc_analysis` table and
  two prompt types in `analysis_prompt`; endpoints under `/documentation-analysis`; a GitHub
  client method to download a repository snapshot. The GitHub client, built today only when PR
  tracking is enabled with a GitHub repository, becomes available to this feature too.
- `ui`: a new page and menu entry, shown only when the feature is enabled.
- Configuration: a new top-level `documentation-analysis` block in `application.yaml`, exposed
  through the Helm chart. The API pod's existing writable `/tmp` holds the local copy.
- Concurrency: a documentation run can be active alongside a Support Summary refresh or a
  manual analysis run, so two LLM jobs can call the provider at once. Today there is only ever
  one. A new ADR records this.
- LLM cost and time: far more per ticket than the existing classification, which sends only the
  thread. With the GitHub source on the reference repository used for sizing, each ticket costs
  a routing call of roughly 20k input tokens (the page index) and, for a documentation
  question, a classification call of roughly 35k to 165k (one or two product areas). That is in
  the order of 5 to 19 million input tokens per 100 tickets, and a busy window is likely to
  take hours, not minutes.
  These are estimates: task 1.2 measures tokens and seconds per ticket and replaces them. The
  daily limit caps the spend.
- Provider timeouts: the proxy provider's default of 20 seconds and the Anthropic provider's of
  60 seconds are both likely too short for the classification call.
- GitHub access: the bot's credentials need read access to the contents of the documentation
  repository and nothing more. They are checked at startup when the feature is enabled.
- Personal data: the bot stores text derived from ticket threads (question, answer summary,
  rationale). The new columns must be added to `erasure-compliance.yaml` and reviewed. Thread
  sanitisation (ADR-005) removes nothing unless a deployment configures patterns; the stored
  text is passed through the same patterns before it is saved.
- Data sent to the LLM provider: documentation content, which the bot has not sent before, as
  well as thread text.
- Documentation: `api/service/docs/configuration.md`, the support engineer and leadership user
  guides, a runbook entry, and a note in `CONTRIBUTING.md` on how OpenSpec changes relate to
  ADRs.
