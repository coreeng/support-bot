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
    channel-id: ${SLACK_TICKET_CHANNEL_ID} # Channel ID (C1234567890) where tenants post queries
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
  escalation-teams: # Teams available for query escalation
    - label: wow # Label showed on the UI
      code: wow # Team ID. Must be unique. Have to match platform team name unless platform-integration.fetch.ignore-unknown-teams is set to true
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
    repos — omit only if every tracked repo is public) and `read:org` (resolve
    `github-team-slug` membership).
  - Fine-grained PAT: repository permissions **Pull requests: Read**,
    **Contents: Read**, **Metadata: Read**; organization permission
    **Members: Read** (only if any repo uses `github-team-slug`).
- **`auth-mode: app`** (a GitHub App via `app-id` / `installation-id` /
  `private-key-pem`): the same permissions as the fine-grained PAT above —
  **Pull requests: Read**, **Contents: Read**, **Metadata: Read**, and
  **Members: Read** (org) when `github-team-slug` is used.

**GitLab** (`gitlab` block — a Personal, Group, or Project access token, sent as
the `PRIVATE-TOKEN` header). GitLab supports two permission models; use whichever
your instance offers.

*Classic token scopes* (available on all GitLab versions):

- Scope **`read_api`**.
- The token's identity needs at least the **Reporter** role on every tracked
  project (to read merge requests, approvals, diffs, and repository files).
- Only when a repo sets the optional `gitlab-group-path` does the token
  additionally need at least the **Reporter** role on that reviewer group, so it
  can list the group's members (including inherited members). Repos that omit
  `gitlab-group-path` need no group-level access.

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
| **Group: Read**                      | only if `gitlab-group-path` set   | Access the reviewer group.                                                |
| **Member: Read**                     | only if `gitlab-group-path` set   | List the reviewer group's members (including inherited members).         |

Note that fine-grained PATs are still rolling out (~75% REST API coverage at
beta), so on older instances or for full coverage the classic `read_api` scope
above is the safe choice.

For self-hosted instances (either model), point `gitlab.api-base-url` (globally
or per-repo) at that instance's `/api/v4` endpoint and issue the token there.

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
  members of that group (including inherited members) count as a qualifying
  review.

`gitlab-group-path` is **optional**. When it is omitted, the bot accepts **any**
approval GitLab records on the MR. Omit it when your organisation already
controls who may approve through GitLab's own rules — e.g. **CODEOWNERS** or
required-approval rules — so that GitLab stays the single source of truth for
approval validity and the bot simply mirrors it. Set `gitlab-group-path` only
when you additionally want the bot to restrict qualifying approvals to a
specific reviewer group.

### Validation rules

Checked at startup; any failure aborts boot:

- `provider` is `github` (default) or `gitlab`.
- Repository `name`s are unique (case-insensitive). GitHub names are exactly
  `org/repo`; GitLab names allow nested groups
  (`group/subgroup/project`).
- `github-team-slug` is only valid on GitHub repos; `gitlab-group-path` and a
  per-repo `gitlab:` block are only valid on GitLab repos.
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

`ROLE_ESCALATION` is for teams that *receive* escalations — typically product or platform teams outside the core support team. Escalation team members must be in the support channel to see and respond to escalations.

When a support engineer escalates a ticket, the bot posts in the Slack thread and tags the team's Slack group (`enums.escalation-teams[].slack-group-id`). Escalation team members need to be in the support channel to see and respond to the thread. Those who log into the UI get `ROLE_ESCALATION`, which allows them to view and resolve escalations assigned to their team.

**How membership is resolved:** unlike `ROLE_SUPPORT_ENGINEER` and `ROLE_LEADERSHIP` which use a simple `group-ref: slack:<ID>`, `ROLE_ESCALATION` is resolved through `platform-integration` (Azure, GCP, Kubernetes). This made sense when escalation teams mirrored tenant teams already tracked in those identity sources. However, since the Slack group ID is already configured on each escalation team for tagging, using it for membership resolution too (the same way support/leadership work) would be simpler and sufficient for Slack-first deployments — this is a known limitation.
