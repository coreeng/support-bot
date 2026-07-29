# Frontend Review

## Purpose

Find frontend regressions in Next.js, React, TypeScript, UI behavior, API usage, auth gating, user-facing error handling, and frontend implementation quality.

## Change Context

Review the supplied change context: changed files, changed modules, change intent, module guidance, relevant ADRs, and other repository evidence. Use the change intent to check that the changed UI and frontend behavior match the intended user workflow and acceptance criteria.

## Scope Detection

Run this review when the change includes files under `ui/`. If no frontend files changed, report `not applicable` using the review summary format.

When the diff includes files under `ui/`, the shared review context must include `ui/AGENTS.md`, and the Frontend Review agent must validate the PR against every applicable rule, convention, anti-pattern, and testing expectation in that file. Treat `ui/AGENTS.md` as a checklist covering package-manager and formatting rules, design-system tokens, shadcn primitives, page/section/card anatomy, numeric formatting, tables and pagination, filters, charts, dialogs, spacing rhythm, animations, theming, cursor affordances, and the pre-PR checklist. Cite `ui/AGENTS.md` as repo evidence for any violation.

## Review Checklist

- Routes, layouts, components, and API calls preserve expected behavior.
- Auth/session checks happen at the right boundary and do not rely only on hidden UI controls.
- Frontend RBAC or feature gating is consistent with backend authorization and ADRs.
- API calls use the intended contracts, error mapping, caching, and revalidation behavior.
- Loading, empty, error, and permission-denied states are handled.
- User input is validated or safely encoded before reaching APIs or structured formats.
- Changed UI remains usable on desktop and mobile and follows the `ui/AGENTS.md` design system.
- Implementation is simple, cohesive, and consistent with established naming and component patterns.
- Tests cover changed user-visible behavior where the repo has a test pattern for it.
- Public and internal frontend contracts preserve expected compatibility unless the change intentionally updates them.
- Dead code, stale comments, debug UI, local URLs, secrets, and accidental test data are absent.

## Approval Rules

Approve when changed frontend behavior is correct, authorized, maintainable, and covered by appropriate verification or clear repo evidence.

## Request Changes Rules

Request changes for broken user flows, missing auth boundary checks, unsafe data exposure, contract mismatches, unhandled critical states, implementation that does not satisfy the intended user workflow in the change context, missing required tests for changed behavior, or confusing frontend design that materially increases maintenance risk.
