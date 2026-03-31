# Local Dex module

This module runs Dex locally with Docker using the upstream image.

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

1. Start OpenLDAP first (`make -C ldap run-local`). Use a host reachable from the Dex container, e.g. `host.docker.internal:389` (macOS/Windows) or attach Dex to the same Docker network as LDAP.
2. In `dex/.env.local`, set `DEX_LDAP_ENABLED=true` and the `DEX_LDAP_*` variables from `dex/.env.example`, then `make -C dex render-config` and restart Dex.
3. On the API, set `platform-integration.jwt-groups.enabled: true` and `mappings` so Dex `groups` claim values (e.g. LDAP `cn` of `groupOfUniqueNames`) map to your platform team codes — see `api/service/docs/configuration.md`.
4. Allow LDAP test users if you use an allow-list, e.g. `ALLOWED_DOMAINS=supportbot.local`.

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
