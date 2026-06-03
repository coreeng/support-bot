# User guide: ROLE_SUPPORT_ENGINEER

Support engineers are the primary operators of the support bot. This role is assigned to members of the support Slack group (`team.support.group-ref` in config) and grants full ticket management capabilities plus access to all analytics.

This guide assumes you are also familiar with the [base user guide](./role-user.md).

## Working from Slack

Most of the ticket lifecycle happens directly in the support channel without needing to open the UI.

### Creating a ticket

When a tenant posts a question in the support channel, add the 👀 (`eyes`) reaction to their message. The bot will:

1. Create a ticket and record you as the assignee (if assignment is enabled — first reactor wins).
2. Add a 🎫 (`ticket`) reaction to confirm the ticket was created.
3. Post a form in the thread for you to fill in the ticket details (tags, impact, author's team).

Only support team members can trigger ticket creation — reactions from other users are silently ignored.

A few things to know:
- React to the **top-level message**, not a reply in the thread. Reactions on thread replies are ignored.
- Re-adding the reaction after removing it is safe — the handler is idempotent.
- If assignment is disabled on your deployment, the ticket is still created but not auto-assigned.

### Closing a ticket

When the issue is resolved, the bot posts a ✅ (`white_check_mark`) reaction on the original message automatically when the ticket is closed (via the UI or the bot's own close flow).

### Escalating a ticket

When a ticket is created, the bot posts a message in the thread with two buttons:

- **Full Summary** — opens a modal with the ticket summary
- **Escalate** — opens a modal to select the escalation team (only shown when the ticket is not yet closed)

Click **Escalate**, select the target team, and confirm. The bot will tag that team's Slack group in the thread, log the escalation against the ticket, and add a ⚠️ (`warning`) reaction to the original message. The escalation team must be in the support channel to see the tag.

### Stale tickets

If an open ticket has had no activity for the configured period (default: 3 days), the bot marks it stale and sends a reminder in the thread. It will repeat that reminder daily until action is taken. You can clear stale status by updating the ticket in the UI.

---

## Managing tickets

Click any ticket row to open the edit modal. As a support engineer you can change:

| Field | Options |
|---|---|
| **Status** | `Opened`, `Closed`, `Stale` |
| **Support engineer** | Any member of the support team (if assignment is enabled) |
| **Author's team** | The tenant team who raised the issue — the bot suggests teams based on the thread context |
| **Tags** | One or more tags from the configured tag registry (at least one required) |
| **Impact** | One of the configured impact levels (required) |

All four required fields (status, author's team, tags, impact) must be set before you can save. If you try to close a ticket that has unresolved escalations, the modal warns you — closing the ticket also closes all its escalations.

The "Open in Slack" button in the modal footer takes you directly to the original thread if you need more context before editing.

## Metrics dashboard

You have access to all analytics pages:

- **Stats** — the main dashboard with first-response and resolution SLA charts, weekly ticket counts, and escalation breakdowns by team, tag, and impact
- **SLA** — SLA percentile and distribution views
- **Health** — service health indicators

These pages are also accessible to leadership, but only support engineers can feed new data into them via analysis (see below).

## Knowledge gaps and support area analysis

The Knowledge Gaps page shows support areas and knowledge gaps identified from historical ticket data. As a support engineer you can refresh this data by running or managing analysis.

### When automated analysis is enabled

Click **Run Analysis**, select a query window (last week, month, or quarter), and click **Run Analysis** again. The page shows live progress while the job runs. Once complete, the support area summary updates automatically.

### When automated analysis is disabled (manual workflow)

The same button opens a panel with three options:

1. **Export** — downloads a ZIP of the raw thread texts from the selected time window. This is the input you feed to the analysis script.
2. **Analysis Bundle** — downloads the analysis prompt and script. Run this locally or in CI against the exported data to produce a `.jsonl` results file.
3. **Import** — uploads a `.jsonl` results file produced by the analysis bundle. This updates the support area summary visible to everyone.

The typical manual cycle is: Export → run the bundle locally → Import.

## Escalations

You can view the full escalations list and its filters. To escalate a ticket, use the **Escalate** button on the bot's ticket message in the Slack thread — see [Escalating a ticket](#escalating-a-ticket) above.

## Tips for new support engineers

- Tag tickets accurately — tags feed the escalation trend and knowledge gap analysis, so they're more useful than they appear.
- Set "Author's team" before closing a ticket. The bot suggests likely teams based on the thread, but verify it — this field drives the per-team metrics.
- If a ticket has been sitting as "opened" for a while with no reply, mark it "stale" rather than leaving it open — it keeps the metrics honest.
- Check the Stats page weekly to spot patterns (recurring tags, teams with high escalation rates) that might warrant proactive docs or a retro.
