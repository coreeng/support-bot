# ADR: Single OAuth2/OIDC Provider for Authentication

**Date:** 2026-06-03
**Status:** Proposed

---

## Context

Support Agent currently hard-codes its set of identity providers to exactly two: `google` and `azure`.
The literal provider IDs are duplicated across the codebase and `application.yaml` oauth2 configuration.

This model makes it difficult to deploy Support Agent without code change.
Users may want to configure their own corporate IdP — Okta, Keycloak, Ping, an internal OIDC gateway.
Every new provider currently requires a Java change, a YAML change, a UI change, and a Helm change.
There is no value in the backend knowing *which* IdP is in use: every supported provider speaks the same standard (OAuth2 authorization code + OIDC discovery),
and the post-login flow (mint internal JWT, look up teams, derive roles) is identical regardless of issuer.

## Dex as a federating proxy

A single generic OAuth2 configuration is a first step to solve this issue.
Using Dex as this single provider has the added benefit of allowing us to support multiple upstream providers.

In the bundled-Dex deployment mode, **Dex does not authenticate users itself**.
It acts as an OIDC proxy in front of one or more upstream identity providers — typically Azure AD, Google, and GitHub — configured as Dex *connectors*.
Users continue to sign in to their actual corporate IdP;
Dex orchestrates the upstream OAuth2/OIDC exchange and re-issues a single normalised OIDC ID token to Support Agent.

From Support Agent's perspective Dex is indistinguishable from any other OIDC provider: same `authorization_endpoint`, `token_endpoint`, `jwks_uri`, `userinfo_endpoint`, same `openid email profile [groups]` scopes, same signed JWT ID token.
The `iss` claim is always Dex's issuer URI; the JWT is validated against Dex's JWKS, not the upstream IdP's.
Treating Dex as "just another OIDC provider" eliminates the need for any Dex-specific — or Azure-, Google-, GitHub-specific — code path in the application.
All per-upstream variation lives in Dex's connector configuration.


## Dex Flow

Support Agent Api is configured with a single oauth2 provider `sso` pointing at Dex issuer or any other OIDC provider (such as Azure or Google).
Dex is configured with one or more upstream connectors (Azure, Google, GitHub, …)

1. Browser -> Dex /auth
   https://support-bot-dex.gcp-prod-internal.cecg.platform.cecg.io/dex/auth?...client_id=support-agent...

2. Dex shows connector choice
   Azure AD
   Google

3. User chooses Azure and Dex redirects browser -> Azure authorization endpoint
   https://login.microsoftonline.com/<tenant>/oauth2/v2.0/authorize?...

4. User logs in with Azure

5. Azure redirects browser -> Dex callback
   https://support-bot-dex.gcp-prod-internal.cecg.platform.cecg.io/dex/callback?code=...&state=...

6. Dex exchanges Azure code server-side and stores Azure tokens, it generates its own code (or reuses Azure code) to return to Support Agent UI

7. Dex redirects browser -> Support Agent callback with its code
   https://support-bot-app-cecg-ui.gcp-prod-internal.cecg.platform.cecg.io/api/oauth/callback/sso?code=...&state=...

8. Support Agent UI exchanges Dex code for Dex tokens

9. Support Agent API exchanges Dex token for Dex user info and mint Support Agent JWT

10. Next.js stores API JWT in session

## Dex Flow Options

These options change how Dex is exposed to the browser and Support Agent API.

It is desirable to
- simplify Dex deployment at companies that that strict egress policies by eliminating exposing dedx to the Internet
- eliminate additional callbacks that need to be registered at IdP providers such as Azure

### Dex issuer URL options

### Option 1 - external Dex issuer Url with discovery

### Option 2 - external Dex issuer Url overwritten cluster internal discovery, JWKS, token and userinfo endpoints

### Dex callback URL options

### Option 1 - connector IdP uses Dex callback directly

### Option 2 - connector IdP uses Dex Support agent UI callback

## Affected Personas

- **Operators deploying Support Agent**: configure a single IdP through standard Spring properties; choose between bundling Dex (as a Helm subchart) or pointing at an external provider.
- **Users running Support Agent in their own environment**: can integrate with their existing corporate IdP without forking the codebase.
- **Support Agent developers**: lose two provider-specific code paths; gain a single, standards-based auth surface that is easier to test and reason about.
- **End users**: see one consistent SSO action on the login screen instead of a per-provider button.

---

## Decision Drivers

- Standards conformance: any OAuth2/OIDC-compliant provider must work without Java changes.
- Configuration over code: provider details belong in `application.yaml` / Helm values, not in `OAuth2ClientConfig.java`.
- Dex is infrastructure, not an application concern: bundling Dex is a deployment choice, not a backend feature flag.
- Backward-compatible migration: existing `GOOGLE_*` / `AZURE_*` deployments must keep working through a defined transition period.
- Simpler UI: one SSO button, optionally rebranded per deployment, instead of an enumerated list.

---

## Options Considered

### Keep the registry, add a third entry for Dex

Add `if (isNotBlank(dexClientId) …) providers.add("dex")` to `OAuth2AvailabilityChecker` and a `dexClientRegistration(...)` method to `OAuth2ClientConfig`. Rejected: it perpetuates the closed enumeration, requires a Java change for every new IdP, and bakes the false premise that "Dex" is meaningfully different from any other OIDC provider.

### N configurable providers, dynamic list at runtime

Allow the operator to register an arbitrary number of providers under `spring.security.oauth2.client.registration.*` and expose all of them via `GET /auth/providers`. Rejected for this iteration: no current use case requires more than one active login provider, and exposing multiple buttons re-introduces the UI complexity we are trying to remove. The single-provider model is a strict subset of the multi-provider model and can be relaxed later if needed.

### One configurable provider, driven by OIDC discovery

Operators configure a single OAuth2/OIDC provider. Where the provider supports OIDC discovery (Dex, Azure, Google, Okta, Keycloak, …) only the `issuer-uri`, `client-id`, `client-secret`, and `scope` are required; Spring Security resolves the rest from `<issuer>/.well-known/openid-configuration`. Endpoints may also be specified explicitly for non-discoverable providers. **Selected.**

---

## Decision

### 1. Single Active Provider

Support Agent supports exactly one active OAuth2/OIDC login provider at any time. The active provider is configured through standard Spring properties under a fixed registration ID (proposed: `sso`). The same configuration shape works for Dex, Azure, Google, and any other OAuth2/OIDC-compliant IdP.

### 2. Configuration Shape

Provider configuration uses the standard Spring Security OAuth2 client properties, with no Support Agent-specific keys:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          sso:
            client-id: ${OAUTH2_CLIENT_ID}
            client-secret: ${OAUTH2_CLIENT_SECRET}
            scope: openid, email, profile          # add `groups` when team mapping is enabled
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          sso:
            issuer-uri: ${OAUTH2_ISSUER_URI}       # enables OIDC discovery
            # Optional overrides for non-discoverable providers:
            # authorization-uri: …
            # token-uri: …
            # jwk-set-uri: …
            # user-info-uri: …
            user-name-attribute: email             # configurable username claim
```

When `issuer-uri` is set and the provider exposes a valid discovery document, Spring Security populates `authorization-uri`, `token-uri`, `jwk-set-uri`, and `user-info-uri` automatically. All endpoints remain individually overridable for providers that do not support discovery.

### 3. Backend Changes

- `OAuth2ClientConfig.java` is deleted. `ClientRegistration` beans are produced by Spring Boot's autoconfiguration from the properties above.
- `OAuth2AvailabilityChecker` no longer enumerates providers by ID. It asks the `ClientRegistrationRepository` whether any registration exists and exposes a single boolean (`isOAuth2Available()`) plus, for the UI, a single optional `ProviderInfo` (registration ID, display name, optional icon hint) derived from properties — not from a hard-coded switch.
- `OAuthUrlService` and `OAuthExchangeService` continue to look up the registration by ID, but the ID is no longer validated against a `{"google", "azure"}` set. Any unknown ID returns 400 as today.
- `OAuth2SuccessHandler` and `OAuthExchangeService` keep their existing post-login pipeline (allow-list → team lookup → role derivation → JWT mint → `AuthCodeStore`). None of that logic is provider-specific.
- `GET /auth/providers` returns at most one entry. Its shape is widened from `List<String>` to a small object carrying the registration ID and presentation hints, so the UI can render branded copy without re-introducing a backend allow-list.

### 4. Claim Model and Group / Team Mapping

Support Agent reads claims from a single source — the ID token (and, where present, the `/userinfo` response) issued by the configured OIDC provider. In the bundled-Dex mode this is always Dex; the upstream Azure / Google / GitHub claims are not consumed by Support Agent directly.

**Claim normalisation happens in Dex, not in Support Agent.** Every Dex connector is responsible for mapping its upstream user representation into Dex's standard OIDC claim set:

| Claim                | Source per upstream (via Dex connector)                                                                                          |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `sub`                | Dex-generated, stable per `(connector_id, upstream user id)`. Opaque to Support Agent.                                           |
| `iss`                | Dex's issuer URI. Identical regardless of upstream.                                                                              |
| `email`              | Azure: `mail` / `userPrincipalName`. Google: primary account email. GitHub: primary verified email (requires `user:email` scope).|
| `email_verified`     | Set by Dex per connector. GitHub: only true when the upstream email is marked verified.                                          |
| `name`               | Azure: `displayName`. Google: `name`. GitHub: `name` (falls back to `login` when unset).                                         |
| `preferred_username` | Azure: `userPrincipalName`. Google: email local-part. GitHub: `login`.                                                           |
| `groups`             | Azure: AAD group object IDs or display names (per connector config). Google: G-Suite groups (when enabled). GitHub: `org:team` strings for the user's org/team memberships. |
| `federated_claims`   | Dex adds `{connector_id, user_id}` so downstream systems *can* tell which upstream signed the user in, if they need to.          |

Support Agent's behaviour:

- It depends on the **normalised Dex claim set** (`email`, `name`, `groups`), not on per-upstream payload shape. Code paths must not branch on `federated_claims.connector_id` to special-case Azure vs. Google vs. GitHub.
- Team mapping (currently inferred via regex over `TeamType.name()` in `OAuth2SuccessHandler` and `OAuthExchangeService`) is generalised to read from a configurable claim name (default `groups`) and a configurable mapping of claim values → `TeamType` / `Team`. The mapping rules apply to the configured provider regardless of which upstream the user came from. The mapping is opt-in and defaults to off, preserving today's behaviour where group/team information comes from `TeamService`.
- The `extractEmail` / `extractName` fallback chain in `OAuthExchangeService` is retained and made claim-name-driven (configurable username claim, defaulting to `email` with fallback to `preferred_username`).
- When Support Agent is configured to talk directly to Azure or Google (no Dex), the same code reads the same claim names from the upstream-issued token. Operators are responsible for ensuring those claims are emitted (Azure: enable the `email` optional claim and a groups claim; Google: request `openid email profile` and, for groups, use Cloud Identity Groups API or a Workspace-aware IdP). Per-upstream quirks are documented but not coded around.

**Per-upstream normalisation responsibilities** when Dex is bundled:

- **Azure connector**: configure `groups: true` and choose between group object IDs and display names; document the implication for the operator's group-to-team mapping. The `email` claim requires the `User.Read` scope and an Azure app registration that emits `email`.
- **Google connector**: configure `hostedDomains` for Workspace tenancy; request the `groups` scope only if the connector is configured to fetch Workspace groups (otherwise `groups` will be absent and team mapping falls back to allow-list / `TeamService`).
- **GitHub connector**: configure `orgs` to scope group membership to specific organisations; `groups` claim values are `org:team` strings. Operators map these to `TeamType` via the configurable mapping above.

This keeps the application layer ignorant of upstream identity providers. Adding a new upstream (e.g. GitLab, Bitbucket, LDAP) is a Dex configuration change with zero Support Agent code change.

### 5. UI Changes

- `ui/src/auth.config.ts` removes the hard-coded `provider === "google" || provider === "azure"` check from the `backend-code` credentials provider. The `provider` value is passed through opaquely to `POST /auth/oauth/exchange`; backend rejection (400) is the source of truth for "unknown provider".
- The login page renders a single SSO action. The default copy is `Login via SSO` with a generic shield/lock icon. When `GET /auth/providers` returns a known provider hint (e.g. `google`, `azure`, `dex`), the UI may substitute branded copy and icon; the allow-list of known hints lives in the UI and is purely cosmetic — authentication does not depend on it.
- In the bundled-Dex mode the SSO button still leads to the same single OIDC endpoint. Dex itself presents the upstream connector chooser (Azure / Google / GitHub) — or, when Dex is configured with a single connector, skips straight through to it. Support Agent's UI does not enumerate upstream IdPs and does not need to be redeployed when a connector is added or removed in Dex.
- The redirect-flow callback path (`/api/oauth/callback/<provider>`) is collapsed to a single route or generalised to accept the configured registration ID.

### 6. Helm and Bundled Dex

Dex remains an **optional** Helm subchart, not a runtime dependency of Support Agent. Two deployment modes are supported through the same single-provider configuration path:

- **Bundled Dex (`dex.enabled: true`)**: the chart deploys Dex, generates a static OIDC client for Support Agent, and renders the Spring OAuth2 properties to point at the in-cluster Dex issuer (`https://<dex-host>/dex`). Dex is configured with one or more upstream connectors (Azure, Google, GitHub, …) supplied by the operator in `dex.connectors`. Support Agent sees Dex as a single external OIDC provider; it has no awareness that Dex is bundled or which connectors are wired behind it.
- **External provider (`dex.enabled: false`, default)**: operators supply `OAUTH2_ISSUER_URI`, `OAUTH2_CLIENT_ID`, and `OAUTH2_CLIENT_SECRET` (and optionally `OAUTH2_USERNAME_CLAIM`, scope overrides, explicit endpoints). The chart renders these into the same properties. The external provider may itself be a Dex instance run by the operator, a corporate IdP, or Azure/Google directly.

The chart's `values.yaml` exposes:

- a single `auth.oauth2` block with the keys listed in section 2 (used in both modes);
- a `dex` block (bundled mode only) carrying `dex.connectors[]` — a pass-through of the upstream OIDC/OAuth credentials Dex needs (Azure tenant + client, Google client, GitHub OAuth app + orgs, …) and `dex.groupsClaim` controls. The chart never injects these values into Support Agent's environment; they flow only into Dex's `config.yaml`.

Legacy `GOOGLE_*` / `AZURE_*` values, if still set on the Support Agent Deployment, are translated by the chart into the new single-provider block during a deprecation window and produce a warning; they do **not** create multiple simultaneous registrations. Operators migrating from direct Azure/Google integration to bundled Dex move the same upstream credentials from Support Agent's env vars into `dex.connectors`.

### 7. Migration

- The ADR is accepted before code work begins.
- The new `sso` registration is introduced alongside the existing `google` / `azure` registrations behind a feature flag; both paths coexist for one release.
- The Helm chart gains the new `auth.oauth2` block and a translation shim for the legacy env vars.
- The UI is updated to render a single SSO action and stops branching on provider string.
- The legacy `google` / `azure` registrations, hard-coded UI checks, and provider-specific env vars are removed in the following release. The legacy `OAuth2ClientConfig.java`, the provider list in `OAuth2AvailabilityChecker`, and the `provider === "google" || provider === "azure"` check in `auth.config.ts` are all deleted.

## Open Questions

- **Registration ID**: proposed `sso`. An alternative is to let the operator pick the ID (e.g. `dex`, `corp`) so the discovery URL `/oauth2/authorization/<id>` reads naturally. Either way, the backend must not treat the ID as semantically meaningful.
- **Username claim default**: `email` matches today's behaviour but is not always present (e.g. the GitHub Dex connector may omit `email` when the user has no verified public email). The existing `extractEmail` fallback chain in `OAuthExchangeService` should be retained and made claim-name-driven, with a documented fallback order (`email` → `preferred_username` → `federated_claims.user_id` as last resort).
- **Group claim shape**: `groups` is the de facto standard (Dex, Keycloak, Okta) but its values vary by upstream — Azure may emit GUIDs or display names, GitHub emits `org:team`, Google emits group email addresses. The team-mapping configuration must accept arbitrary string values, not assume a particular format.
- **`federated_claims` exposure**: whether to surface `federated_claims.connector_id` on `UserPrincipal` for auditing / debugging. Default: no — exposing it tempts code paths to branch on upstream identity, which this ADR explicitly disallows.

---

## Consequences

### Positive

- Adding a new IdP becomes a configuration change, not a code change.
- The backend no longer carries provider-specific branches; `OAuth2ClientConfig.java` is deleted.
- Dex is treated as deployment infrastructure, decoupling its lifecycle from the application.
- The UI login surface is consistent across deployments; per-deployment branding is presentational only.
- Group/team claim mapping works uniformly across providers.

### Negative / Trade-offs

- Operators currently relying on the legacy `GOOGLE_*` / `AZURE_*` env vars must migrate within one release. A translation shim and clear deprecation warnings mitigate this.
- Only one active login provider is supported. Deployments that today configure both Google and Azure simultaneously must front them with Dex (or another federating IdP) configured with both as connectors.
- Bundled Dex adds an extra in-cluster component to operate (a stateful service with its own signing keys, storage, and upstream credentials). The chart provides sensible defaults but operators must understand that Dex is the trust anchor for Support Agent in this mode.
- Per-upstream claim quirks (GitHub email visibility, Azure groups format, Google Workspace groups availability) move from Java code into operator-owned Dex configuration. This is correct architecturally but shifts cognitive load to whoever runs the cluster.
- Providers without OIDC discovery require operators to specify endpoints explicitly. This is unavoidable for non-OIDC OAuth2 providers.

### Neutral

- The bundled Dex Helm subchart is optional and ships disabled by default; operators with an existing IdP are not forced to deploy Dex.
- Allow-list (`security.allow-list.emails` / `…domains`) and team/role derivation are unchanged and continue to apply to the configured provider.
