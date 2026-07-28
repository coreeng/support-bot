# Contributing to the support bot

We welcome contributions of all forms — from bug reports and documentation improvements to new features.
This guide outlines the process and expectations for contributing to ensure consistency and maintainability.

## Requirements

To contribute to this project, you will need:

- **Java 21**: Required to build and run the project.
- **Gradle**: The project uses the Gradle wrapper (`gradlew`) for builds, tests and code generation.
- **Docker Desktop or alternative**: Used for running the local database and optionally building Docker images.

## Running Support Bot locally

The full stack — PostgreSQL, OpenLDAP, Dex, the API and the UI — starts with one target:

```bash
source ./pathToMyVariablesFile/file.sh
make run-local-dex-ldap
```

That first line is your own variables file — a shell script of `export` statements, described in step 2. It must be
sourced in the **same shell** that runs `make`: the API and LDAP Makefiles `source` their `.env.local` files, and
`dex/scripts/render_config.py` resolves `${NAME}` references from the process environment. Nothing loads it for you.

Alongside Java 21 and Docker you will also need Python 3 (used to render the Dex config), a Slack app with Socket Mode
enabled and a channel for it to watch (see [api/service/README.md](api/service/README.md)), and these ports free:
`3000` (UI), `8080` and `8081` (API and management), `5432` (Postgres), `389` (LDAP), `18081` (phpLDAPadmin),
`5556` and `5558` (Dex).

### 1. Create the four `.env.local` files

Every module ships an `.env.example` documenting each setting in full. The four files below are a working set for this
stack — start from them and adjust. All four are gitignored, and secrets stay out of them by referencing variables
exported from your variables file (step 2) rather than pasting values. The name inside `${...}` must match the exported
name exactly.

**`api/.env.local`**

```bash
CORS_ALLOWED_ORIGINS=*

JWT_SECRET=local-dev-jwt-secret-change-me-in-production-min-256-bits

GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}

AZURE_CLIENT_ID=${AZURE_CLIENT_ID}
AZURE_CLIENT_SECRET=${AZURE_CLIENT_SECRET}
AZURE_TENANT_ID=${AZURE_TENANT_ID}
AZURE_CLIENT_LOG_LEVEL=BODY

# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Slack Configuration (values for local slack app)
SLACK_TOKEN=${SLACK_TOKEN}
SLACK_SOCKET_TOKEN=${SLACK_SOCKET_TOKEN}
SLACK_SIGNING_SECRET=${SLACK_SIGNING_SECRET}
SLACK_TICKET_CHANNEL_ID=${SLACK_TICKET_CHANNEL_ID}

ALLOWED_EMAILS=your.email@cecg.io
ALLOWED_DOMAINS=cecg.io

GITHUB_AUTH_MODE="app"
GITHUB_APP_ID="${GITHUB_APP_ID}"
GITHUB_APP_INSTALLATION_ID="${GITHUB_APP_INSTALLATION_ID}"
GITHUB_APP_PRIVATE_KEY_PEM="${GITHUB_APP_PRIVATE_KEY_PEM}"

GITLAB_TOKEN=${GITLAB_TOKEN}

ELEVATE_BASE_URL=${ELEVATE_BASE_URL}
ELEVATE_CLIENT_ID=${ELEVATE_CLIENT_ID}
ELEVATE_CLIENT_SECRET=${ELEVATE_CLIENT_SECRET}

ELEVATE_AGENT_NAME=Support Bot Integration
SUPPORT_BOT_URL=http://localhost:3000
SUPPORT_BOT_VERSION=dev
```

`JWT_SECRET` must be at least 256 bits. Leave `UI_ORIGIN` unset — `api/Makefile` runs with
`SPRING_PROFILES_ACTIVE=local`, where the OAuth redirect defaults to `http://localhost:3000`. To offer Dex on the login
page as well, add three more values matching `dex/.env.local`; without them the stack still runs, the API just does not
register Dex as a login provider:

```bash
DEX_CLIENT_ID=support-bot-dex
DEX_CLIENT_SECRET=<same value as dex/.env.local>
DEX_ISSUER_URI=http://127.0.0.1:5556
```

**`ui/.env.local`**

```bash
# Internal backend API URL (server-side only, never exposed to browser)
BACKEND_URL=http://localhost:8080

# This app's public URL (for OAuth callbacks). The origin must match the API's OAuth redirect origin,
# or proxied OAuth returns 400. localhost and 127.0.0.1 are distinct; use one canonical URL everywhere.
NEXTAUTH_URL=http://localhost:3000

# Generate with: openssl rand -base64 32
AUTH_SECRET=${AUTH_SECRET}
```

All three are required — the UI refuses to boot without them.

**`dex/.env.local`**

```bash
DEX_ISSUER=http://127.0.0.1:5556
DEX_CLIENT_ID=support-bot-dex
DEX_CLIENT_SECRET=replace-me
DEX_REDIRECT_URIS=http://localhost:3000/api/oauth/callback/dex,http://localhost:8080/login/oauth2/code/dex,http://127.0.0.1:3000/api/oauth/callback/dex,http://127.0.0.1:8080/login/oauth2/code/dex,http://127.0.0.1:5556/callback,http://localhost:5556/callback

DEX_LOCAL_USER_EMAIL=your.email@cecg.io
DEX_LOCAL_USER_USERNAME=admin
DEX_LOCAL_USER_ID=08a8684b-db88-4b73-90a9-3cd1661f5466
DEX_LOCAL_USER_PASSWORD_HASH=${DEX_USER_PW_HASH}

DEX_ENABLE_PASSWORD_DB=true

DEX_LDAP_ENABLED=true
DEX_LDAP_HOST=host.docker.internal:389
DEX_LDAP_BIND_DN=cn=admin,dc=supportbot,dc=local
DEX_LDAP_BIND_PW=changeme
DEX_LDAP_USER_SEARCH_BASE=ou=People,dc=supportbot,dc=local
DEX_LDAP_GROUP_SEARCH_BASE=ou=Groups,dc=supportbot,dc=local

# Microsoft (Entra) connector. The app registration needs http://127.0.0.1:5556/callback
# among its Web platform redirect URIs.
DEX_MICROSOFT_ENABLED=true
DEX_MICROSOFT_CLIENT_ID=${DEX_MICROSOFT_CLIENT_ID}
DEX_MICROSOFT_CLIENT_SECRET=${DEX_MS_CLIENT_SECRET}
DEX_MICROSOFT_TENANT=${DEX_MICROSOFT_TENANT}
```

You need at least one way to sign in — the static password DB, LDAP, or one of the social connectors.
`DEX_LDAP_BIND_PW` must equal `LDAP_ADMIN_PASSWORD` in `ldap/.env.local`. `host.docker.internal:389` reaches LDAP
through the port it publishes on the host, which works on macOS and Windows; `openldap:389` uses the compose service
name over the shared `supportbot-ldap` network instead.

**`ldap/.env.local`**

```bash
# Password for the local OpenLDAP admin.
LDAP_ADMIN_PASSWORD=changeme

# Required for bootstrap users alice/bob (LDIF is generated before compose up).
LDAP_BOOTSTRAP_USER_PASSWORD=change-me-local-only
```

### 2. Create your variables file

Name it whatever you like and keep it **outside the repository** so it can never be committed. It holds every secret
the `.env.local` files reference, and you source it by path when you start the stack. Prefer a shared secret store over
copying the file between machines.

```bash
export GOOGLE_CLIENT_ID=<fill in yours here>
export GOOGLE_CLIENT_SECRET=<fill in yours here>
export AZURE_CLIENT_ID=<fill in yours here>
export AZURE_CLIENT_SECRET=<fill in yours here>
export AZURE_TENANT_ID=<fill in yours here>
export SLACK_TOKEN=<fill in yours here>
export SLACK_SOCKET_TOKEN=<fill in yours here>
export SLACK_SIGNING_SECRET=<fill in yours here>
export SLACK_TICKET_CHANNEL_ID=<fill in yours here>
export GITHUB_APP_ID=<fill in yours here>
export GITHUB_APP_INSTALLATION_ID=<fill in yours here>
export GITHUB_APP_PRIVATE_KEY_PEM=<fill in yours here>
export GITLAB_TOKEN=<fill in yours here>
export ELEVATE_BASE_URL=<fill in yours here>
export ELEVATE_CLIENT_ID=<fill in yours here>
export ELEVATE_CLIENT_SECRET=<fill in yours here>
export DEX_MICROSOFT_CLIENT_ID=<fill in yours here>
export DEX_MICROSOFT_TENANT=<fill in yours here>
export DEX_MS_CLIENT_SECRET=<fill in yours here>
export DEX_USER_PW_HASH=<fill in yours here>
export AUTH_SECRET=<fill in yours here>
```

Generate `AUTH_SECRET` with `openssl rand -base64 32` and `DEX_USER_PW_HASH` with
`docker run --rm ghcr.io/dexidp/dex:latest dex hash bcrypt` (see [dex/README.md](dex/README.md)).

### 3. Start the stack

```bash
source ./pathToMyVariablesFile/file.sh
make run-local-dex-ldap
```

The target starts Postgres and waits for it, starts OpenLDAP and waits for the container, renders
`dex/config/config.yaml` and starts Dex, then runs the API and UI in the foreground with `[API]` and `[UI]` prefixes.
The UI is on <http://localhost:3000>, the API on <http://localhost:8080>, and phpLDAPadmin on
<http://localhost:18081>.

Startup is healthy when the log shows `Started SupportBotApplication`, `SocketModeClient: New session is open` and
`Active login providers: [...]` listing the providers you configured.

To run without Dex and LDAP — Postgres, API and UI only — use `make run-local` instead.

### 4. Stopping and restarting

`Ctrl+C` stops only the API and UI; Postgres, OpenLDAP and Dex keep running in Docker. To stop everything:

```bash
make stop-local-dex-ldap
```

Prefer that target over tearing down `ldap/` on its own. `ldap/docker-compose.yaml` **owns** the `supportbot-ldap`
network and `dex/docker-compose.yaml` consumes it as `external`, so Dex must be removed before the network it depends
on — which is exactly the order `stop-local-dex-ldap` uses.

### Troubleshooting

**`failed to set up container networking: network <id> not found` when Dex starts.** The `supportbot-ldap` network was
recreated with a new ID while the previous `dex-dex-1` container survived, still pinned to the old one. Recreate the
container with `make -C dex down-local && make -C dex run-local`, or `docker rm -f dex-dex-1` if the first command also
fails on the missing network. Stopping with `make stop-local-dex-ldap` avoids it.

**OpenLDAP will not start again after a restart, or `ldap/bootstrap/` contains stray `sed*` files.** The osixia
entrypoint rewrites the bootstrap LDIFs in place on the bind mount, and on macOS that can leave temp files behind which
crash the next boot's `chown`. Remove them and start LDAP again:

```bash
rm -f ldap/bootstrap/sed*
make -C ldap run-local
```

If the container still will not come up, force a clean bootstrap with
`docker compose -f ldap/docker-compose.yaml down -v`. That also removes the `supportbot-ldap` network, so recreate Dex
afterwards as described above.

**`.env.local: X references ${Y}, which is not an exported environment variable`.** Your variables file was not sourced
in this shell. The Dex renderer fails loudly on unresolved references; the API and LDAP Makefiles do not — `source`
substitutes an empty string — so a missing export there surfaces later as an authentication or validation failure
instead.

**API exits with `elevate.base-url, elevate.client-id, and elevate.client-secret must either all be configured or all
be blank`.** One or two of the three resolved to empty. Export all three, or leave all three unset.

**API fails to open the Slack socket.** `SLACK_TOKEN`, `SLACK_SOCKET_TOKEN` and `SLACK_SIGNING_SECRET` have no
defaults, and empty values reach Slack as an authentication failure.

**A login button is missing from the UI.** The API registers an identity provider only when its whole credential set is
non-blank. Compare the `Active login providers: [...]` startup line against what you expected.

**A snakeyaml or fabric8 `KubeConfigUtils` stack trace during startup.** Harmless. The Kubernetes client bean is built
unconditionally and parses your `~/.kube/config`, but nothing in the local stack consumes it. Add
`export KUBECONFIG=/path/that/does/not/exist` to your variables file to silence it.

**A port is still in use after an unclean exit.** `make stop-local-api-ui` kills whatever is listening on 8080 and
3000.

## Code Guidelines

- **Unit tests**: Every module and function must be covered with unit tests.
- **Functional tests**: Required for all new features and bug fixes.
- **Integration tests**: Add integration tests when introducing or modifying any third-party service or external dependency.

> We require all code changes to be accompanied by adequate test coverage

## Contributing

### Update the README

Make sure to update the `README.md` if necessary.

### How to Raise a PR

* Fork the repository on GitHub and clone your fork locally.
* Create a feature branch, e.g: `git checkout -b feature_xyz`.
* Create your changes, run tests and linting (see the service [README.md](api/service/README.md)) and commit locally.
  * Use clear, descriptive [commit messages](https://www.conventionalcommits.org/en/v1.0.0/). Example:
    * `feat: add support for X`
    * `fix: correct bug in Y`
    * `docs: update local run guide in readme`
    * `test: add functional test for Z`
* Push them to your GitHub fork via `git push -u origin feature_xyz`. This will create the `feature_xyz` branch within your GitHub fork.
* Once your branch is pushed, open a Pull Request from your fork’s branch to the main repository’s `main` branch.

### Fill in the PR form

* Title: Short and descriptive (e.g: `fix: handle null values in function myFunc()`)
* Description: Explain what the change does and why it is needed.
* Checklist: Confirm the tests (unit/functional/integration) are included as applicable and passing.

### Submit the PR

* Maintainers will review your submission, provide feedback if needed, and merge it once it meets the project’s requirements. We commit
to review the PR within 3 working days.

## Review Skill Beta

Use the `support-bot-review` skill when reviewing support-bot PRs or local branches. The skill is in beta: treat its output as reviewer support, not as an automated decision. Reviewers are expected to understand every finding, validate it against the codebase, and make their own judgement before requesting changes or approving a PR.

The review skill requires Atlassian MCP access when a Jira ticket is supplied or inferred. Configure Atlassian MCP with a Jira read-only personal API token before using the skill for ticket-backed work. If the Atlassian MCP server is unavailable, the skill exits early and asks you to set it up rather than reviewing without ticket context.

To configure Atlassian MCP in your MCP client:

1. Create an Atlassian personal API token with only Jira read scopes.
2. Base64-encode `<email>:<api-token>`.
3. Add the Atlassian MCP server using this endpoint and authorization header:

   ```json
   {
     "atlassian": {
       "type": "remote",
       "url": "https://mcp.atlassian.com/v1/mcp",
       "headers": {
         "Authorization": "Basic <base64-email-colon-token>"
       }
     }
   }
   ```

4. Restart your MCP client if it does not hot-reload MCP configuration.
5. Verify read access by asking your agent to read a known EL issue.

The Jira cloud ID used by the review skill is `33d26043-7c2e-4336-9417-5b2f478506e7`. Do not grant write scopes for review-skill usage.

During beta, improve the review system when it produces an invalid, unclear, duplicated, or missing finding. Depending on where the issue came from, update the relevant guidance in `AGENTS.md`, `.agents/skills/support-bot-review`, or `docs/reviews/`. The goal is to iterate until the review process is reliable enough to become automated.

## Coding standards/style

* We use [PMD](https://pmd.github.io/) for linting
* Use meaningful variable and function names
* Keep functions small and focused

### Reporting Bugs/Issues

When reporting Bugs or Issues, please raise a new GitHub issue in the repository, adding a `Bug` label.
Please include whether the issue is consistently reproducible, steps to reproduce, expected behaviour and environment details.

# Contributor Code of Conduct

We are committed to fostering a welcoming and harassment-free community for everyone, regardless of age, body size, disability, ethnicity, gender identity and expression, level of experience, education, socio-economic status, nationality, personal appearance, race, religion, or sexual orientation.

## Our Standards

**Examples of positive behavior include:**
- Using welcoming and inclusive language
- Respecting different viewpoints and experiences
- Accepting constructive feedback gracefully
- Showing empathy and kindness toward others

**Examples of unacceptable behavior include:**
- The use of sexualized language or imagery
- Trolling, personal attacks, or derogatory comments
- Public or private harassment
- Publishing others’ private information without consent
- Other conduct that could reasonably be considered unprofessional

## Enforcement

Project maintainers are responsible for clarifying and enforcing this Code of Conduct.
In cases of unacceptable behavior, maintainers may take appropriate action, including warnings, temporary bans, or permanent bans.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant](https://www.contributor-covenant.org), version 2.1.
