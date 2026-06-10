# ADR: Single OAuth2/OIDC Provider for Authentication

**Date:** 2026-06-05
**Status:** Proposed

---

## Context

Support Agent currently hard-codes its set of identity providers to exactly two: `google` and `azure`.
The literal provider IDs are duplicated across the codebase and `application.yaml` oauth2 configuration.

This model makes it difficult to deploy Support Agent without code change.
Users may want to configure their own corporate IdP — Okta, Keycloak, Ping, an internal OIDC gateway.
Every new provider currently requires a Java change, a YAML change, a UI change, and a Helm change.
There is no value in the backend knowing *which* IdP is in use: every supported provider speaks the same standard (OAuth2 authorization code + OIDC discovery),
and the post-login flow (issue internal JWT, look up teams, derive roles) is identical regardless of issuer.

## Dex as a federating proxy

A single generic OAuth2 configuration is a first step to solve this issue.
Using an OIDC Proxy like Dex as this single provider is the second step that allows us to support multiple upstream providers.

In the bundled-Dex deployment mode, **Dex does not authenticate users itself**.
It acts as an OIDC proxy in front of one or more upstream identity providers — typically Azure AD or Google or any other OIDC provider  — configured as Dex *connectors*.
Users continue to sign in to their actual corporate IdP;
Dex orchestrates the upstream OAuth2/OIDC exchange and re-issues a single normalised OIDC ID token to Support Agent.

From Support Agent's perspective Dex is indistinguishable from any other OIDC provider: same `authorization_endpoint`, `token_endpoint`, `jwks_uri`, `userinfo_endpoint`, same `openid email profile [groups]` scopes, same signed JWT ID token.
The `iss` claim is always Dex's issuer URI; the JWT is validated against Dex's JWKS, not the upstream IdP's.
Treating Dex as "just another OIDC provider" eliminates the need for any Dex-specific — or Azure-, Google-specific — code path in the application.
All per-upstream variation lives in Dex's connector configuration.

## Single OIDC Provider Flow

Support Agent Api is configured with a single OAuth2 provider `sso` pointing at Azure, Google or any other OIDC Provider.
This flow is unchanged from today's flow, except that we have a single generic provider `sso` instead of `google` and `azure`.

## Dex Flow

Support Agent Api is configured with a single OAuth2 provider `sso` pointing at Dex issuer.
Dex is configured with one or more upstream connectors (Azure, Google, …)

1. Support Agent UI in a Browser -> Dex /auth
   https://support-bot-dex.gcp-prod-internal.cecg.platform.cecg.io/dex/auth?...client_id=support-agent...

2. Dex shows connector choice
   - Azure AD
   - Google
   - ...

3. User chooses Azure and Dex redirects browser -> Azure authorization endpoint
   https://login.microsoftonline.com/<tenant>/oauth2/v2.0/authorize?...

4. User logs in with Azure

5. Azure redirects browser -> Dex callback
   https://support-bot-dex.gcp-prod-internal.cecg.platform.cecg.io/dex/callback?code=...&state=...

6. Dex exchanges Azure code server-side and stores Azure tokens internally

7. Dex generates its own auth code and redirects browser -> Support Agent UI callback with its code
   https://support-bot-app-cecg-ui.gcp-prod-internal.cecg.platform.cecg.io/api/oauth/callback/sso?code=...&state=...

8. Support Agent UI delegates to Next.js Auth component to handle authentication

9. Next.js invokes a plugin function provided by us to make an API call to Support Agent API

10. Support Agent API exchanges Dex code for Dex OIDC token, calls Dex user info endpoint, issues Support Agent JWT with user teams and roles and returns it to Next.js

11. Next.js encrypts and stores API JWT in session cookie


### Dex issuer URL options

These options are meant to explore and simplify Dex deployment at companies that have strict egress policies by eliminating exposing Dex to the Internet

They change how Dex is exposed to Support Agent API.
These options do not change how Dex authorization_endpoint and callback_endpoint are exposed to the browser.

### Option 1 - Autodiscovery via external ingress

- Issuer URL: https://support-bot-dex.tools.pit.prod.gspcloud.com/dex
- Discovery goes over the Internet
- JWKS, Token and userinfo also go over the Internet

#### Pros

- Consistent with commercial IdPs like Azure and Google
- Very simple configuration - everything is auto-discovered
- Issuer is accessible via browser for debugging by a developer or client
- Browser authorization_endpoint and callback_endpoint are the same as issuer URL

#### Cons

- External connectivity needs approval from security teams at every client that enforces egress controls
- Deploying Support Bot to another cluster requires Dex issuer URL change (unless a stable identity domain name is configured - this is typically outside of ingress controller capabilities)

### Option 2 - Autodiscovery via internal ingress

- Issuer URL: https://support-bot-dex-internal.tools.pit.prod.gspcloud.com/dex
- Discovery goes over private corporate network
- JWKS, Token and userinfo also go over private corporate network

#### Pros

- Same as in Option 1
- no egress approval required

#### Cons

- Deploying Support Bot to another cluster requires Dex issuer URL change (with the same caveat about a stable identity domain)

### Option 3 - Autodiscovery via namespace local service or pod

- Issuer URL: http://dex:5556/dex
- Discovery goes over cluster local network
- JWKS, Token and userinfo also go over cluster local network

#### Pros

- Same as in Option 1
- no egress approval required
- Support Bot can be deployed to any cluster without Dex issuer URL change

#### Cons

- we must override Dex authorization_endpoint and callback_endpoint to use ingress URLs


### Dex callback URL options

These options change what callback URL IdPs use to send auth code to Dex. Callback URL must be registered at the IdP.

It is desirable to simplify migration to Dex from a single IdP by eliminating an additional callback that needs to be registered at the IdP providers.

### Option 1 - IdP uses Dex callback directly

#### Prod

- Simple to understand and implement
- Consistent with the spec
- this is an expected behaviour

#### Cons

- When migrating from a single IdP to Dex, Dex callback URL must be whitelisted at the IdP

### Option 2 - IdP re-uses Support agent UI callback to proxy callback to Dex

#### Prod

- No additional callback URL needs to be registered at the IdP when migrating from a single IdP to Dex

#### Cons

- More complex to implement
- this is un-expected
- Only makes sense when migrating from a single IdP to Dex


## Decision Drivers

- Standards conformance: any OAuth2/OIDC-compliant provider must work without Java changes.
- Configuration over code: provider details belong in `application.yaml` / Helm values, not in `OAuth2ClientConfig.java`.
- Dex is infrastructure, not an application concern: bundling Dex is a deployment choice, not a backend feature flag.
- Backward-compatible migration: existing `GOOGLE_*` / `AZURE_*` deployments must keep working through a defined transition period.
- Simpler UI: one SSO button, optionally rebranded per deployment, instead of an enumerated list.

## Decision

### 1. Single Active Provider

Support Agent configures exactly one active OAuth2/OIDC login provider at any time.
The active provider is configured through standard Spring properties under a fixed registration ID (proposed: `sso`).
The same configuration shape works for Dex, Azure, Google, and any other OAuth2/OIDC-compliant IdP.

### 2. Use service-based issuer URL for Dex

### 3. Use Dex callback directly

### 4. Configuration Shape

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

When `issuer-uri` is set and the provider exposes a valid discovery document, Spring Security populates `authorization-uri`, `token-uri`, `jwk-set-uri`, and `user-info-uri` automatically.
All endpoints remain individually overridable for providers that do not support discovery.

### 3. Backend Changes

- `OAuth2ClientConfig.java` is deleted. `ClientRegistration` beans are produced by Spring Boot's autoconfiguration from the properties above.
- `OAuth2AvailabilityChecker` no longer enumerates providers by ID. It asks the `ClientRegistrationRepository` whether any registration exists and exposes a single boolean (`isOAuth2Available()`) plus, for the UI, a single optional `ProviderInfo` (registration ID, display name, optional icon hint) derived from properties — not from a hard-coded switch.
- `OAuthUrlService` and `OAuthExchangeService` continue to look up the registration by ID, but the ID is no longer validated against a `{"google", "azure"}` set. Any unknown ID returns 400 as today.
- `OAuth2SuccessHandler` and `OAuthExchangeService` keep their existing post-login pipeline (allow-list → team lookup → role derivation → JWT mint → `AuthCodeStore`). None of that logic is provider-specific.
- `GET /auth/providers` returns at most one entry. Its shape is widened from `List<String>` to a small object carrying the registration ID and presentation hints, so the UI can render branded copy without re-introducing a backend allow-list.

### 4. Claim Model and Group / Team Mapping

Support Agent reads claims from a single source — the ID token (and, where present, the `/userinfo` response) issued by the configured OIDC provider.
In the bundled-Dex mode this is always Dex; the upstream Azure / Google / GitHub claims are not consumed by Support Agent directly.

**Claim normalisation happens in Dex, not in Support Agent.** Every Dex connector is responsible for mapping its upstream user representation into Dex's standard OIDC claim set:

| Claim                | Source per upstream (via Dex connector)                                                                                                                                     |
|----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sub`                | Dex-generated, stable per `(connector_id, upstream user id)`. Opaque to Support Agent.                                                                                      |
| `iss`                | Dex's issuer URI. Identical regardless of upstream.                                                                                                                         |
| `email`              | Azure: `mail` / `userPrincipalName`. Google: primary account email. GitHub: primary verified email (requires `user:email` scope).                                           |
| `name`               | Azure: `displayName`. Google: `name`. GitHub: `name` (falls back to `login` when unset).                                                                                    |
| `preferred_username` | Azure: `userPrincipalName`. Google: email local-part. GitHub: `login`.                                                                                                      |
| `groups`             | Azure: AAD group object IDs or display names (per connector config). Google: G-Suite groups (when enabled). GitHub: `org:team` strings for the user's org/team memberships. |

Support Agent's behaviour:

- It depends on the **normalised Dex claim set** (`email`, `name`, `groups`), not on per-upstream payload shape. Code paths must not branch on `federated_claims.connector_id` to special-case Azure vs. Google vs. GitHub.
- Team mapping (currently inferred via regex over `TeamType.name()` in `OAuth2SuccessHandler` and `OAuthExchangeService`) is generalised to read from a configurable claim name (default `groups`) and a configurable mapping of claim values → `TeamType` / `Team`. The mapping rules apply to the configured provider regardless of which upstream the user came from. The mapping is opt-in and defaults to off, preserving today's behaviour where group/team information comes from `TeamService`.
- The `extractEmail` / `extractName` fallback chain in `OAuthExchangeService` is retained and made claim-name-driven (configurable username claim, defaulting to `email` with fallback to `preferred_username`).
- When Support Agent is configured to talk directly to Azure or Google (no Dex), the same code reads the same claim names from the upstream-issued token. Operators are responsible for ensuring those claims are emitted (Azure: enable the `email` optional claim and a groups claim; Google: request `openid email profile` and, for groups, use Cloud Identity Groups API or a Workspace-aware IdP). Per-upstream quirks are documented but not coded around.

**Per-upstream normalisation responsibilities** when Dex is bundled:

- **Azure connector**: configure `groups: true` and choose between group object IDs and display names; document the implication for the operator's group-to-team mapping. The `email` claim requires the `User.Read` scope and an Azure app registration that emits `email`.
- **Google connector**: configure `hostedDomains` for Workspace tenancy; request the `groups` scope only if the connector is configured to fetch Workspace groups (otherwise `groups` will be absent and team mapping falls back to allow-list / `TeamService`).

### 5. UI Changes

- `ui/src/auth.config.ts` removes the hard-coded `provider === "google" || provider === "azure"` check from the `backend-code` credentials provider. The `provider` value is passed through opaquely to `POST /auth/oauth/exchange`; backend rejection (400) is the source of truth for "unknown provider".
- The login page renders a single SSO action. The default copy is `Login via SSO` with a generic shield/lock icon. When `GET /auth/providers` returns a known provider hint (e.g. `google`, `azure`, `dex`), the UI may substitute branded copy and icon; the allow-list of known hints lives in the UI and is purely cosmetic — authentication does not depend on it.
- In the bundled-Dex mode the SSO button still leads to the same single OIDC endpoint. Dex itself presents the upstream connector chooser (Azure / Google / GitHub) — or, when Dex is configured with a single connector, skips straight through to it. Support Agent's UI does not enumerate upstream IdPs and does not need to be redeployed when a connector is added or removed in Dex.
- The redirect-flow callback path (`/api/oauth/callback/<provider>`) is collapsed to a single route or generalised to accept the configured registration ID.

## Consequences

### Positive

- Adding a new IdP becomes a configuration change, not a code change.
- The backend no longer carries provider-specific branches; `OAuth2ClientConfig.java` is deleted.
- Dex is treated as deployment infrastructure, decoupling its lifecycle from the application.
- The UI login is consistent across deployments; per-deployment branding is presentational only.
- Group/team claim mapping works uniformly across providers.

### Negative / Trade-offs

- This is a breaking change. Deployments that today configure both Google and Azure simultaneously must front them with Dex (or another federating IdP) configured with both as connectors.
- Bundled Dex adds an extra in-cluster component to operate (a stateful service with its own signing keys, storage, and upstream credentials).

### Neutral

- The bundled Dex Helm subchart is optional and ships disabled by default; operators with an existing IdP are not forced to deploy Dex.
- Allow-list (`security.allow-list.emails` / `…domains`) and team/role derivation are unchanged and continue to apply to the configured provider.
