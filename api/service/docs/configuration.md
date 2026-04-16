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

pr-review-tracking: # Detects PR links in support threads and manages their lifecycle (SLA tracking, escalation, auto-close)
  enabled: false # Feature flag — off by default
  poll-cron: 0 0 9-18 * * 1-5 # Cron schedule for the lifecycle poller (default: business hours Mon–Fri)
  pr-emoji: pr # Slack emoji added to the message when a PR is detected — must exist in your Slack workspace
  tags: # Required when enabled: tag codes from enums.tags applied to the ticket when the bot auto closes it
    - <tag-code>
  impact: <impact-code> # Required when enabled: impact code from enums.impacts applied when the bot auto-closes a ticket
  repositories: # Repositories to watch. At least one entry is required when enabled.
    - name: my-org/my-repo # Repository in org/repo format
      owning-team: <team-code> # Team code from enums.escalation-teams — escalated when the SLA is breached
      sla: PT48H # SLA duration for PRs in this repository (ISO 8601 duration, e.g. PT48H = 48 hours)
  github:
    api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
    auth-mode: ${GITHUB_AUTH_MODE:token} # token | app
    token: ${GITHUB_TOKEN:} # Personal Access Token — used only when auth-mode=token
    app-id: ${GITHUB_APP_ID:} # GitHub App ID — used only when auth-mode=app
    installation-id: ${GITHUB_APP_INSTALLATION_ID:} # GitHub App installation ID — used only when auth-mode=app
    private-key-pem: ${GITHUB_APP_PRIVATE_KEY_PEM:} # Private key input — used only when auth-mode=app
                                                     # Accepts raw PEM content or base64-encoded PEM
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

For local development you can omit **`UI_ORIGIN`** when using a **local-like** Spring profile (`local`, `test`, `functionaltests`, `integrationtests`, `integrationtests-oidc`), when **`spring.profiles.active` / `SPRING_PROFILES_ACTIVE` is unset or blank** (e.g. plain `java -jar` without those env vars), or when **only** the Spring **`default`** profile is active. Otherwise the API fails at startup if `UI_ORIGIN` is unset or blank.

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
