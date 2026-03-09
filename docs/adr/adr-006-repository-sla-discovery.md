# ADR: Repository SLA Discovery

**Date:** 2026-03-10
**Status:** Proposed

---

## Context

PR review SLAs are defined statically in the deployment config ([ADR-004](adr-004-pr-identification-and-sla-tracking.md)). When a repository's SLA changes, someone must update the config and redeploy.

Repositories can declare their own SLA in a standard file at the repo root. The bot should be able to read this file directly so repository teams own their SLA without needing a redeployment.

---

## Decision Drivers

- Existing deployments must continue to work with minimal config migration.
- Static and dynamic config should use the same SLA structure.
- Clients should get started with minimal configuration.

---

## Decision

### SLA structure

Whether defined inline or in a repo file, the SLA structure is the same:

```yaml
default: 48h
overrides:
  - path: infra/**
    sla: 1w
  - path: monitoring/**
    sla: 24h
```

| Field | Description |
|-------|------------|
| `default` | SLA that applies to all PRs unless overridden |
| `overrides` | Optional list of path-specific SLAs, checked in order |
| `overrides[].path` | Glob pattern matching files changed in the PR (e.g. `infra/**`) |
| `overrides[].sla` | SLA for PRs that touch files matching this path |

Overrides are checked in order against all files changed in the PR. The first override that matches any changed file wins and its SLA applies to the whole PR. If no override matches, `default` is used.

Both ISO-8601 (`PT48H`) and Go-style (`48h`, `2d`, `1w`) durations are supported. Values like `undefined` or `0` are treated as invalid. When found in a repo file, the bot falls back to inline config. When found in inline config, SLA tracking is skipped.

### Config

Each repository can define its SLA inline, from a file, or both:

```yaml
pr-review-tracking:
  sla-discovery:
    cache: PT24H                # how long to cache the file per repo

  repositories:
    # SLA from a file (if file missing, SLA tracking skipped)
    - name: org/repo-a
      owning-team: team-x
      sla:
        file: ".pr-sla.yaml"

    # SLA from a file, with inline fallback
    - name: org/repo-b
      owning-team: team-y
      sla:
        file: ".pr-sla.yaml"
        default: 48h

    # SLA defined inline
    - name: org/repo-c
      owning-team: team-z
      sla:
        default: 72h
        overrides:
          - path: infra/**
            sla: 1w
          - path: monitoring/**
            sla: 24h
```

When `file` is set, the bot fetches it from the repo's default branch via GitHub API and caches it. If the file can't be read, it falls back to inline `default`/`overrides` if provided. If no SLA can be resolved at all, SLA tracking is skipped for that PR.

### Migration from ADR-004

The per-repo `sla` field changes from a flat duration to a structured block:

```yaml
# Before (ADR-004)
- name: org/repo
  owning-team: team-code
  sla: PT48H

# After
- name: org/repo
  owning-team: team-code
  sla:
    default: PT48H
```

---

## Consequences

### Positive

- Repo teams own their SLAs, no redeployment when SLAs change
- Per-path overrides let repos define different SLAs for different areas
- Static and dynamic config use the same structure
- Inline fallback provides a safety net during rollout

### Negative / Trade-offs

- SLA file is cached per repo (default 24h), so file changes take up to `cache` duration to take effect
- Bot needs an additional GitHub API call to fetch PR changed files for override matching
- YAML files only

### Neutral

- Business day calendars (counting SLAs in working days with holiday awareness), separate enhancement
