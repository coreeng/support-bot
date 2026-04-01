# Runbook: Dex, LDAP, and Support Bot auth

Operational order and troubleshooting for the optional **LDAP** and **Dex** modules and how they connect to the **API** and **UI**.

## Local development: start order

Start dependencies before consumers:

1. **PostgreSQL** — `make db-up` from the repo root (or your own Postgres).
2. **LDAP** (if using Dex + LDAP) — `make -C ldap run-local`. Wait until port `389` accepts connections.
3. **Dex** — `make -C dex render-config` then `make -C dex run-local`. If Dex runs in Docker and LDAP on the host, set `DEX_LDAP_HOST=host.docker.internal:389` (macOS/Windows) or join both services on the same Docker network.
4. **API** — `cd api && make run-local` (loads `api/.env.local` with `DEX_*`, DB, Slack, etc.).
5. **UI** — `cd ui && make run-local` (or use root `make run-local` to run API + UI together after DB is up).

Using **only** Dex static users (no LDAP) skips step 2; ensure Dex config does not enable the LDAP connector.

## Integration / Kubernetes: suggested deploy order

Apply secrets first, then workloads that depend on them.

1. **Secrets**
   - `ldap-secrets` (`admin-password`) — see [`api/k8s/ldap/README.md`](../../api/k8s/ldap/README.md).
   - `dex-secrets` (`client-secret`, and `ldap-bind-password` when `dex.ldap.enabled`) — see [`api/k8s/dex/README.md`](../../api/k8s/dex/README.md).
2. **LDAP** — `make ldap-deploy-integration` (or equivalent `helm upgrade` with your tenant values). Confirm the Service (e.g. `ldap:389`) is reachable from the namespace where Dex will run.
3. **Dex** — `make dex-deploy-integration`. Set `dex.ldap.enabled: true` and matching host/DNs when Dex should use the cluster LDAP Service.
4. **Support Bot API** — deploy or upgrade the main app chart with `DEX_CLIENT_ID`, `DEX_CLIENT_SECRET`, `DEX_ISSUER_URI`, and application config for `platform-integration.jwt-groups` if you map LDAP groups to tenant teams. Optional: `security.oauth2.login-providers: [dex]` so the UI only offers Dex even when `GOOGLE_*` / `AZURE_*` are set for other purposes.

Exact namespaces and release names depend on your P2P / tenant layout; align Dex `ldap.host` with the in-cluster DNS name of the LDAP Service.

### Google / Microsoft connectors on Dex (Kubernetes)

When `dex.google.enabled` or `dex.microsoft.enabled` is true in [`api/k8s/dex/values.yaml`](../../api/k8s/dex/values.yaml), register **separate** OAuth apps with Google / Entra whose redirect URI is **`{dex.issuer}/callback`** (Dex’s callback), not the Support Bot API’s `/login/oauth2/code/...` URLs. Populate `dex-secrets` keys `google-client-id`, `google-client-secret`, `microsoft-client-id`, and `microsoft-client-secret` as documented in [`api/k8s/dex/README.md`](../../api/k8s/dex/README.md). Set `dex.microsoft.tenant` in Helm values for the Microsoft connector.

## Troubleshooting

### `redirect_uri` / `Unregistered redirect_uri` (Dex)

**Symptoms:** Dex or the IdP rejects the login; browser or Dex logs mention an unregistered redirect URI.

**Checks:**

- Dex `staticClients.redirectURIs` must include every URL the app uses to complete OAuth:
  - API (Spring): `https://<api-host>/login/oauth2/code/dex` and the same for `http://localhost:8080` when local.
  - Next.js UI callback: `http://localhost:3000/api/oauth/callback/dex` (and `127.0.0.1` if you use it).
- Scheme, host, port, and path must match **exactly** (no trailing slash mismatch).
- After changing Dex config, restart Dex and re-render local config (`make -C dex render-config`).

### `user_not_allowed` (UI) / allow-list rejection (API)

**Symptoms:** Login fails with `user_not_allowed` on the login page, or API logs show allow-list rejection during token exchange.

**Checks:**

- If `security.allow-list.emails` or `security.allow-list.domains` is set, the authenticated user’s email must match.
- LDAP test users use addresses like `alice@supportbot.local` — add `supportbot.local` to `ALLOWED_DOMAINS` or the specific email to `ALLOWED_EMAILS`.
- Empty allow-list means all SSO users are allowed (see [`api/README.md`](../../api/README.md)).

### Wrong or missing teams after LDAP login (JWT groups / claim mismatch)

**Symptoms:** User logs in via Dex + LDAP but has no tenant teams, or teams do not match LDAP membership.

**Checks:**

- Dex must issue a **`groups`** (or configured) claim for LDAP users — enable LDAP connector + `groups` scope; confirm with Dex `/userinfo` or decoded `id_token`.
- API `platform-integration.jwt-groups.enabled` must be `true` with **`mappings`** whose `claim-values` match what Dex actually emits (case-insensitive string match). If Dex sends a full group DN, list that exact string in `claim-values`, or adjust Dex LDAP `nameAttr` so short names (e.g. `developers`) appear in the claim.
- `team-code` in each mapping must match a platform team from `platform-integration.teams-scraping` (and enums where relevant).
- **Google / Azure via Dex** do not rely on JWT groups for tenant membership; those paths still use static-user / Azure / GCP fetchers. Only the **`dex`** OAuth client triggers `jwt-groups` merging.

### Bcrypt / config render (local Dex)

**Symptoms:** Dex fails to parse password hash or LDAP bind fails.

**Checks:**

- Use `make -C dex render-config` (Python renderer) so `$` in bcrypt hashes is not stripped by the shell.
- LDAP: verify `DEX_LDAP_BIND_DN` / `DEX_LDAP_BIND_PW` against phpLDAPadmin or `ldapwhoami`.

### Helm template failures (`core-platform-app` test hooks)

**Symptoms:** `helm template` fails on `templates/tests/*.yaml` when using `secretKeyRef` in `envVarsArr`.

**Checks:**

- LDAP module: `ldap/scripts/helm_ldap.sh` strips chart test templates before render/deploy — use `make -C ldap template` / `make ldap-template`.

## Automated checks (CI)

- **Dex:** `.github/workflows/dex-fast-feedback.yaml` — `make p2p-build` in `dex/` (Helm template validation).
- **LDAP:** `.github/workflows/ldap-fast-feedback.yaml` — same for `ldap/`.

End-to-end browser login flows (Dex + LDAP + API + UI) are not covered by a single matrix job in this repository; use the sequences above for manual smoke tests.

## Related documentation

- [Dex module (local)](../../dex/README.md)
- [LDAP module (local)](../../ldap/README.md)
- [Dex Helm values](../../api/k8s/dex/README.md)
- [LDAP Helm values](../../api/k8s/ldap/README.md)
- [Service configuration reference](../../api/service/docs/configuration.md)
