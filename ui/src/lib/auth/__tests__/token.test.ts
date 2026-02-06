import { getApiUrl } from '../token'

describe('getApiUrl', () => {
  it('returns localhost:8080 when running locally', () => {
    expect(getApiUrl('localhost')).toBe('http://localhost:8080')
  })

  it('strips -ui from hostname to reach the API', () => {
    const apiUrl = getApiUrl('support-bot-app-cecg-ui.example.com', 'https:')

    expect(apiUrl).toBe('https://support-bot-app-cecg.example.com')
    expect(`${apiUrl}/auth/me`).toBe('https://support-bot-app-cecg.example.com/auth/me')
    expect(`${apiUrl}/auth/token`).toBe('https://support-bot-app-cecg.example.com/auth/token')
    expect(`${apiUrl}/oauth2/authorization/google`).toBe('https://support-bot-app-cecg.example.com/oauth2/authorization/google')
  })

  it('works with environment-suffixed hostnames', () => {
    const apiUrl = getApiUrl('support-bot-app-cecg-ui-integration.example.com', 'https:')

    expect(apiUrl).toBe('https://support-bot-app-cecg-integration.example.com')
    expect(`${apiUrl}/auth/me`).toBe('https://support-bot-app-cecg-integration.example.com/auth/me')
  })

  it('preserves the protocol', () => {
    expect(getApiUrl('support-bot-app-cecg-ui.example.com', 'http:')).toBe('http://support-bot-app-cecg.example.com')
  })

  it('returns hostname as-is when there is no -ui to strip', () => {
    expect(getApiUrl('support-bot-app-cecg.example.com', 'https:')).toBe('https://support-bot-app-cecg.example.com')
  })
})
