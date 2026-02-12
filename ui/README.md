# Support Bot UI

Next.js frontend for the Support Bot platform.

## Prerequisites

- Node.js 20+
- Support Bot API running (see `../api/`)

## Quick Start

```bash
# Install dependencies
yarn install

# Configure environment
cp .env.example .env.local

# Generate auth secret
openssl rand -base64 32
# Copy output to AUTH_SECRET in .env.local

# Start dev server
yarn dev
```

Opens at http://localhost:3000

## Environment Variables

All environment variables are **required**. The app will not start if any are missing.

| Variable | Description | Example |
|----------|-------------|---------|
| `BACKEND_URL` | Internal backend API URL (server-side only, never exposed to browser) | `http://localhost:8080` |
| `NEXTAUTH_URL` | This app's public URL (used for OAuth callbacks) | `http://localhost:3000` |
| `AUTH_SECRET` | Secret for JWT encryption. Generate with `openssl rand -base64 32` | `eQLD+j0kU2rldOUs7wBdQLwuk0AnYbyx+HqklXBn6co=` |

## Architecture

The UI uses a **server-side API proxy pattern**:

- All backend communication goes through server-only API routes (`/api/*`)
- Auth tokens are never exposed to the browser
- Queries use API Routes + React Query for caching
- Mutations use Server Actions with `useTransition`

```
Browser → /api/* routes → Backend API
              ↓
         (auth added server-side)
```

## Scripts

| Command | Description |
|---------|-------------|
| `yarn dev` | Start development server with Turbopack |
| `yarn build` | Production build |
| `yarn start` | Start production server |
| `yarn lint` | Run ESLint |
| `yarn test` | Run tests |
| `yarn test:watch` | Run tests in watch mode |

## Project Structure

```
src/
├── app/                    # Next.js App Router pages
│   ├── api/               # API route handlers (server-side proxy)
│   └── ...                # Page components
├── components/            # React components
├── hooks/                 # Client-side React hooks
├── lib/
│   ├── api/              # Server-only API layer (import "server-only")
│   ├── hooks/            # Data fetching hooks (React Query)
│   └── server-actions/   # Server Actions for mutations
└── instrumentation.ts    # Startup env var validation
```
