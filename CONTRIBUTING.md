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
source ./variables.sh
make run-local-dex-ldap
```

`variables.sh` must be sourced in the **same shell** that runs `make`. The API and LDAP Makefiles `source` their
`.env.local` files, and `dex/scripts/render_config.py` resolves `${NAME}` references from the process environment.
Nothing reads `variables.sh` for you.

Alongside Java 21 and Docker you will also need Python 3 (used to render the Dex config), a Slack app with Socket Mode
enabled and a channel for it to watch (see [api/service/README.md](api/service/README.md)), and these ports free:
`3000` (UI), `8080` and `8081` (API and management), `5432` (Postgres), `389` (LDAP), `18081` (phpLDAPadmin),
`5556` and `5558` (Dex).

### 1. Create the four `.env.local` files

Each module ships an `.env.example` to copy:

```bash
cp api/.env.example  api/.env.local
cp ui/.env.example   ui/.env.local
cp dex/.env.example  dex/.env.local
cp ldap/.env.example ldap/.env.local
```

All four are gitignored. Keep secrets out of them by referencing exported variables — `SLACK_TOKEN=${SLACK_TOKEN}`
rather than a pasted token. The name inside `${...}` must match the exported name exactly.

Beyond the examples, this workflow needs:

**`api/.env.local`**

- `JWT_SECRET` — a literal of at least 256 bits (`openssl rand -base64 32`).
- `SLACK_*`, `GOOGLE_*`, `AZURE_*`, `ELEVATE_*`, `GITHUB_*` and `GITLAB_TOKEN` — as `${...}` references.
- To offer Dex on the login page, add the three values commented out in the example, matching `dex/.env.local`:

  ```bash
  DEX_CLIENT_ID=support-bot-dex
  DEX_CLIENT_SECRET=<same value as dex/.env.local>
  DEX_ISSUER_URI=http://127.0.0.1:5556
  ```

  Without them the stack still runs — the API simply does not register Dex as a login provider.
- Leave `UI_ORIGIN` unset. `api/Makefile` runs with `SPRING_PROFILES_ACTIVE=local`, where the OAuth redirect defaults
  to `http://localhost:3000`.

**`ui/.env.local`** — `BACKEND_URL=http://localhost:8080`, `NEXTAUTH_URL=http://localhost:3000` and
`AUTH_SECRET=${AUTH_SECRET}`. All three are required; the UI refuses to boot without them. The origin of
`NEXTAUTH_URL` must match the API's OAuth redirect origin above, or proxied OAuth returns 400.

**`dex/.env.local`** — enable at least one way to sign in: `DEX_ENABLE_PASSWORD_DB=true` with the `DEX_LOCAL_USER_*`
values, and/or `DEX_LDAP_ENABLED=true`. `DEX_LDAP_BIND_PW` must equal `LDAP_ADMIN_PASSWORD` in `ldap/.env.local`, and
`DEX_LDAP_HOST` should be `openldap:389` — the compose service name, since both projects share the `supportbot-ldap`
network.

**`ldap/.env.local`** — `LDAP_ADMIN_PASSWORD` and `LDAP_BOOTSTRAP_USER_PASSWORD`, the latter used to render the
`alice` and `bob` bootstrap users.

### 2. Create `variables.sh`

Create it at the repository root with every secret the `.env.local` files reference. It is gitignored — never commit
it, and prefer a shared secret store over copying the file between machines.

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
source ./variables.sh
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

**`.env.local: X references ${Y}, which is not an exported environment variable`.** `variables.sh` was not sourced in
this shell. The Dex renderer fails loudly on unresolved references; the API and LDAP Makefiles do not — `source`
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
`export KUBECONFIG=/path/that/does/not/exist` to `variables.sh` to silence it.

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
