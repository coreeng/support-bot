# ADR: Dedicated Support Bot Helm Chart

**Date:** 2026-04-30
**Status:** Proposed

---

## Context

We usually deploy applications using the generic `core-platform-app` chart.
`core-platform-app` is CECG's shared application Helm chart, maintained in
[`coreeng/core-platform-assets`](https://github.com/coreeng/core-platform-assets/tree/main/charts/core-platform-app),
and is the default chart used by CECG repositories for internal application deployments.

Support Bot is a different case. It is not only deployed from this repository into our own environments; it is also published as a **product artifact** for downstream installation. This repository already publishes both:

- a Docker image
- a Helm chart (`oci://ghcr.io/coreeng/charts/support-bot`)

That makes the Helm chart part of the product delivery model rather than only an internal deployment convenience.

Support Bot also already has a multi-component deployment shape. The current chart can deliver:

- the API
- the UI

The generic platform chart is intentionally broad and designed to deploy many kinds of applications. Support Bot's published chart needs to optimize for Support Bot's own install and runtime shape.

---

## Decision Drivers

- Prioritise **client-side usability and portability**.
- Keep the published chart aligned with the Support Bot product boundary.
- Support delivery of multiple related components in one product chart.

---

## Options Considered

### Reuse the generic platform chart

This would align Support Bot with the usual platform default and reduce the number of bespoke deployment artifacts we maintain.

However, it would mean Support Bot no longer ships its own Helm chart artifact and instead relies on a generic internal deployment chart plus Support Bot-specific configuration. That is a poorer fit for Support Bot as a product distributed to downstream consumers.

### Keep a dedicated Support Bot chart

This treats the Helm chart as part of the Support Bot product surface.

It allows the chart to represent Support Bot-specific configuration and to package the product's delivered components together, instead of forcing that shape into a generic chart intended for arbitrary applications.

### Use `core-platform-app` internally and publish the Support Bot chart externally

This would keep this repository's P2P deployments aligned with the usual CECG deployment model by using
`core-platform-app` for internal environments, while still treating the Support Bot Helm chart as a published OSS
artifact for downstream consumers.

In that model, the published chart would need its own validation and release gates, either inside the existing P2P flow
or as a separate delivery unit if API, UI, and Helm are split later.

This is a credible future direction, but it creates two deployment surfaces that must stay semantically aligned.

---

## Decision

Support Bot will keep a **dedicated published Helm chart** rather than replace the public chart with the generic
`core-platform-app` chart.

This is an intentional exception to the normal platform default.

The reason is that Support Bot's chart is a **published product artifact** for downstream installation, not just an internal deployment wrapper. The chart should therefore optimise for Support Bot's consumer install experience, portability, and product-specific component structure.

This ADR records the chart strategy only. It does **not** imply any immediate refactor or convergence work for the current chart.

---

## Consequences

### Positive

- Support Bot has a clear, product-owned installation path.
- The chart can evolve around Support Bot-specific needs without being constrained by a generic app model.
- Bundled delivery of related components such as API and UI remains a first-class concern.

### Negative / Trade-offs

- Support Bot owns the maintenance of its chart.
- This remains an intentional exception to the usual platform-chart reuse pattern.
