# Backend-Proxied OAuth Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep all OAuth credentials in the backend while preventing direct browser-to-backend communication by having the UI server proxy OAuth requests.

**Architecture:** The UI server acts as a proxy for OAuth. It fetches the OAuth authorization URL from the backend (server-to-server), redirects the browser to Google/Azure, receives the callback, and forwards the auth code to the backend for token exchange. OAuth credentials never leave the backend.

**Tech Stack:** Next.js 16, NextAuth v5, Spring Boot, Google OAuth, Azure AD OAuth

---

## Target Flow

```
Browser                          UI Server                         Backend                    Google/Azure
   │                                   │                              │                            │
   │─── Click "Login" ────────────────>│                              │                            │
   │                                   │                              │                            │
   │                                   │─── GET /auth/oauth-url ─────>│                            │
   │                                   │    ?provider=google          │                            │
   │                                   │    &redirect_uri=UI_CALLBACK │                            │
   │                                   │                              │                            │
   │                                   │<── { url: "https://..." } ───│                            │
   │                                   │                              │                            │
   │<── 307 Redirect to Google ────────│                              │                            │
   │                                   │                              │                            │
   │───────────────────────────────────────────────────────────────────────────────────────────────>│
   │                                   │                              │                            │
   │<── Redirect with code ─────────────────────────────────────────────────────────────────────────│
   │    /api/auth/callback?code=XXX    │                              │                            │
   │                                   │                              │                            │
   │─── code in URL ──────────────────>│                              │                            │
   │                                   │─── POST /auth/oauth/exchange>│                            │
   │                                   │    { provider, code,         │                            │
   │                                   │      redirect_uri }          │                            │
   │                                   │                              │─── Exchange code ─────────>│
   │                                   │                              │<── Tokens ─────────────────│
   │                                   │                              │                            │
   │                                   │<── { token: "backend-jwt" }──│                            │
   │                                   │                              │                            │
   │<── Session established ───────────│                              │                            │
```

**Key properties:**
- OAuth credentials (CLIENT_ID, CLIENT_SECRET) stay in **backend only**
- Browser never talks directly to backend
- UI server proxies all OAuth requests server-to-server
- Backend validates auth codes and exchanges them with OAuth providers

---

## Part 1: Backend Changes

### Task 1: Create OAuth URL Generation Endpoint

**Files:**
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java`

**Step 1: Add OAuth URL endpoint**

Add to `AuthController.java`:

```java
/**
 * Generate OAuth authorization URL for a provider.
 * The UI calls this server-to-server, then redirects the browser.
 */
@GetMapping("/oauth-url")
public ResponseEntity<OAuthUrlResponse> getOAuthUrl(
        @RequestParam String provider,
        @RequestParam String redirectUri) {

    if (!"google".equalsIgnoreCase(provider) && !"azure".equalsIgnoreCase(provider)) {
        return ResponseEntity.badRequest().build();
    }

    // Build the OAuth authorization URL
    String authUrl = buildAuthorizationUrl(provider, redirectUri);
    if (authUrl == null) {
        return ResponseEntity.status(500).build();
    }

    return ResponseEntity.ok(new OAuthUrlResponse(authUrl));
}

private String buildAuthorizationUrl(String provider, String redirectUri) {
    // This will be implemented using Spring's OAuth2 client registration
    // to get the authorization URI with proper client_id and scopes
    return null; // Placeholder
}

public record OAuthUrlResponse(String url) {}
```

**Step 2: Commit placeholder**

```bash
git add api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java
git commit -m "wip(api): add OAuth URL endpoint placeholder"
```

---

### Task 2: Implement OAuth URL Builder Service

**Files:**
- Create: `api/service/src/main/java/com/coreeng/supportbot/security/OAuthUrlService.java`
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java`

**Step 1: Create OAuthUrlService**

```java
package com.coreeng.supportbot.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthUrlService {
    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * Build the OAuth authorization URL for a provider.
     *
     * @param provider "google" or "azure"
     * @param redirectUri the UI's callback URL
     * @return the authorization URL to redirect the browser to
     */
    public Optional<String> buildAuthorizationUrl(String provider, String redirectUri) {
        String registrationId = provider.toLowerCase();
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);

        if (registration == null) {
            log.warn("No OAuth2 registration found for provider: {}", provider);
            return Optional.empty();
        }

        String state = generateState();

        String authUrl = registration.getProviderDetails().getAuthorizationUri()
                + "?client_id=" + encode(registration.getClientId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(String.join(" ", registration.getScopes()))
                + "&state=" + encode(state);

        return Optional.of(authUrl);
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
```

**Step 2: Update AuthController to use service**

```java
private final OAuthUrlService oAuthUrlService;

@GetMapping("/oauth-url")
public ResponseEntity<OAuthUrlResponse> getOAuthUrl(
        @RequestParam String provider,
        @RequestParam String redirectUri) {

    var urlOpt = oAuthUrlService.buildAuthorizationUrl(provider, redirectUri);
    if (urlOpt.isEmpty()) {
        return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok(new OAuthUrlResponse(urlOpt.get()));
}
```

**Step 3: Commit**

```bash
git add api/service/src/main/java/com/coreeng/supportbot/security/
git commit -m "feat(api): add OAuth URL generation service"
```

---

### Task 3: Create OAuth Code Exchange Endpoint

**Files:**
- Create: `api/service/src/main/java/com/coreeng/supportbot/security/OAuthExchangeService.java`
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java`

**Step 1: Create OAuthExchangeService**

```java
package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthExchangeService {
    private static final Pattern LEADERSHIP_PATTERN = Pattern.compile("leadership", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_PATTERN = Pattern.compile("support", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCALATION_PATTERN = Pattern.compile("escalation", Pattern.CASE_INSENSITIVE);

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final JwtService jwtService;
    private final TeamService teamService;
    private final SupportTeamService supportTeamService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Exchange an OAuth authorization code for a backend JWT.
     *
     * @param provider "google" or "azure"
     * @param code the authorization code from the OAuth callback
     * @param redirectUri the same redirect_uri used in the authorization request
     * @return backend JWT if exchange succeeds
     */
    public Optional<String> exchangeCode(String provider, String code, String redirectUri) {
        String registrationId = provider.toLowerCase();
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);

        if (registration == null) {
            log.warn("No OAuth2 registration found for provider: {}", provider);
            return Optional.empty();
        }

        try {
            // Exchange code for tokens with provider
            Map<String, Object> tokenResponse = exchangeCodeForTokens(registration, code, redirectUri);
            if (tokenResponse == null) {
                return Optional.empty();
            }

            // Get user info from provider
            String accessToken = (String) tokenResponse.get("access_token");
            Map<String, Object> userInfo = fetchUserInfo(registration, accessToken);
            if (userInfo == null) {
                return Optional.empty();
            }

            // Extract user details
            String email = extractEmail(userInfo);
            String name = extractName(userInfo, email);

            log.info("OAuth exchange successful for user via {}", provider);

            // Look up teams and compute roles
            var teams = teamService.listTeamsByUserEmail(email);
            var roles = computeRoles(email, teams);

            // Generate backend JWT
            var principal = new UserPrincipal(email, name, teams, roles);
            return Optional.of(jwtService.generateToken(principal));

        } catch (Exception e) {
            log.error("OAuth code exchange failed for {}: {}", provider, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeForTokens(
            ClientRegistration registration, String code, String redirectUri) {

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        var body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("client_id", registration.getClientId());
        body.add("client_secret", registration.getClientSecret());

        var request = new HttpEntity<>(body, headers);

        try {
            return restTemplate.postForObject(
                    registration.getProviderDetails().getTokenUri(),
                    request,
                    Map.class);
        } catch (Exception e) {
            log.error("Token exchange failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(ClientRegistration registration, String accessToken) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        var request = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(
                    registration.getProviderDetails().getUserInfoEndpoint().getUri(),
                    org.springframework.http.HttpMethod.GET,
                    request,
                    Map.class).getBody();
        } catch (Exception e) {
            log.error("User info fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractEmail(Map<String, Object> userInfo) {
        Object email = userInfo.get("email");
        if (email != null) {
            return email.toString().toLowerCase(Locale.ROOT);
        }
        Object preferredUsername = userInfo.get("preferred_username");
        if (preferredUsername != null) {
            return preferredUsername.toString().toLowerCase(Locale.ROOT);
        }
        throw new IllegalStateException("Unable to extract email from user info");
    }

    private String extractName(Map<String, Object> userInfo, String fallbackEmail) {
        Object name = userInfo.get("name");
        if (name != null && !name.toString().isBlank()) {
            return name.toString();
        }
        Object givenName = userInfo.get("given_name");
        Object familyName = userInfo.get("family_name");
        if (givenName != null || familyName != null) {
            String fullName = ((givenName != null ? givenName : "") + " " +
                              (familyName != null ? familyName : "")).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
        }
        return fallbackEmail;
    }

    private ImmutableList<Role> computeRoles(String email, ImmutableList<Team> teams) {
        var roles = ImmutableList.<Role>builder();
        roles.add(Role.user);

        if (supportTeamService.isLeadershipMemberByUserEmail(email) ||
            hasTeamType(teams, LEADERSHIP_PATTERN)) {
            roles.add(Role.leadership);
        }
        if (supportTeamService.isMemberByUserEmail(email) ||
            hasTeamType(teams, SUPPORT_PATTERN)) {
            roles.add(Role.supportEngineer);
        }
        if (hasTeamType(teams, ESCALATION_PATTERN)) {
            roles.add(Role.escalation);
        }

        return roles.build();
    }

    private boolean hasTeamType(ImmutableList<Team> teams, Pattern pattern) {
        return teams.stream()
                .flatMap(t -> t.types().stream())
                .map(TeamType::name)
                .anyMatch(type -> pattern.matcher(type).find());
    }
}
```

**Step 2: Add exchange endpoint to AuthController**

```java
private final OAuthExchangeService oAuthExchangeService;

/**
 * Exchange OAuth authorization code for backend JWT.
 * The UI calls this after receiving the callback from the OAuth provider.
 */
@PostMapping("/oauth/exchange")
public ResponseEntity<TokenResponse> exchangeOAuthCode(@RequestBody OAuthExchangeRequest request) {
    if (request.provider() == null || request.code() == null || request.redirectUri() == null) {
        return ResponseEntity.badRequest().build();
    }

    var jwtOpt = oAuthExchangeService.exchangeCode(
            request.provider(), request.code(), request.redirectUri());

    if (jwtOpt.isEmpty()) {
        log.warn("OAuth code exchange failed for provider: {}", request.provider());
        return ResponseEntity.status(401).build();
    }

    return ResponseEntity.ok(new TokenResponse(jwtOpt.get()));
}

public record OAuthExchangeRequest(String provider, String code, String redirectUri) {}
```

**Step 3: Update SecurityConfig to allow new endpoints**

In `SecurityConfig.java`, update:

```java
.requestMatchers("/auth/token", "/auth/oauth-url", "/auth/oauth/exchange")
.permitAll()
```

**Step 4: Commit**

```bash
git add api/service/src/main/java/com/coreeng/supportbot/security/
git commit -m "feat(api): add OAuth code exchange endpoint"
```

---

## Part 2: UI Changes

### Task 4: Create Backend OAuth API Functions

**Files:**
- Modify: `ui/src/lib/api/auth-api.ts`

**Step 1: Add functions to call backend OAuth endpoints**

```typescript
/**
 * Get OAuth authorization URL from backend.
 * Called server-to-server - credentials stay in backend.
 */
export async function getBackendOAuthUrl(
  provider: "google" | "azure",
  redirectUri: string
): Promise<{ url: string } | null> {
  const params = new URLSearchParams({
    provider,
    redirectUri,
  });

  const response = await publicFetch(`/auth/oauth-url?${params.toString()}`);

  if (!response.ok) {
    console.error("Failed to get OAuth URL:", response.status);
    return null;
  }

  return response.json();
}

/**
 * Exchange OAuth authorization code for backend JWT.
 * Called server-to-server after OAuth callback.
 */
export async function exchangeOAuthCode(
  provider: "google" | "azure",
  code: string,
  redirectUri: string
): Promise<{ token: string } | null> {
  const response = await publicFetch("/auth/oauth/exchange", {
    method: "POST",
    body: JSON.stringify({ provider, code, redirectUri }),
  });

  if (!response.ok) {
    console.error("OAuth code exchange failed:", response.status);
    return null;
  }

  return response.json();
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/auth-api.ts
git commit -m "feat(ui): add backend OAuth API functions"
```

---

### Task 5: Update OAuth Route to Proxy Through Backend

**Files:**
- Modify: `ui/src/app/api/oauth/[provider]/route.ts`

**Step 1: Update route to get URL from backend**

```typescript
import { NextRequest, NextResponse } from "next/server";
import { getBackendOAuthUrl } from "@/lib/api/auth-api";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;

  if (provider !== "google" && provider !== "azure") {
    return NextResponse.json({ error: "Invalid provider" }, { status: 400 });
  }

  // Build the callback URL for this UI
  const callbackUrl = new URL(
    `/api/auth/callback/${provider}`,
    process.env.NEXTAUTH_URL
  ).toString();

  // Get OAuth URL from backend (server-to-server)
  const result = await getBackendOAuthUrl(provider, callbackUrl);

  if (!result) {
    return NextResponse.json(
      { error: "Failed to get OAuth URL" },
      { status: 500 }
    );
  }

  return NextResponse.redirect(result.url);
}
```

**Step 2: Commit**

```bash
git add ui/src/app/api/oauth/
git commit -m "feat(ui): proxy OAuth initiation through backend"
```

---

### Task 6: Create OAuth Callback Route

**Files:**
- Create: `ui/src/app/api/auth/callback/[provider]/route.ts`

**Step 1: Create callback route**

```typescript
import { NextRequest, NextResponse } from "next/server";
import { exchangeOAuthCode } from "@/lib/api/auth-api";
import { signIn } from "next-auth/react";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ provider: string }> }
) {
  const { provider } = await params;
  const searchParams = request.nextUrl.searchParams;
  const code = searchParams.get("code");
  const error = searchParams.get("error");

  if (error) {
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", error);
    return NextResponse.redirect(loginUrl);
  }

  if (!code) {
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", "No authorization code received");
    return NextResponse.redirect(loginUrl);
  }

  // Build the same callback URL used in the authorization request
  const callbackUrl = new URL(
    `/api/auth/callback/${provider}`,
    process.env.NEXTAUTH_URL
  ).toString();

  // Exchange code for token via backend (server-to-server)
  const result = await exchangeOAuthCode(
    provider as "google" | "azure",
    code,
    callbackUrl
  );

  if (!result) {
    const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
    loginUrl.searchParams.set("error", "Token exchange failed");
    return NextResponse.redirect(loginUrl);
  }

  // Redirect to login page with the backend JWT
  // The login page will use NextAuth's Credentials provider to establish session
  const loginUrl = new URL("/login", process.env.NEXTAUTH_URL);
  loginUrl.searchParams.set("token", result.token);
  return NextResponse.redirect(loginUrl);
}
```

**Step 2: Commit**

```bash
git add ui/src/app/api/auth/callback/
git commit -m "feat(ui): add OAuth callback route for backend exchange"
```

---

### Task 7: Update Login Page to Handle Token

**Files:**
- Modify: `ui/src/app/login/page.tsx`

**Step 1: Handle token parameter (from callback)**

The login page already handles `?code=` for the legacy flow. Update to also handle `?token=`:

```typescript
// In useEffect, add token handling:
const token = searchParams.get("token");

useEffect(() => {
  if (isLoading) return;

  // If we have a token directly (from new OAuth flow), establish session
  if (token) {
    signIn("backend-token", {
      token,
      callbackUrl,
      redirect: true,
    });
    return;
  }

  // Legacy: If we have a code, exchange it via NextAuth
  if (code) {
    signIn("backend-oauth", {
      code,
      callbackUrl,
      redirect: true,
    });
    return;
  }

  // If already authenticated, redirect
  if (isAuthenticated) {
    router.replace(callbackUrl);
  }
}, [token, code, isAuthenticated, isLoading, callbackUrl, router]);
```

**Step 2: Commit**

```bash
git add ui/src/app/login/page.tsx
git commit -m "feat(ui): handle token from OAuth callback"
```

---

### Task 8: Add Token-Based Credentials Provider

**Files:**
- Modify: `ui/src/auth.config.ts`

**Step 1: Add backend-token provider**

```typescript
// Add a new Credentials provider that accepts a token directly
Credentials({
  id: "backend-token",
  name: "Backend Token",
  credentials: {
    token: { label: "Token", type: "text" },
  },
  async authorize(credentials) {
    const token = credentials?.token as string;
    if (!token) return null;

    try {
      // Token is already a valid backend JWT, just fetch user data
      const userData = await fetchUserWithToken(token);
      if (!userData) {
        console.error("User fetch failed");
        return null;
      }

      return {
        id: userData.email as string,
        email: userData.email as string,
        name: userData.name as string,
        teams: (userData.teams as Array<{ label: string; code: string; types: string[] }>).map((t) => ({
          ...t,
          name: t.code || t.label,
        })),
        roles: userData.roles as string[],
        accessToken: token,
      };
    } catch (error) {
      console.error("Authorization error:", error);
      return null;
    }
  },
}),
```

**Step 2: Commit**

```bash
git add ui/src/auth.config.ts
git commit -m "feat(ui): add backend-token credentials provider"
```

---

## Summary

### What Changed

**Backend:**
- `GET /auth/oauth-url` - Returns OAuth authorization URL (credentials stay in backend)
- `POST /auth/oauth/exchange` - Exchanges auth code for backend JWT (credentials stay in backend)
- `OAuthUrlService` - Builds OAuth URLs using Spring's ClientRegistrationRepository
- `OAuthExchangeService` - Exchanges codes with OAuth providers

**UI:**
- `/api/oauth/[provider]` - Now fetches OAuth URL from backend, then redirects browser
- `/api/auth/callback/[provider]` - Receives callback, exchanges code via backend
- `auth.config.ts` - Added `backend-token` provider for direct token auth

### Properties

| Property | Value |
|----------|-------|
| OAuth credentials location | Backend only |
| Browser talks to backend | Never |
| Token exchange | Backend to OAuth provider |
| UI server role | Proxy only |

### Environment Variables

No changes needed. OAuth credentials remain in backend's `application.yaml`:
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`
