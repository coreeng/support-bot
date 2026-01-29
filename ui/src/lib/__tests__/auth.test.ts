import { authOptions } from '../auth'

// Mock environment variables
const ORIGINAL_ENV = process.env

describe('Auth Options - Redirect Callback', () => {
    beforeEach(() => {
        jest.resetModules()
        process.env = { ...ORIGINAL_ENV }
    })

    afterAll(() => {
        process.env = ORIGINAL_ENV
    })

    const runRedirect = async (url: string, baseUrl: string) => {
        if (authOptions.callbacks?.redirect) {
            return await authOptions.callbacks.redirect({ url, baseUrl })
        }
        throw new Error('Redirect callback not defined')
    }

    describe('Standard Security Checks', () => {
        const baseUrl = 'https://app.example.com'

        it('should allow relative URLs', async () => {
            const url = '/dashboard'
            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(`${baseUrl}${url}`)
        })

        it('should allow same-origin URLs', async () => {
            const url = 'https://app.example.com/dashboard'
            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(url)
        })

        it('should default to baseUrl for unknown external hosts', async () => {
            const url = 'https://evil.com/phishing'
            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(baseUrl)
        })
    })

    describe('ALLOWED_REDIRECT_HOSTS Configuration', () => {
        const baseUrl = 'https://app.example.com'

        it('should allow whitelisted external host', async () => {
            process.env.ALLOWED_REDIRECT_HOSTS = 'https://trusted.com'
            const url = 'https://trusted.com/callback'
            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(url)
        })

        it('should allow whitelisted wildcards (subdomains)', async () => {
            process.env.ALLOWED_REDIRECT_HOSTS = 'https://*.trusted.com'

            const url1 = 'https://sub.trusted.com/callback'
            const result1 = await runRedirect(url1, baseUrl)
            expect(result1).toBe(url1)

            const url2 = 'https://deep.sub.trusted.com/callback'
            const result2 = await runRedirect(url2, baseUrl)
            expect(result2).toBe(url2)
        })

        it('should BLOCK subdomain spoofing (Open Redirect Fix)', async () => {
            process.env.ALLOWED_REDIRECT_HOSTS = 'https://trusted.com'

            // Attack: trusted.com.evil.com
            const url = 'https://trusted.com.evil.com/callback'
            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(baseUrl)
        })

        it('should BLOCK partial matches', async () => {
            process.env.ALLOWED_REDIRECT_HOSTS = 'https://trusted.com'

            // Attack: trusted.com-fake.com
            const url = 'https://trusted.com-fake.com'
            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(baseUrl)
        })

        it('should BLOCK wildcard subdomain spoofing', async () => {
            process.env.ALLOWED_REDIRECT_HOSTS = 'https://*.trusted.com'

            // Attack: sub.trusted.com.evil.com
            const url = 'https://sub.trusted.com.evil.com'
            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(baseUrl)
        })

        it('should handle malformed URLs gracefully', async () => {
            process.env.ALLOWED_REDIRECT_HOSTS = 'https://trusted.com'
            const url = 'not-a-valid-url'
            // According to logic, if it doesn't start with / and new URL fails, it might return baseUrl 
            // OR fail in new URL(url). Our code catches basic errors in the allowedHosts block,
            // but relative URL check happens first. 
            // 'not-a-valid-url' does not start with /. new URL('not-a-valid-url') throws.
            // The code catches it? Let's verify our code logic in auth.ts

            const result = await runRedirect(url, baseUrl)
            expect(result).toBe(baseUrl)
        })
    })
})
