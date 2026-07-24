# Backend Review

## Purpose

Find backend regressions in the Spring Boot service under `api/` — controllers, services, repositories, entities, migrations, transactions, validation, Slack integration, tests, and backend implementation quality.

## Change Context

Review the supplied change context: changed files, changed modules, Jira ticket context, module guidance, relevant ADRs, and other repository evidence. Use Jira context to check that backend behavior, data model changes, auth behavior, and API semantics match the intended workflow and acceptance criteria.

## Scope Detection

Run this review when the change includes files under `api/`. If no backend files changed, report `not applicable` using the review summary format.

## Common Backend Guidance

- Read root `AGENTS.md` and `api/AGENTS.md`; module guidance overrides root guidance.
- Follow existing Lombok usage patterns in `api/` rather than introducing manual boilerplate.
- Use constructor injection, `@RestController` for APIs, `@Service` for business logic, `@Repository` for data access, and `@ControllerAdvice` for global exception handling.
- Prefer maintained Spring or Spring Boot functionality over custom framework-style code for lifecycle hooks, configuration binding, validation, scheduling, transactions, conditionals, web/error handling, and resource management. If custom logic appears to solve a framework concern, check current Spring/Spring Boot documentation or another reliable current source before raising a finding.
- Use Flyway migrations named `V{version}__{description}.sql` under `api/service/src/main/resources/db/migration`, proper JPA annotations, and `@Transactional` for data modifications.
- Run or require `./gradlew spotlessApply` after Java changes; ErrorProne warnings are errors (`-Werror`), NullAway applies to `com.coreeng.supportbot`, and Checkstyle enforces `UPPER_SNAKE_CASE` enum constants.
- When adding or changing enum codes, follow `docs/enum-codes.md` and avoid orphaned enum references (see `docs/runbooks/orphaned-enum-references.md`).
- Keep Slack integration changes consistent with the established Slack gateway and handler patterns in `api/`.
- Cover every module and function with unit tests; add functional tests (`api/functional/`) for new features and bug fixes, and integration tests (`api/integration-tests/`) when introducing or modifying a third-party service or external dependency, per `CONTRIBUTING.md`.
- Apply SOLID: cohesive responsibilities, narrow interfaces, injected dependencies, and strategy/composition for extension rather than broad branching.
- Keep payloads intentional, distinguish absent/unknown from empty, add size guardrails for unbounded data, encode boundary values, and authorize every endpoint.

## Review Checklist

- Controllers and auth entry points use established current-user, role, and authorization patterns.
- Services keep business logic cohesive and use constructor-injected dependencies.
- Changes avoid custom framework-style logic when Spring or Spring Boot already provides a simpler supported feature; reviewers verify current Spring/Spring Boot functionality before making this a finding.
- Data access respects transaction boundaries and existing repository patterns.
- Migrations, JPA entities, DTOs, and database constraints stay consistent.
- Validation and error mapping produce safe, intentional API behavior.
- Async, scheduled, startup, and external-provider work is durable or coordinated where required by ADRs.
- Implementation is simple, cohesive, and consistent with established naming and module patterns.
- Tests cover external behavior, including functional tests when new functionality warrants it.
- Public and internal backend contracts preserve expected compatibility unless the change intentionally updates them.
- No debug flags, local URLs, secrets, stale comments, dead code, or unrelated cleanup are introduced.

## Approval Rules

Approve when backend behavior is correct, authorized, maintainable, consistent with module and ADR guidance, and aligned with the Jira ticket's intended behavior.

## Request Changes Rules

Request changes for missing authorization, unsafe migrations, broken transaction semantics, incorrect API/auth behavior, implementation that does not satisfy the Jira ticket's intended backend behavior, missing required behavioral tests, confusing backend design that materially increases maintenance risk, or a `fix now` finding for custom logic that duplicates likely built-in Spring functionality after the reviewer verifies the current Spring/Spring Boot feature and the built-in would reduce maintenance risk without violating repo requirements.
