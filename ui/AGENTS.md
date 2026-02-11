# Agent Guidelines for ui/

## Package Manager

Use **yarn** for all package operations. Do not use npm or pnpm.

## Validation Commands

Run these commands to validate changes before committing:

| Command | Purpose |
|---------|---------|
| `yarn lint` | Run ESLint to check for code quality issues |
| `yarn test` | Run Jest tests |
| `yarn build` | Build the Next.js application |

### Recommended Workflow

1. After making code changes: `yarn lint`
2. After modifying components or logic: `yarn test`
3. Before committing: `yarn build` (catches type errors and build issues)
