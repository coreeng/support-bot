# Configuration Status Report

## Summary of Changes Made

### Fixed Issues

1. **OAuth2ClientConfig** - Removed hardcoded `${AZURE_TENANT_ID:common}` default. Now reads from `spring.security.oauth2.client.provider.azure.tenant-id` and requires all three (client-id, client-secret, tenant-id) to register Azure OAuth.

2. **PlatformTeamsConfig** - Removed `System.getenv("AZURE_CLIENT_LOG_LEVEL")`. Now uses Spring property `platform-integration.azure.client.log-level` with default `NONE` in application.yaml.

3. **CorsConfig** - Changed behavior:
   - Empty/unset → **No CORS** (restrictive, same-origin only)
   - `"*"` → Allow all origins (explicit opt-in for development)
   - `"domain1.com,domain2.com"` → Allow specific domains (HTTPS only)
   - Removed hardcoded `localhost:3000` fallback
   - Now reads from `SecurityProperties.cors().allowedOrigins()` instead of separate `@Value`

4. **api/.env.example** - Updated to set `CORS_ALLOWED_ORIGINS=*` for development.

---

## Complete API Configuration Table

### Required Properties

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `security.jwt.secret` | `JWT_SECRET` | *(none)* | JWT signing key (min 256 bits) |
| `slack.creds.token` | `SLACK_TOKEN` | *(none)* | Slack bot token (xoxb-...) |
| `slack.creds.socket-token` | `SLACK_SOCKET_TOKEN` | *(none)* | Slack socket token (xapp-...) |
| `slack.creds.signing-secret` | `SLACK_SIGNING_SECRET` | *(none)* | Slack signing secret |
| `slack.ticket.channel-id` | `SLACK_TICKET_CHANNEL_ID` | *(none)* | Slack ticket channel ID |

### CORS Configuration

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `security.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | *(empty)* | CORS origins - see options below |

**CORS Options:**
- `*` - Allow all origins (development only)
- `example.com,myapp.io` - Allow specific domains and subdomains (HTTPS only)
- Empty/unset - No CORS (same-origin only, most restrictive)

### Security & JWT

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `security.jwt.expiration` | - | `24h` | JWT token expiration time |
| `security.oauth2.redirect-uri` | `UI_ORIGIN` | `http://localhost:3000/login` | OAuth2 callback redirect URI |
| `security.test-bypass.enabled` | - | `false` | Enable test auth bypass |

### OAuth2 - Google (Optional)

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `spring.security.oauth2.client.registration.google.client-id` | `GOOGLE_CLIENT_ID` | *(empty)* | Google OAuth client ID |
| `spring.security.oauth2.client.registration.google.client-secret` | `GOOGLE_CLIENT_SECRET` | *(empty)* | Google OAuth client secret |

> **Note:** Both client-id and client-secret required to enable Google OAuth.

### OAuth2 - Azure (Optional)

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `spring.security.oauth2.client.registration.azure.client-id` | `AZURE_CLIENT_ID` | *(empty)* | Azure AD client ID |
| `spring.security.oauth2.client.registration.azure.client-secret` | `AZURE_CLIENT_SECRET` | *(empty)* | Azure AD client secret |
| `spring.security.oauth2.client.provider.azure.tenant-id` | `AZURE_TENANT_ID` | *(empty)* | Azure AD tenant ID |

> **Note:** All three (client-id, client-secret, tenant-id) required to enable Azure OAuth.

### Database

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/postgres` | PostgreSQL connection URL |
| `spring.datasource.username` | `DB_USERNAME` | `postgres` | Database username |
| `spring.datasource.password` | `DB_PASSWORD` | `postgres` | Database password |

### Slack Configuration

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `slack.mode` | - | `socket` | Slack connection mode (socket/http) |
| `slack.enable-request-verification` | - | `true` | Verify Slack request signatures |
| `slack.ticket.expected-initial-reaction` | - | `eyes` | Emoji to create ticket |
| `slack.ticket.response-initial-reaction` | - | `ticket` | Emoji for ticket response |
| `slack.ticket.resolved-reaction` | - | `white_check_mark` | Emoji for resolved ticket |
| `slack.ticket.escalated-reaction` | - | `rocket` | Emoji for escalated ticket |

### Platform Integration - Azure

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `platform-integration.azure.enabled` | - | `false` | Enable Azure user fetching |
| `platform-integration.azure.client.base-url` | - | *(empty)* | Azure Graph API base URL override |
| `platform-integration.azure.client.log-level` | `AZURE_CLIENT_LOG_LEVEL` | `NONE` | Azure client log level (NONE/BASIC/HEADERS/BODY) |

### Platform Integration - General

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `platform-integration.enabled` | - | `true` | Enable platform integration |
| `platform-integration.fetch.max-concurrency` | - | `64` | Max concurrent fetches |
| `platform-integration.fetch.timeout` | - | `30s` | Fetch timeout |
| `platform-integration.gcp.enabled` | - | `false` | Enable GCP user fetching |
| `platform-integration.static-user.enabled` | - | `true` | Enable static user mapping |
| `platform-integration.teams-scraping.static.enabled` | - | `true` | Enable static team config |

### Feature Flags

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `rbac.enabled` | - | `true` | Enable role-based access control |
| `metrics.enabled` | - | `false` | Enable metrics collection |
| `metrics.refresh-interval` | - | `60s` | Metrics refresh interval |
| `mock-data.enabled` | - | `false` | Enable mock data |
| `ai.sentiment-analysis.enabled` | - | `false` | Enable AI sentiment analysis |
| `ticket.staleness-check-job.enabled` | - | `true` | Enable stale ticket checks |

### Server Configuration

| Property Path | Environment Variable | Default | Purpose |
|--------------|---------------------|---------|---------|
| `server.port` | - | `8080` | Main server port |
| `server.shutdown` | - | `graceful` | Shutdown mode |
| `management.server.port` | - | `8081` | Actuator/metrics port |

---

## Environment Variable Summary

### Required for Production

```bash
JWT_SECRET=<your-jwt-secret-minimum-256-bits>
SLACK_TOKEN=<xoxb-your-bot-token>
SLACK_SOCKET_TOKEN=<xapp-your-socket-token>
SLACK_SIGNING_SECRET=<your-signing-secret>
SLACK_TICKET_CHANNEL_ID=<C0000000000>
CORS_ALLOWED_ORIGINS=<yourdomain.com>
```

### Optional - Database (if not using defaults)

```bash
DB_URL=jdbc:postgresql://your-host:5432/your-db
DB_USERNAME=your-username
DB_PASSWORD=your-password
```

### Optional - OAuth Providers

```bash
# Google OAuth (both required to enable)
GOOGLE_CLIENT_ID=<your-google-client-id>
GOOGLE_CLIENT_SECRET=<your-google-client-secret>

# Azure OAuth (all three required to enable)
AZURE_CLIENT_ID=<your-azure-client-id>
AZURE_CLIENT_SECRET=<your-azure-client-secret>
AZURE_TENANT_ID=<your-azure-tenant-id>
```

### Optional - UI Origin

```bash
UI_ORIGIN=https://your-frontend-domain.com
```

### Optional - Debugging

```bash
AZURE_CLIENT_LOG_LEVEL=BODY  # NONE, BASIC, HEADERS, BODY
```
