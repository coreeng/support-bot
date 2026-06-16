# User guides

Role-based guides for new team members. Start with the base user guide, then read the guide for your role.

| Guide | Role | Who it's for |
|---|---|---|
| [Base user](./role-user.md) | `ROLE_USER` | Everyone — assigned automatically on login |
| [Support engineer](./role-support-engineer.md) | `ROLE_SUPPORT_ENGINEER` | Support team members who manage and close tickets |
| [Leadership](./role-leadership.md) | `ROLE_LEADERSHIP` | Support leads who review metrics and trends |
| [Escalation team](./role-escalation.md) | `ROLE_ESCALATION` | Teams that receive escalations from the support team |

Roles are additive — you can hold more than one. Membership is based on Slack group membership and takes effect at next login.

| Capability | User | Support Engineer | Leadership | Escalation |
|------------|:----:|:----------------:|:----------:|:----------:|
| View tickets, escalations, tenant requests | ✅ | ✅ | ✅ | ✅ |
| View knowledge gaps summary | ✅ | ✅ | ✅ | ✅ |
| Edit tickets (status, tags, impact, assignee) | ❌ | ✅ | ❌ | ❌ |
| Close/escalate tickets | ❌ | ✅ | ❌ | ❌ |
| View metrics (Stats, SLA, Health) | ❌ | ✅ | ✅ | ❌ |
| Run and import knowledge gap analysis | ❌ | ✅ | ❌ | ❌ |
| "Escalated to My Team" widget | ❌ | ❌ | ❌ | ✅ |

For how roles are configured and assigned, see the [configuration docs](../../api/service/docs/configuration.md#roles).
