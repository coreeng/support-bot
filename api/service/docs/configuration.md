# Overview

Support Bot service is a Spring Boot application, which means that it has certain
[conventions](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files)
regarding its configuration.

# General approach
Support Bot docker image will contain a jar file under `/application` folder.
Since it's a Spring Boot application, you can mount your `application.yaml` with configurations to `/application/application.yaml`
and the app should automatically find your configuration.

Support Bot already has the default configuration provided.
You might want to adjust it for your own needs.
In the next section, you'll find default values and configuration options specific to Support Bot.

> Note: Spring Boot provides a lot of configuration options. Address Spring documentation for more information.

# Default Configuration
```yaml
management:
  server:
    port: 8081
  endpoints:
    web:
      base-path: /
      exposure:
        include: health, prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http:
          server:
            requests: true

spring:
  main:
    banner-mode: off
  application:
    name: support-bot
  cloud:
# Uncomment in case you require Azure integration
#     azure:
#       profile:
#         tenant-id: ${AZURE_TENANT_ID}
#       credential:
#         client-id: ${AZURE_CLIENT_ID}
#         client-secret: ${AZURE_CLIENT_SECRET}
    gcp:
      core:
        enabled: false # enable only in case GCP integration is enabled
      credentials:
        scopes: [ "https://www.googleapis.com/auth/cloud-identity.groups.readonly" ]
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/postgres}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      data-source-properties:
        reWriteBatchedInserts: true

slack:
  creds: # Credentials of Slack App
    token: ${SLACK_TOKEN} # Token like: xoxb-abc-def
    socket-token: ${SLACK_SOCKET_TOKEN} # Token like: xapp-1-abc-def-ghi
    signing-secret: ${SLACK_SIGNING_SECRET} # Token like: 1234567890abcdef
  ticket:
    # Recommended: monitor one or more channels via `channels`, each with its own `track` mode:
    #   QUERIES - only normal support queries (PR detection disabled)
    #   PRS     - only PR-link tickets (the normal query/reaction flow is suppressed)
    #   BOTH    - both (default when `track` is omitted)
    # When `channels` is non-empty it takes precedence over the deprecated `channel-id` below.
    # channels:
    #   - name: product-support
    #     id: C1234567890
    #     track: BOTH
    #   - name: product-support-pr
    #     id: C2345678901
    #     track: PRS
    #   - name: product-support-queries
    #     id: C3456789012
    #     track: QUERIES
    # Deprecated: legacy single-channel config, kept for backward compatibility. Equivalent to one
    # `channels` entry tracking BOTH; prefer `channels` above for new deployments.
    channel-id: ${SLACK_TICKET_CHANNEL_ID:} # Channel ID (C1234567890) where tenants post queries
    expected-initial-reaction: eyes # Reaction to trigger ticket creation -- emoji name needs to already exist in slack
    response-initial-reaction: ticket # Reaction posted when ticket is created -- emoji name needs to already exist in slack
    resolved-reaction: white_check_mark # Reaction posted when ticket is resolved -- emoji name needs to already exist in slack
    escalation-reaction: warning # Reaction posted when ticket is escalated -- emoji name needs to already exist in slack

ticket:
  staleness-check-job: # Job that check for stale tickets – open tickets that didn't have any interactions over some period
    enabled: true
    find-stale-cron: 0 0 9 * * 1-5 # Schedule for identifying stale tickets
    time-to-stale: 3d
    remind-about-stale-cron: 0 10 9 * * 1-5 # Schedule for reminding about stale tickets in case no action is performed
    stale-reminder-interval: 1d
  assignment: # Auto-assign (store to the DB) tickets to the first user who reacts with the configured emoji
    enabled: true
    encryption: # Encrypt assignee Slack user IDs before storing
      enabled: true
      key: ${TICKET_ASSIGNMENT_ENCRYPTION_KEY:} # Encryption key (AES-256-GCM). Required when encryption.enabled=true; assignment skipped if missing.

enums:
  # Codes are immutable primary keys: unique and non-blank within each list (and across the static
  # platform teams). The app validates this at startup and fails fast on a duplicate/blank code.
  escalation-teams: # Teams available for query escalation
    - label: wow # Label showed on the UI
      code: wow # Team ID. Must be unique. Have to match a platform team code unless platform-integration.fetch.ignore-unknown-teams is set to true
      slack-group-id: S08948NBMED # Slack group ID that will be tagged on escalations
  tags: # Ticket tags
    - label: Ingresses # Label showed on the UI
      code: ingresses # Tag ID
    - label: Networking
      code: networking
    - label: Persistence/Brokers
      code: persistence-brokers
    - label: Observability
      code: observability
    - label: DNS
      code: dns
  impacts: # Ticket impacts
    - label: Production Blocking # Label showed on the UI
      code: productionBlocking # Impact ID
    - label: BAU Blocking
      code: bauBlocking
    - label: Abnormal Behaviour
      code: abnormalBehaviour

platform-integration: # Whether to enable platform integration to automatically scrape for teams and members
  enabled: true
  fetch:
    max-concurrency: 64 # Maximum number of concurrent requests when fetching team data
    timeout: 30s # Timeout for fetching all team data
    ignore-unknown-teams: false # Whether to allow escalation teams that don't exist in platform teams.
                                 # If false, startup will fail if any escalation team is not found in platform teams.
                                 # If true, escalation-only teams are allowed (they will have only 'escalation' type).
  jwt-groups: # Optional: map Dex ID-token group claims (LDAP) into platform tenant teams
    enabled: false # When true, merges mapped teams for OAuth provider "dex" only; Google/Azure still use static-user / Azure / GCP below
    claim-name: groups # OIDC claim to read (Dex LDAP connector should populate this)
    mappings: # Each LDAP group value Dex puts in `groups` matches at most one mapping (first match wins per value). Use separate LDAP groups if you need several jwt-mapped teams.
      - claim-values: [developers] # Example: LDAP group cn when Dex groupSearch nameAttr is cn (or full DN if Dex emits that)
        team-code: wow # Platform/escalation team code from teams-scraping / enums (adds TENANT + ESCALATION → ESCALATION app role)
      - claim-values: [support-admins]
        team-code: support # Must match team.support.code (→ SUPPORT_ENGINEER role)
      - claim-values: [ldap-leadership]
        team-code: support-leadership # Must match team.leadership.code (→ LEADERSHIP role)
  gcp:
    app-name: Support Bot # Used by GCP client
    enabled: true
  azure:
    enabled: false
  teams-scraping: # team-name <-> cloud group id scrapper configuration
    static: # Explicitly-listed teams (no cloud scraping); each team's members come from its group-ref
      enabled: true
      teams:
        - name: My Team # display value shown in the UI
          code: my-team # optional immutable identity used for mapping (ticket/escalation refs + escalation<->platform join); defaults to name
          group-ref: my-group # group whose members belong to the team
    core-platform: # Scraper specific to CECG's Core Platform
      enabled: true
    k8s-generic: # A generic scraper that might be used in any K8S environment
      enabled: false
      config:
        api-version: v1
        api-group: ""
        kind: Namespace # Search for namespaces with the following filter
        namespace: null # Namespace filter, null for global resources
        filter:
          name-regexp: null # Regexp filter for namespace names
          label-selector: "root.tree.hnc.x-k8s.io/depth" # Label selector filter for namespace labels. Look [here](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/) for syntax
        teamName: # Will use the namespace name from resource.metadata.name as a team name
          cel-expression: resource.metadata.name
        groupRef: # Will use the `group-id` annotation as a group reference, e.g. Azure group id, GCP Group email. Supposed to be changed to a real configuration.
          cel-expression: resource.metadata.annotations.group-id

team:
  support:
    name: Core Support # Label showed on the UI
    slack-group-id: S08948NBMED # Slack group ID of the support team
  leadership:
    name: Support Leadership # Label showed on the UI
    code: support-leadership # Slack group ID of the support leadership team

homepage: # Bot homepage configuration
  useful-links: # Adds a "Useful Links" section to the Slack Home tab
    - title: Weekly Trends # Link title
      url: https://grafana.example.com/d/support-weekly-trends # Link URL
      description: Ticket volume and response performance over the last week # Optional description
    - title: Ticket Insights
      url: https://grafana.example.com/d/support-ticket-insights
      description: Lifecycle metrics and SLA adherence for active tickets
    - title: Escalation Overview
      url: https://grafana.example.com/d/support-escalations
      description: Escalation rate and resolution timing per team
    - title: Tag Insights
      url: https://grafana.example.com/d/support-tag-insights
      description: Request distribution and trends by tag

ai: # AI powered features
  sentiment-analysis: # Analyze tenant and support sentiment per ticket
    enabled: false

rbac: # Restrict ticket creation/editing for tenants
  enabled: true

mock-data: # Generate mock data in case DB is empty. Purely for testing/demo purposes
  enabled: false

metrics: # Prometheus metrics populated from database
  enabled: true # Set to false to disable
  refresh-interval: 60s # How often to refresh ticket metrics e.g. 60s

# PR review tracking — detects PR/MR links in support threads and manages their lifecycle
# (SLA tracking, escalation, auto-close). Full operator reference (token permissions, per-repo
# settings, GitLab, message customisation) is in the "PR review tracking" section under Integrations below.
pr-review-tracking:
  enabled: false # Feature flag — off by default
  poll-cron: 0 0 9-18 * * 1-5 # Cron for the lifecycle poller (default: business hours Mon–Fri UTC)
  pr-emoji: pr # Slack reaction added when a PR/MR is detected — must already exist in your workspace
  tags: # Required when enabled: tag code(s) from enums.tags applied when the bot auto-closes the ticket
    - <tag-code>
  impact: <impact-code> # Required when enabled: impact code from enums.impacts applied on auto-close
  repositories: # Repositories to watch. At least one entry is required when enabled.
    - name: my-org/my-repo # org/repo (GitHub) or group/.../project (GitLab)
      # provider: github # github (default) | gitlab
      owning-team: <team-code> # Team code from enums.escalation-teams — chased when the SLA is breached
      sla:
        default: 48h # SLA duration (e.g. 48h, 7d). See the reference below for file-based and per-path SLAs.
  github: # Required only if any repo uses provider: github
    api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
    auth-mode: ${GITHUB_AUTH_MODE:token} # token | app
    token: ${GITHUB_TOKEN:} # Personal Access Token — used only when auth-mode=token
    app-id: ${GITHUB_APP_ID:} # GitHub App ID — used only when auth-mode=app
    installation-id: ${GITHUB_APP_INSTALLATION_ID:} # GitHub App installation ID — used only when auth-mode=app
    private-key-pem: ${GITHUB_APP_PRIVATE_KEY_PEM:} # Private key input — used only when auth-mode=app
                                                     # Accepts raw PEM content or base64-encoded PEM
  gitlab: # Required only if any repo uses provider: gitlab
    api-base-url: ${GITLAB_API_BASE_URL:https://gitlab.com/api/v4} # must include /api/v4, no trailing slash
    token: ${GITLAB_TOKEN:}
```

For deployment versatility across different secret delivery mechanisms, you can base64-encode the PEM file into a single line before storing it:

```bash
base64 < <pemfile> | tr -d '\n'
```

# Integrations
## Slack
Slack integration is essential for the Support Bot.
You have to create a Slack App for the Support Bot with the following manifest:
```yaml
# Feel free to adjust display_information
display_information:
  name: Core Support Bot
  description: Core Support Bot
  background_color: "#0040ff"
features:
  app_home:
    home_tab_enabled: true
    messages_tab_enabled: false
    messages_tab_read_only_enabled: true
  bot_user:
    display_name: Core Support Bot
    always_online: false
oauth_config:
  scopes:
    bot:
      - app_mentions:read
      - channels:history
      - chat:write
      - groups:history
      - reactions:read
      - reactions:write
      # User scopes are used for mapping users to teams
      - usergroups:read
      - users:read
      - users:read.email
settings:
  event_subscriptions:
    bot_events:
      - app_home_opened
      - app_mention
      - message.channels
      - message.groups
      - reaction_added
      - subteam_members_changed
  interactivity:
    is_enabled: true
  org_deploy_enabled: false
  socket_mode_enabled: true
  token_rotation_enabled: false
```

Use **`SLACK_TOKEN` = Bot User OAuth Token** (`xoxb-...`) from **OAuth & Permissions** after the app is installed to the workspace. An Incoming Webhooks–only setup (or a token whose scopes are just `incoming-webhook`) is not sufficient: on startup the service loads support team membership via Slack user groups and needs scopes such as **`usergroups:read`** (see manifest above). If you see `missing_scope` / `Needed: usergroups:read`, add the missing **Bot Token Scopes** in the Slack app, click **Reinstall to Workspace**, then update `SLACK_TOKEN` with the new `xoxb-` token.

## Kubernetes Cluster
Kubernetes cluster integration is used for scraping team to cloud group relations.
It requires ServiceAccount configuration depending on your scraping mode.
If you use Core Platform integration, these are the expected roles:
- Namespaces: read
- RoleBindings: read in all tenant namespaces

If you configure a generic kubernetes scraper, the required roles will depend on the configuration you provide.

## Google Cloud
You will need GCP integration in case you manage your organization members using Google Groups.
You will need
to create a GCP Service Account with [Groups Reader](https://support.google.com/a/answer/2405986?hl=en) role.

In GCP Service Accounts identified by emails, and they usually have a domain distinct from your organization domain.
In case you face problems assigning the [Groups Reader](https://support.google.com/a/answer/2405986?hl=en) role an email
outside your organization, you should try to create a Google Group under your domain,
assign the role to the newly created group and make the Service Account a member of the group.

> Note: Support Bot to GCP is purely server-to-server communication, meaning you don't need to configure domain-wide delegation.

## Azure Cloud
You will need Azure Cloud integration in case you manage your organization members using Microsoft Entra ID.
You will need to register an application with the following parameters:
1. Supported account types – `Accounts in this organizational directory only`
2. API Permissions:
2.1 `GroupMember.Read.All` with `Application` type
2.2 `User.ReadBasic.All` with `Application` type

You will also need
to create a secret for the registered application so that it can be used for authentication by Support Bot.

## Single Sign-On (SSO)

SSO is supported via Google, Azure AD, and Dex (OIDC) using the OAuth2 Authorisation Code flow that is faciliated by the API.
One or more providers can be enabled depending on what your organisation uses.
At least one provider must be configured. A provider is enabled when both its client ID and client secret are set.

**Default (product):** `security.oauth2.login-providers` is omitted or empty in `application.yaml`. The API then registers **every** fully configured IdP—Google, Azure AD, and/or Dex—and the login UI offers all of them. Use a non-empty `login-providers` list only when you intentionally want to hide some IdPs (see below).

### Environment variables

Set these on the **API**:

| Variable | Description                                                                                                                            |
|----------|----------------------------------------------------------------------------------------------------------------------------------------|
| `JWT_SECRET` | Signing key for JWTs. Must be at least 256 bits. **Required**  the app will fail to start if not set.                                  |
| `UI_ORIGIN` | Public UI base URL (scheme + host + port, no path), e.g. `https://support-bot-ui.example.com`. Drives `security.oauth2.redirect-uri` (default `${UI_ORIGIN:http://localhost:3000}/login`). **Must match the origin of `NEXTAUTH_URL` on the Next.js app** (same scheme, host, and port) or proxied OAuth returns HTTP 400. Outside local-like Spring profiles, the API **fails at startup** if `UI_ORIGIN` is unset or blank. See [UI origin contract](#ui-origin-contract) below. |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID. Leave empty to disable Google SSO.                                                                            |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret.                                                                                                           |
| `AZURE_CLIENT_ID` | Azure AD application (client) ID. Leave empty to disable Azure AD SSO.                                                                 |
| `AZURE_CLIENT_SECRET` | Azure AD client secret.                                                                                                                |
| `AZURE_TENANT_ID` | Azure AD directory (tenant) ID.                                                                                                        |
| `DEX_CLIENT_ID` | Dex OAuth2 client ID. Leave empty to disable Dex SSO.                                                                                     |
| `DEX_CLIENT_SECRET` | Dex OAuth2 client secret.                                                                                                              |
| `DEX_ISSUER_URI` | Dex OIDC issuer URI, for example `https://dex.example.com/dex`.                                                                         |
| `DEX_INTERNAL_BASE_URL` | Optional in-cluster base URL for server-to-server calls (`/token`, `/keys`, `/userinfo`). The `client_secret` is sent here, so use a trusted address — preferably the full svc FQDN, e.g. `http://dex.<namespace>.svc.cluster.local:5556`. If unset, `DEX_ISSUER_URI` is used for both browser and server-to-server. |
| `DEX_SCOPES` | Optional comma-separated scopes used for Dex login. Defaults to `openid,email,profile,groups`.                                            |

### Login provider allowlist (`security.oauth2.login-providers`)

Optional YAML list under `security.oauth2.login-providers` (values: `google`, `azure`, `dex`, case-insensitive).

- **Omitted or empty (default):** every provider with complete credentials is registered and shown on the login UI (multi-IdP).
- **Non-empty (e.g. `[dex]`):** only those OAuth2 registration ids are registered and advertised, even if other IdP env vars are set. Use this to offer **Dex-only** (or any subset) while keeping `GOOGLE_*` / `AZURE_*` in the environment for other purposes (for example Azure Cloud integration or shared secrets).

If the allowlist is non-empty but **no** registration is created (typo in the list, or a listed provider lacks full credentials), the API logs a **WARN** at startup and SSO via `/auth/oauth-url` is disabled until configuration is fixed.

Team membership still comes from `platform-integration` (static-user, Azure Graph, GCP, Slack, `jwt-groups` for Dex) — this setting only affects which OAuth flows the API exposes.

> Note: The `AZURE_*` variables above are shared between SSO (user login) and
> [Azure Cloud integration](#azure-cloud) (reading group memberships from Entra ID).
> A single Azure AD app registration is used for both.

Set this on the **UI**:

| Variable | Description |
|----------|-------------|
| `BACKEND_URL` | Internal URL of the API, e.g. `http://support-bot:8080`. Defaults to `http://localhost:8080`. Used by the server-side proxy. |
| `NEXTAUTH_URL` | Public URL of this Next.js app. Its **origin** must match **`UI_ORIGIN` on the API** so OAuth `redirect_uri` validation succeeds. |

### UI origin contract

The UI builds OAuth callbacks as `{origin of NEXTAUTH_URL}/api/oauth/callback/{provider}`. The API accepts that `redirect_uri` only if its **scheme + host + port** equals the origin parsed from `security.oauth2.redirect-uri` (by default `${UI_ORIGIN:http://localhost:3000}/login`, so the origin is `UI_ORIGIN`).

Use **one canonical public UI URL** everywhere:

1. Set **`NEXTAUTH_URL`** on the UI (e.g. `https://support.example.com` or `http://localhost:3000`).
2. Set **`UI_ORIGIN`** on the API to the **same origin** (no path), e.g. `https://support.example.com` or `http://localhost:3000`.

Avoid mixing `localhost` vs `127.0.0.1`, `http` vs `https`, or different ports between the two — mismatches produce **HTTP 400** on `/auth/oauth-url` and `/auth/oauth/exchange` for **all** UI-driven IdPs (Google, Azure, Dex).

For local development you can omit **`UI_ORIGIN`** when **every** activated Spring profile is either the implicit **`default`** profile or one of **`local`**, **`test`**, **`functionaltests`**, **`integrationtests`**, **`integrationtests-oidc`** (see `OAuthUiOriginStartupWarning` in the API). That includes plain `java -jar` with no `SPRING_PROFILES_ACTIVE` / `spring.profiles.active`, and cases where the property string and resolved profiles disagree (empty resolved profiles with no explicit property, or only `default`). If **any** non-local profile is active (e.g. `nft`, `production`), set **`UI_ORIGIN`** or the API fails at startup when it is unset or blank — **unless no OAuth2 login provider is fully configured**, in which case UI-driven SSO is disabled and **`UI_ORIGIN` is not required**.

When deploying with the repo **Helm chart** (`helm-chart/`), you can set **`publicWebOrigin`** once (scheme + host + port, no path): the chart appends **`UI_ORIGIN`** on the API deployment and **`NEXTAUTH_URL`** on the UI deployment from that value. Avoid also setting duplicate `UI_ORIGIN` / `NEXTAUTH_URL` entries in `env` / `ui.env` for the same pods.

### Google OAuth

Console: https://console.cloud.google.com/apis/credentials

1. Create Credentials > OAuth client ID > Web application
2. Add **authorized redirect URIs** that match what the **UI** sends (the Support Bot app starts OAuth from Next.js and passes this URI to Google). Use your UI origin from `NEXTAUTH_URL` (same scheme, host, and port — Google treats `localhost` and `127.0.0.1` as different):
   - `http://localhost:3000/api/oauth/callback/google`
   - `http://127.0.0.1:3000/api/oauth/callback/google` (if you open the app at `127.0.0.1`)
   - `https://<your-ui-domain>/api/oauth/callback/google` for production
3. Optionally also register the API’s Spring redirect if you use server-initiated OAuth without the UI path:
   - `http://localhost:8080/login/oauth2/code/google`
   - `https://<your-api-domain>/login/oauth2/code/google`
4. Copy the Client ID into `GOOGLE_CLIENT_ID`
5. Copy the Client Secret into `GOOGLE_CLIENT_SECRET`

If you see **Error 400: `redirect_uri_mismatch`**, the URI in Google Cloud Console does not **exactly** match the `redirect_uri` query parameter on the authorize request (including trailing slashes). Compare against the Network tab or add every host/port variant you use locally.

### Azure AD

Portal: https://portal.azure.com > Microsoft Entra ID > App registrations

1. New registration > name it > single tenant
2. Authentication > Add platform > Web > add redirect URIs (same pattern as Google — UI-driven login uses the Next.js callback):
   - `http://localhost:3000/api/oauth/callback/azure`
   - `http://127.0.0.1:3000/api/oauth/callback/azure` if needed
   - `https://<your-ui-domain>/api/oauth/callback/azure`
3. Optional API-only redirects:
   - `http://localhost:8080/login/oauth2/code/azure`
   - `https://<your-api-domain>/login/oauth2/code/azure`
4. Overview > copy the Application (client) ID into `AZURE_CLIENT_ID`
5. Overview > copy the Directory (tenant) ID into `AZURE_TENANT_ID`
6. Certificates & secrets > New client secret > copy the value into `AZURE_CLIENT_SECRET`
7. API permissions > Add > Microsoft Graph > Delegated:
   - `email`
   - `openid`
   - `profile`

> Note: For UI login, redirect URIs must match the **UI** origin (`NEXTAUTH_URL`) and path `/api/oauth/callback/{provider}`, not only the API’s `/login/oauth2/code/...` URLs.

### Dex (OIDC)

Dex docs: https://dexidp.io/

For local Dex setup in this repository, see [`dex/README.md`](../../../../dex/README.md).
For Kubernetes deployment values (platform chart), see [`api/k8s/dex/README.md`](../../../k8s/dex/README.md).

1. Create a Dex OAuth2 client for Support Bot.
2. Set:
   - `DEX_CLIENT_ID`
   - `DEX_CLIENT_SECRET`
   - `DEX_ISSUER_URI`
   - `DEX_INTERNAL_BASE_URL` (optional) — in-cluster URL for token/keys/userinfo. Use the full svc FQDN (`http://dex.<ns>.svc.cluster.local:5556`). If omitted, `DEX_ISSUER_URI` is used for server-to-server calls too.
3. Configure redirect URIs in Dex:
   - `http://localhost:8080/login/oauth2/code/dex`
   - `https://<your-api-domain>/login/oauth2/code/dex`
4. Ensure Dex is configured to return the claims Support Bot needs for login (`email` and `name`/`preferred_username`).
5. For **LDAP → Dex → JWT groups → tenant teams**, enable `platform-integration.jwt-groups` and map claim values to `team-code` (see the `jwt-groups` block under `platform-integration` earlier in this document).

> Note: Dex can be enabled alongside Google and Azure. By default (`login-providers` omitted or empty), the login screen lists **every** fully configured provider. When `login-providers` is non-empty, only those registration ids are shown.

#### Troubleshooting (Dex / OAuth / LDAP)

| Symptom | What to check |
|--------|----------------|
| **`redirect_uri` / unregistered redirect** | Dex `staticClients.redirectURIs` must list both API callbacks (`/login/oauth2/code/dex`) and UI callbacks (`/api/oauth/callback/dex` on the UI origin). Match scheme, host, and port exactly. |
| **`user_not_allowed`** | `security.allow-list` (`ALLOWED_EMAILS` / `ALLOWED_DOMAINS`) must include the user’s email or domain (e.g. LDAP users under `@supportbot.local`). |
| **Missing or wrong teams after LDAP login** | Dex must emit the configured claim (default `groups`). `jwt-groups.mappings[].claim-values` must match those strings (case-insensitive). `team-code` must match a platform team. Only the **`dex`** registration uses `jwt-groups`; Google/Azure direct clients use static-user / Azure / GCP only. |

Full operational order and integration sequencing: [auth Dex/LDAP runbook](../../../../docs/runbooks/auth-dex-ldap.md).

## PR review tracking

`pr-review-tracking` detects PR/MR links posted in support threads and manages
their lifecycle: SLA tracking, escalation to the owning team, and ticket
auto-close. The **end-user** view of the feature — what it does and how it shows
up in Slack and the dashboard — is documented in the
[PR tracking user guide](../../../docs/user-guides/pr-tracking.md). This section
is the **operator** reference: how to wire it up.

All settings live under `pr-review-tracking` in `application.yaml`. Validation
runs at startup and **fails fast** on a bad config — a misconfigured block stops
the app from starting rather than failing silently at runtime.

### Global settings

```yaml
pr-review-tracking:
  enabled: true                            # master feature flag (default: false)
  poll-cron: "0 0 9-18 * * 1-5"            # lifecycle poller schedule (default: business hours, Mon–Fri UTC)
  pr-emoji: pr                             # Slack reaction added to the detected message (default: pr)
  tags: [PR]                               # tag code(s) from enums.tags, applied when the bot auto-closes the ticket
  impact: Information Request              # impact code from enums.impacts, applied on auto-close
  duration-unit: days                      # optional; how bare SLA numbers are read: hours | days | weeks (default: days)
  sla-discovery:
    cache: PT24H                           # optional; TTL for in-repo SLA files and GitLab group/branch lookups (default: PT24H)

  github:                                  # required only if any repo uses provider: github
    api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
    auth-mode:    ${GITHUB_AUTH_MODE:token}      # token | app (default: token)
    token:        ${GITHUB_TOKEN:}               # PAT — required when auth-mode=token
    app-id:           ${GITHUB_APP_ID:}          # GitHub App fields — required when auth-mode=app
    installation-id:  ${GITHUB_APP_INSTALLATION_ID:}
    private-key-pem:  ${GITHUB_APP_PRIVATE_KEY_PEM:}   # raw PEM or base64-encoded PEM

  gitlab:                                  # required only if any repo uses provider: gitlab
    api-base-url: ${GITLAB_API_BASE_URL:https://gitlab.com/api/v4}   # must include /api/v4, no trailing slash
    token:        ${GITLAB_TOKEN:}

  repositories:                            # at least one entry is required when enabled
    - name: my-org/my-repo
      owning-team: support-team
      sla:
        default: 48h
```

| Key | Required | Default | Description |
|-----|----------|---------|-------------|
| `enabled` | — | `false` | Master feature flag. When false, no PR-tracking beans, schedulers, or REST endpoints are created. |
| `poll-cron` | when enabled | `0 0 9-18 * * 1-5` | Spring cron expression for the lifecycle poller. |
| `pr-emoji` | — | `pr` | Slack reaction added to the detected message. Must already exist in the workspace. |
| `tags` | when enabled | — | One or more codes from `enums.tags`, applied to the ticket on auto-close. |
| `impact` | when enabled | — | A code from `enums.impacts`, applied to the ticket on auto-close. |
| `duration-unit` | — | `days` | How a bare numeric SLA value is interpreted: `hours`, `days`, or `weeks`. |
| `sla-discovery.cache` | — | `PT24H` | TTL (ISO-8601 duration) for cached in-repo SLA files and GitLab group-membership / default-branch lookups. |
| `github` | when any GitHub repo | — | GitHub connection block (see [Token permissions](#token-permissions)). |
| `gitlab` | when any GitLab repo | — | GitLab connection block (see [Token permissions](#token-permissions)). |
| `repositories` | when enabled | — | Repositories to watch; at least one entry. See [Per-repository configuration](#per-repository-configuration). |

### Token permissions

The bot only ever **reads** from the provider — it never writes to PRs/MRs.
Grant the minimum read scopes:

**GitHub** (`github` block):

- **`auth-mode: token`** (a Personal Access Token in `github.token`):
  - Classic PAT: `repo` (read PRs, reviews, and file contents on **private**
    repos — omit only if every tracked repo is public) and `read:org`
    (organization membership read — see the note below).
  - Fine-grained PAT: repository permissions **Pull requests: Read**,
    **Contents: Read**, **Metadata: Read**; organization permission
    **Members: Read** (organization membership read — see the note below).
- **`auth-mode: app`** (a GitHub App via `app-id` / `installation-id` /
  `private-key-pem`): the same permissions as the fine-grained PAT above —
  **Pull requests: Read**, **Contents: Read**, **Metadata: Read**, plus
  **Members: Read** (org) for the membership read below.

**Organization membership read** — `read:org` (classic) / **Members: Read**
(fine-grained, App) — is needed only when the bot has to resolve a GitHub
**team**:

- a repo sets `github-team-slug`, so the team's members' reviews count toward the
  SLA; or
- a `requires-codeowners` repo has a **team** (not just individuals) listed in its
  `CODEOWNERS`, so the bot can name that team in the "chase the code owner"
  detected message.

It is **optional** otherwise, and the bot fails open without it: detection and the
code-owner gate still work — individual code owners and the GraphQL
`reviewDecision` are read without org access, and a team code owner is simply
**omitted** from the chase list rather than dropping the PR. Caveat: a
**fine-grained PAT** can still be denied team reads over GitHub's GraphQL API even
with **Members: Read** granted; if a team code owner won't appear, use a classic
PAT with `read:org` or a GitHub App.

**GitLab** (`gitlab` block — a Personal, Group, or Project access token, sent as
the `PRIVATE-TOKEN` header). GitLab supports two permission models; use whichever
your instance offers.

*Classic token scopes* (available on all GitLab versions):

- Scope **`read_api`**.
- The token's identity needs at least the **Reporter** role on every tracked
  project (to read merge requests, approvals, diffs, and repository files).
- Only when a repo sets `gitlab-group-path` does the
  token additionally need at least the **Reporter** role on the referenced
  group(s), so it can list their members (including inherited and invited-group
  members — see
  [Team and group membership resolution](#team-and-group-membership-resolution)).
  Repos that don't set it need no group-level access.

*Fine-grained (granular) permissions* — GitLab's
[fine-grained personal access tokens](https://docs.gitlab.com/auth/tokens/fine_grained_access_tokens/),
introduced as a beta in **GitLab 18.10**. Instead of `read_api`, grant these
read-only resource permissions (the exact set below is a verified working token):

| Permission (resource: action)        | Required                          | Used for                                                                 |
|--------------------------------------|-----------------------------------|--------------------------------------------------------------------------|
| **Repository: Read**                 | yes                               | Read the in-repo SLA file and project metadata (default branch).         |
| **Merge Request: Read**              | yes                               | Read MR details and the MR diff (changed-file matching for path filters). |
| **Approval Setting: Read**           | yes                               | Read the MR's approval configuration.                                     |
| **Merge Request Approval Rule: Read**| yes                               | Read the MR's approval state (who has approved).                          |
| **Group: Read**                      | if `gitlab-group-path` set         | Access the reviewer group.                                              |
| **Member: Read**                     | if `gitlab-group-path` set         | List the reviewer group's members (incl. inherited / invited — see [resolution](#team-and-group-membership-resolution)). |

Note that fine-grained PATs are still rolling out (~75% REST API coverage at
beta), so on older instances or for full coverage the classic `read_api` scope
above is the safe choice.

For self-hosted instances (either model), point `gitlab.api-base-url` (globally
or per-repo) at that instance's `/api/v4` endpoint and issue the token there.

Author admission (`exclude-author-teams`) needs **no** provider scopes on either
side: it resolves team membership through the bot's platform teams (your Slack/IdP
groups, keyed by email), not the VCS provider — see [Author admission](#author-admission).

### Per-repository configuration

Each entry under `repositories` is either **SLA-tracked** (has an `sla` block)
or **no-SLA** (has a `paths` list) — never both. No-SLA repos track only PRs/MRs
touching the listed paths and never escalate.

| Key | Required | Applies to | Description |
|-----|----------|------------|-------------|
| `name` | yes | both | `org/repo` (GitHub) or `group/project` / nested `group/subgroup/project` (GitLab). Unique, case-insensitive. |
| `owning-team` | yes | both | Code from `enums.escalation-teams`; chased when the SLA is breached. |
| `provider` | — | both | `github` (default) or `gitlab`. |
| `github-team-slug` | — | GitHub only | Team whose members' reviews count as a qualifying review. Falls back to the PR's requested team reviewers when omitted. |
| `gitlab-group-path` | — | GitLab only | Reviewer group whose members' approvals count (see [Approver validation and CODEOWNERS](#approver-validation-and-codeowners)). |
| `requires-codeowners` | — | both | `false` (default) / `true`. When `true`, gate the merge on **code-owner** approval read from the provider, and chase the code owner before the SLA clock starts. See [Code-owner merge gate](#code-owner-merge-gate-requires-codeowners). |
| `exclude-author-teams` | — | both | Author admission deny-list: when non-empty, a PR/MR is **skipped** if the Slack user who posted the link belongs to one of these platform teams. Values are platform team codes (same vocabulary as `owning-team`). See [Author admission](#author-admission). |
| `sla` | one of `sla`/`paths` | both | SLA block — see below. |
| `paths` | one of `sla`/`paths` | both | Glob list; a no-SLA repo tracks only PRs/MRs touching these paths. |
| `gitlab` | — | GitLab only | Per-repo `api-base-url` / `token` override of the global `gitlab` block. |
| `messages` | — | both | Per-event Slack copy overrides — see [Customising the Slack messages](#customising-the-slack-messages). |

The `sla` block has:

- `default` — a duration like `48h` or `7d`. Required unless `file` is set.
- `file` — path to an in-repo SLA file; `default` is the fallback when the file
  is missing or invalid.
- `overrides` — optional list of `{ path, sla }` for path-specific SLAs.

**GitHub, fixed SLA:**

```yaml
repositories:
  - name: my-org/my-repo
    # provider: github                    # implicit default
    owning-team: support-team
    github-team-slug: support-reviewers    # optional; falls back to the PR's requested teams
    exclude-author-teams: [platform-team]  # optional; skip PRs whose Slack poster is in one of these platform teams
    sla:
      default: 48h
      overrides:                           # optional, path-specific
        - path: infra/**
          sla: 7d
```

**GitHub, SLA discovered from a file in the repo:**

```yaml
  - name: my-org/my-repo
    owning-team: support-team
    sla:
      file: .pr-sla.yaml
      default: 48h                         # fallback when the file is missing or invalid
```

**GitLab on gitlab.com:**

```yaml
  - name: my-group/my-project
    provider: gitlab
    owning-team: support-team
    gitlab-group-path: my-group/reviewers  # optional; approvals by this group's members count
    exclude-author-teams: [platform-team]  # optional; skip MRs whose Slack poster is in one of these platform teams
    sla:
      default: 48h
```

**GitLab self-hosted, with a per-repo connection override:**

```yaml
  - name: platform/infra/cluster-config    # nested groups are supported
    provider: gitlab
    owning-team: support-team
    gitlab-group-path: platform/reviewers
    gitlab:
      api-base-url: https://gitlab.internal.example.com/api/v4
      token:        ${GITLAB_INTERNAL_TOKEN}
    sla:
      file: .pr-sla.yaml
      default: 7d
```

**No-SLA, path-scoped (works for either provider):**

```yaml
  - name: my-org/no-sla-repo
    owning-team: support-team
    paths:
      - infra/**
      - rbac/**
```

#### Approver validation and CODEOWNERS

This is how the bot decides whose review counts toward an approval:

- **GitHub** — `github-team-slug` selects the team whose members' reviews count.
  When omitted, the bot falls back to the PR's requested team reviewers; if none
  were requested, any review counts.
- **GitLab** — `gitlab-group-path` plays the same role: only approvals by
  members of that group (including inherited and invited-group members — see
  [Team and group membership resolution](#team-and-group-membership-resolution))
  count as a qualifying review.

`gitlab-group-path` is **optional**. When it is omitted, the bot accepts **any**
approval GitLab records on the MR. Omit it when your organisation already
controls who may approve through GitLab's own rules — e.g. **CODEOWNERS** or
required-approval rules — so that GitLab stays the single source of truth for
approval validity and the bot simply mirrors it. Set `gitlab-group-path` only
when you additionally want the bot to restrict qualifying approvals to a
specific reviewer group.

#### Code-owner merge gate (`requires-codeowners`)

Set `requires-codeowners: true` on a repo whose merges are gated on **code-owner**
approval. It changes *when* the SLA clock runs and *who* gets chased, so the bot
doesn't escalate a PR that is still legitimately waiting on a code owner:

1. **At detection** the PR is reported as waiting on its **code owners** (named,
   where the provider lists them), and the SLA clock is **held** — the repo's PR
   sits in `OPEN` with no live deadline.
2. **When the code owners have approved and the PR is mergeable**, it moves to
   `AWAITING_MERGE`, the SLA clock **starts**, and the chase switches to the
   **owning team to merge**.
3. **If the merge deadline passes**, it becomes `MERGE_ESCALATED` (owning team
   escalated again). The ticket closes **only when the provider reports the PR
   merged** — never on "mergeable" alone.

The end-user view of these states is in the
[PR tracking user guide](../../../docs/user-guides/pr-tracking.md#code-owner-repositories).

**Repository prerequisites (these are not optional).** The bot never reads or
parses the `CODEOWNERS` file or matches paths itself — it mirrors the *provider's*
code-owner verdict. That verdict is only meaningful if the repo is actually
configured for code owners, so each tracked code-owner repo must have **both**:

1. **A `CODEOWNERS` file** (`.github/CODEOWNERS`, repo-root `CODEOWNERS`, or
   `docs/CODEOWNERS`), with owners who have **write** access to the repo.
2. **Branch protection that requires review from Code Owners** on the default
   branch (GitHub: "Require review from Code Owners" on the branch-protection rule
   *or* a ruleset with `require_code_owner_review`; GitLab: Code Owner approval
   required on the protected branch).

Without (2), the provider treats code owners as ordinary suggested reviewers and
its signals never reflect code-owner status — the gate silently becomes a no-op.

**How the verdict is sourced, per provider:**

- **GitHub** — a GraphQL query reads `reviewDecision` (`APPROVED` ⟹ all required
  code owners have approved) and the `reviewRequests` whose `asCodeOwner` is true
  (the owners still owed a review — the chase list). When the query succeeds but
  returns **no** `reviewDecision`, GitHub requires no code-owner review for that
  PR's changed paths (nothing code-owned was touched): the gate doesn't apply, so
  the PR **advances straight to the merge phase** rather than waiting on a code
  owner. A *failed* query is treated as unresolved — the PR keeps waiting and the
  poll retries — so a transient GraphQL error never advances a PR by mistake.
  (This is deliberately unlike GitLab, which fails *closed* on its analogous "no
  `code_owner` rule" case — see below.) The gate and **individual** code owners
  need no extra token scope beyond the **Pull requests: Read** / `repo` already
  required, and the bot makes **no** branch-protection API call. The GraphQL call
  is issued **only** for `requires-codeowners` repos, so other repos pay nothing.
  The one exception: naming a **team** code owner in the chase list reads the team,
  which needs organization membership read — see
  [Token permissions](#token-permissions). Without it the gate and individual
  owners are unaffected; the team is just omitted from the message.
- **GitLab** — the MR's `approval_state` `code_owner` rules. Only rules that
  actually require an approval gate (`approvals_required >= 1`); a rule with
  `approvals_required = 0` is `approved` with zero approvals (a vacuously-satisfied
  section) and is ignored, so the gate can't open on it. Of the gating rules,
  `approved` = satisfied and each unapproved rule's `eligible_approvers` = chase
  list. **GitLab Code Owners is a Premium/Ultimate feature** — on instances/plans
  without it (or when the target branch doesn't require code-owner approval) there
  is no gating `code_owner` rule, and the gate fails **closed**, *not* open: the
  MR is held in `OPEN` indefinitely — no merge clock, no escalation — and closes
  only when the MR is actually merged/closed. Do **not** enable
  `requires-codeowners` on such a repo; validate against a real MR first.

**SLA.** The merge clock (`AWAITING_MERGE` → `MERGE_ESCALATED`) uses the repo's
configured `sla`. A `requires-codeowners` repo with **no** `sla` block still gets
the held-clock / code-owner-chase behaviour, but never starts a merge clock and
so never reaches `MERGE_ESCALATED`. `requires-codeowners` is independent of
`gitlab-group-path` / `github-team-slug` (which restrict *whose* review counts) —
you can use either, both, or neither.

**Example — GitHub repo gated on code owners:**

```yaml
  - name: my-org/codeowned-repo
    owning-team: support-team
    requires-codeowners: true              # chase the code owner; clock held until they approve
    sla:
      default: 48h                         # the *merge* clock once code owners have approved
```

#### Author admission

`exclude-author-teams` gates **whether a PR/MR is tracked at all**, based on the
**Slack user who posted the link** — distinct from approver validation above,
which decides whose *review* counts. When the list is non-empty the bot **skips**
a PR if the poster belongs to at least one of the listed teams (**any-of**);
the skip happens *before* any tracking record, Slack reaction, in-thread
notification, or escalation is created. This applies to SLA and no-SLA repos
alike. When the list is empty (the default) every PR is tracked, exactly as
without the setting.

Entries are **platform team codes** — the same codes used for `owning-team` and
configured under `enums.escalation-teams`. The bot takes the poster's
**Slack-profile email** and matches it against the platform team's members — the
same mechanism that powers ticket team suggestions. Because membership is resolved
through the platform teams (not the VCS provider), the gate is fully
provider-agnostic and needs **no** GitHub/GitLab org-membership token scopes.

**Where membership comes from.** Each platform team (configured under
`platform-integration.teams-scraping`) names a `group-ref`, and its roster is
resolved from the matching identity source — a **Slack usergroup** (`slack:`),
**Google Cloud Identity** (`google:`), **Azure AD** (`azure:`), or the **static**
member map — then reduced to a set of emails. (For a `slack:` group-ref the bot
enumerates the usergroup and looks up each member's Slack-profile email.) At match
time the poster's own Slack-profile email is checked against that set.

> **Membership comes from the *platform team's* `group-ref`, not the escalation
> team's mention group.** A team code appears in two places: under
> `enums.escalation-teams` (where `group-ref: "slack:…"` is the usergroup the bot
> *@-mentions*) and under `platform-integration.teams-scraping` (where the team's
> `group-ref` defines its **roster**). The gate uses the roster. So for
> `exclude-author-teams: [wow]` to match, the *platform* team `wow` must have a
> roster `group-ref` that resolves to the poster — point it at a Slack usergroup
> (`group-ref: "slack:S08948NBMED"`), a Google/Azure group, or the static map.
>
> **`jwt:` groups are the exception:** their membership is known only per
> authenticated request (via the user's token), so they can't be enumerated at
> PR-detection time — a `jwt:`-backed team resolves to empty and the gate falls open
> (tracks).

> The identity checked is the Slack user who *posted* the PR link in the support
> channel — not the VCS author of the PR. In the support flow these are usually the
> same person, but a teammate reposting someone else's PR is admitted/skipped on
> *their own* team membership.

The gate **fails open**: it skips a PR only when the poster's team membership
resolved and one of their teams is excluded. If the poster has no resolvable Slack
email, or the lookup fails, the PR is tracked anyway and a warning is logged — a
transient lookup failure never silently drops a legitimate PR. When first rolling
this out, confirm via the logs that the gate is admitting/skipping as you expect.

#### Team and group membership resolution

The reviewer team (`github-team-slug` / `gitlab-group-path`) resolves a team/group
reference to a set of member logins — and the two providers expand **nested** teams
in **opposite directions**. This decides which team/group you should name. (Author
admission does **not** use this mechanism: `exclude-author-teams` resolves via
platform-team membership — see [Author admission](#author-admission).)

**GitHub** — members come from the team slug via the org Teams API, which
**includes the members of child (nested) teams**. Naming a *parent* team captures
everyone in the teams beneath it; naming a child team does **not** pull in the
parent's members.

Org `acme`, nested teams `A ▸ B ▸ C` and standalone `D`; direct members
`alice→A`, `bob→B`, `carol→C`, `dave→D`:

| Team named | Resolves to       |
|------------|-------------------|
| `A`        | alice, bob, carol |
| `B`        | bob, carol        |
| `C`        | carol             |
| `D`        | dave              |

**GitLab** — members come from `GET /groups/:path/members/all`, which includes
the group's direct members, members **inherited from ancestor (parent) groups**,
and members of **invited (shared) groups** — but **not** members who sit only in a
*descendant subgroup*. Naming a *subgroup* captures it plus everything above it;
naming a parent group does **not** reach down into its subgroups.

Group `A`, subgroups `A/B ▸ A/B/C`, separate group `D`, and group `E` invited into
`A/B`; direct members `alice→A`, `bob→A/B`, `carol→A/B/C`, `dave→D`, `erin→E`:

| Group path named | Resolves to             |
|------------------|-------------------------|
| `A`              | alice                   |
| `A/B`            | alice, bob, erin        |
| `A/B/C`          | alice, bob, carol, erin |
| `D`              | dave                    |

In short: on **GitHub** name the team at the *top* of the hierarchy to cover
everything beneath it; on **GitLab** name the *exact subgroup* you mean (it also
covers ancestor and invited-group members). A GitLab *parent-group* path will
**not** match reviewers who live only in its subgroups — the group resolves
successfully but without them, so their approvals won't count toward the owning
team. Name the *exact subgroup* instead.

When membership cannot be resolved at all (missing read scope, or the API call
fails) the bot degrades gracefully rather than failing hard: reviewer filtering
accepts all reviews.

### Validation rules

Checked at startup; any failure aborts boot:

- `provider` is `github` (default) or `gitlab`.
- Repository `name`s are unique (case-insensitive). GitHub names are exactly
  `org/repo`; GitLab names allow nested groups
  (`group/subgroup/project`).
- `github-team-slug` is only valid on GitHub repos; `gitlab-group-path` and a
  per-repo `gitlab:` block are only valid on GitLab repos.
- `exclude-author-teams` is valid on both providers; the list is optional, but
  any entry present must be non-blank. The author-admission gate is active only
  while the list is non-empty.
- `owning-team` must exist in `enums.escalation-teams`; `tags` / `impact` must
  reference `enums.tags` / `enums.impacts`.
- Each repo has **either** an `sla` block **or** a non-empty `paths` list.
- When any repo uses `provider: gitlab`, `gitlab.token` must resolve (globally
  or per-repo) and `gitlab.api-base-url` must include the `/api/v4` segment with
  no trailing slash.
- When any repo uses `provider: github`, the `github` block must be configured
  for the chosen `auth-mode` (token, or all three App fields).
- A `messages.escalated` override is rejected on a no-SLA repo (it can never
  fire).

### Customising the Slack messages

Each repo can override the default Slack copy for any event with a CEL
expression. Templates compile at startup; a bad template logs a warning and
falls back to the built-in default (the feature is fail-safe). The provider only
selects the **default** wording (GitHub says "PR #N", GitLab says "MR !N") — a
custom override always wins.

```yaml
    messages:
      detected:           '"PR " + string(pr_number) + " detected. SLA: " + sla_duration + ", deadline: " + sla_deadline + "."'
      escalated:          '"Contact #pr-reviews in Slack to chase this review."'
      approved:           '"PR " + string(pr_number) + " approved — ready to merge!"'
      changes-requested:  '"Changes requested on PR " + string(pr_number) + ". Please review the feedback."'
      merged:             '"PR " + string(pr_number) + " merged. Thanks!"'
      closed:             '"PR " + string(pr_number) + " closed."'
```

Available CEL variables:

| Variable        | Type   | Notes                                                       |
|-----------------|--------|-------------------------------------------------------------|
| `pr_number`     | int    | Convert with `string(pr_number)` for concatenation          |
| `pr_url`        | string | Full PR/MR URL                                             |
| `repo_name`     | string | `org/repo` (GitHub) or `group/.../project` (GitLab)         |
| `repo_url`      | string | Full repository URL                                        |
| `owning_team`   | string | Team code from `enums.escalation-teams`                     |
| `sla_duration`  | string | e.g. `2 days`; empty for no-SLA repos                       |
| `sla_deadline`  | string | e.g. `Wed 08 May at 17:00 UTC`; empty for no-SLA repos      |
| `provider`      | string | `github` or `gitlab`                                        |

Setting an `escalated` message on a no-SLA repo is rejected at startup.

## Roles

Every authenticated user is assigned one or more roles that control what they can do in the UI.

| Role | Who it's for | Capabilities |
|---|---|---|
| `ROLE_USER` | Everyone — assigned automatically on login | View tickets |
| `ROLE_LEADERSHIP` | Support leads | View tickets, metrics dashboards, and aggregate views |
| `ROLE_SUPPORT_ENGINEER` | Support team members | Everything in `ROLE_LEADERSHIP` plus manage tickets: update status, assign, tag, close |
| `ROLE_ESCALATION` | Teams that *receive* escalations | Resolve escalations raised against their team |

Roles are additive — a user can hold multiple roles simultaneously.

### Assigning roles via Slack groups (recommended)

The simplest way to manage roles is to point `team.support.group-ref` and `team.leadership.group-ref` at Slack user groups. At login time the API resolves group members from Slack and assigns the corresponding role — no redeployment needed when membership changes.

```yaml
team:
  support:
    name: Core Support
    group-ref: slack:<SLACK_GROUP_ID>       # members get ROLE_SUPPORT_ENGINEER
  leadership:
    name: Support Leadership
    group-ref: slack:<SLACK_GROUP_ID>       # members get ROLE_LEADERSHIP

platform-integration:
  enabled: true                             # must be true or the app fails to start
  teams-scraping:
    static:
      enabled: true                         # no teams needed — acts as a no-op placeholder
```

Add or remove people from those Slack groups and their role takes effect at their next login.

> **Prerequisite:** the Slack bot token must have the `usergroups:read` scope. If the scope is missing, group membership resolution fails silently and no support/leadership roles are assigned.

> **Note:** Slack group membership is resolved directly via the Slack API and does not depend on `platform-integration`. However, `platform-integration.enabled: true` is required or the app will fail to start. If you are not using any cloud identity source, the `teams-scraping.static` block with no teams listed is sufficient.

### Assigning roles via LDAP groups (Dex)

When authenticating through Dex with an LDAP backend, roles can alternatively be derived from LDAP group membership via the `platform-integration.jwt-groups` config. Dex populates a `groups` claim in the ID token with the user's LDAP groups; the API maps those to team codes which resolve to roles.

```yaml
platform-integration:
  jwt-groups:
    enabled: true
    claim-name: groups          # LDAP groups claim emitted by Dex
    mappings:
      - claim-values: [support-admins]
        team-code: support      # Must match team.support.code → ROLE_SUPPORT_ENGINEER
      - claim-values: [support-leads]
        team-code: support-leadership  # Must match team.leadership.code → ROLE_LEADERSHIP
```

This path requires `platform-integration.jwt-groups.enabled: true` and only applies to the `dex` OAuth provider — Google and Azure logins are not affected.

### `ROLE_ESCALATION`

`ROLE_ESCALATION` is for teams that *receive* escalations — typically product or platform teams outside the core support team.

When a support engineer escalates a ticket, the bot tags the team's Slack group in the thread — no UI login required. Those who do log into the UI get `ROLE_ESCALATION`, which gives them a dedicated view of escalations assigned to their team.

Unlike `ROLE_SUPPORT_ENGINEER` and `ROLE_LEADERSHIP` (resolved from the `team.support` / `team.leadership` Slack groups), `ROLE_ESCALATION` membership comes from `platform-integration.teams-scraping`. That roster can be sourced from a Slack usergroup (`slack:`), Azure, GCP, Kubernetes, or a static map:

```yaml
enums:
  escalation-teams:
    - label: Platform Team
      code: platform-team                   # must match platform-integration.teams-scraping name below
      group-ref: <CLOUD_GROUP_ID>           # Slack group ID tagged on escalation

platform-integration:
  enabled: true
  teams-scraping:
    static:                                 # or core-platform / k8s-generic / gcp / azure
      enabled: true
      teams:
        - name: platform-team              # must match enums.escalation-teams[].code above
          group-ref: <CLOUD_GROUP_ID>      # cloud group whose members get ROLE_ESCALATION
```

> **Known limitation:** since the Slack group ID is already configured on each escalation team for tagging, using it for membership resolution too (as support/leadership do) would be simpler for Slack-first deployments — but this is not currently supported.
