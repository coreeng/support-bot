# Allow-List Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restrict SSO login to configured email addresses and/or domains, with an opt-in model (unconfigured = allow all).

**Architecture:** A new `AllowListService` checks the user's email against configured sets before any JWT is issued. Both auth paths (`OAuth2SuccessHandler` redirect flow, `OAuthExchangeService` direct exchange flow) call the service. The Helm chart renders lists into the existing ConfigMap. The UI shows a dedicated "not onboarded" page on rejection.

**Tech Stack:** Java 25 / Spring Boot 3.5.9, Next.js (TypeScript), Helm 3, JUnit 5 / Mockito, Jest / React Testing Library.

**Design doc:** `docs/plans/2026-02-13-allow-list-design.md`

---

### Task 1: AllowListService — Tests and Implementation

**Files:**
- Create: `api/service/src/main/java/com/coreeng/supportbot/security/AllowListService.java`
- Create: `api/service/src/test/java/com/coreeng/supportbot/security/AllowListServiceTest.java`
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/SecurityProperties.java`

**Step 1: Add `AllowListProperties` to `SecurityProperties`**

In `api/service/src/main/java/com/coreeng/supportbot/security/SecurityProperties.java`, add the new record and field. The file currently declares the record at line 8-9 as:

```java
public record SecurityProperties(
        JwtProperties jwt, OAuth2Properties oauth2, CorsProperties cors, TestBypassProperties testBypass) {
```

Change it to:

```java
public record SecurityProperties(
        JwtProperties jwt,
        OAuth2Properties oauth2,
        CorsProperties cors,
        TestBypassProperties testBypass,
        AllowListProperties allowList) {
```

Add a compact constructor to default `allowList` when null (for backwards compatibility with existing tests that construct `SecurityProperties` with 4 args — those will need updating too):

```java
public SecurityProperties {
    if (allowList == null) {
        allowList = new AllowListProperties(null, null);
    }
}
```

Add the new record inside the class body (after `TestBypassProperties`):

```java
public record AllowListProperties(List<String> emails, List<String> domains) {
    public AllowListProperties {
        if (emails == null) emails = List.of();
        if (domains == null) domains = List.of();
    }
}
```

Add `import java.util.List;` to the imports.

**Step 2: Write the failing test for `AllowListService`**

Create `api/service/src/test/java/com/coreeng/supportbot/security/AllowListServiceTest.java`:

```java
package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllowListServiceTest {

    private AllowListService createService(List<String> emails, List<String> domains) {
        var props = new SecurityProperties(
                new SecurityProperties.JwtProperties("unused-secret-that-is-long-enough-for-256-bits", Duration.ofHours(1)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/login"),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(emails, domains));
        return new AllowListService(props);
    }

    @Test
    void emptyLists_allowsEveryone() {
        var service = createService(List.of(), List.of());
        assertTrue(service.isAllowed("anyone@anywhere.com"));
    }

    @Test
    void nullLists_allowsEveryone() {
        var service = createService(null, null);
        assertTrue(service.isAllowed("anyone@anywhere.com"));
    }

    @Test
    void emailInList_allowed() {
        var service = createService(List.of("alice@example.com"), List.of());
        assertTrue(service.isAllowed("alice@example.com"));
    }

    @Test
    void emailNotInList_rejected() {
        var service = createService(List.of("alice@example.com"), List.of());
        assertFalse(service.isAllowed("bob@example.com"));
    }

    @Test
    void domainInList_allowed() {
        var service = createService(List.of(), List.of("example.com"));
        assertTrue(service.isAllowed("anyone@example.com"));
    }

    @Test
    void domainNotInList_rejected() {
        var service = createService(List.of(), List.of("example.com"));
        assertFalse(service.isAllowed("anyone@other.com"));
    }

    @Test
    void emailMatchTakesPriorityOverMissingDomain() {
        var service = createService(List.of("special@other.com"), List.of("example.com"));
        assertTrue(service.isAllowed("special@other.com"));
    }

    @Test
    void caseInsensitive_email() {
        var service = createService(List.of("Alice@Example.COM"), List.of());
        assertTrue(service.isAllowed("alice@example.com"));
    }

    @Test
    void caseInsensitive_domain() {
        var service = createService(List.of(), List.of("Example.COM"));
        assertTrue(service.isAllowed("user@example.com"));
    }

    @Test
    void whitespaceInConfig_trimmed() {
        var service = createService(List.of("  alice@example.com  "), List.of("  corp.io  "));
        assertTrue(service.isAllowed("alice@example.com"));
        assertTrue(service.isAllowed("bob@corp.io"));
    }

    @Test
    void blankEntries_ignored() {
        var service = createService(List.of("", "  ", "alice@example.com"), List.of());
        assertTrue(service.isAllowed("alice@example.com"));
        // Blank entries should not cause empty-list = allow-all behavior
        assertFalse(service.isAllowed("bob@other.com"));
    }

    @Test
    void onlyDomainsConfigured_emailListEffectivelyEmpty() {
        var service = createService(List.of(), List.of("allowed.com"));
        assertTrue(service.isAllowed("anyone@allowed.com"));
        assertFalse(service.isAllowed("anyone@blocked.com"));
    }

    @Test
    void onlyEmailsConfigured_domainListEffectivelyEmpty() {
        var service = createService(List.of("one@specific.com"), List.of());
        assertTrue(service.isAllowed("one@specific.com"));
        assertFalse(service.isAllowed("two@specific.com"));
    }
}
```

**Step 3: Run the test to verify it fails**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --tests "com.coreeng.supportbot.security.AllowListServiceTest" --no-daemon`

Expected: Compilation failure — `AllowListService` does not exist yet.

**Step 4: Implement `AllowListService`**

Create `api/service/src/main/java/com/coreeng/supportbot/security/AllowListService.java`:

```java
package com.coreeng.supportbot.security;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AllowListService {
    private final Set<String> emails;
    private final Set<String> domains;

    public AllowListService(SecurityProperties properties) {
        this.emails = properties.allowList().emails().stream()
                .map(e -> e.toLowerCase(Locale.ROOT).trim())
                .filter(e -> !e.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.domains = properties.allowList().domains().stream()
                .map(d -> d.toLowerCase(Locale.ROOT).trim())
                .filter(d -> !d.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (!emails.isEmpty() || !domains.isEmpty()) {
            log.info("Allow-list active: {} emails, {} domains", emails.size(), domains.size());
        }
    }

    public boolean isAllowed(String email) {
        if (emails.isEmpty() && domains.isEmpty()) {
            return true;
        }
        if (emails.contains(email)) {
            return true;
        }
        var domain = email.substring(email.indexOf('@') + 1);
        return domains.contains(domain);
    }
}
```

**Step 5: Run the test to verify it passes**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --tests "com.coreeng.supportbot.security.AllowListServiceTest" --no-daemon`

Expected: All 14 tests PASS.

**Step 6: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/service/src/main/java/com/coreeng/supportbot/security/AllowListService.java \
        api/service/src/test/java/com/coreeng/supportbot/security/AllowListServiceTest.java \
        api/service/src/main/java/com/coreeng/supportbot/security/SecurityProperties.java
git commit -m "feat(api): add AllowListService for email/domain allow-listing

Introduces AllowListService that checks user emails against configured
allow-lists. When both lists are empty, all users are allowed (opt-in).
Adds AllowListProperties to SecurityProperties."
```

---

### Task 2: UserNotAllowedException

**Files:**
- Create: `api/service/src/main/java/com/coreeng/supportbot/security/UserNotAllowedException.java`

**Step 1: Create the exception class**

Create `api/service/src/main/java/com/coreeng/supportbot/security/UserNotAllowedException.java`:

```java
package com.coreeng.supportbot.security;

public class UserNotAllowedException extends RuntimeException {
    public UserNotAllowedException(String email) {
        super("User not in allow list: " + email);
    }
}
```

**Step 2: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/service/src/main/java/com/coreeng/supportbot/security/UserNotAllowedException.java
git commit -m "feat(api): add UserNotAllowedException for allow-list rejections"
```

---

### Task 3: Integrate Allow-List into OAuth2SuccessHandler

**Files:**
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/OAuth2SuccessHandler.java:22,27-31,37-57`
- Modify: `api/service/src/test/java/com/coreeng/supportbot/security/OAuth2SuccessHandlerTest.java:43-51,62-73`

**Step 1: Write the failing test — rejected user redirects with error**

In `OAuth2SuccessHandlerTest.java`, first update `createHandler()` to accept an `AllowListService` param. The existing method at lines 43-52 constructs `SecurityProperties` with 4 args — update to 5 and pass allow-list config. Add a default overload that uses empty lists (all existing tests pass through unchanged):

Add these two helper methods (replacing the existing `createHandler()`):

```java
private OAuth2SuccessHandler createHandler(List<String> allowedEmails, List<String> allowedDomains) {
    var props = new SecurityProperties(
            new SecurityProperties.JwtProperties(TEST_SECRET, Duration.ofHours(24)),
            new SecurityProperties.OAuth2Properties("http://localhost:3000/auth/callback"),
            new SecurityProperties.CorsProperties(null),
            new SecurityProperties.TestBypassProperties(false),
            new SecurityProperties.AllowListProperties(allowedEmails, allowedDomains));
    var jwtService = new JwtService(props);
    var authCodeStore = new AuthCodeStore();
    var allowListService = new AllowListService(props);
    return new OAuth2SuccessHandler(props, jwtService, authCodeStore, teamService, supportTeamService, allowListService);
}

private OAuth2SuccessHandler createHandler() {
    return createHandler(List.of(), List.of());
}
```

Add `import java.util.List;` to the imports.

Then add the new test:

```java
@Test
void onAuthenticationSuccess_userNotInAllowList_redirectsWithError() throws Exception {
    // given — allow-list restricts to @allowed.com only
    var handler = createHandler(List.of(), List.of("allowed.com"));
    var auth = mockAuth(Map.of("email", "user@blocked.com", "name", "Blocked User"));

    // when
    handler.onAuthenticationSuccess(request, response, auth);

    // then — redirect with error, NOT with code
    verify(response).sendRedirect(argThat(url ->
            url.equals("http://localhost:3000/auth/callback?error=user_not_allowed")));
    verify(teamService, org.mockito.Mockito.never()).listTeamsByUserEmail(org.mockito.ArgumentMatchers.anyString());
}
```

**Step 2: Run the test to verify it fails**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --tests "com.coreeng.supportbot.security.OAuth2SuccessHandlerTest" --no-daemon`

Expected: Compilation failure — `OAuth2SuccessHandler` constructor doesn't accept `AllowListService` yet.

**Step 3: Modify `OAuth2SuccessHandler` to accept and use `AllowListService`**

In `api/service/src/main/java/com/coreeng/supportbot/security/OAuth2SuccessHandler.java`:

Add the field. The class uses `@RequiredArgsConstructor` so just add it after the existing fields (line 31):

```java
private final AllowListService allowListService;
```

In `onAuthenticationSuccess()`, after `var email = extractEmail(oauth2User);` (line 38) and before `var name = extractName(oauth2User);` (line 39), insert the allow-list check:

```java
if (!allowListService.isAllowed(email)) {
    log.warn("User not in allow list");
    var redirectUri = UriComponentsBuilder.fromUriString(properties.oauth2().redirectUri())
            .queryParam("error", "user_not_allowed")
            .build()
            .toUriString();
    response.sendRedirect(redirectUri);
    return;
}
```

**Step 4: Run the test to verify it passes**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --tests "com.coreeng.supportbot.security.OAuth2SuccessHandlerTest" --no-daemon`

Expected: All 5 tests PASS (4 existing + 1 new).

**Step 5: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/service/src/main/java/com/coreeng/supportbot/security/OAuth2SuccessHandler.java \
        api/service/src/test/java/com/coreeng/supportbot/security/OAuth2SuccessHandlerTest.java
git commit -m "feat(api): enforce allow-list in OAuth2SuccessHandler

Reject users not in the allow-list before generating a JWT.
Redirects to UI with ?error=user_not_allowed."
```

---

### Task 4: Integrate Allow-List into OAuthExchangeService

**Files:**
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/OAuthExchangeService.java:34-38,97-110`
- Create: `api/service/src/test/java/com/coreeng/supportbot/security/OAuthExchangeServiceTest.java`

**Step 1: Write the failing test**

Create `api/service/src/test/java/com/coreeng/supportbot/security/OAuthExchangeServiceTest.java`:

```java
package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamService;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class OAuthExchangeServiceTest {

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TeamService teamService;

    @Mock
    private SupportTeamService supportTeamService;

    private OAuthExchangeService createService(List<String> allowedEmails, List<String> allowedDomains) {
        var props = new SecurityProperties(
                new SecurityProperties.JwtProperties(
                        "test-jwt-secret-for-unit-tests-minimum-256-bits", Duration.ofHours(1)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/login"),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(allowedEmails, allowedDomains));
        var jwtService = new JwtService(props);
        var allowListService = new AllowListService(props);
        return new OAuthExchangeService(
                clientRegistrationRepository, restTemplate, jwtService, teamService, supportTeamService, allowListService);
    }

    private void mockGoogleOAuth(String email) {
        var registration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .build();
        when(clientRegistrationRepository.findByRegistrationId("google")).thenReturn(registration);

        // Mock token exchange
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("access_token", "mock-access-token"));

        // Mock user info
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("email", email, "name", "Test User"), HttpStatus.OK));
    }

    @Test
    void exchangeCodeForToken_userNotInAllowList_throws() {
        // given — only allowed.com domain
        var service = createService(List.of(), List.of("allowed.com"));
        mockGoogleOAuth("user@blocked.com");
        when(teamService.listTeamsByUserEmail("user@blocked.com")).thenReturn(ImmutableList.of());

        // when/then
        assertThrows(UserNotAllowedException.class,
                () -> service.exchangeCodeForToken("google", "auth-code", "http://localhost:3000/callback"));
    }

    @Test
    void exchangeCodeForToken_userInAllowList_succeeds() {
        // given — allowed.com domain
        var service = createService(List.of(), List.of("allowed.com"));
        mockGoogleOAuth("user@allowed.com");
        when(teamService.listTeamsByUserEmail("user@allowed.com")).thenReturn(ImmutableList.of());

        // when — should not throw
        var token = service.exchangeCodeForToken("google", "auth-code", "http://localhost:3000/callback");

        // then
        assert token != null && !token.isBlank();
    }

    @Test
    void exchangeCodeForToken_emptyAllowList_allowsAll() {
        // given — no restrictions
        var service = createService(List.of(), List.of());
        mockGoogleOAuth("anyone@anywhere.com");
        when(teamService.listTeamsByUserEmail("anyone@anywhere.com")).thenReturn(ImmutableList.of());

        // when — should not throw
        var token = service.exchangeCodeForToken("google", "auth-code", "http://localhost:3000/callback");

        // then
        assert token != null && !token.isBlank();
    }
}
```

**Step 2: Run the test to verify it fails**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --tests "com.coreeng.supportbot.security.OAuthExchangeServiceTest" --no-daemon`

Expected: Compilation failure — `OAuthExchangeService` constructor doesn't accept `AllowListService`.

**Step 3: Modify `OAuthExchangeService`**

In `api/service/src/main/java/com/coreeng/supportbot/security/OAuthExchangeService.java`:

Add the field after the existing fields (after line 38):

```java
private final AllowListService allowListService;
```

After `var email = extractEmail(userInfo);` (line 98) and before `var name = extractName(userInfo);` (line 99), insert:

```java
if (!allowListService.isAllowed(email)) {
    log.warn("User not in allow list");
    throw new UserNotAllowedException(email);
}
```

**Step 4: Run the test to verify it passes**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --tests "com.coreeng.supportbot.security.OAuthExchangeServiceTest" --no-daemon`

Expected: All 3 tests PASS.

**Step 5: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/service/src/main/java/com/coreeng/supportbot/security/OAuthExchangeService.java \
        api/service/src/test/java/com/coreeng/supportbot/security/OAuthExchangeServiceTest.java
git commit -m "feat(api): enforce allow-list in OAuthExchangeService

Throws UserNotAllowedException when user email is not in the
configured allow-list (direct OAuth exchange flow)."
```

---

### Task 5: Handle UserNotAllowedException in AuthController

**Files:**
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java:72-85`

**Step 1: Add the catch clause**

In `api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java`, the `exchangeOAuthCode` method at lines 72-85 currently has:

```java
    @PostMapping("/oauth/exchange")
    public ResponseEntity<TokenResponse> exchangeOAuthCode(@RequestBody OAuthExchangeRequest request) {
        try {
            var jwt = oauthExchangeService.exchangeCodeForToken(
                    request.provider(), request.code(), request.redirectUri());
            return ResponseEntity.ok(new TokenResponse(jwt));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid OAuth provider: {}", request.provider());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.error("OAuth exchange failed", e);
            return ResponseEntity.status(500).build();
        }
    }
```

Add a catch for `UserNotAllowedException` **before** the `IllegalStateException` catch (since `UserNotAllowedException` extends `RuntimeException`, not `IllegalStateException`, order doesn't strictly matter, but keeping it semantically before the generic catch is cleaner):

```java
    @PostMapping("/oauth/exchange")
    public ResponseEntity<TokenResponse> exchangeOAuthCode(@RequestBody OAuthExchangeRequest request) {
        try {
            var jwt = oauthExchangeService.exchangeCodeForToken(
                    request.provider(), request.code(), request.redirectUri());
            return ResponseEntity.ok(new TokenResponse(jwt));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid OAuth provider: {}", request.provider());
            return ResponseEntity.badRequest().build();
        } catch (UserNotAllowedException e) {
            log.warn("User not in allow list");
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException e) {
            log.error("OAuth exchange failed", e);
            return ResponseEntity.status(500).build();
        }
    }
```

**Step 2: Run existing tests to verify nothing breaks**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --no-daemon`

Expected: All tests PASS.

**Step 3: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java
git commit -m "feat(api): return 403 when user not in allow-list

AuthController catches UserNotAllowedException from the direct
OAuth exchange endpoint and returns HTTP 403."
```

---

### Task 6: Wire AllowListService into SecurityConfig

**Files:**
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/SecurityConfig.java:24-30,78-81`

**Step 1: Add `AllowListService` dependency and update `oauth2SuccessHandler` bean**

In `SecurityConfig.java`, add the field. The class uses `@RequiredArgsConstructor`, so add after the existing fields (after line 30):

```java
private final AllowListService allowListService;
```

Update the `oauth2SuccessHandler()` bean method (line 79-81) from:

```java
return new OAuth2SuccessHandler(properties, jwtService, authCodeStore, teamService, supportTeamService);
```

to:

```java
return new OAuth2SuccessHandler(properties, jwtService, authCodeStore, teamService, supportTeamService, allowListService);
```

**Step 2: Run all API tests to verify wiring is correct**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --no-daemon`

Expected: All tests PASS.

**Step 3: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/service/src/main/java/com/coreeng/supportbot/security/SecurityConfig.java
git commit -m "feat(api): wire AllowListService into SecurityConfig

Injects AllowListService into OAuth2SuccessHandler bean."
```

---

### Task 7: Add allow-list config to application.yaml

**Files:**
- Modify: `api/service/src/main/resources/application.yaml:21-30`

**Step 1: Add the config section**

In `application.yaml`, after the existing `security:` block (line 30, after `enabled: false`), add the allow-list properties. The `security:` block currently ends at line 30. Add after `test-bypass: enabled: false`:

```yaml
  allow-list:
    emails: ${ALLOWED_EMAILS:}
    domains: ${ALLOWED_DOMAINS:}
```

So lines 21-32 become:

```yaml
security:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 24h
  oauth2:
    redirect-uri: ${UI_ORIGIN:http://localhost:3000}/login
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:}
  test-bypass:
    enabled: false
  allow-list:
    emails: ${ALLOWED_EMAILS:}
    domains: ${ALLOWED_DOMAINS:}
```

**Step 2: Run all API tests**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --no-daemon`

Expected: All tests PASS.

**Step 3: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/service/src/main/resources/application.yaml
git commit -m "feat(api): add allow-list config to application.yaml

Supports ALLOWED_EMAILS and ALLOWED_DOMAINS env vars for local dev.
Empty defaults preserve current allow-all behavior."
```

---

### Task 8: Helm Chart — ConfigMap and Values

**Files:**
- Modify: `api/k8s/service/values.yaml`
- Modify: `api/k8s/service/templates/configmap.yaml`

**Step 1: Add `auth` section to `values.yaml`**

In `api/k8s/service/values.yaml`, add the `auth` section before the `env` section. Insert before line 91 (`# Environment variables`):

```yaml
# Auth allow-list configuration (optional).
# When both lists are empty, all SSO-authenticated users are allowed.
# Rendered into the ConfigMap application.yaml under security.allow-list.
auth:
  allowedDomains: []
  allowedEmails: []

```

**Step 2: Update `configmap.yaml` to render allow-list**

In `api/k8s/service/templates/configmap.yaml`, the current content is:

```yaml
{{- if .Values.configMap.create -}}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Values.configMap.name | default (include "support-bot.fullname" .) }}
  labels:
    {{- include "support-bot.labels" . | nindent 4 }}
  {{- with .Values.configMap.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
data:
#trivy:ignore:AVD-KSV-0109 ConfigMap contains placeholder refs; real secrets are provided via K8s Secrets
  application.yaml: |
    {{- toYaml .Values.configMap.config | nindent 4 }}
{{- end }}
```

Replace the `data:` section (lines 12-16) with:

```yaml
data:
#trivy:ignore:AVD-KSV-0109 ConfigMap contains placeholder refs; real secrets are provided via K8s Secrets
  application.yaml: |
    {{- toYaml .Values.configMap.config | nindent 4 }}
    {{- if or .Values.auth.allowedEmails .Values.auth.allowedDomains }}
    security:
      allow-list:
        {{- if .Values.auth.allowedEmails }}
        emails:
        {{- range .Values.auth.allowedEmails }}
          - {{ . | quote }}
        {{- end }}
        {{- end }}
        {{- if .Values.auth.allowedDomains }}
        domains:
        {{- range .Values.auth.allowedDomains }}
          - {{ . | quote }}
        {{- end }}
        {{- end }}
    {{- end }}
{{- end }}
```

Note: Using `range` + `quote` instead of `toYaml` ensures each entry is properly quoted (handles entries that might look like numbers or contain special chars).

**Step 3: Verify Helm template renders correctly**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && helm template test-release k8s/service --set auth.allowedDomains[0]=example.com --set auth.allowedEmails[0]=user@other.com | grep -A 20 "application.yaml"`

Expected: The ConfigMap should contain:

```yaml
security:
  allow-list:
    emails:
      - "user@other.com"
    domains:
      - "example.com"
```

Also verify empty lists produce no allow-list block:

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && helm template test-release k8s/service | grep -A 5 "allow-list"`

Expected: No output (allow-list section not rendered).

**Step 4: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/k8s/service/values.yaml api/k8s/service/templates/configmap.yaml
git commit -m "feat(chart): add auth allow-list to Helm chart

Adds auth.allowedEmails and auth.allowedDomains values that render
into the ConfigMap application.yaml. Empty lists = no restriction."
```

---

### Task 9: UI — Handle 403 in Callback Routes

**Files:**
- Modify: `ui/src/app/api/auth/callback/google/route.ts:31-35`
- Modify: `ui/src/app/api/auth/callback/azure/route.ts:31-35`

**Step 1: Update Google callback route**

In `ui/src/app/api/auth/callback/google/route.ts`, the current error handling at lines 31-35 is:

```typescript
  if (!response.ok) {
    console.error("OAuth code exchange failed:", response.status);
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", "Token exchange failed");
    return NextResponse.redirect(loginUrl);
  }
```

Replace with:

```typescript
  if (!response.ok) {
    console.error("OAuth code exchange failed:", response.status);
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    if (response.status === 403) {
      loginUrl.searchParams.set("error", "user_not_allowed");
    } else {
      loginUrl.searchParams.set("error", "Token exchange failed");
    }
    return NextResponse.redirect(loginUrl);
  }
```

**Step 2: Update Azure callback route**

In `ui/src/app/api/auth/callback/azure/route.ts`, apply the identical change at lines 31-35:

```typescript
  if (!response.ok) {
    console.error("OAuth code exchange failed:", response.status);
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    if (response.status === 403) {
      loginUrl.searchParams.set("error", "user_not_allowed");
    } else {
      loginUrl.searchParams.set("error", "Token exchange failed");
    }
    return NextResponse.redirect(loginUrl);
  }
```

**Step 3: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add ui/src/app/api/auth/callback/google/route.ts \
        ui/src/app/api/auth/callback/azure/route.ts
git commit -m "feat(ui): handle 403 from backend as user_not_allowed

When the backend returns 403 during OAuth exchange, redirect to
the login page with error=user_not_allowed instead of the generic
'Token exchange failed' message."
```

---

### Task 10: UI — "Not Onboarded" Page in Login

**Files:**
- Modify: `ui/src/app/login/page.tsx:169-183`
- Modify: `ui/src/app/login/__tests__/page.test.tsx`

**Step 1: Write the failing test**

In `ui/src/app/login/__tests__/page.test.tsx`, add a new test in the "Basic rendering" section (after the existing error test at line 81):

```typescript
  it('shows not-onboarded message for user_not_allowed error', () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams('error=user_not_allowed') as any)

    render(<LoginPage />)

    expect(screen.getByText('Access Restricted')).toBeInTheDocument()
    expect(screen.getByText(/not been onboarded/)).toBeInTheDocument()
    expect(screen.queryByText('Authentication Error')).not.toBeInTheDocument()
  })
```

**Step 2: Run the test to verify it fails**

Run: `cd /Users/pyo/Work/coreeng/support-bot/ui && npm test -- --testPathPattern="login/__tests__/page.test" --no-coverage`

Expected: FAIL — the page currently shows "Authentication Error" for all errors.

**Step 3: Update the login page**

In `ui/src/app/login/page.tsx`, the error rendering block at lines 169-183 currently is:

```tsx
  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="max-w-md w-full space-y-8 p-8 text-center">
          <h2 className="text-2xl font-bold text-red-600">Authentication Error</h2>
          <p className="text-gray-600">{error}</p>
          <button
            onClick={() => router.replace("/login")}
            className="text-blue-600 hover:underline"
          >
            Try again
          </button>
        </div>
      </div>
    );
  }
```

Replace with:

```tsx
  if (error === "user_not_allowed") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="max-w-md w-full space-y-8 p-8 text-center">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Access Restricted</h2>
          <p className="text-gray-600 dark:text-gray-400">
            You have successfully authenticated but your user has not been
            onboarded to the Support UI.
          </p>
          <p className="text-gray-500 dark:text-gray-500 text-sm">
            Please contact your administrator for access.
          </p>
          <button
            onClick={() => router.replace("/login")}
            className="text-blue-600 hover:underline"
          >
            Back to login
          </button>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="max-w-md w-full space-y-8 p-8 text-center">
          <h2 className="text-2xl font-bold text-red-600">Authentication Error</h2>
          <p className="text-gray-600">{error}</p>
          <button
            onClick={() => router.replace("/login")}
            className="text-blue-600 hover:underline"
          >
            Try again
          </button>
        </div>
      </div>
    );
  }
```

**Step 4: Run the test to verify it passes**

Run: `cd /Users/pyo/Work/coreeng/support-bot/ui && npm test -- --testPathPattern="login/__tests__/page.test" --no-coverage`

Expected: All tests PASS.

**Step 5: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add ui/src/app/login/page.tsx ui/src/app/login/__tests__/page.test.tsx
git commit -m "feat(ui): show 'not onboarded' page for allow-list rejection

When error=user_not_allowed, display a friendly message explaining
the user has authenticated but is not onboarded, instead of the
generic authentication error."
```

---

### Task 11: Documentation

**Files:**
- Modify: `api/README.md`
- Modify: `api/k8s/service/README.md`

**Step 1: Update `api/README.md`**

Add an "Authentication" section after the existing content. The file currently ends at line 18. Append:

```markdown

## Authentication

### Allow-List

Access to the Support UI can be restricted to specific email addresses and/or email domains.

**Environment Variables** (for local development):

| Variable | Description | Example |
|----------|-------------|---------|
| `ALLOWED_EMAILS` | Comma-separated list of allowed email addresses | `alice@example.com,bob@corp.io` |
| `ALLOWED_DOMAINS` | Comma-separated list of allowed email domains | `example.com,corp.io` |

**Behavior:**
- When **both lists are empty or unset**, all SSO-authenticated users are allowed (default, current behavior).
- When **either list is configured**, only users whose email matches an entry in `ALLOWED_EMAILS` or whose email domain matches an entry in `ALLOWED_DOMAINS` can log in.
- Users not in the allow-list see an "Access Restricted" page after SSO authentication.
- Email and domain matching is case-insensitive.

For Kubernetes deployments, see the Helm chart's `auth.allowedEmails` and `auth.allowedDomains` values.
```

**Step 2: Update `api/k8s/service/README.md`**

Add an "Auth Allow-List" section before the "Health and Metrics" section at line 90. Insert before that line:

```markdown
## Auth Allow-List

Restrict SSO login to specific email addresses and/or domains:

```yaml
auth:
  allowedDomains:
    - example.com
    - corp.io
  allowedEmails:
    - external.contractor@partner.com
    - special.user@other.org
```

- `auth.allowedEmails` (list): Email addresses allowed to log in. Default `[]`.
- `auth.allowedDomains` (list): Email domains allowed to log in (any user `@domain`). Default `[]`.

When both lists are empty (the default), all SSO-authenticated users are allowed. When either list is configured, only matching users can log in. Users not in the allow-list see an "Access Restricted" page.

These values are rendered into the ConfigMap `application.yaml` under `security.allow-list`.

```

**Step 3: Commit**

```bash
cd /Users/pyo/Work/coreeng/support-bot
git add api/README.md api/k8s/service/README.md
git commit -m "docs: document allow-list configuration

Adds authentication allow-list docs to api/README.md (env vars for
local dev) and Helm chart README (values for Kubernetes deployment)."
```

---

### Task 12: Full Test Suite Verification

**Step 1: Run all API tests**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && ./gradlew :service:test --no-daemon`

Expected: All tests PASS.

**Step 2: Run all UI tests**

Run: `cd /Users/pyo/Work/coreeng/support-bot/ui && npm test -- --no-coverage`

Expected: All tests PASS.

**Step 3: Run Helm lint**

Run: `cd /Users/pyo/Work/coreeng/support-bot/api && helm lint k8s/service`

Expected: No errors.
