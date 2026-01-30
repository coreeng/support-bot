# Functional Testing with Authentication

## Overview

The Support UI uses authentication middleware that protects all `/api/*` routes. This document explains how functional tests work with this security layer.

---

## How Tests Bypass Authentication

### Test Mode Bypass
The middleware (`src/middleware.ts`) includes a special test mode:

```typescript
// TEST MODE: Allow functional tests to bypass JWT validation
if (process.env.NODE_ENV === 'test') {
  const sessionCookie = request.cookies.get('next-auth.session-token')
  if (sessionCookie) {
    // Session cookie exists - allow test to proceed
    return NextResponse.next()
  }
}
```

**Why this is safe:**
- Only activates when `NODE_ENV=test` (never in production)
- Still requires a session cookie to be present (prevents completely open access)
- The functional tests mock the session cookie and session data
- Production/development modes always validate real JWT tokens

---

## Running Tests

### ✅ Correct Way (with NODE_ENV=test)
```bash
# Run all tests
NODE_ENV=test yarn test:cucumber

# Run specific feature
NODE_ENV=test npx cucumber-js tests/features/dashboards.feature

# Debug mode
NODE_ENV=test PWDEBUG=1 yarn test:cucumber
```

### ❌ Incorrect Way (without NODE_ENV=test)
```bash
# This will fail with 401 Unauthorized errors
yarn test:cucumber  # ❌ Missing NODE_ENV=test
```

**Note:** The `package.json` script automatically sets `NODE_ENV=test`, so `yarn test:cucumber` works correctly.

---

## How Authentication Works in Tests

### 1. Session Cookie Setup
In `tests/steps/hooks.ts` (Before hook):
```typescript
await this.context.addCookies([
  {
    name: 'next-auth.session-token',
    value: 'mock-session-token-for-testing',
    domain: cookieDomain,
    path: '/',
    httpOnly: true,
    secure: false,
    sameSite: 'Lax'
  }
]);
```

### 2. Session API Mock
```typescript
await this.page.route('**/api/auth/session', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      user: {
        email: "functional-test@example.com",
        teams: [...],
        isLeadership: true,
        isSupportEngineer: true
      }
    })
  });
});
```

### 3. Middleware Bypass
When the test makes an API request:
1. Middleware sees `NODE_ENV=test`
2. Checks for `next-auth.session-token` cookie ✅
3. Allows request to proceed
4. Test can mock the backend API responses

---

## Security Considerations

### Why Not Generate Real JWTs?
- Requires sharing `NEXTAUTH_SECRET` with tests (security risk)
- Complex setup for test environment
- Playwright can't easily sign JWTs
- Test mode bypass is cleaner and equally secure

### Why This Doesn't Compromise Security
1. **Environment-gated**: Only works when `NODE_ENV=test`
2. **Never runs in production**: Production always uses `NODE_ENV=production`
3. **Still requires cookie**: Can't access APIs without setting cookie
4. **Local only**: Functional tests run locally or in CI, not against production

### Production Deployment
When deployed to production:
- `NODE_ENV=production` is set
- Test bypass is **never** active
- All API requests require valid JWT tokens
- Authentication is fully enforced

---

## Troubleshooting

### Tests Failing with 401 Errors

**Symptoms:**
```
Error: Request failed with status 401
Message: "Authentication required"
```

**Solution:**
Ensure `NODE_ENV=test` is set:
```bash
NODE_ENV=test yarn test:cucumber
```

### Session Cookie Not Being Set

**Check:**
1. Cookie domain matches `SERVICE_ENDPOINT` hostname
2. Cookie is set in `Before` hook before page navigation
3. Browser context accepts cookies

### API Mocks Not Working

**Remember:**
- Mock routes must be set up BEFORE navigating to the page
- Each scenario clears routes, so set them up in `Before` hook or scenario steps
- `/api/auth/session` must always be mocked for authentication to work

---

## Adding New Test Scenarios

When adding new scenarios that access protected APIs:

1. **Session is already set up** in the `Before` hook (no action needed)
2. **Mock your API endpoints** in your scenario steps:
```typescript
When('user views tickets', async function (this: CustomWorld) {
  // Mock the /api/ticket endpoint
  await this.page.route('**/api/ticket*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [...] })
    });
  });
  
  // Navigate or interact with UI
  await this.page.click('[data-testid="tickets-tab"]');
});
```

---

## CI/CD Integration

### GitHub Actions / Jenkins
```yaml
- name: Run functional tests
  run: |
    cd p2p/tests/functional
    NODE_ENV=test yarn test:cucumber
  env:
    SERVICE_ENDPOINT: http://localhost:3000
```

### Docker
```dockerfile
ENV NODE_ENV=test
CMD ["yarn", "test:cucumber"]
```

---

## Related Files

- `src/middleware.ts` - Authentication middleware with test mode bypass
- `p2p/tests/functional/tests/steps/hooks.ts` - Session cookie and mock setup
- `p2p/tests/functional/tests/helpers/auth-mocks.ts` - Authorization endpoint mocks
- `SECURITY.md` - Overall security documentation

---

## Questions?

If you encounter authentication issues in tests:
1. Check `NODE_ENV=test` is set
2. Verify session cookie is present in browser context
3. Ensure `/api/auth/session` is mocked
4. Check middleware logs for authentication attempts

