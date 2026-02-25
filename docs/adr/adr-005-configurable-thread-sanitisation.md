# ADR: Configurable Thread Sanitisation for Data Export

**Status:** Proposed

## Context

The support bot exports Slack threads for AI analysis (see [ADR-001](adr-001-support-area-summary.md)). Before export, thread content is sanitised to remove PII.

Sanitisation rules are currently hardcoded. Different deployments have different privacy requirements, and changing what gets sanitised shouldn't require a product release.

## Decision

Sanitisation will become config-driven. Deployments will define two things:

1. **What to remove** — a list of regex patterns
2. **What to keep** — a file of exception words (used by name detection)

### Example

```yaml
summary-data:
  sanitisation:
    patterns:
      - "<?@?[UW][A-Z0-9]{8,}>?"                             # slack mentions
      - "<mailto:[^|>]+\\|[^>]+>"                            # slack mailto links
      - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"    # email addresses
    remove-capitalised-words: true
    exceptions-file: commonly-capitalised-words.txt
```

### How it works

| Field | What it does |
|-------|-------------|
| `patterns` | List of regexes. Each one removes all matches from every message. Applied in order. |
| `remove-capitalised-words` | When `true`, detects and removes capitalised words that look like names (e.g. "John Smith"). |
| `exceptions-file` | Words to keep even when `remove-capitalised-words` is on. One word per line. Deployments point this to their own file if needed. |

### Defaults

Out of the box, the bot removes Slack mentions and capitalised names using the bundled `commonly-capitalised-words.txt` exceptions file. To add email removal or any other pattern, deployments add a regex to `patterns` in their values file. No code change needed.

## Consequences

### Positive

- Updating sanitisation rules is a config change, not a release
- Each deployment controls its own privacy rules
- Backwards compatible. Defaults match current behaviour

### Negative

- Patterns can only remove, not mask or redact
- Name detection is not perfect — it may remove words that aren't names or miss names that don't follow typical capitalisation
