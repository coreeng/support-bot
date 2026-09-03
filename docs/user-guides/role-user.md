# User guide: ROLE_USER

Everyone who logs into the support bot UI is automatically assigned `ROLE_USER`. No special group membership is required — if you can authenticate, you have this role.

This guide covers what you can see and do as a standard authenticated user.

## What you can access

### Tickets

The home page shows all support tickets across the teams you have access to (the old `/tickets` address still redirects here, filters included). Each row represents a support thread from Slack.

You can:
- Browse and filter tickets by date, status, team, impact, and tag
- Click a ticket to open its detail view, which shows the original Slack message, an AI summary, status history, and any escalations on that ticket
- Follow the "Open in Slack" link in the detail view to jump directly to the original thread

You cannot edit ticket fields. The detail modal is read-only — fields like status, tags, and impact are displayed but not editable. Only support engineers can make changes.

### Escalations

The Escalations page lists all escalations raised across the support channel. You can filter by date range, status (ongoing/resolved), team, impact, and tag, and sort by any column.

This is a read-only view. You can see which tickets were escalated, to which team, when, and how long they've been open.

### Tenant requests

The Tenant Requests page is accessible to all authenticated users. It provides a view of requests raised by tenant teams.

## What you cannot access

- **Metrics dashboard** (Stats, SLA, Health pages) — requires `ROLE_SUPPORT_ENGINEER` or `ROLE_LEADERSHIP`
- **Support Summary** (`/summary`, the page that replaced Knowledge Gaps) — requires `ROLE_SUPPORT_ENGINEER` or `ROLE_LEADERSHIP`; it does not appear in your sidebar
- **Editing tickets** — requires `ROLE_SUPPORT_ENGINEER`
- **Re-running ticket classification** — requires `ROLE_SUPPORT_ENGINEER` (API only; the Support Summary classifies tickets automatically when opened)
- **"Escalated to My Team" widget** — requires `ROLE_ESCALATION`

## Getting additional access

If you need to manage tickets or view metrics, ask the support lead to add you to the appropriate Slack group:

| What you need | Slack group to join |
|---|---|
| Edit tickets, view metrics and the Support Summary | Support engineer group |
| View metrics and the Support Summary only | Leadership group |
| Resolve escalations assigned to your team | Escalation team group |

Role membership takes effect at your next login.
