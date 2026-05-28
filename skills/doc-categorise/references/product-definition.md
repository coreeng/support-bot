---
name: Product definition input schema
description: How the skill ingests product and journey context — folder layout, file schemas, paste fallback, parsing rules, and execution gating. Load at the start of every run, before any scanning or classification.
---

# Product definition

This file specifies how the skill resolves the two values it cannot proceed without:

- `product_name` — a single string identifying the product the documentation belongs to.
- `journeys` — a list of journey records (possibly empty) representing the things a user does with the product.

There are two routes to resolve these values:

1. A `product-definition/` folder at the root of the repository being scanned (preferred — durable, version-controlled, reusable).
2. An interactive paste fallback at run time (used when the folder is missing or invalid).

The skill ingests this data only. It does **not** yet use journeys, users, features, or variations for classification, journey-relevance tagging, audience inference, gap analysis, or per-row suggested actions. Those are downstream steps and will be specified separately. For now, this file defines what comes in and where it lives.

## Location

The skill looks for a folder named `product-definition/` at the root of the repository being scanned. The skill is **read-only** with respect to this folder — it never creates, edits, moves, or deletes anything inside it. The folder is created and maintained by the human running the skill, not the skill itself.

If the folder is absent the skill falls back to the paste flow described below; it does **not** scaffold the folder from pasted input.

## Folder layout

```
<repo-root>/
  product-definition/
    product.md
    journeys/
      <journey-slug>.md
      <journey-slug>.md
      ...
```

- `product.md` — one file per run. Holds product-level metadata.
- `journeys/` — a folder of one-file-per-journey markdown files. Slugs are arbitrary; the skill identifies a journey by the `name` field in its frontmatter, not by filename.

The skill MUST exclude `product-definition/**` from the documentation scan, regardless of include globs supplied by the user. Journey files are inputs to the skill, not documentation to classify.

## `product.md` schema

Markdown document with YAML frontmatter. The body is free-text and is not parsed.

| Field      | Required | Type             | Notes                                                                                                                       |
| ---------- | -------- | ---------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `name`     | Yes      | non-empty string | The product name. Resolves to `product_name` for the rest of the skill.                                                     |
| `owners`   | Yes      | list of strings  | The product's owners — team names, individual names, or both.                                                               |
| `features` | No       | list of strings  | The product's features. Free-text labels. Verb-shaped names ("run-workload") are preferred over noun-shaped ("compute").    |

Example:

```yaml
---
name: Core Platform
owners: [Platform team]
features: [run-workload, ingress, observability]
---
```

## `journeys/<slug>.md` schema

Each file holds one journey. Markdown document with YAML frontmatter. The body is free-text describing the journey (expected outcome, prerequisites, edge cases); the skill reads the body but does not parse it. Richer prose helps downstream steps match documentation to the journey.

| Field         | Required | Type             | Notes                                                                                                                                                              |
| ------------- | -------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `name`        | Yes      | non-empty string | The journey name. Should be evergreen — chosen at an abstraction level that survives implementation churn.                                                         |
| `description` | No       | string           | One- or two-sentence description of the journey.                                                                                                                   |
| `users`       | No       | list of strings  | The user(s) who perform this journey. Plain labels — e.g. `[end-user]`, `[platform-engineer, sre]`.                                                                |
| `feature`     | No       | string           | The product feature this journey belongs to. Should match an entry in `product.md`'s `features` list; the skill does not enforce this and does not warn on mismatches. |
| `variations`  | No       | list of strings  | Distinct paths through the same journey — e.g. `[dev, staging, prod]`, `[stateless, stateful, cron-job]`, `[linux, macos, windows]`. Variations are paths through one journey, not separate journeys. |

Example:

```yaml
---
name: Deploy a workload
description: Get an application built and running on the platform, reachable by its consumers.
users: [end-user, application-developer]
feature: run-workload
variations: [stateless, stateful, cron-job]
---

# Deploy a workload

To deploy a workload the user needs a built image, a target environment, and credentials...
```

## Parsing rules

1. Read `product-definition/product.md`. If the file is missing, unreadable, has no frontmatter, or has a frontmatter without a non-empty `name` field, fall through to the paste fallback for **both** product and journeys — do not partially populate.
2. Read every `*.md` file under `product-definition/journeys/` (non-recursive — files directly under `journeys/`, not in subfolders).
3. For each journey file, parse the frontmatter. If the file has no frontmatter, or its frontmatter has no non-empty `name`, skip the file and record it in the run summary as a skipped input. Do not error out.
4. Trim whitespace on every scalar value. Empty strings after trimming are treated as absent.
5. For list fields, accept both YAML list syntax (`[a, b, c]` or `- a\n- b\n- c`) and a single string (which is normalised to a one-element list).
6. After parsing, print the resolved product (name, owners, features) and the journey list (names, counts, and any skipped files) back to the user. The user confirms before scanning begins.

## Paste fallback

If `product-definition/product.md` is missing or invalid (per rule 1 above):

### Step A — Ask for the product name

Ask the user verbatim:

> "What product is this documentation for?"

The user may type any value. Do not show suggestions. Apply the validation rules in *Validation* below.

If the user responds with `not applicable` (case-insensitive), set `product_name = "not provided"`.

### Step B — Ask for the journey list

Ask the user verbatim:

> "Paste your list of journeys (one per line, or markdown bullets). Type `not applicable` if there are no journeys."

If the user responds with a single line `not applicable` (case-insensitive), set `journeys = []`.

Otherwise, parse the paste line-by-line:

- Trim whitespace on each line.
- Strip a leading markdown bullet marker if present (`- `, `* `, or `1. ` / `2. ` / ...).
- Skip blank lines after trimming.
- Each remaining line becomes a journey record with `name` set to the line content and all other fields absent.

Pasted input is held in memory for the duration of the run only. The skill does **not** write the paste to disk and does **not** scaffold a `product-definition/` folder from it.

## Validation

The product name must be:
- non-empty after trimming,
- single-line,
- free of markdown formatting characters (`#`, `*`, `_`, `` ` ``, `[`, `]`).

Reject and re-ask if any rule is violated.

For pasted journeys, each non-blank line is accepted as a journey name; per-line validation is not applied (richer-prose journey descriptions belong in the folder route).

## Execution gating

The skill MUST NOT proceed to:

- repo scanning
- classification
- rewriting
- file generation
- reporting

until both `product_name` and `journeys` are resolved. `journeys` may be the empty list — that is a valid resolution and the skill MUST proceed in that case (it simply has no journey context for downstream steps).

## Run summary

Before any scanning, the skill prints a confirmation block to the user with:

- The resolved `product_name`.
- The resolved owners and features (if from the folder route).
- The count and names of resolved journeys.
- The count and paths of skipped journey files (if any).
- The route used: `product-definition/` folder, paste, or a mix.

The user confirms before the skill proceeds.
