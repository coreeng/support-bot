# User Allow-List for SSO Login

## Problem

Currently, any user who can authenticate via Google or Azure SSO receives a valid JWT and gains access to the Support UI. We need to restrict login to an explicit set of allowed email addresses and/or email domains, configured per environment.

## Design Decisions

- **Opt-in**: When both lists are empty/unconfigured, all SSO users are allowed (current behavior preserved).
- **Enforcement in API**: The check happens server-side before any JWT is issued. Two auth paths exist (OAuth2SuccessHandler redirect flow, OAuthExchangeService direct exchange flow) and both are guarded.
- **Configuration via ConfigMap**: Helm renders the allow-list into the existing ConfigMap `application.yaml` (mounted at `/app/config`). This avoids CSV env var size limits and keeps lists auditable. Env vars (`ALLOWED_EMAILS`, `ALLOWED_DOMAINS`) are still supported for local dev.
- **UI feedback**: A distinct "not onboarded" page is shown instead of the generic auth error.

## Configuration Chain

```
Helm values.yaml             →  ConfigMap application.yaml    →  Spring Boot merge     →  AllowListService

auth:                            security:                       SecurityProperties        isAllowed(email)
  allowedDomains:                  allow-list:                     .allowList()
    - example.com                    domains:                        .emails()
  allowedEmails:                       - example.com                .domains()
    - user@other.com                 emails:
                                       - user@other.com
```

For local development, env vars work too:

```
ALLOWED_EMAILS=a@x.com,b@y.com  →  application.yaml: ${ALLOWED_EMAILS:}  →  List<String>
ALLOWED_DOMAINS=x.com,y.com     →  application.yaml: ${ALLOWED_DOMAINS:}  →  List<String>
```

## Auth Flow (with allow-list)

```
User → SSO Provider → API (OAuth2SuccessHandler / OAuthExchangeService)
                          │
                    AllowListService.isAllowed(email)?
                      ├─ yes → generate JWT, redirect with code/token (existing flow)
                      └─ no  → redirect with ?error=user_not_allowed (no JWT generated)
                                  │
                          UI callback route passes error through
                                  │
                          /login?error=user_not_allowed
                                  │
                          Login page shows "not onboarded" message
```

## API Changes

### SecurityProperties.java

Add `AllowListProperties` record:

```java
public record AllowListProperties(List<String> emails, List<String> domains) {
    public AllowListProperties {
        if (emails == null) emails = List.of();
        if (domains == null) domains = List.of();
    }
}
```

Add `AllowListProperties allowList` field to the main record.

### AllowListService.java (new)

```java
@Service
public class AllowListService {
    private final Set<String> emails;   // normalized lowercase, trimmed
    private final Set<String> domains;  // normalized lowercase, trimmed

    public AllowListService(SecurityProperties properties) { /* normalize + collect to Set */ }

    public boolean isAllowed(String email) {
        if (emails.isEmpty() && domains.isEmpty()) return true;  // opt-in
        if (emails.contains(email)) return true;
        String domain = email.substring(email.indexOf('@') + 1);
        return domains.contains(domain);
    }
}
```

### UserNotAllowedException.java (new)

Runtime exception to distinguish allow-list rejection from other auth failures.

### OAuth2SuccessHandler.java

After `extractEmail()`, before `computeRoles()`:
- Call `allowListService.isAllowed(email)`
- If rejected: redirect to `{redirectUri}?error=user_not_allowed`, return (no JWT)

### OAuthExchangeService.java

After `extractEmail()`, before `computeRoles()`:
- Call `allowListService.isAllowed(email)`
- If rejected: throw `UserNotAllowedException`

### AuthController.java

In `/auth/oauth/exchange`, catch `UserNotAllowedException` → return 403.

### SecurityConfig.java

Wire `AllowListService` into the `OAuth2SuccessHandler` bean constructor.

### application.yaml

```yaml
security:
  allow-list:
    emails: ${ALLOWED_EMAILS:}
    domains: ${ALLOWED_DOMAINS:}
```

## Helm Chart Changes

### values.yaml

```yaml
auth:
  allowedDomains: []
  allowedEmails: []
```

### templates/configmap.yaml

Append `security.allow-list` block when either list is non-empty, after the existing `configMap.config` rendering.

### templates/deployment.yaml

No changes needed.

## UI Changes

### callback/google/route.ts and callback/azure/route.ts

When backend returns 403, redirect to `/login?error=user_not_allowed`.

### login/page.tsx

When `error === "user_not_allowed"`, render:

> **Access Restricted**
>
> You have successfully authenticated but your user has not been onboarded to the Support UI.
>
> Please contact your administrator for access.
>
> [Back to login]

## Documentation

### api/k8s/service/README.md

Add "Auth Allow-List" section documenting `auth.allowedEmails` and `auth.allowedDomains` Helm values.

### api/README.md

Add "Authentication" section documenting `ALLOWED_EMAILS` and `ALLOWED_DOMAINS` env vars, and the opt-in behavior.

## Test Plan

| Test | Type | What it verifies |
|------|------|-----------------|
| AllowListServiceTest | API unit | empty lists → allow all; email match; domain match; rejection; case insensitivity; whitespace; email-only vs domain-only |
| OAuth2SuccessHandlerTest (new cases) | API unit | rejected user → redirect with ?error=user_not_allowed, no JWT generated |
| OAuthExchangeServiceTest (new) | API unit | rejected user → throws UserNotAllowedException; allowed user → returns token |
| Login page test (new case) | UI unit | error=user_not_allowed → renders "not onboarded" message |

## Files Changed

| File | Change |
|------|--------|
| `api/.../security/SecurityProperties.java` | Add AllowListProperties record + field |
| `api/.../security/AllowListService.java` | **New** |
| `api/.../security/UserNotAllowedException.java` | **New** |
| `api/.../security/OAuth2SuccessHandler.java` | Add allow-list check |
| `api/.../security/OAuthExchangeService.java` | Add allow-list check |
| `api/.../security/AuthController.java` | Catch UserNotAllowedException → 403 |
| `api/.../security/SecurityConfig.java` | Wire AllowListService |
| `api/.../resources/application.yaml` | Add allow-list section |
| `api/k8s/service/values.yaml` | Add auth section |
| `api/k8s/service/templates/configmap.yaml` | Render allow-list into ConfigMap |
| `api/k8s/service/README.md` | Document auth allow-list values |
| `api/README.md` | Document env vars and behavior |
| `api/.../security/AllowListServiceTest.java` | **New** |
| `api/.../security/OAuth2SuccessHandlerTest.java` | Add rejection test |
| `api/.../security/OAuthExchangeServiceTest.java` | **New** |
| `ui/src/app/api/auth/callback/google/route.ts` | Handle 403 |
| `ui/src/app/api/auth/callback/azure/route.ts` | Handle 403 |
| `ui/src/app/login/page.tsx` | "Not onboarded" rendering |
| `ui/src/app/login/__tests__/page.test.tsx` | Test "not onboarded" rendering |
