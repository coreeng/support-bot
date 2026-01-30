// Capture the authOptions passed into NextAuth via module load
type JwtCallback = (params: {
  token: Record<string, unknown>
  account?: { provider: string }
  profile?: { email?: string; name?: string; preferred_username?: string }
  user?: { name?: string }
}) => Promise<Record<string, unknown>>

type NextAuthMockOptions = {
  callbacks: {
    jwt: JwtCallback
  }
}

let capturedOptions: NextAuthMockOptions | null = null

jest.mock('next-auth', () => ({
  __esModule: true,
  default: (options: NextAuthMockOptions) => {
    capturedOptions = options
    return { handlers: { GET: () => null, POST: () => null } }
  },
}))

// Providers are not exercised in these tests; mock them as identity fns
jest.mock('next-auth/providers/azure-ad', () => ({
  __esModule: true,
  default: jest.fn(() => ({})),
}))
jest.mock('next-auth/providers/google', () => ({
  __esModule: true,
  default: jest.fn(() => ({})),
}))

describe('NextAuth jwt callback (role/type mapping)', () => {
  const OLD_ENV = process.env

  beforeEach(async () => {
    jest.resetModules()
    process.env = {
      ...OLD_ENV,
      BACKEND_SERVICE_URL: 'http://backend',
      NEXTAUTH_SECRET: 'test-secret-for-testing-only',
    }
    capturedOptions = null
    ;(global as typeof globalThis & { fetch: jest.Mock }).fetch = jest.fn()
    // Load the route module to populate capturedOptions
    await import('../[...nextauth]/route')
  })

  afterEach(() => {
    process.env = OLD_ENV
    jest.clearAllMocks()
  })

  it('maps teams with types into token flags and teams', async () => {
    // Mock /user and /team?type=escalation
    ;(fetch as jest.Mock)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          teams: [
            { label: 'Leadership Group', types: ['leadership'] },
            { label: 'Core Support', types: ['support'] },
            { label: 'Escalation Squad', types: ['escalation'] },
            { label: 'Tenant A', types: ['tenant'] },
          ],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [{ label: 'Escalation Squad', code: 'escalation-squad' }],
      })

    const jwt = capturedOptions!.callbacks.jwt

    const token: Record<string, unknown> = {}
    const account = { provider: 'azure' }
    const profile = { email: 'user@example.com', name: 'User' }

    const result = await jwt({ token, account, profile, user: { name: 'User' } })

    expect(result.email).toBe('user@example.com')
    expect(result.name).toBe('User')
    // Rehydrate minified teams for testing
    const minTeams = (result.minTeams as Array<{ n: string, t: string[] }>) || []
    const teamNames = minTeams.map((t) => t.n)
    expect(teamNames).toEqual(
      expect.arrayContaining(['Leadership Group', 'Core Support', 'Escalation Squad', 'Tenant A'])
    )
    expect(result.isLeadership).toBe(true)
    expect(result.isSupportEngineer).toBe(true)
    expect(result.isEscalation).toBe(true)
  })

  it('normalizes email-style team names by stripping the domain', async () => {
    // Team labels returned as emails should be normalized to the local-part
    ;(fetch as jest.Mock)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          teams: [
            { label: 'core-elevate@cecg.io', types: ['support'] },
            { label: 'core-elevate-admin@cecg.io', types: ['leadership'] },
          ],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      })

    const jwt = capturedOptions!.callbacks.jwt

    const token: Record<string, unknown> = {}
    const account = { provider: 'azure' }
    const profile = { email: 'antoine@cecg.io', name: 'Antoine' }

    const result = await jwt({ token, account, profile, user: { name: 'Antoine' } })
    // Rehydrate minified teams for testing
    const minTeams = (result.minTeams as Array<{ n: string, t: string[] }>) || []
    const teamNames = minTeams.map((t) => t.n)

    expect(teamNames).toEqual(
      expect.arrayContaining(['core-elevate', 'core-elevate-admin'])
    )
    expect(result.isSupportEngineer).toBe(true)
    expect(result.isLeadership).toBe(true)
  })

  it('falls back to safe defaults when backend fetch fails', async () => {
    ;(fetch as jest.Mock).mockRejectedValue(new Error('network'))

    const jwt = capturedOptions!.callbacks.jwt
    const token: Record<string, unknown> = {}
    const account = { provider: 'azure' }
    const profile = { email: 'user@example.com', name: 'User' }

    const result = await jwt({ token, account, profile, user: { name: 'User' } })

    expect(result.email).toBe('user@example.com')
    expect(result.minTeams).toEqual([])
    expect(result.isLeadership).toBe(false)
    expect(result.isSupportEngineer).toBe(false)
    expect(result.isEscalation).toBe(false)
  })
})

