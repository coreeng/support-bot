# Local Dex module

This module runs Dex locally with Docker using the upstream image.

For **start order** (LDAP → Dex → API → UI), **integration deploy order**, and **troubleshooting** (`redirect_uri`, `user_not_allowed`, JWT groups), see [docs/runbooks/auth-dex-ldap.md](../docs/runbooks/auth-dex-ldap.md).

## 1) Prepare `.env.local`

Copy the example env file:

```bash
cp dex/.env.example dex/.env.local
```

Update `dex/.env.local` values.

Generate a password hash for Dex local user (replace `admin` with your password):

```bash
htpasswd -bnBC 10 "" "admin" | tr -d ':\n' | sed 's/$2y/$2a/'
```

Use the output as `DEX_LOCAL_USER_PASSWORD_HASH`.
No quoting or escaping is required for the bcrypt hash in `.env.local`.

To **turn off** Dex’s built-in “Log in with Email” (static users) and use **only** connectors (LDAP, Google, Microsoft), set `DEX_ENABLE_PASSWORD_DB=false` in `.env.local`, then `make -C dex render-config` and restart Dex. You must have at least one connector enabled or nobody can sign in.

## 2) Render Dex config from `.env.local`

```bash
make -C dex render-config
```

## 3) Start Dex

```bash
docker compose -f dex/docker-compose.yaml up -d
# or:
make -C dex run-local
```

Dex endpoints:

- Issuer: `http://127.0.0.1:5556`
- Telemetry: `http://127.0.0.1:5558`

## 4) Wire Support Bot API

Set these in `api/.env.local`:

```dotenv
DEX_CLIENT_ID=support-bot-dex
DEX_CLIENT_SECRET=<same-as-staticClients-secret>
DEX_ISSUER_URI=http://127.0.0.1:5556
DEX_SCOPES=openid,email,profile,groups
```

Then run the API:

```bash
cd api && make run-local
```

### LDAP via Dex (Stage 3)

1. Start OpenLDAP first (`make -C ldap run-local`). With the repo compose files, Dex joins network `supportbot-ldap`; set `DEX_LDAP_HOST=openldap:389` in `dex/.env.local`. Otherwise use `host.docker.internal:389` (macOS/Windows) only if LDAP is published on the host and Dex is not on the LDAP network.
2. In `dex/.env.local`, set `DEX_LDAP_ENABLED=true` and the `DEX_LDAP_*` variables from `dex/.env.example`, then `make -C dex render-config` and restart Dex.
3. On the API, set `platform-integration.jwt-groups.enabled: true` and `mappings` so Dex `groups` claim values (e.g. LDAP `cn` of `groupOfUniqueNames`) map to your platform team codes — see `api/service/docs/configuration.md`.
4. Allow LDAP test users if you use an allow-list, e.g. `ALLOWED_DOMAINS=supportbot.local`.

### Google / Microsoft via Dex (optional)

Set `DEX_GOOGLE_ENABLED=true` or `DEX_MICROSOFT_ENABLED=true` plus the client id/secret variables from `dex/.env.example`, then `make -C dex render-config`. Register OAuth apps whose **authorized redirect URI** is `{DEX_ISSUER}/callback` (e.g. `http://127.0.0.1:5556/callback` for local Dex), not the Support Bot API callback. Aligns with [`api/k8s/dex/README.md`](../api/k8s/dex/README.md) Helm connector settings.

## 5) Redirect URIs

Ensure Dex `staticClients.redirectURIs` includes:

- `http://localhost:3000/api/oauth/callback/dex`
- `http://127.0.0.1:3000/api/oauth/callback/dex`

## 6) Stage 1 lifecycle commands

Validate Dex module values against `core-platform-app`:

```bash
make dex-template
```

Deploy Dex module to integration:

```bash
make dex-deploy-integration
```

Deploy Dex module to production:

```bash
make dex-deploy-prod
```

GitHub workflows:

- `.github/workflows/dex-fast-feedback.yaml` (P2P fast feedback for the Dex module)

## Troubleshooting (quick)

| Issue | Hint |
|-------|------|
| Unregistered **`redirect_uri`** | Add exact UI/API callback URLs to `staticClients.redirectURIs` in rendered `config/config.yaml`; see [runbook](../docs/runbooks/auth-dex-ldap.md). |
| **`user_not_allowed`** after Dex login | API allow-list must allow the user email/domain (`ALLOWED_EMAILS` / `ALLOWED_DOMAINS`). |
| LDAP bind / connector errors | Check `DEX_LDAP_*` in `.env.local`, `DEX_LDAP_ENABLED=true`, and `make -C dex render-config` after changes. |
| Bcrypt / hash errors | Use `make -C dex render-config` (Python) so `$` in hashes is not mangled by the shell. |
