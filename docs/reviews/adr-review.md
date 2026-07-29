# ADR Review

## Purpose

Identify which accepted ADRs are relevant to a change and check whether the implementation conforms to those decisions.

## Change Context

Review the supplied change context: changed files, changed modules, change intent, module guidance, relevant repository documents, and the selected ADRs. Use the change intent to understand the intended architectural scope and whether the work introduces a new decision that should be documented.

## Scope Detection

Run this review for every change. Treat it as higher risk when the change includes:

- Architecture, cross-service contracts, authentication, RBAC, APIs, or infrastructure
- Database migrations, testing strategy, deployment strategy, external integrations, or model/provider choices
- Behavior that appears to update, replace, or bypass a documented decision

## Review Checklist

- Identify the accepted ADRs under `docs/adr/` relevant to the changed files and behavior.
- Include the selected ADRs in the review output, even when no conformance findings are raised.
- For each selected ADR, state why it is relevant to the change.
- Check whether the implementation follows each selected ADR's decision and consequences.
- Check whether any ADR-described limitation has changed and needs documentation updates.
- Check whether a new architectural decision is being introduced without an ADR.
- Use repo evidence, not assumptions; cite ADR numbers and paths in findings.

## Approval Rules

Approve when selected ADRs have been included in the review output and no blocking conformance issue remains.

## Request Changes Rules

Request changes when the change contradicts an accepted ADR, bypasses an ADR-mandated path, introduces a material architectural decision without documentation, leaves directly relevant ADR text misleading, or omits selected ADRs from the ADR review output.
