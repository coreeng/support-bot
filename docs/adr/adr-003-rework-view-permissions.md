# ADR: Rework View Permissions for Tickets and Escalations

**Date:** 2026-02-24
**Status:** Accepted

---

## Context

The previous visibility model constrained non privileged users to only view their own team's tickets and escalations.

This can create friction in regards to cross-team collaboration and reduce dashboard adoption. In day-to-day support workflows,
it would be useful for teams to have visibility into tickets and escalations outside their own team, for cross-team incident
discovery, triage awareness, and learning from other's experiences.

The underlying data is Slack-derived operational data already broadly accessible within organization slack workspaces,
so strict UI-level isolation for these dashboards does not add meaningful security value in this context.

At the same time, we still need role-based controls for other UI views (for example, Analytics and SLA-related pages) to
avoid expanding access to privileged workflows.

---

## Affected Personas

- **Tenant users**: members of a team on a kubernetes cluster. Previously limited to their own data; now can browse other teams.
- **Escalation team members**: members of an on-call or escalation team. Same expanded access to Tickets/Escalations.
- **Support engineers / Leadership**: no change; retain full access to all tabs and data.

---

## Decision Drivers

- Increase adoption of Tickets and Escalations dashboards across tenant teams.
- Keep default UX intuitive: users should start in their own team scope.
- Preserve existing role-based restrictions for privileged tabs.
- Increase shared situational awareness.
- Prepare for follow-up capability: shareable deep links to filtered views.

---

## Decision

### 1. Visibility model for Tickets and Escalations

For authenticated users, Tickets and Escalations data visibility is expanded to support cross-team exploration through filtering.

- Default state for tenant users: scoped to their own team (current team scope).
- Users can switch team scope via team dropdowns/filters and view other teams' data.
- "Current team scope" remains the initial context and baseline behavior.

### 2. Team selection behavior

Team selector and page-level filters are used to control viewing scope:

- Sidebar "View as" selection defines the baseline scope.
- Page-level team filters can refine/override within the dashboard context.
- Filters are reset appropriately when the global scope changes to avoid stale selections carrying across contexts.

### 3. No-team behavior

When a logged-in user has no team assignments, team selection controls are not shown and dashboards do not expose data.

### 4. Access boundaries retained

Role-based restrictions for support-specific tabs remain unchanged (for example Analytics and SLA Dashboard).
This ADR changes data visibility patterns for Tickets/Escalations viewing, not privileged feature access.

### 5. Shareable filter links (follow-up scope)

This model intentionally establishes the groundwork for a later enhancement: encoding active filter state in the URL so
users can share pre-filtered Tickets/Escalations views. That work is tracked separately and is not part of this change.

---

## Consequences

### Positive

- Higher practical usefulness of Tickets/Escalations for all teams.
- Better cross-team collaboration and faster incident/escalation discovery.
- Product foundation for shareable filtered dashboard links.

### Negative / Trade-offs

- Users viewing cross-team data may encounter tickets or escalations outside their immediate context, which could be distracting if scoping labels are not clearly visible.
- Requires careful UI clarity so users always understand their active scope and filters (e.g., "Viewing as Team X" indicators).

### Neutral

- Support-only tab restrictions remain intact.
- No change to underlying Slack data source; change is primarily in UI visibility and filtering behavior.

