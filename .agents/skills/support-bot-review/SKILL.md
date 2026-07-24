---
name: elevate-review
description: Use when reviewing an Elevate change, local branch, or GitHub PR before merge, especially when focused security, ADR, frontend, backend, or high-risk-surface feedback is needed.
---

# Elevate Review

## Overview

Run five independent, focused reviews against either the current branch or a GitHub PR number. PR-number reviews must happen in an isolated git worktree. The skill builds one shared review context, including Jira ticket context from the Atlassian MCP server, dispatches one separate general-purpose worker per review, then deduplicates and moderates findings before printing PR-ready output.

## Required Inputs

- Shared review context built by this skill
- Review rubrics in `docs/reviews/`
- Root `AGENTS.md`
- Touched component `AGENTS.md` files
- Relevant `docs/adr/*.md`, plans, and issue documents
- Jira ticket context when a Jira key is supplied or inferred, or the user's explicit reason when there is no Jira ticket

## Entry Modes

| User Request | Mode | Review Location |
|--------------|------|-----------------|
| Review the current branch, local change, local PR, or current work | Local branch mode | Current checkout |
| Review PR `<number>` | PR number mode | `.worktrees/pr-<number>-review` |

## Local Branch Mode Preflight

1. Identify the current branch.
2. Compare with `origin/main` using the merge-base diff, not local `main` and not only uncommitted changes.
3. Capture changed files, changed delivery units, and a diff summary for the shared review context.

Useful commands:

```bash
git diff --name-status origin/main...HEAD
git diff --stat origin/main...HEAD
git diff origin/main...HEAD
```

If `origin/main` may be stale, say so in the console summary. Do not use local `main` as a fallback.

## PR Number Mode Preflight

Use this mode when the user supplies a PR number. Do not check out the PR in the current workspace.

1. Verify `gh` is available and authenticated.
2. Read PR metadata:

```bash
gh pr view <number> --json number,headRefName,baseRefName,headRepositoryOwner,headRepository
```

3. Create or reuse an isolated worktree at `.worktrees/pr-<number>-review`:
   - If the path does not exist, create the `.worktrees` parent and add a worktree for the PR branch.
   - If the path exists, reuse it only if it is already a git worktree for the same PR branch.
   - If the path exists for anything else, stop and ask before overwriting or deleting it.
4. Check out the PR in that worktree, not in the original checkout. Create the worktree from the remote-tracking base branch, not local `main`:

```bash
mkdir -p .worktrees
git worktree add --detach .worktrees/pr-<number>-review origin/<baseRefName>
gh pr checkout <number>
```

Run `gh pr checkout <number>` with the worktree as the working directory.

5. In the worktree, fetch or update the PR base branch if needed, then compare against the remote-tracking PR base branch from metadata. Never compare a PR against local `main`:

```bash
git diff --name-status origin/<baseRefName>...HEAD
git diff --stat origin/<baseRefName>...HEAD
git diff origin/<baseRefName>...HEAD
```

6. Capture PR number, worktree path, head branch, base branch, changed files, changed delivery units, and a diff summary for the shared review context.

If `gh` is unavailable, authentication fails, PR metadata cannot be read, or PR checkout fails, stop with a clear blocker. Do not fall back to reviewing the current checkout.

## Jira Context

Gather Jira ticket context once and include it in the shared review context.

1. If the user supplies a Jira key such as `PT-456`, use it.
2. If no key is supplied, infer one from PR title/body, branch name, or commits in the reviewed range.
3. If no key can be inferred, prompt the user before dispatching review workers. The user must either provide the Jira key or state that there is no Jira ticket and give a short reason.
4. If the user provides a Jira key, use it. If the user states there is no Jira ticket, set `Jira: none - <user reason>` in the shared review context.
5. If a key is found or provided, read the issue context with the Atlassian MCP Jira issue-read tool, such as `getJiraIssue` or `mcp__atlassian__getJiraIssue`. Use cloud ID `33d26043-7c2e-4336-9417-5b2f478506e7` and request these fields:
   - `summary`
   - `status`
   - `assignee`
   - `description`
   - `parent`
   - `customfield_10093` (Motivation)
   - `customfield_10094` (Requirements)
   - `customfield_10097` (Wireframe)
6. If no Atlassian MCP Jira issue-read tool is available, authentication fails, the configured token lacks Jira read access, or the Jira issue cannot be fetched, stop before dispatching review workers. Tell the user to configure Atlassian MCP with a read-only Jira API token as described in `CONTRIBUTING.md`, then rerun the review.
7. Extract useful review context from the returned issue:
    - key
    - summary
    - status
    - assignee
    - description
    - parent or epic when available
    - motivation, requirements, and wireframe fields when available for epics
8. Jira descriptions and custom fields may be Atlassian Document Format JSON; recursively extract text from `content` nodes and preserve headings, list items, and links where useful.

Do not use `Jira: not provided` merely because inference failed. The shared review context must contain either fetched Jira context or `none - <user reason>`. A known Jira key without fetched Jira context is a blocker, not review context.

Missing Jira context is not automatically a `fix now`. Treat it as unknown intent unless the implementation appears to solve the wrong problem or the missing ticket context makes merge unsafe.

## Shared Review Context

Build this once before running any focused review. Each review must use this as the single source of truth for what changed and why.

```text
Review Target: local branch | PR <number>
Worktree: <path or N/A>
Head: <branch or SHA>
Base: <base branch>
Diff Range: <base>...HEAD
Diff Commands: <commands used>
Changed Files: <name-status summary>
Changed Delivery Units: <list>
Diff Summary: <stat and concise behavioral summary>
Jira: <key, summary, status, relevant description/requirements; key with fetch failure; or none - user reason>
Guidance Read: <root/component AGENTS.md files>
Relevant Repo Context: <ADRs, plans, issue docs, tests, or N/A>
```

Do not let individual reviews choose a different base branch, diff range, or Jira ticket. If a reviewer believes the shared context is wrong, it must report that as a finding instead of silently changing inputs.

## Review Workers

Run these independently. Dispatch one separate general-purpose worker for each review:

| Review | Rubric |
|--------|--------|
| Security Review | `docs/reviews/security-review.md` |
| ADR Review | `docs/reviews/adr-review.md` |
| Frontend Review | `docs/reviews/frontend-review.md` |
| Backend Review | `docs/reviews/backend-review.md` |
| High-Risk Surface Review | `docs/reviews/high-risk-surface-review.md` |

Each review worker gets exactly two inputs:

- The shared review context from this skill
- That review's rubric

Worker instructions:

- Use a host-provided general-purpose worker.
- Workers are read-only: they must not edit files, run formatters, apply fixes, commit, or create report files.
- Each worker returns one Markdown review section using the required summary format.
- Each worker must decide only its own review outcome: `approved`, `request changes`, or `not applicable`.
- Run the five workers concurrently when possible; otherwise run them sequentially without changing their prompts or shared context.
- The coordinating worker must not print the returned sections immediately. It must first run finding moderation, then print the moderated five-section output and compact index.
- If a worker fails, do not synthesize its approval. Mark that review as `request changes` with a `fix now` finding explaining that the review could not be completed.

Prompt template for each review worker:

```text
You are running the <Review Name> for an Elevate change. Do not modify files.

Use exactly these inputs:
1. Shared review context:
<paste shared review context>

2. Rubric:
<paste docs/reviews/<rubric>.md>

Return one GitHub-flavored Markdown review section using the Elevate Review summary format. Use only these finding classifications: fix now, defer, reject with reason. Do not emit an overall verdict. Do not choose a different diff range, base branch, Jira ticket, or rubric.
```

## Finding Classifications

Every finding must use exactly one classification:

| Classification | Meaning |
|----------------|---------|
| `fix now` | Must be fixed before merge. Any `fix now` makes that review `request changes`. |
| `defer` | Valid concern, but not blocking this change. Defer-only reviews are `approved`. |
| `reject with reason` | Considered and explicitly not a problem. Include the rationale. |

Do not use severity-only labels such as blocker, major, minor, or nit. Do not emit `approved with comments`.

## Finding Moderation

After all review workers return and before printing the final review, moderate the findings.

### Deduplicate Findings

1. Parse every finding from every review section.
2. Group findings that describe the same underlying issue, even when they cite different rubrics, files, wording, or impacts.
3. Treat findings as duplicates when fixing one code or contract problem would resolve all of them.
4. Do not group findings merely because they touch the same file, endpoint, or feature.
5. Pick one canonical finding per duplicate group. Prefer, in order:
   - the finding with the clearest root-cause statement and action;
   - the finding from the most directly owning review area;
   - the earliest review in this order: Security, ADR, Frontend, Backend, High-Risk Surface.
6. Preserve useful evidence from duplicates by merging it into the canonical finding if it adds materially different context.

Example duplicate group: if Backend Review and High-Risk Surface Review both report that exported YAML omits DB identifiers required for import update-vs-create semantics, keep one canonical finding and suppress the duplicate in the later section.

### User Classification Gates

Before printing the final review, prompt the user to confirm moderated finding classifications. Run the `fix now` gate first, then the `defer` gate.

#### Fix-Now Gate

Prompt the user with the full canonical YAML for all unique findings currently classified as `fix now`, followed by a concise classification checklist.

Prompt format:

````text
Review the unique fix-now findings before I print the PR-ready review. Mark each as fix now, defer, or reject with reason.

```yaml
- id: 1
  current_classification: fix now
  canonical_review: <Review Name>
  summary: <short issue summary>
  file: <path:line or N/A>
  issue: <full issue>
  evidence: <full evidence>
  impact: <full impact>
  action: <full action>
```

1. [fix now/defer/reject with reason] <short issue> (<canonical review>, <file>)
2. [fix now/defer/reject with reason] <short issue> (<canonical review>, <file>)
````

Rules:

- Ask once for all unique `fix now` findings.
- Include a short summary and the full canonical finding fields before the checklist: file, issue, evidence, impact, and action. Do not require the user to ask for more detail before classifying.
- Do not print the final review until the user has classified every unique `fix now` finding.
- If the user keeps a finding as `fix now`, the canonical review remains `request changes`.
- If the user marks a finding as `defer`, change the canonical finding and every duplicate in that group to `defer`.
- If the user marks a finding as `defer` and gives a reason, include that reason in the finding action.
- If the user marks a finding as `defer` and gives no reason, keep the reviewer-provided action as the deferred follow-up.
- If the user marks a finding as `reject with reason`, require or ask for the reason before continuing. Change the canonical finding and every duplicate in that group to `reject with reason`, and include the reason in the action.
- If there are no unique `fix now` findings, skip this gate.

#### Defer Gate

After the fix-now gate, prompt the user with the full canonical YAML for all unique findings currently classified as `defer`, including findings that were originally `defer` and findings reclassified to `defer` during the fix-now gate, followed by a concise classification checklist.

Prompt format:

````text
Review the unique defer findings before I print the PR-ready review. Confirm each as defer, optionally add a reason, or reject with reason.

```yaml
- id: 1
  current_classification: defer
  canonical_review: <Review Name>
  summary: <short issue summary>
  file: <path:line or N/A>
  issue: <full issue>
  evidence: <full evidence>
  impact: <full impact>
  action: <full action>
```

1. [defer/reject with reason] <short issue> (<canonical review>, <file>) reason: <optional>
2. [defer/reject with reason] <short issue> (<canonical review>, <file>) reason: <optional>
````

Rules:

- Ask once for all unique `defer` findings after fix-now reclassification is complete.
- Include a short summary and the full canonical finding fields before the checklist: file, issue, evidence, impact, and action. Do not require the user to ask for more detail before classifying.
- Do not print the final review until the user has confirmed every unique `defer` finding.
- If the user confirms `defer` and gives a reason, include that reason in the finding action.
- If the user confirms `defer` and gives no reason, keep the reviewer-provided action as the deferred follow-up.
- If the user marks a finding as `reject with reason`, require or ask for the reason before continuing. Change the canonical finding and every duplicate in that group to `reject with reason`, and include the reason in the action.
- If there are no unique `defer` findings, skip this gate.

### Suppress Duplicates In Final Output

Keep all five review sections in the final output, but avoid repeating the full text of duplicate findings.

For the canonical review section, print the full canonical finding with the user-approved classification.

For later duplicate sections, replace the full duplicate with a compact suppression finding:

```text
- classification: reject with reason | defer
  file: <duplicate file or N/A>
  issue: Duplicate finding: <short issue>.
  evidence: Same underlying issue as <Canonical Review> finding.
  impact: Already represented once in the PR-ready review output.
  action: See <Canonical Review> finding.
```

Use `reject with reason` for duplicate suppression when the canonical finding remains `fix now` or is reclassified as `reject with reason`. Use `defer` when the canonical finding is classified as `defer`.

## Markdown Summary Format

Print the final moderated review as GitHub-flavored Markdown. Use a compact metadata table, second-level review headings, bold field labels, and fenced `yaml` blocks for findings so the output can be pasted directly into GitHub or Jira.

````markdown
| Field | Value |
|---|---|
| Review Target | local branch \| PR <number> |
| Worktree | <path or N/A> |
| Head | <branch or SHA> |
| Base | <base branch> |
| Jira | <key and summary, fetch failure, or not provided> |

## <Review Name>
**Outcome:** approved | request changes | not applicable  
**Rubric:** `docs/reviews/<file>.md`  
**Scope:** <why this review applies, or why it is not applicable>

**Findings:**
```yaml
- classification: fix now | defer | reject with reason
  file: path:line or N/A
  issue: concise statement
  evidence: diff, ADR, AGENTS.md, or repo evidence
  impact: concrete consequence
  action: required fix, deferred follow-up, or rejection rationale
```

If no findings:
**Findings:** none
````

After all five moderated sections, print only a compact Markdown table of outcomes. Do not collapse them into a single overall verdict.

After printing the final moderated review and compact outcomes table to the console, ask the user which delivery action to take next:

1. Save the printed review to a file.
2. Add the printed review as a comment on the PR.

Do not save a file or post a PR comment before the user chooses one of these options. If the review target has no known PR number, say that PR commenting needs a PR number before offering the file option.

## Lifecycle Rules

- A review with any `fix now` finding is `request changes`.
- A review with no findings is `approved`, unless its rubric says `not applicable`.
- A review with only `defer`, `reject with reason`, and/or duplicate-suppression findings is `approved`.
- Reviews remain independent during worker execution. Deduplication happens only in the coordinating worker's moderation step after all reviews have returned.
- If evidence is insufficient, classify the uncertainty as `fix now` only when merge would be unsafe without resolving it; otherwise use `defer` with the missing evidence.

## Test Recommendations

This review skill does not run functional, integration, Extended, or Kind tests. When the change warrants one of those workflows, recommend the applicable repository testing skill after the moderated review. Do not invoke that skill or execute its commands automatically; any later run must follow that skill's separate preview and explicit-confirmation contract.

## Common Mistakes

| Mistake | Correction |
|---------|------------|
| Reviewing only the working tree | Review `origin/main...HEAD` for local branch mode, including committed branch changes. |
| Comparing against local `main` | Use `origin/main...HEAD` for local branch mode and `origin/<baseRefName>...HEAD` for PR number mode. Local `main` can be stale or unrelated to the PR base. |
| Checking out a PR in the current workspace | For PR numbers, create and review inside `.worktrees/pr-<number>-review`. |
| Falling back to the current branch after PR checkout fails | Stop with a clear blocker instead. |
| Letting each review decide what to diff | Build one shared review context and pass it to every review. |
| Ignoring a supplied Jira key | Include Jira context in the shared review context. |
| Setting Jira to not provided after inference fails | Ask the user for the Jira key or a no-ticket reason before dispatching review workers. |
| Fetching Jira ad hoc in each review worker | Fetch Jira once with Atlassian MCP before dispatching workers. |
| Continuing after Atlassian MCP fails for a known Jira key | Stop early and tell the user to configure Atlassian MCP with a read-only Jira API token. |
| Running all reviews in the coordinating worker | Dispatch one separate general-purpose worker per review. |
| Creating report files before the user chooses file output | Print the review to the console first, then offer file output or PR comment as the next action. |
| Printing plain-text output | Print the final moderated review as GitHub-flavored Markdown using the Markdown Summary Format. |
| Producing one combined verdict | Keep outcomes independent per review. |
| Using severity labels | Use only `fix now`, `defer`, or `reject with reason`. |
| Treating deferred findings as blocking | Defer-only reviews are `approved`. |
| Skipping ADR lookup | ADR review must identify and include selected accepted ADRs in the review output. |
| Treating `elevate-frontend/AGENTS.md` as background only | Frontend Review must validate frontend PRs against every applicable instruction in `elevate-frontend/AGENTS.md`. |
| Printing raw worker output directly | First dedupe findings and run the fix-now and defer classification gates. |
| Repeating the same fix-now issue in multiple sections | Keep the canonical finding and replace duplicates with compact suppression findings. |

## Red Flags

- "This is just a quick review" means still run all five review sections.
- "PR 123" means PR number mode; do not review the current checkout.
- "main...HEAD is close enough" means stop; use `origin/<baseRefName>...HEAD` for PR number mode or `origin/main...HEAD` for local branch mode.
- "PT-456" means include Jira ticket context in every focused review.
- "The rubric says diff" means use the shared context; rubrics do not own diff selection.
- "I'll just do the reviews myself" means stop and dispatch one separate general-purpose worker per review.
- "Frontend/backend did not change" means emit `not applicable`, not skip the section silently.
- "No findings" still requires an explicit outcome and scope note.
- "One review already caught it" does not remove another review worker's independent responsibility; it only affects final moderated output.
- "The workers are done" does not mean print final output; first dedupe and run the user classification gates.
