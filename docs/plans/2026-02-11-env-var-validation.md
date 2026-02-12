# Environment Variable Simplification & Validation

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce env vars from 6 to 3, proxy OAuth through middleware, add startup validation.

**Architecture:** Route all OAuth through `/backend` proxy so browser never needs direct API access. Validate required env vars at server startup via `instrumentation.ts`.

**Tech Stack:** Next.js 16 instrumentation hook, TypeScript

---

## Summary

**Before (6 confusing env vars with fallbacks):**
- `NEXT_PUBLIC_API_URL`, `API_BACKEND_URL`, `BACKEND_URL`, `API_BACKEND_PUBLIC_URL`
- `NEXTAUTH_URL`, `NEXTAUTH_SECRET`
- Brittle hostname derivation logic in login page

**After (3 explicit env vars, no fallbacks):**
- `AUTH_SECRET` - JWT encryption (with `NEXTAUTH_SECRET` legacy alias)
- `NEXTAUTH_URL` - callback base URL
- `BACKEND_URL` - internal API URL (all traffic proxied through middleware)

**Key change:** OAuth redirects go through `/backend/oauth2/authorization/{provider}` instead of directly to API. Browser never needs to know the API's public URL.

---

## Prerequisites: Backend Configuration

Before deploying, update the Spring Boot API's OAuth redirect URIs to point through the UI proxy:

```yaml
# application.yaml (or environment config)
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            redirect-uri: "${UI_BASE_URL}/backend/oauth2/callback/google"
          azure:
            redirect-uri: "${UI_BASE_URL}/backend/oauth2/callback/azure"
```

Where `UI_BASE_URL` is `https://ui.chatbot.com` (or your UI's public URL).

Also register these redirect URIs in Google Cloud Console and Azure AD app registration.

---

### Task 1: Create Startup Validation

**Files:**
- Create: `ui/src/instrumentation.ts`

**Step 1: Create the instrumentation file**

```typescript
/**
 * Next.js instrumentation hook for server startup validation.
 * @see https://nextjs.org/docs/app/building-your-application/optimizing/instrumentation
 */

const REQUIRED_ENV_VARS = [
  "AUTH_SECRET",
  "NEXTAUTH_URL",
  "BACKEND_URL",
] as const;

// Legacy aliases (checked if primary is missing)
const ENV_ALIASES: Record<string, string> = {
  AUTH_SECRET: "NEXTAUTH_SECRET",
};

function getEnvVar(name: string): string | undefined {
  const value = process.env[name];
  if (value) return value;

  // Check for legacy alias
  const alias = ENV_ALIASES[name];
  if (alias) {
    const aliasValue = process.env[alias];
    if (aliasValue) {
      console.warn(
        `  Using deprecated ${alias}, please rename to ${name}`
      );
      return aliasValue;
    }
  }

  return undefined;
}

export async function register() {
  // Only validate on Node.js server runtime (not edge, not build)
  if (process.env.NEXT_RUNTIME !== "nodejs") {
    return;
  }

  const missing: string[] = [];

  for (const name of REQUIRED_ENV_VARS) {
    if (!getEnvVar(name)) {
      const alias = ENV_ALIASES[name];
      missing.push(alias ? `${name} (or ${alias})` : name);
    }
  }

  if (missing.length > 0) {
    console.error("\n" + "=".repeat(60));
    console.error("Missing required environment variables:");
    console.error("=".repeat(60));
    for (const name of missing) {
      console.error(`   - ${name}`);
    }
    console.error("");
    console.error("Copy .env.example to .env.local and fill in values:");
    console.error("   cp .env.example .env.local");
    console.error("");
    console.error("Generate AUTH_SECRET with:");
    console.error("   openssl rand -base64 32");
    console.error("=".repeat(60) + "\n");
    process.exit(1);
  }
}
```

**Step 2: Commit**

```bash
git add ui/src/instrumentation.ts
git commit -m "feat(ui): add startup validation for required env vars"
```

---

### Task 2: Simplify login page (remove derivation, use proxy)

**Files:**
- Modify: `ui/src/app/login/page.tsx`

**Step 1: Remove getApiUrl function and update handleLogin**

Delete lines 8-21 (the `getApiUrl` function).

Then modify `handleLogin` (around line 52) to use the `/backend` proxy:

Find:
```typescript
  const handleLogin = (provider: "google" | "azure") => {
    const apiUrl = getApiUrl();
    const currentUrl = new URL(window.location.href);

    // Store callback URL for after OAuth
    const returnUrl = callbackUrl || "/";

    // Build OAuth URL that returns to this page with the code
    const loginPageUrl = `${currentUrl.origin}/login?callbackUrl=${encodeURIComponent(returnUrl)}`;

    // Check if we're in an iframe
    const isInIframe = (() => {
      try {
        return window.self !== window.top;
      } catch {
        return true;
      }
    })();

    // Build the OAuth URL with proper redirect
    const oauthUrl = `${apiUrl}/oauth2/authorization/${provider}`;
```

Replace with:
```typescript
  const handleLogin = (provider: "google" | "azure") => {
    // OAuth goes through /backend proxy - browser never needs direct API access
    const oauthUrl = `/backend/oauth2/authorization/${provider}`;

    // Check if we're in an iframe
    const isInIframe = (() => {
      try {
        return window.self !== window.top;
      } catch {
        return true;
      }
    })();
```

Also remove the now-unused variables `currentUrl`, `returnUrl`, and `loginPageUrl` if they're not used elsewhere in the function.

**Step 2: Commit**

```bash
git add ui/src/app/login/page.tsx
git commit -m "refactor(ui): route OAuth through /backend proxy, remove URL derivation"
```

---

### Task 3: Simplify api-url-provider.ts

**Files:**
- Modify: `ui/src/lib/api/providers/api-url-provider.ts`

**Step 1: Replace entire file content**

```typescript
/**
 * API URL for server-side communication with the backend.
 * Must be set via BACKEND_URL environment variable.
 */
export const API_BACKEND_URL = process.env.BACKEND_URL!;
```

**Step 2: Commit**

```bash
git add ui/src/lib/api/providers/api-url-provider.ts
git commit -m "refactor(ui): simplify API URL provider, remove dead code and fallbacks"
```

---

### Task 4: Update auth.config.ts

**Files:**
- Modify: `ui/src/auth.config.ts`

**Step 1: Replace getApiUrl function (lines 16-31)**

Find:
```typescript
// Runtime API URL derivation (matches existing token.ts logic)
function getApiUrl(): string {
  if (typeof window !== "undefined") {
    const hostname = window.location.hostname;
    const protocol = window.location.protocol;

    if (hostname === "localhost") {
      return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
    }

    const apiHostname = hostname.replace("-ui", "");
    return `${protocol}//${apiHostname}`;
  }

  // Server-side: use environment variable
  return process.env.API_BACKEND_URL || process.env.BACKEND_URL || "http://localhost:8080";
}
```

Replace with:
```typescript
// Server-side API URL (this file only runs on server)
function getApiUrl(): string {
  return process.env.BACKEND_URL!;
}
```

**Step 2: Commit**

```bash
git add ui/src/auth.config.ts
git commit -m "refactor(ui): simplify auth config, use BACKEND_URL directly"
```

---

### Task 5: Update middleware.ts

**Files:**
- Modify: `ui/src/middleware.ts`

**Step 1: Remove fallback from BACKEND_URL usage (line 15)**

Find:
```typescript
    const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
```

Replace with:
```typescript
    const backendUrl = process.env.BACKEND_URL!;
```

**Step 2: Commit**

```bash
git add ui/src/middleware.ts
git commit -m "refactor(ui): remove fallback from middleware backend URL"
```

---

### Task 6: Update next.config.ts

**Files:**
- Modify: `ui/next.config.ts`

**Step 1: Simplify allowedOrigins (lines 18-21)**

Find:
```typescript
      allowedOrigins: process.env.NEXTAUTH_URL
        ? [new URL(process.env.NEXTAUTH_URL).host]
        : ["localhost:3000"],
```

Replace with:
```typescript
      allowedOrigins: [new URL(process.env.NEXTAUTH_URL!).host],
```

**Step 2: Commit**

```bash
git add ui/next.config.ts
git commit -m "refactor(ui): remove fallback from next.config allowedOrigins"
```

---

### Task 7: Update .env.example

**Files:**
- Modify: `ui/.env.example`

**Step 1: Replace entire file content**

```bash
# =============================================================================
# Required Environment Variables
# =============================================================================
# All variables below MUST be set. The app will not start without them.
# Copy this file to .env.local and fill in values.

# Internal backend API URL
# Used by Next.js server for API calls (middleware proxy, auth token exchange)
# In Kubernetes: http://api-service.bot-api.svc.cluster.local:8080
BACKEND_URL=http://localhost:8080

# NextAuth base URL (this app's public URL)
# Used for OAuth callback redirects
# In production: https://ui.chatbot.com
NEXTAUTH_URL=http://localhost:3000

# Auth secret for JWT encryption
# Generate with: openssl rand -base64 32
# Also accepts NEXTAUTH_SECRET as legacy alias
AUTH_SECRET=
```

**Step 2: Commit**

```bash
git add ui/.env.example
git commit -m "docs(ui): update .env.example with simplified 3-var config"
```

---

### Task 8: Update .env.local for development

**Files:**
- Modify: `ui/.env.local`

**Step 1: Replace content with simplified config**

```bash
# Development environment
BACKEND_URL=http://localhost:8080
NEXTAUTH_URL=http://localhost:3000
AUTH_SECRET=dev-secret-min-32-chars-for-testing
```

**Step 2: Do NOT commit** (.env.local should be in .gitignore)

---

### Task 9: Update README.md

**Files:**
- Modify: `ui/README.md`

**Step 1: Update environment variables documentation**

Find and replace the environment variables section with:

```markdown
## Environment Variables

All variables are **required**. The app will not start without them.

| Variable | Description | Example |
|----------|-------------|---------|
| `BACKEND_URL` | Internal API URL for server-side calls | `http://localhost:8080` |
| `NEXTAUTH_URL` | This app's public URL for OAuth callbacks | `http://localhost:3000` |
| `AUTH_SECRET` | JWT encryption secret (32+ chars) | `openssl rand -base64 32` |

### Kubernetes Example

```yaml
env:
  - name: BACKEND_URL
    value: "http://api-service.bot-api.svc.cluster.local:8080"
  - name: NEXTAUTH_URL
    value: "https://ui.chatbot.com"
  - name: AUTH_SECRET
    valueFrom:
      secretKeyRef:
        name: ui-secrets
        key: auth-secret
```
```

**Step 2: Commit**

```bash
git add ui/README.md
git commit -m "docs(ui): update README with simplified env var documentation"
```

---

### Task 10: Test the validation

**Step 1: Test missing env vars**

```bash
cd ui
mv .env.local .env.local.bak
npm run dev
```

Expected: Server exits with error listing all 3 missing env vars.

**Step 2: Restore and verify app starts**

```bash
mv .env.local.bak .env.local
npm run dev
```

Expected: Server starts successfully.

**Step 3: Test OAuth flow through proxy**

1. Navigate to `http://localhost:3000/login`
2. Click "Continue with Google"
3. Verify browser navigates to `http://localhost:3000/backend/oauth2/authorization/google`
4. Verify redirect to Google OAuth works

---

## Verification Checklist

- [ ] App fails to start with clear error when any required env var is missing
- [ ] App starts successfully when all 3 required env vars are present
- [ ] No `|| "default"` fallback patterns remain in codebase
- [ ] `NEXT_PUBLIC_API_URL` removed entirely
- [ ] `API_BACKEND_URL` references replaced with `BACKEND_URL`
- [ ] `API_BACKEND_PUBLIC_URL` removed entirely
- [ ] OAuth login uses `/backend/oauth2/authorization/{provider}` path
- [ ] `NEXTAUTH_SECRET` still works as legacy alias for `AUTH_SECRET`

---

## Backend Configuration Reminder

After UI deployment, ensure the Spring Boot API is configured with OAuth redirect URIs pointing through the UI:

```
Google: https://ui.chatbot.com/backend/oauth2/callback/google
Azure:  https://ui.chatbot.com/backend/oauth2/callback/azure
```

These must also be registered in Google Cloud Console and Azure AD.
