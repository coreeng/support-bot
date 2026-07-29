# High-Risk Surface Review

## Purpose

Find risks on high-blast-radius surfaces: database schema, APIs, new modules, dependencies, external integrations, async work, and deployment behavior.

## Change Context

Review the supplied change context: changed files, changed modules, change intent, module guidance, relevant ADRs, and other repository evidence. Use the change intent to check whether high-risk changes are necessary for the requested work and whether the intended scope justifies their blast radius.

## Scope Detection

Run this review for every change. Treat the review as especially important when the change includes:

- Flyway migrations, JPA entities, indexes, constraints, or data backfills
- Public or cross-service APIs, DTOs, wire formats, API clients, or generated contracts
- New modules, services, packages, routes, jobs, or schedulers
- Library, plugin, container, chart, or build dependencies
- External provider integrations (Slack, GitHub, LLM providers), retries, idempotency, queues, scheduled work, startup work, or long-running jobs
- Helm, Kubernetes, secrets, resource limits, autoscaling, or deployment config
- Integration, end-to-end, or functional tests that create persistent data, users, external-provider objects, files, browser state, cloud resources, or other shared-environment resources

## Review Checklist

- Database changes are backward-compatible where required; migrations live in `api/service/src/main/resources/db/migration` and are safe for existing data, repeatable environments, and multi-replica deployments.
- API changes have explicit contracts, auth behavior, validation, pagination/size limits, and error semantics.
- New modules or services have ownership, tests, configuration, deployment, monitoring, and security boundaries.
- New dependencies are necessary, maintained, licensed appropriately, and do not duplicate existing capabilities.
- Async, scheduled, startup, and external-provider work follows idempotency, durable claiming, or coordination patterns required by ADRs.
- Deployment changes do not introduce single-replica assumptions, secret exposure, or resource regressions.
- High-risk behavior has suitable tests or documented verification evidence.

## Integration Test Resource Cleanup

When reviewing integration, end-to-end, or functional tests, explicitly check whether the test creates persistent resources and how those resources are cleaned up.

- Test data cleanup is registered before or at the same time as the resource is created, not only after post-create assertions succeed.
- Cleanup runs on failure paths, timeouts, skipped assertions, and partial setup wherever the test framework supports hooks or finally blocks.
- Cleanup covers all persistent resources the test can create, including application records, users, external-provider objects, files, browser storage, and cloud resources.
- Cleanup is isolated to the resources owned by the current test run and cannot delete shared fixtures or another run's resources.
- Failures in best-effort cleanup are visible enough to diagnose leaked resources.
- Tests that intentionally leave resources behind document why that is safe for repeatable CI and shared environments.

## Approval Rules

Approve when changed high-risk surfaces are intentional, bounded, tested or verifiable, consistent with ADRs, and justified by the change context.

## Request Changes Rules

Request changes for unsafe migrations, under-specified API changes, unjustified dependencies, unowned modules, uncoordinated async/scheduled work, deployment regressions, high-risk changes outside the intended scope in the change context, high-risk changes without adequate verification, or integration tests that can leak persistent resources in shared environments when setup or post-create assertions fail.
