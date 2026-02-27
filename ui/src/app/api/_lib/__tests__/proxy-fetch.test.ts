import { proxyFetch } from '../backend-fetch'

describe('proxyFetch', () => {
  const originalFetch = global.fetch

  afterEach(() => {
    global.fetch = originalFetch
    delete process.env.PROXY_LOGGING
    jest.restoreAllMocks()
  })

  it('logs when PROXY_LOGGING=true', async () => {
    process.env.PROXY_LOGGING = 'true'
    global.fetch = jest.fn(() => Promise.resolve({ status: 200, ok: true } as Response))
    const spy = jest.spyOn(console, 'log').mockImplementation()

    const res = await proxyFetch('proxy', '/test', 'http://localhost/test', { method: 'GET' })

    expect(res.status).toBe(200)
    expect(spy).toHaveBeenCalledWith(expect.stringContaining('[proxy] GET /test 200'))
  })

  it('does not log when PROXY_LOGGING is not set', async () => {
    global.fetch = jest.fn(() => Promise.resolve({ status: 200, ok: true } as Response))
    const spy = jest.spyOn(console, 'log').mockImplementation()

    await proxyFetch('proxy', '/test', 'http://localhost/test', { method: 'GET' })

    expect(spy).not.toHaveBeenCalled()
  })

  it('always logs non-ok responses', async () => {
    global.fetch = jest.fn(() => Promise.resolve({ status: 500, ok: false } as Response))
    const spy = jest.spyOn(console, 'error').mockImplementation()

    const res = await proxyFetch('proxy', '/test', 'http://localhost/test', { method: 'GET' })

    expect(res.status).toBe(500)
    expect(spy).toHaveBeenCalledWith(expect.stringContaining('[proxy] GET /test 500'))
  })
})
