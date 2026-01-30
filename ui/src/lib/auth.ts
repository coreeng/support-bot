import { NextAuthOptions } from "next-auth"
import AzureADProvider from "next-auth/providers/azure-ad"
import GoogleProvider from "next-auth/providers/google"

// Type definitions for backend data structures
interface RawTeam {
  label?: string
  code?: string
  types?: string[]
}

interface ProcessedTeam {
  name: string
  types: string[]
}

interface L2Team {
  label?: string
  code?: string
}

// Validate required environment variables
const isBuildTime = process.env.NEXT_PHASE === 'phase-production-build' || process.env.NEXT_PHASE === 'phase-development-build'
const isTestEnvironment = process.env.NODE_ENV === 'test'

if (!process.env.NEXTAUTH_SECRET && !isBuildTime && !isTestEnvironment) {
  if (process.env.NODE_ENV === 'production') {
    throw new Error('NEXTAUTH_SECRET environment variable is required for secure JWT signing')
  } else {
    console.warn('⚠️  NEXTAUTH_SECRET is not set. Authentication will not work properly.')
  }
}

if (!process.env.NEXTAUTH_URL && process.env.NODE_ENV === 'production' && !isBuildTime && !isTestEnvironment) {
  console.warn('⚠️  NEXTAUTH_URL should be set in production for proper OAuth callback handling')
}

const providers = []

if (process.env.AZURE_AD_CLIENT_ID && process.env.AZURE_AD_CLIENT_SECRET && process.env.AZURE_AD_TENANT_ID) {
  providers.push(
    AzureADProvider({
      clientId: process.env.AZURE_AD_CLIENT_ID,
      clientSecret: process.env.AZURE_AD_CLIENT_SECRET,
      tenantId: process.env.AZURE_AD_TENANT_ID,
      authorization: {
        params: {
          scope: 'openid profile email User.Read',
        },
      },
    })
  )
}

if (process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET) {
  providers.push(
    GoogleProvider({
      clientId: process.env.GOOGLE_CLIENT_ID,
      clientSecret: process.env.GOOGLE_CLIENT_SECRET,
    })
  )
}

if (providers.length === 0) {
  console.warn('⚠️  No OAuth providers configured.')
}

export const authOptions: NextAuthOptions = {
  providers,
  secret: process.env.NEXTAUTH_SECRET || (isBuildTime || isTestEnvironment ? 'test-or-build-time-dummy-secret' : undefined),



  // Fix for iframe authentication:
  // Cookies must be SameSite=None and Secure to work in a cross-origin iframe.
  cookies: {
    sessionToken: {
      name: `${process.env.NODE_ENV === 'production' ? '__Secure-' : ''}next-auth.session-token`,
      options: {
        httpOnly: true,
        sameSite: 'none',
        path: '/',
        secure: true, // required for sameSite: 'none'
      },
    },
    callbackUrl: {
      name: `${process.env.NODE_ENV === 'production' ? '__Secure-' : ''}next-auth.callback-url`,
      options: {
        sameSite: 'none',
        path: '/',
        secure: true,
        httpOnly: true,
      },
    },
    csrfToken: {
      name: `${process.env.NODE_ENV === 'production' ? '__Host-' : ''}next-auth.csrf-token`,
      options: {
        httpOnly: true,
        sameSite: 'none',
        path: '/',
        secure: true,
      },
    },
  },

  session: {
    strategy: "jwt" as const,
    maxAge: 24 * 60 * 60, // 24 hours
  },
  jwt: {
    maxAge: 24 * 60 * 60, // 24 hours
  },
  callbacks: {
    async redirect({ url, baseUrl }) {
      // 1. Handle our custom popup callback specifically
      if (url.includes('/api/auth/popup/callback')) {
        return url
      }



      // 3. Standard Security Check - Only allow safe redirects
      // Allow relative URLs
      if (url.startsWith('/')) return `${baseUrl}${url}`

      try {
        const targetUrl = new URL(url)

        // Allow URLs on the same origin
        if (targetUrl.origin === baseUrl) return url

        // Allow specific external domains configured via environment variable
        if (process.env.ALLOWED_REDIRECT_HOSTS) {
          const allowedHosts = process.env.ALLOWED_REDIRECT_HOSTS.split(',').map(h => h.trim())

          if (allowedHosts.includes('*')) return url

          // STRICT VALIDATION: Check hostname match or subdomain match
          const isAllowed = allowedHosts.some(allowed => {
            if (allowed.startsWith('https://*.')) {
              const domain = allowed.replace('https://*.', '')
              return targetUrl.hostname.endsWith(`.${domain}`)
            }
            try {
              return new URL(allowed).hostname === targetUrl.hostname
            } catch {
              return false
            }
          })

          if (isAllowed) return url
        }
      } catch {
        // If URL parsing fails, deny the redirect
        // console.error('[Auth Redirect] Invalid URL:', url)
        return baseUrl
      }

      return baseUrl
    },
    async jwt({ token, account, profile, user }) {
      if (account && profile) {
        const azureProfile = profile as typeof profile & { preferred_username?: string }
        const userEmail = profile.email || azureProfile.preferred_username || user?.email || ''

        try {
          const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'

          // Use Promise.allSettled to simplify fetching logic
          const [userRes, l2Res] = await Promise.allSettled([
            fetch(`${backendUrl}/user?email=${encodeURIComponent(userEmail)}`, {
              headers: { 'Content-Type': 'application/json' },
            }),
            fetch(`${backendUrl}/team?type=escalation`)
          ])

          // Helper to safely extract JSON
          const getJson = async (res: PromiseSettledResult<Response>) =>
            (res.status === 'fulfilled' && res.value.ok) ? await res.value.json() : null

          const userData = await getJson(userRes)
          const l2Teams = (await getJson(l2Res)) || []

          // Error logging is now handled by the getJson helper function

          const teamsRaw = Array.isArray(userData?.teams) ? userData.teams : []

          const mappedTeams: ProcessedTeam[] = teamsRaw.map((t: RawTeam) => {
            const rawName = t?.code || t?.label || ''
            return {
              name: rawName.includes('@') ? rawName.split('@')[0] : rawName,
              types: t?.types || []
            }
          }).filter((t: ProcessedTeam) => t.name)

          // Role calculations
          const isLeadership = mappedTeams.some((t: ProcessedTeam) => t.types.some((x: string) => /leadership/i.test(x)))
          const isSupportEngineer = mappedTeams.some((t: ProcessedTeam) => t.types.some((x: string) => /support/i.test(x)))
          const l2Names = l2Teams.map((t: L2Team) => t.label || t.code)
          const isEscalation = mappedTeams.some((t: ProcessedTeam) =>
            t.types.some((x: string) => /escalation/i.test(x)) || l2Names.includes(t.name)
          )

          // Token Storage
          token.email = userEmail
          token.name = profile.name || user?.name || ''
          token.isLeadership = isLeadership
          token.isEscalation = isEscalation
          token.isSupportEngineer = isSupportEngineer

          // === MINIFICATION ===
          token.minTeams = mappedTeams.map((t: ProcessedTeam) => ({ n: t.name, t: t.types }));

        } catch (error) {
          console.error('Fetch error:', error)
          // Fallback defaults
          token.minTeams = []
        }
      }
      return token
    },
    async session({ session, token }) {
      if (session.user) {
        session.user.email = token.email as string
        session.user.name = token.name as string

        // === REHYDRATION ===
        const minTeams = (token.minTeams as Array<{ n: string, t: string[] }>) || []
        session.user.teams = minTeams.map(mt => ({
          name: mt.n,
          types: mt.t || [],
          groupRefs: [],
        }))

        session.user.isLeadership = !!token.isLeadership
        session.user.isEscalation = !!token.isEscalation
        session.user.isSupportEngineer = !!token.isSupportEngineer
      }
      return session
    },
  },
  pages: {
    signIn: '/api/auth/popup/redirect',
  },
  debug: process.env.NODE_ENV === 'development',
}