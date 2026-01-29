# Support Bot UI

Next.js frontend for the Support Bot platform.

## Prerequisites

- Node.js 20+
- PostgreSQL running on port 5432
- Support Bot API running on port 8080 (optional — UI works partially without it)

## Setup

1. Install dependencies:
   ```bash
   yarn install
   ```

2. Generate Prisma client:
   ```bash
   DATABASE_URL="postgresql://postgres:postgres@localhost:5432/postgres" npx prisma generate
   ```

3. Copy `.env.local.example` to `.env.local` and fill in your credentials:
   - `BACKEND_URL` — Support Bot API (default: `http://localhost:8080`)
   - `DATABASE_URL` — PostgreSQL connection string
   - `NEXTAUTH_URL`, `NEXTAUTH_SECRET` — NextAuth session config
   - `AZURE_AD_CLIENT_ID`, `AZURE_AD_CLIENT_SECRET`, `AZURE_AD_TENANT_ID` — Azure AD OAuth
   - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` — Google OAuth (optional)
   - `DATADOG_API_KEY`, `DATADOG_APP_KEY`, `DATADOG_SITE` — Datadog integration (optional)

## Development

```bash
yarn dev
```

Opens at http://localhost:3000

## Tests

```bash
yarn test
```
