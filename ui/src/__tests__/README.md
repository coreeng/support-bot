# Authentication Expiry and Redirect Tests

This directory contains tests for the JWT/Session expiry and redirect behavior.

## Test Files

### 1. `auth-expiry-redirect.test.tsx`
Unit tests for the authentication expiry and redirect logic.

**Tests:**
- Backend JWT expiry → 401 → redirect to login with callback URL
- NextAuth session expiry → 401 → redirect to login with callback URL
- Graceful handling of signOut errors when session already expired
- Callback URL preservation for different pages
- API routes return 401 instead of redirecting

**Run:**
```bash
npm test auth-expiry-redirect
```

### 2. `oauth-callback-preservation.test.ts`
Unit tests for OAuth callback URL preservation through the authentication flow.

**Tests:**
- Callback URL stored in cookie when starting OAuth flow
- Callback URL retrieved from cookie in OAuth callback
- Callback URL passed to login page after OAuth completes
- Cookie cleanup after use
- Cookie security (httpOnly, secure in production)
- Error handling (OAuth errors, token exchange failures)

**Run:**
```bash
npm test oauth-callback-preservation
```

## Running Tests

### Run all tests
```bash
npm test
```

### Run tests in watch mode
```bash
npm run test:watch
```

### Run specific test file
```bash
npm test auth-expiry-redirect
```

### Run with coverage
```bash
npm test -- --coverage
```

## E2E Tests

E2E tests are located in `ui/e2e/auth-expiry-redirect.spec.ts`.

**Run E2E tests:**
```bash
npx playwright test
```

**Run E2E tests in UI mode:**
```bash
npx playwright test --ui
```

**Run specific E2E test:**
```bash
npx playwright test auth-expiry-redirect
```

## Test Scenarios Covered

### 1. Backend JWT Expiry
- User is on a page (e.g., `/knowledge-gaps`)
- Backend JWT expires (15 seconds in test config)
- User performs an action (e.g., "Start Analysis")
- API returns 401
- Client-side handler:
  - Calls `signOut({ redirect: false })`
  - Redirects to `/login?callbackUrl=/knowledge-gaps`
- User logs in
- Redirected back to `/knowledge-gaps`

### 2. NextAuth Session Expiry
- User is on a page
- NextAuth session expires (15 seconds in test config)
- User performs an action
- API returns 401 (no session to send JWT)
- Client-side handler:
  - Calls `signOut({ redirect: false })` (may throw error)
  - Catches error gracefully
  - Redirects to `/login?callbackUrl=/knowledge-gaps`
- User logs in
- Redirected back to `/knowledge-gaps`

### 3. OAuth Callback URL Preservation
- User clicks "Continue with Google" on `/login?callbackUrl=/knowledge-gaps`
- OAuth start endpoint:
  - Stores `/knowledge-gaps` in `oauth-callback-url` cookie
  - Redirects to Google OAuth
- Google redirects back to `/api/auth/callback/google?code=...`
- OAuth callback endpoint:
  - Reads `/knowledge-gaps` from cookie
  - Exchanges code for JWT
  - Deletes cookie
  - Redirects to `/login?token=...&callbackUrl=/knowledge-gaps`
- Login page:
  - Calls `signIn("backend-token", { token })`
  - Redirects to `/knowledge-gaps`

### 4. Proxy Behavior
- API routes (e.g., `/api/analysis/run`) return 401, not redirect
- Page routes (e.g., `/tickets`) redirect to login when unauthenticated
- Public routes (e.g., `/login`) are accessible without authentication

## Configuration

### Test JWT Expiry
To test JWT expiry, set in `api/service/src/main/resources/application.yaml`:
```yaml
jwt:
  expiration: 15000  # 15 seconds
```

### Test NextAuth Session Expiry
To test NextAuth session expiry, set in `ui/src/auth.config.ts`:
```typescript
session: {
  strategy: "jwt",
  maxAge: 15, // 15 seconds
},
```

**Remember to restart the servers after changing these values!**

## Debugging Tests

### Enable verbose logging
```bash
npm test -- --verbose
```

### Run single test
```bash
npm test -- -t "should redirect to login with callback URL when API returns 401"
```

### Debug in VS Code
Add to `.vscode/launch.json`:
```json
{
  "type": "node",
  "request": "launch",
  "name": "Jest Debug",
  "program": "${workspaceFolder}/ui/node_modules/.bin/jest",
  "args": ["--runInBand", "--no-cache"],
  "console": "integratedTerminal",
  "internalConsoleOptions": "neverOpen"
}
```

## Common Issues

### Tests fail with "Cannot find module"
Make sure all dependencies are installed:
```bash
cd ui
npm install
```

### Tests timeout
Increase timeout in test file:
```typescript
jest.setTimeout(10000) // 10 seconds
```

### Mock not working
Clear Jest cache:
```bash
npm test -- --clearCache
```

## CI/CD Integration

Add to your CI pipeline:
```yaml
- name: Run UI tests
  run: |
    cd ui
    npm test -- --ci --coverage
```

## Contributing

When adding new authentication-related features:
1. Add unit tests to verify the behavior
2. Add E2E tests for the complete flow
3. Update this README with new test scenarios
4. Ensure all tests pass before submitting PR

## Related Files

- `ui/src/proxy.ts` - Proxy configuration (allows API routes through)
- `ui/src/auth.config.ts` - NextAuth configuration
- `ui/src/lib/hooks/index.ts` - API helper with 401 handling
- `ui/src/components/knowledgegaps/knowledge-gaps.tsx` - Component with 401 handlers
- `ui/src/app/api/auth/start/[provider]/route.ts` - OAuth start (sets cookie)
- `ui/src/app/api/auth/callback/google/route.ts` - OAuth callback (reads cookie)
- `ui/src/app/login/page.tsx` - Login page (redirects to callback URL)

