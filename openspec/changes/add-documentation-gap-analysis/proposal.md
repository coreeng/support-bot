## Why

Support engineers answer the same questions repeatedly because the platform's tenant-facing
documentation does not cover them, or covers them wrongly. The bot classifies why each ticket
was raised, but nothing compares a ticket with the documentation, so nobody can say which pages
are missing or wrong, or show that the documentation is improving.

## What Changes

- A new **Documentation Analysis** page, listed under Support Summary in the menu. For a chosen
  window it shows, for every closed ticket, whether the documentation:
  - answers the question correctly (`correct`);
  - does not cover it (`missing`);
  - is wrong about it (`incorrect`);
  - is not relevant, because the ticket is not a documentation question (`not_applicable`).
- Opening the page classifies the window's closed tickets that have no current result, in the
  background, with progress shown, as the Support Summary page does. There is no scheduled job.
- Classification extends the bot's existing LLM ticket analysis: the same provider, prompt
  versioning, and run-and-resume behaviour.
- The bot reads the documentation from one configured GitHub repository. It keeps a local copy
  and fetches again only when the copy is absent or was fetched more than 24 hours ago.

### Behaviour details

| Choice | Proposal | Main tradeoff |
|---|---|---|
| Evidence | Each result shows the pages consulted, a quoted passage and a short rationale. A `correct` or `incorrect` result whose quote is not in the documentation is discarded. | A person can check each verdict; some tickets stay unclassified. |
| Meaning of `incorrect` | The documentation contradicts the answer support gave, so it applies only to threads that contain a support answer. | Gives the model something to compare against; an error nobody corrected in the thread is not detected. |
| Staleness | A result is redone only when the prompts change, not when the documentation changes. Each result records the documentation version it was made against. | Cheap and simple; a ticket stays `missing` after its page is written. |
| Fetch failure | When a refresh fails and a copy exists, the run uses the old copy. | The page keeps working; results may use documentation fetched more than 24 hours ago. |
| Access | The Support Summary page's roles: leadership and support engineers. | A reader's visit starts LLM work, as it does there. |
| Window | The Support Summary page's default and 92-day limit. | Bounds the work one visit can start. |
| Enabling | On when an LLM provider and a documentation repository are both configured. | Nothing extra to set; no separate off switch. |
| Failed tickets | A ticket that fails is retried only after a delay, 24 hours by default. The delay is configurable. | Visits do not repeat failing model calls; a ticket that failed for a passing reason waits a day. |

The architectural decisions (a separate results table, routing by product area, a separate job
lock, the local copy under `/tmp`) are in `design.md`.

### Out of scope

- A scheduled run. It can be added later on the same entry point.
- Suggesting or raising documentation fixes.
- Comparing documentation with code changes.
- Answering tenants from the documentation.
- Classifying tickets that are still open.
- More than one documentation repository, or sources other than GitHub.
- A second, wider check of `missing` verdicts.
- Skipping tickets based on the existing ticket classification. Every closed ticket is analysed.
- GitHub settings of their own. This feature uses the credentials configured for PR tracking.

## Capabilities

### New Capabilities

- `documentation-gap-analysis`: classifies closed tickets against the configured documentation
  and shows the results, with evidence, for a chosen window.

### Modified Capabilities

None. The Support Summary page and the existing ticket classification behave as before.

## Impact

- `api`: a new package next to `analysis` and `summary`; a `doc_analysis` table and two prompt
  types in `analysis_prompt`; endpoints under `/documentation-analysis`; a GitHub
  client method to download a repository snapshot. The GitHub client, built today only when PR
  tracking is enabled with a GitHub repository, becomes available to this feature too.
- `ui`: a new page and menu entry, shown only when the feature is enabled.
- Configuration: a new top-level `documentation-analysis` block in `application.yaml`, exposed
  through the Helm chart. The API pod's existing writable `/tmp` holds the local copy.
- LLM cost and time: two model calls per ticket, the second carrying one or two
  product areas of documentation. A first visit to a busy window can run for tens of minutes,
  and the proxy provider's default 20-second timeout is likely too short.
- GitHub access: the bot's credentials need read access to the documentation repository.
- Personal data: the bot stores text derived from ticket threads (question, answer summary,
  rationale). The new columns must be added to `erasure-compliance.yaml` and reviewed.
- Documentation: `api/service/docs/configuration.md`, the support engineer and leadership user
  guides, and a runbook entry.
