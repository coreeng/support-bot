import nextConfig from '../../next.config'

interface NextConfigHeader {
  key: string
  value: string
}

interface NextConfigHeaders {
  source: string
  headers: NextConfigHeader[]
}

describe('Next.js Configuration', () => {
  describe('CSP Headers', () => {
    it('should include CSP headers in configuration', () => {
      expect(nextConfig).toHaveProperty('headers')
      expect(typeof nextConfig.headers).toBe('function')
    })

    it('should configure CSP for all routes', async () => {
      expect(nextConfig.headers).toBeDefined()
      expect(typeof nextConfig.headers).toBe('function')
      const headers: NextConfigHeaders[] = await nextConfig.headers!()
      expect(headers).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            source: '/(.*)',
            headers: expect.arrayContaining([
              expect.objectContaining({
                key: 'Content-Security-Policy',
                value: expect.stringContaining('frame-ancestors'),
              }),
            ]),
          }),
        ])
      )
    })

    it('should restrict iframe embedding to Datadog domains', async () => {
      expect(nextConfig.headers).toBeDefined()
      expect(typeof nextConfig.headers).toBe('function')
      const headers: NextConfigHeaders[] = await nextConfig.headers!()
      const cspHeader = headers.find((h) => h.source === '/(.*)')?.headers?.find(
        (header) => header.key === 'Content-Security-Policy'
      )

      expect(cspHeader).toBeDefined()
      expect(cspHeader?.value).toContain("'self'")
      expect(cspHeader?.value).toContain('https://*.datadoghq.com')
      expect(cspHeader?.value).toContain('https://*.datadoghq.eu')
      expect(cspHeader?.value).toContain('frame-ancestors')
    })

    it('should have correct CSP syntax', async () => {
      expect(nextConfig.headers).toBeDefined()
      expect(typeof nextConfig.headers).toBe('function')
      const headers: NextConfigHeaders[] = await nextConfig.headers!()
      const cspHeader = headers.find((h) => h.source === '/(.*)')?.headers?.find(
        (header) => header.key === 'Content-Security-Policy'
      )

      expect(cspHeader?.value).toMatch(/^frame-ancestors/)
      expect(cspHeader?.value).toContain("'self' https://*.datadoghq.com https://*.datadoghq.eu")
    })
  })

  describe('Output Configuration', () => {
    it('should be configured for standalone deployment', () => {
      expect(nextConfig.output).toBe('standalone')
    })
  })
})
