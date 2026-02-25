# ADR: Configurable Thread Sanitisation for Data Export

**Status:** Proposed

## Context

The support bot exports Slack threads for AI analysis (see [ADR-001](adr-001-support-area-summary.md)). Before export, thread content is sanitised to remove PII.

Sanitisation rules are currently hardcoded. Different deployments have different privacy requirements, and changing what gets sanitised shouldn't require a product release.

## Decision

Sanitisation will become fully config-driven. Deployments will define two things:

1. **What to remove** — a list of regex patterns
2. **What to keep** — a list of words to preserve even if matched by a pattern

By default, no sanitisation is applied. Each deployment configures the rules appropriate to their needs.

### Example

```yaml
summary-data:
  sanitisation:
    patterns:
      - "<?@?[UW][A-Z0-9]{8,}>?"                            # slack mentions
      - "<mailto:[^|>]+\\|[^>]+>"                           # slack mailto links
      - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"   # email addresses
    exceptions:
      - Monday
      - Tuesday
      - Kubernetes
      - Azure
```

### How it works

| Field | What it does |
|-------|-------------|
| `patterns` | List of regexes. Each one removes all matches from every message. Applied in order. |
| `exceptions` | List of words to keep. If a pattern matches one of these words, it is not removed. |

### Defaults

No sanitisation is applied out of the box. Deployments configure their own `patterns` and `exceptions` in their values files. No code change needed to add or update rules.

## Consequences

### Positive

- Updating sanitisation rules is a config change, not a release
- Each deployment controls its own privacy rules
- No opinions baked into the product — deployments opt in to what they need

### Negative

- Patterns can only remove, not mask or redact
