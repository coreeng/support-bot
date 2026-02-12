# NextAuth-Managed OAuth Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move OAuth flow from backend-managed to NextAuth-managed so the browser never directly communicates with the backend API, keeping it internal.

**Architecture:** NextAuth handles OAuth with Google/Azure directly. After successful OAuth, NextAuth calls the backend server-to-server to exchange the OAuth ID token for a backend JWT and fetch user data. The backend API remains internal (not exposed to browsers).

**Tech Stack:** NextAuth v5, Next.js 16, Google OAuth, Azure AD OAuth, Spring Boot (backend)

---

## Current Flow (Problem)

```
Browser                          Next.js Server                    Backend API                 Google/Azure
   │                                   │                              │                            │
   │─── Click "Login" ────────────────>│                              │                            │
   │                                   │                              │                            │
   │<── 307 Redirect to Backend ───────│                              │                            │
   │    http://backend:8080/oauth2/... │                              │                            │
   │                                   │                              │                            │
   │─────────────────────────────────────────────────────────────────>│                            │
   │                                   │              (BACKEND EXPOSED TO BROWSER)                 │
   │                                   │                              │                            │
   │                                   │                              │─── OAuth redirect ────────>│
   │                                   │                              │<── Auth code ──────────────│
   │                                   │                              │                            │
   │<───────────────────────── Redirect with code ────────────────────│                            │
   │    /login?code=XXX                │                              │                            │
   │                                   │                              │                            │
   │─── Forward code ─────────────────>│                              │                            │
   │                                   │─── Exchange code ───────────>│                            │
   │                                   │<── JWT token ────────────────│                            │
   │                                   │                              │                            │
   │<── Session established ───────────│                              │                            │
```

**Problem:** Backend must be publicly accessible for browser redirects (lines 4-6).

---

## Target Flow (Solution)

```
Browser                          Next.js Server                    Backend API                 Google/Azure
   │                                   │                              │                            │
   │─── Click "Login" ────────────────>│                              │                            │
   │                                   │                              │                            │
   │<── 307 Redirect to Google ────────│                              │                            │
   │    (NextAuth handles OAuth)       │                              │                            │
   │                                   │                              │                            │
   │───────────────────────────────────────────────────────────────────────────────────────────────>│
   │                                   │                              │                            │
   │<──────────────────────────────────────────────────────── Auth code ───────────────────────────│
   │    /api/auth/callback/google      │                              │                            │
   │                                   │                              │                            │
   │─── Forward code ─────────────────>│                              │                            │
   │                                   │─── Exchange with Google ─────────────────────────────────>│
   │                                   │<── Google tokens (id_token) ──────────────────────────────│
   │                                   │                              │                            │
   │                                   │─── POST /auth/oidc ─────────>│                            │
   │                                   │    { provider, id_token }    │  (server-to-server)        │
   │                                   │                              │                            │
   │                                   │<── { token: "backend-jwt" }──│                            │
   │                                   │                              │                            │
   │<── Session established ───────────│                              │                            │
```

**Key difference:** Browser only talks to Next.js and OAuth providers. Backend stays internal.

---

## Backend Analysis (Current State)

### Existing Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/auth/token` | POST | Exchange auth code for JWT (current flow) |
| `/auth/me` | GET | Get current user info |
| `/auth/logout` | POST | Logout |
| `/oauth2/authorization/{provider}` | GET | Spring OAuth2 redirect (to be deprecated) |

### Key Backend Files

| File | Purpose |
|------|---------|
| `api/.../security/AuthController.java` | Auth endpoints |
| `api/.../security/OAuth2SuccessHandler.java` | Post-OAuth processing (team lookup, role computation, JWT generation) |
| `api/.../security/JwtService.java` | JWT generation and validation |
| `api/.../teams/TeamService.java` | Team lookup by email |
| `api/.../teams/SupportTeamService.java` | Leadership/support membership checks |

### Logic to Reuse

The `OAuth2SuccessHandler` contains the core logic we need:
- `extractEmail()` - Extract email from OAuth claims
- `extractName()` - Extract name from OAuth claims
- `computeRoles()` - Compute roles based on teams
- Team lookup via `TeamService.listTeamsByUserEmail()`
- JWT generation via `JwtService.generateToken()`

---

## Part 1: Backend Changes

### Task 1: Create OIDC Token Exchange Endpoint

**Files:**
- Modify: `api/service/src/main/java/com/coreeng/supportbot/security/AuthController.java`
- Create: `api/service/src/main/java/com/coreeng/supportbot/security/OidcTokenService.java`

**Step 1: Create OidcTokenService for ID token validation**

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OidcTokenService {
    private static final Pattern leadershipPattern = Pattern.compile("leadership", Pattern.CASE_INSENSITIVE);
    private static final Pattern supportPattern = Pattern.compile("support", Pattern.CASE_INSENSITIVE);
    private static final Pattern escalationPattern = Pattern.compile("escalation", Pattern.CASE_INSENSITIVE);

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String AZURE_ISSUER_TEMPLATE = "https://login.microsoftonline.com/%s/v2.0";

    private final SecurityProperties properties;
    private final JwtService jwtService;
    private final TeamService teamService;
    private final SupportTeamService supportTeamService;

    /**
     * Validate an OIDC ID token and exchange it for a backend JWT.
     */
    public Optional<String> exchangeIdToken(String provider, String idToken) {
        try {
            var issuerUrl = getIssuerUrl(provider);
            if (issuerUrl == null) {
                log.warn("Unknown provider: {}", provider);
                return Optional.empty();
            }

            // Decode and validate the ID token using provider's JWK set
            JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUrl);
            Jwt jwt = decoder.decode(idToken);

            var email = extractEmail(jwt);
            var name = extractName(jwt);

            log.info("OIDC token validated for user via {}", provider);

            var teams = teamService.listTeamsByUserEmail(email);
            var roles = computeRoles(email, teams);

            var principal = new UserPrincipal(email, name, teams, roles);
            var backendJwt = jwtService.generateToken(principal);

            return Optional.of(backendJwt);
        } catch (JwtException e) {
            log.warn("ID token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String getIssuerUrl(String provider) {
        if ("google".equalsIgnoreCase(provider)) {
            return GOOGLE_ISSUER;
        }
        if ("azure".equalsIgnoreCase(provider)) {
            var tenantId = properties.oauth2().azure().tenantId();
            if (tenantId == null || tenantId.isBlank()) {
                log.error("Azure tenant ID not configured");
                return null;
            }
            return String.format(AZURE_ISSUER_TEMPLATE, tenantId);
        }
        return null;
    }

    private String extractEmail(Jwt jwt) {
        var email = jwt.getClaimAsString("email");
        if (email != null) {
            return email.toLowerCase(Locale.ROOT);
        }
        var preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null) {
            return preferredUsername.toLowerCase(Locale.ROOT);
        }
        throw new IllegalStateException("Unable to extract email from ID token");
    }

    private String extractName(Jwt jwt) {
        var name = jwt.getClaimAsString("name");
        if (name != null) {
            return name;
        }
        var givenName = jwt.getClaimAsString("given_name");
        var familyName = jwt.getClaimAsString("family_name");
        if (givenName != null || familyName != null) {
            return ((givenName != null ? givenName : "") + " " + (familyName != null ? familyName : "")).trim();
        }
        return extractEmail(jwt);
    }

    private ImmutableList<Role> computeRoles(String email, ImmutableList<Team> teams) {
        var roles = ImmutableList.<Role>builder();
        roles.add(Role.user);

        if (computeIsLeadership(email, teams)) {
            roles.add(Role.leadership);
        }
        if (computeIsSupportEngineer(email, teams)) {
            roles.add(Role.supportEngineer);
        }
        if (computeIsEscalation(teams)) {
            roles.add(Role.escalation);
        }

        return roles.build();
    }

    private boolean computeIsLeadership(String email, ImmutableList<Team> teams) {
        return supportTeamService.isLeadershipMemberByUserEmail(email) || hasTeamType(teams, leadershipPattern);
    }

    private boolean computeIsSupportEngineer(String email, ImmutableList<Team> teams) {
        return supportTeamService.isMemberByUserEmail(email) || hasTeamType(teams, supportPattern);
    }

    private boolean computeIsEscalation(ImmutableList<Team> teams) {
        return hasTeamType(teams, escalationPattern);
    }

    private boolean hasTeamType(ImmutableList<Team> teams, Pattern pattern) {
        return teams.stream()
                .flatMap(t -> t.types().stream())
                .map(TeamType::name)
                .anyMatch(type -> pattern.matcher(type).find());
    }
}
```

**Step 2: Add endpoint to AuthController.java**

Add to the existing `AuthController.java`:

```java
// Add field
private final OidcTokenService oidcTokenService;

// Add endpoint
@PostMapping("/oidc")
public ResponseEntity<TokenResponse> exchangeOidcToken(@RequestBody OidcTokenRequest request) {
    if (request.provider() == null || request.idToken() == null) {
        return ResponseEntity.badRequest().build();
    }

    var jwtOpt = oidcTokenService.exchangeIdToken(request.provider(), request.idToken());
    if (jwtOpt.isEmpty()) {
        log.warn("OIDC token exchange failed for provider: {}", request.provider());
        return ResponseEntity.status(401).build();
    }

    return ResponseEntity.ok(new TokenResponse(jwtOpt.get()));
}

// Add record
public record OidcTokenRequest(String provider, String idToken) {}
```

**Step 3: Update SecurityConfig.java**

Add `/auth/oidc` to public endpoints:

```java
.requestMatchers("/auth/token", "/auth/oidc").permitAll()
```

**Step 4: Verify/add dependency**

Ensure `build.gradle` has:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```

**Step 5: Commit**

```bash
git add api/service/src/main/java/com/coreeng/supportbot/security/
git commit -m "feat(api): add OIDC token exchange endpoint for NextAuth integration"
```

---

## Part 2: UI Changes

### Task 2: Update Environment Configuration

**Files:**
- Modify: `ui/.env.example`
- Modify: `ui/src/instrumentation.ts`

**Step 1: Update .env.example**

```bash
# =============================================================================
# Required Environment Variables
# =============================================================================

# Internal backend API URL (server-side only, never exposed to browser)
BACKEND_URL=http://localhost:8080

# This app's public URL (for OAuth callbacks)
NEXTAUTH_URL=http://localhost:3000

# Auth secret for JWT encryption
# Generate with: openssl rand -base64 32
AUTH_SECRET=

# =============================================================================
# OAuth Provider Credentials (at least one required)
# =============================================================================

# Google OAuth (from Google Cloud Console)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# Azure AD OAuth (from Azure Portal)
# AZURE_AD_CLIENT_ID=
# AZURE_AD_CLIENT_SECRET=
# AZURE_AD_TENANT_ID=
```

**Step 2: Update instrumentation.ts**

Add OAuth provider validation:

```typescript
function validateOAuthProviders(): string | null {
  const hasGoogle = process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET;
  const hasAzure = process.env.AZURE_AD_CLIENT_ID &&
                   process.env.AZURE_AD_CLIENT_SECRET &&
                   process.env.AZURE_AD_TENANT_ID;

  if (!hasGoogle && !hasAzure) {
    return "At least one OAuth provider required (GOOGLE_CLIENT_ID/SECRET or AZURE_AD_*)";
  }
  return null;
}

export async function register() {
  const missing: string[] = [];

  for (const name of REQUIRED_ENV_VARS) {
    if (!getEnvVar(name)) {
      const alias = ENV_ALIASES[name];
      missing.push(alias ? `${name} (or ${alias})` : name);
    }
  }

  const oauthError = validateOAuthProviders();
  if (oauthError) {
    missing.push(oauthError);
  }

  // ... rest unchanged
}
```

**Step 3: Commit**

```bash
git add ui/.env.example ui/src/instrumentation.ts
git commit -m "chore(ui): add OAuth provider env vars configuration"
```

---

### Task 3: Add OIDC Token Exchange Function

**Files:**
- Modify: `ui/src/lib/api/auth-api.ts`

**Step 1: Add exchangeOidcToken function**

```typescript
/**
 * Exchange OAuth provider ID token for backend JWT.
 * Called server-side after NextAuth completes OAuth.
 */
export async function exchangeOidcToken(
  provider: "google" | "azure",
  idToken: string
): Promise<{ token: string } | null> {
  const response = await publicFetch("/auth/oidc", {
    method: "POST",
    body: JSON.stringify({ provider, idToken }),
  });

  if (!response.ok) {
    console.error(`OIDC token exchange failed for ${provider}:`, response.status);
    return null;
  }

  return response.json();
}
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/auth-api.ts
git commit -m "feat(ui): add OIDC token exchange function"
```

---

### Task 4: Add NextAuth Google Provider

**Files:**
- Modify: `ui/src/auth.config.ts`

**Step 1: Update auth.config.ts**

```typescript
import type { NextAuthConfig } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import Google from "next-auth/providers/google";
import { exchangeCodeForToken, exchangeOidcToken, fetchUserWithToken } from "@/lib/api/auth-api";

// ... existing type declarations ...

export const authConfig: NextAuthConfig = {
  providers: [
    // New: Google OAuth handled by NextAuth
    ...(process.env.GOOGLE_CLIENT_ID ? [
      Google({
        clientId: process.env.GOOGLE_CLIENT_ID,
        clientSecret: process.env.GOOGLE_CLIENT_SECRET!,
      }),
    ] : []),

    // Legacy: Backend-managed OAuth (keep during migration)
    Credentials({
      id: "backend-oauth",
      name: "Backend OAuth",
      credentials: {
        code: { label: "Auth Code", type: "text" },
      },
      async authorize(credentials) {
        // ... existing implementation unchanged ...
      },
    }),
  ],

  callbacks: {
    async jwt({ token, user, account }) {
      // Handle Google OAuth (new flow)
      if (account?.provider === "google" && account.id_token) {
        const backendToken = await exchangeOidcToken("google", account.id_token);
        if (!backendToken) {
          throw new Error("Backend token exchange failed");
        }

        const userData = await fetchUserWithToken(backendToken.token);
        if (!userData) {
          throw new Error("User fetch failed");
        }

        token.accessToken = backendToken.token;
        token.email = userData.email as string;
        token.name = userData.name as string;
        token.teams = (userData.teams as AuthTeam[]) || [];
        token.roles = (userData.roles as string[]) || [];
        return token;
      }

      // Handle legacy backend-oauth flow
      if (user?.accessToken) {
        token.accessToken = user.accessToken as string;
        token.email = user.email!;
        token.name = user.name!;
        token.teams = user.teams as AuthTeam[];
        token.roles = user.roles as string[];
      }

      return token;
    },

    async session({ session, token }) {
      // ... existing implementation unchanged ...
    },
  },

  // ... rest unchanged ...
};
```

**Step 2: Commit**

```bash
git add ui/src/auth.config.ts
git commit -m "feat(ui): add NextAuth Google provider with backend OIDC exchange"
```

---

### Task 5: Add NextAuth Azure Provider

**Files:**
- Modify: `ui/src/auth.config.ts`

**Step 1: Add Azure provider**

```typescript
import AzureAD from "next-auth/providers/azure-ad";

// In providers array:
...(process.env.AZURE_AD_CLIENT_ID ? [
  AzureAD({
    clientId: process.env.AZURE_AD_CLIENT_ID,
    clientSecret: process.env.AZURE_AD_CLIENT_SECRET!,
    tenantId: process.env.AZURE_AD_TENANT_ID!,
  }),
] : []),

// In jwt callback, add alongside Google:
if (account?.provider === "azure-ad" && account.id_token) {
  const backendToken = await exchangeOidcToken("azure", account.id_token);
  if (!backendToken) {
    throw new Error("Backend token exchange failed");
  }

  const userData = await fetchUserWithToken(backendToken.token);
  if (!userData) {
    throw new Error("User fetch failed");
  }

  token.accessToken = backendToken.token;
  token.email = userData.email as string;
  token.name = userData.name as string;
  token.teams = (userData.teams as AuthTeam[]) || [];
  token.roles = (userData.roles as string[]) || [];
  return token;
}
```

**Step 2: Commit**

```bash
git add ui/src/auth.config.ts
git commit -m "feat(ui): add NextAuth Azure AD provider"
```

---

### Task 6: Update Login Page

**Files:**
- Modify: `ui/src/app/login/page.tsx`

**Step 1: Update handleLogin to use NextAuth signIn**

```typescript
import { signIn } from "next-auth/react";

const handleLogin = (provider: "google" | "azure") => {
  // Use NextAuth's built-in signIn
  const providerId = provider === "azure" ? "azure-ad" : "google";
  signIn(providerId, { callbackUrl });
};
```

**Step 2: Keep legacy code handling for migration period**

The `?code=` handling can remain temporarily for the migration period.

**Step 3: Commit**

```bash
git add ui/src/app/login/page.tsx
git commit -m "refactor(ui): use NextAuth signIn for OAuth"
```

---

### Task 7: Cleanup (Post-Migration)

After confirming the new flow works:

**Files to modify:**
- `ui/src/app/api/oauth/` - Delete directory
- `ui/src/lib/api/auth-api.ts` - Remove `getOAuthUrl`, `exchangeCodeForToken`
- `ui/src/auth.config.ts` - Remove `backend-oauth` Credentials provider
- `ui/src/app/login/page.tsx` - Remove `?code=` handling

---

## OAuth Callback URLs

Configure in Google Cloud Console / Azure Portal:

| Provider | Development | Production |
|----------|-------------|------------|
| Google | `http://localhost:3000/api/auth/callback/google` | `https://ui.example.com/api/auth/callback/google` |
| Azure | `http://localhost:3000/api/auth/callback/azure-ad` | `https://ui.example.com/api/auth/callback/azure-ad` |

---

## Migration Strategy

1. **Phase 1:** Deploy backend with new `/auth/oidc` endpoint (backward compatible)
2. **Phase 2:** Deploy UI with NextAuth providers (supports both flows simultaneously)
3. **Phase 3:** Monitor and verify new flow works
4. **Phase 4:** Remove legacy code (Task 7)

---

## Summary

| Component | Before | After |
|-----------|--------|-------|
| **Browser → OAuth** | Via Backend (exposed) | Direct to Google/Azure |
| **Token Exchange** | Backend OAuth flow | NextAuth + backend `/auth/oidc` |
| **Backend Exposure** | Public | Internal only |
| **New Backend Endpoint** | N/A | `POST /auth/oidc` |
| **New Backend Service** | N/A | `OidcTokenService.java` |
