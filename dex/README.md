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
docker run --rm ghcr.io/dexidp/dex:v2.44.0 dex hash-password "admin"
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
make -C dex up-local
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

## 5) Redirect URIs

Ensure Dex `staticClients.redirectURIs` includes:

- `http://localhost:8080/login/oauth2/code/dex`
- `http://127.0.0.1:8080/login/oauth2/code/dex`
