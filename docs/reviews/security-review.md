# Security Review

## Purpose

Find security regressions in a change, especially around APIs, authentication, authorization, view permissions, secrets, and sensitive support-thread data handling.

## Change Context

Review the supplied change context: changed files, changed modules, change intent, module guidance, relevant ADRs, and other repository evidence. Use that context to decide whether the security posture matches the intended users, permissions, and scope.

## Auth, Permissions, And Data ADRs

When the change touches authentication, authorization, view permissions, or export/exposure of support-thread data, explicitly check the relevant ADRs from this list:

- `docs/adr/adr-003-rework-view-permissions.md` — view permissions for tickets and escalations.
- `docs/adr/adr-008-single-oauth2-oidc-provider.md` — single OAuth2/OIDC provider for authentication.
- `docs/adr/adr-005-configurable-thread-sanitisation.md` — configurable thread sanitisation for data export.
- `docs/adr/adr-001-support-area-summary.md` — summary data export/import for knowledge gap analysis.

## Scope Detection

Run this review for every change. Treat it as higher risk when the change includes:

- Controllers, route handlers, middleware, filters, or API clients
- Dex, LDAP, OAuth, OIDC, JWT, session, cookie, token, role, or permission code
- Database access paths or queries that enforce view permissions
- Logs, errors, exports, reports, Slack messages, or payloads that may expose sensitive data
- Dependencies, container images, Helm values, secrets, or deployment configuration

## Review Checklist

- New or changed APIs enforce authentication and authorization at every entry point.
- New or changed APIs have explicit RBAC where user roles matter.
- Auth, permission, and data-export changes conform to the relevant ADRs above.
- Responses, exports, and Slack-facing output do not expose thread content the requesting user cannot view, and exported data respects the configured sanitisation rules.
- Inputs are validated and encoded for their transport and storage boundary.
- Responses, logs, errors, and frontend-visible state do not expose secrets or unrelated user data.
- Secrets and credentials remain in environment/configuration stores, not source code or logs.
- Security-relevant dependency changes are intentional and justified.

## Approval Rules

Approve when the change does not show a blocking security concern, even if deferred follow-up is worth tracking separately.

## Request Changes Rules

Request changes for missing auth/RBAC on new APIs, view-permission bypass, unsanitised or over-broad data export, sensitive data exposure, hard-coded secrets, unsafe token/session handling, or auth behavior that conflicts with accepted ADRs or the intended access model in the change context.
