# UI-to-API Communication: Proxy Pattern Explained

This document explains why the UI uses a `/backend` proxy to communicate with the API, and the trade-offs involved.

## How The Current Architecture Works

```
┌──────────────────────────────────────────────────────────────────────┐
│                         User's Browser                               │
│                                                                      │
│  1. Client code (lib/api.ts) calls:                                  │
│     fetch("/backend/api/tickets", {                                  │
│       headers: { Authorization: "Bearer <token>" }  ← Added by JS    │
│     })                                                               │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼  Same-origin request (no CORS!)
┌──────────────────────────────────────────────────────────────────────┐
│                   Next.js Middleware (middleware.ts)                 │
│                                                                      │
│  NextResponse.rewrite() → Preserves all headers, rewrites URL        │
│  /backend/api/tickets → http://api-service:8080/api/tickets          │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼  Server-to-server (internal network)
┌──────────────────────────────────────────────────────────────────────┐
│                     Spring Boot API (internal)                       │
│                                                                      │
│  Receives: Authorization: Bearer <token> ← Header preserved!         │
└──────────────────────────────────────────────────────────────────────┘
```

### Key Code Path

**Client-side (`lib/api.ts`):**
```typescript
export async function apiGet(path: string) {
  const headers = await getApiHeaders();  // Gets auth token from session
  const res = await fetch(`/backend${path}`, { headers });  // Adds token to request
  return handleResponse(res, path);
}
```

**Middleware (`middleware.ts`):**
```typescript
if (pathname.startsWith("/backend")) {
  const backendUrl = process.env.BACKEND_URL!;
  const path = pathname.replace(/^\/backend/, "");
  return NextResponse.rewrite(new URL(path + req.nextUrl.search, backendUrl));
}
```

The client-side code adds the auth header. The middleware forwards it transparently. The proxy preserves all request headers.

---

## Why Use A Proxy?

### Reason 1: CORS Avoidance

**Without proxy:**
```
Browser on ui.chatbot.com
    → fetch("https://api.chatbot.com/api/tickets")
    → Browser: "Cross-origin request! Let me check CORS..."
    → OPTIONS preflight request
    → API must respond with Access-Control-Allow-* headers
    → Then actual request proceeds
```

**With proxy:**
```
Browser on ui.chatbot.com
    → fetch("/backend/api/tickets")
    → Browser: "Same origin, no CORS needed"
    → Request goes directly (no preflight)
```

The browser thinks it's talking to the same origin, so no CORS headers are required on the API.

### Reason 2: Internal-Only API (Kubernetes Pattern)

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                          │
│                                                                 │
│  ┌─────────────────┐         ┌─────────────────┐               │
│  │   bot-ui ns     │         │   bot-api ns    │               │
│  │                 │         │                 │               │
│  │  Next.js ───────────────→ Spring Boot      │               │
│  │     ↑           │  internal DNS            │               │
│  └─────┼───────────┘         └─────────────────┘               │
│        │                            ✗ No public ingress!       │
└────────┼────────────────────────────────────────────────────────┘
         │
         ▼  Only this is exposed
   ui.chatbot.com (Ingress)
```

The API has **no public URL**. It's only reachable via internal Kubernetes DNS:
```
http://api-service.bot-api.svc.cluster.local:8080
```

The browser can't reach this URL directly - only the Next.js server can.

### Reason 3: Single Domain Simplicity

- One SSL certificate to manage
- One ingress configuration
- One domain in Content-Security-Policy headers
- Cookies work automatically (same origin)
- Simpler debugging (all traffic through one domain)

---

## The Alternative: Direct API Calls

You could expose the API publicly and call it directly:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                          │
│                                                                 │
│  ┌─────────────────┐         ┌─────────────────┐               │
│  │   bot-ui ns     │         │   bot-api ns    │               │
│  │                 │         │                 │               │
│  │  Next.js        │         │  Spring Boot    │               │
│  │     ↑           │         │       ↑         │               │
│  └─────┼───────────┘         └───────┼─────────┘               │
│        │                             │                          │
└────────┼─────────────────────────────┼──────────────────────────┘
         │                             │
         ▼                             ▼
   ui.chatbot.com              api.chatbot.com
      (Ingress)                   (Ingress)
```

### Requirements for Direct API Calls

1. **Create ingress for API** - expose it publicly at `api.chatbot.com`

2. **Configure CORS on Spring Boot:**
   ```java
   @Configuration
   public class CorsConfig implements WebMvcConfigurer {
       @Override
       public void addCorsMappings(CorsRegistry registry) {
           registry.addMapping("/**")
               .allowedOrigins("https://ui.chatbot.com")
               .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
               .allowCredentials(true);
       }
   }
   ```

3. **Change client code** to call API directly:
   ```typescript
   // Before (proxy)
   fetch("/backend/api/tickets", { headers })

   // After (direct)
   fetch("https://api.chatbot.com/api/tickets", { headers })
   ```

4. **Add `NEXT_PUBLIC_API_URL`** - client needs to know the API URL

---

## Trade-off Summary

| Aspect | Proxy Pattern | Direct API |
|--------|---------------|------------|
| API exposure | Internal only | Public |
| CORS config | None needed | Required on API |
| Ingresses | 1 | 2 |
| SSL certificates | 1 | 2 |
| Client env vars | 0 (`/backend` is relative) | 1 (`NEXT_PUBLIC_API_URL`) |
| Extra network hop | Yes (adds ~1-5ms latency) | No |
| Debugging | One domain to trace | Two domains |
| Security surface | Smaller (API not public) | Larger |
| OAuth complexity | Can proxy OAuth too | Needs public API URL |

---

## Recommendations

### Use Proxy Pattern When:
- API should remain internal (not publicly accessible)
- You want to avoid CORS configuration
- Single-domain simplicity is preferred
- Kubernetes internal networking is desired

### Use Direct API Calls When:
- API is already publicly exposed
- You want to eliminate the extra network hop
- API serves multiple clients (not just this UI)
- You're comfortable managing CORS

---

## Current Implementation Choice

This codebase uses the **proxy pattern** with:
- All API calls routed through `/backend/*`
- Middleware rewrites to internal `BACKEND_URL`
- OAuth also proxied through `/backend/oauth2/*`
- Only 3 environment variables needed:
  - `AUTH_SECRET`
  - `NEXTAUTH_URL`
  - `BACKEND_URL`

This keeps the API internal-only and simplifies the deployment architecture.
