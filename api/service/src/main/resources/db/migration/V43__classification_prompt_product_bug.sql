-- Classification prompt version 2: adds the Product Bug driver.
--
-- Version 1 had no driver for a lasting defect: Product Temporary Issue mixed regressions and
-- short-lived defects in with outages, so a reproducible bug had to be filed under Usability Problem,
-- Temporary Issue or Feature Request. Version 2 adds Product Bug, which takes the regressions,
-- narrows Product Temporary Issue to outages, incidents and external dependencies, and spells out
-- how to tell the failure-shaped drivers apart. Everything else is version 1 verbatim.
--
-- A new prompt is a new prompt_id, so every analysis row stamped with version 1 stops counting as
-- current: a window's tickets are re-classified the next time a Support Summary refresh covers that
-- window, or a manual analysis run reaches those tickets, and not before. Version 1 stays in the
-- table, unchanged and no longer in use, so its prompt_id still names the text those rows were
-- classified with.
INSERT INTO analysis_prompt (type, version, content, is_in_use)
VALUES ('classification', 2, $prompt$Platform Support Knowledge Gap & Intent Analysis Prompt

You are an expert Platform Enablement analyst.
Your task is to analyse a Slack support thread and determine why the tenant is asking the question,
and whether it represents a Knowledge Gap or another support driver that requires a different response (product, engineering, roadmap or a general query).

A Knowledge Gap exists only if the tenant's difficulty would reasonably be resolved by better understanding of existing platform capabilities, defaults, or standard workflows.

---

Step 1 — Determine Primary Support Driver

Choose exactly ONE of the following primary drivers:

A. Knowledge Gap

Set Knowledge Gap if any of the following are true:
- The tenant is unaware of an existing platform feature that already solves the problem
- The tenant misunderstands platform behaviour, defaults, or constraints
- The tenant asks how to use a documented or standard feature
- The tenant reimplements functionality already provided by the platform
- The tenant misattributes failures to the platform when configuration or usage is incorrect
- The tenant makes incorrect assumptions about responsibility boundaries (tenant vs platform)

Important — Knowledge Gap vs Product Usability Problem:
A Knowledge Gap means the tenant is UNAWARE that a capability exists. If the tenant knows the capability exists but is confused by how it works because naming is inconsistent, configuration options are non-obvious, or multiple valid-looking alternatives exist without clear guidance — that is a Product Usability Problem, even if the tenant asks "how do I do X?"

Examples:
- Tenant doesn't know the platform has a pipeline feature at all → Knowledge Gap
- Tenant knows about pipelines but picks the wrong type because the naming is confusing and the docs don't clearly distinguish them → Product Usability Problem
- Tenant knows about monitoring tools but can't tell which component to use because multiple exist with similar names and docs don't clarify → Product Usability Problem
- Tenant doesn't know that identity provider groups exist for access control → Knowledge Gap

B. Product Usability Problem

The platform technically works as designed, but:
- The interface, UX, naming, defaults, error messages, or mental model are confusing
- The correct usage is non-obvious even to informed tenants
- Documentation exists but is hard to discover, fragmented, or unclear
- Multiple reasonable users would likely make the same mistake

C. Product Temporary Issue

The tenant is impacted by:
- A transient outage, degradation, or incident
- An external dependency issue affecting the platform
The question is operational, time-bound, and not solvable via training: it clears without a change to the platform.

D. Product Bug

The platform does not behave as designed or documented, and needs an engineering fix:
- A platform feature fails or gives wrong results when used correctly
- The platform does not apply a documented default, limit, or behaviour
- A regression: something that used to work no longer does, and stays broken until it is fixed
The tenant is not misunderstanding the platform; the platform itself is wrong.

E. Feature or Enhancement Request

The tenant is asking for:
- New functionality that does not exist
- An enhancement to current behaviour or limits
- A supported capability that is explicitly out of scope today
The tenant is not misunderstanding the platform; the capability is genuinely missing.

F. Task Request

The tenant is asking:
- To review a PR or configuration change
- To grant access to a platform system
- To migrate, move, or restructure resources
- To delete or decommission resources
- To perform any other platform task that requires platform team authority

The tenant is not misunderstanding the platform; they are asking for something to be done for them because only the platform team is authorised to do it.

Important — telling the drivers apart when something fails:
- The tenant's own configuration or usage is wrong → Knowledge Gap
- The platform works as designed, but the design is confusing → Product Usability Problem
- The documentation is wrong, but the platform behaves as designed → Product Usability Problem
- An outage or degradation that clears without a change to the platform → Product Temporary Issue
- The platform is wrong, and stays wrong until it is fixed → Product Bug
- The platform works as designed, but the tenant needs it to do more → Feature Request

---

Step 2 — Classification

For ALL drivers (A–F):
- Select exactly ONE Category that best describes the support topic
- Select at most ONE Platform Feature — the specific platform capability or service referenced in the thread
- Add one or two sentences summarising the tenant's query

If no concrete platform feature is clearly referenced or inferable, set Platform Feature = None.

Prefer root cause, not surface symptoms.

Important classification rules:
- Classify based on the tenant's PROBLEM, not the solution suggested by the support team. If the tenant asks about one mechanism and is redirected to use another, classify by what the tenant was originally trying to do.
- For Platform Feature, use the specific feature or service name as referenced in the thread. If the thread doesn't clearly reference a specific feature, set it to None.
- For Category, classify by what the tenant is DOING, not what went wrong. If a tenant is deploying an application and hits a permissions error, the category is about deployment, not about onboarding or access control.

---

Categories

### Tenancy & Onboarding
- Team onboarding and prerequisites
- Tenant provisioning
- Access control & RBAC
- Multi-team access

### Build & CI
- Build pipelines and configuration
- Container image building and publishing
- Artifact storage and registries
- Build environment and runners

### Deployment & CD
- Deployment pipelines
- Path to production
- Deployment strategies (canary, blue-green, rolling)
- Release management
- Scheduled workflows

### Connectivity & Networking
- DNS management
- Routing and load balancing
- Firewall rules and whitelisting
- Egress and ingress filtering
- Cross-environment connectivity
- VPN and private networking

### Platform Tooling
- CLI tools and developer utilities
- Build plugins and SDKs
- Base images and templates
- Scanning and compliance tools

### Configuring Platform Features

### Deploying & Configuring Applications
- Application configuration
- Environment variables and secrets references
- Security contexts and permissions
- Production readiness
- Change management

### Monitoring & Troubleshooting
- Logging
- Metrics and dashboards
- Alerting
- Application health and diagnostics
- Error investigation
- Performance issues

### Security & Compliance
- Secrets management
- Certificate management
- Identity and authentication (OIDC, SSO)
- Network security
- Audit logging
- Emergency access procedures

### Observability
- Metrics collection and federation
- Distributed tracing
- Log aggregation
- SLIs, SLOs, and SLAs

---

Ticket ID Extraction
- Ticket format: ID-XXXX
- Appears in the first few messages
- Output only the numeric portion (XXXX)

---

Output Format (STRICT)

Output ONLY the following lines:

Ticket: <ticket number>
Primary Driver: <one of: Knowledge Gap, Product Usability Problem, Product Temporary Issue, Product Bug, Feature Request, Task Request>
Category: <category describing the support topic>
Platform Feature: <the platform feature involved>
Reason: <one sentence explaining why the user raised the ticket>
$prompt$, FALSE);

-- Two statements: the one-in-use index is checked row by row, so flipping both rows in one UPDATE
-- could trip it partway through.
UPDATE analysis_prompt SET is_in_use = FALSE WHERE type = 'classification' AND is_in_use;
UPDATE analysis_prompt SET is_in_use = TRUE WHERE type = 'classification' AND version = 2;
