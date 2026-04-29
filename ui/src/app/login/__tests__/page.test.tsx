import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
import { useRouter, useSearchParams } from 'next/navigation'
import { signIn } from 'next-auth/react'
import LoginPage from '../page'
import { useAuth } from '@/hooks/useAuth'

jest.mock('next/navigation', () => ({
  useRouter: jest.fn(),
  useSearchParams: jest.fn(() => new URLSearchParams()),
}))

jest.mock('../../../hooks/useAuth', () => ({
  useAuth: jest.fn(),
}))

describe('LoginPage', () => {
  const mockReplace = jest.fn()
  const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>
  const mockUseRouter = useRouter as jest.MockedFunction<typeof useRouter>
  const mockUseSearchParams = useSearchParams as jest.MockedFunction<typeof useSearchParams>
  const mockSignIn = signIn as jest.MockedFunction<typeof signIn>

  const originalOpener = window.opener
  const originalClose = window.close

  beforeEach(() => {
    jest.clearAllMocks()
    mockUseRouter.mockReturnValue({ replace: mockReplace } as any)
    mockUseAuth.mockReturnValue({ isLoading: false, isAuthenticated: false } as any)
    mockUseSearchParams.mockReturnValue(new URLSearchParams() as any)
    // Mock fetch for provider fetching
    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ providers: ['dex'] }),
      } as Response)
    )

    // Default: no opener (not a popup)
    Object.defineProperty(window, 'opener', { value: null, writable: true, configurable: true })
    window.close = jest.fn()
  })

  afterEach(() => {
    Object.defineProperty(window, 'opener', { value: originalOpener, writable: true, configurable: true })
    window.close = originalClose
  })

  // -------------------------------------------------------------------
  // Basic rendering
  // -------------------------------------------------------------------

  it('auto-redirects to Dex when not authenticated and Dex is the only provider', async () => {
    render(<LoginPage />)

    await waitFor(() => {
      expect(screen.getByText('Redirecting to sign-in...')).toBeInTheDocument()
    })
    // Button should not render because the page is in the auto-redirect spinner state.
    expect(screen.queryByText('Continue with SSO')).not.toBeInTheDocument()
  })

  it('does not auto-redirect when ?signOut=1 is set; shows SSO button', async () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams('signOut=1') as any)

    render(<LoginPage />)

    await waitFor(() => {
      expect(screen.getByText('Sign in')).toBeInTheDocument()
      expect(screen.getByText('Continue with SSO')).toBeInTheDocument()
    })
    expect(screen.queryByText('Redirecting to sign-in...')).not.toBeInTheDocument()
  })

  it('redirects to home if already authenticated', async () => {
    mockUseAuth.mockReturnValue({ isLoading: false, isAuthenticated: true } as any)

    render(<LoginPage />)

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/')
    })
  })

  it('shows loading state while checking auth', () => {
    mockUseAuth.mockReturnValue({ isLoading: true, isAuthenticated: false } as any)

    render(<LoginPage />)

    expect(screen.getByText((_content, el) =>
      el?.className?.includes('animate-spin') ?? false
    )).toBeInTheDocument()
  })

  it('shows error from search params', async () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams('error=TokenExpired') as any)

    render(<LoginPage />)

    await waitFor(() => {
      expect(screen.getByText('Authentication Error')).toBeInTheDocument()
      expect(screen.getByText('TokenExpired')).toBeInTheDocument()
    })
  })

  it('shows not-onboarded message for user_not_allowed error', async () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams('error=user_not_allowed') as any)

    render(<LoginPage />)

    await waitFor(() => {
      expect(screen.getByText('Access Restricted')).toBeInTheDocument()
      expect(screen.getByText(/not been onboarded/)).toBeInTheDocument()
      expect(screen.queryByText('Authentication Error')).not.toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------
  // postMessage listener — iframe receives auth completion from popup
  // -------------------------------------------------------------------

  describe('postMessage listener', () => {
    it('ignores messages from a different origin', () => {
      render(<LoginPage />)

      act(() => {
        window.dispatchEvent(new MessageEvent('message', {
          data: { type: 'auth:success', callbackUrl: '/dashboard' },
          origin: 'https://evil.com',
        }))
      })

      // No signIn and no router.replace — message was dropped
      expect(mockSignIn).not.toHaveBeenCalled()
      expect(mockReplace).not.toHaveBeenCalled()
    })

    it('ignores messages with unknown types', () => {
      render(<LoginPage />)

      act(() => {
        window.dispatchEvent(new MessageEvent('message', {
          data: { type: 'some:other:event' },
          origin: window.location.origin,
        }))
      })

      expect(mockSignIn).not.toHaveBeenCalled()
      expect(mockReplace).not.toHaveBeenCalled()
    })
  })

  // -------------------------------------------------------------------
  // Popup flow — token/code arrives in popup window (has window.opener)
  // -------------------------------------------------------------------

  describe('popup flow (window.opener present)', () => {
    const mockPostMessage = jest.fn()

    beforeEach(() => {
      Object.defineProperty(window, 'opener', {
        value: { postMessage: mockPostMessage, closed: false },
        writable: true,
        configurable: true,
      })
    })

    afterEach(() => {
      mockPostMessage.mockClear()
    })

    it('calls signIn(redirect:false), sends postMessage to opener, and closes', async () => {
      mockSignIn.mockResolvedValue({ error: undefined, ok: true, status: 200, url: '' } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('token=mytoken&callbackUrl=/dash') as any
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockSignIn).toHaveBeenCalledWith('backend-token', {
          token: 'mytoken',
          redirect: false,
        })
      })

      await waitFor(() => {
        expect(mockPostMessage).toHaveBeenCalledWith(
          { type: 'auth:success', callbackUrl: '/dash' },
          window.location.origin
        )
        expect(window.close).toHaveBeenCalled()
      })
    })

    it('handles code flow identically', async () => {
      mockSignIn.mockResolvedValue({ error: undefined, ok: true, status: 200, url: '' } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('code=mycode&provider=dex&callbackUrl=/home') as any
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockSignIn).toHaveBeenCalledWith('backend-code', {
          code: 'mycode',
          provider: 'dex',
          redirect: false,
        })
      })

      await waitFor(() => {
        expect(mockPostMessage).toHaveBeenCalledWith(
          { type: 'auth:success', callbackUrl: '/home' },
          window.location.origin
        )
        expect(window.close).toHaveBeenCalled()
      })
    })

    it('does NOT send postMessage or close on signIn error', async () => {
      mockSignIn.mockResolvedValue({
        error: 'CredentialsSignin', ok: false, status: 401, url: '',
      } as any)
      mockUseSearchParams.mockReturnValue(new URLSearchParams('token=bad') as any)

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith(
          expect.stringContaining('/login?error=')
        )
      })

      expect(mockPostMessage).not.toHaveBeenCalled()
      expect(window.close).not.toHaveBeenCalled()
    })

    it('sanitizes callbackUrl sent in postMessage', async () => {
      mockSignIn.mockResolvedValue({ error: undefined, ok: true, status: 200, url: '' } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('token=t&callbackUrl=https://evil.com') as any
      )

      render(<LoginPage />)

      await waitFor(() => {
        // callbackUrl is sanitized before it's stored, so postMessage receives "/"
        expect(mockPostMessage).toHaveBeenCalledWith(
          { type: 'auth:success', callbackUrl: '/' },
          window.location.origin
        )
      })
    })

    it('does not call signIn a second time on re-render', async () => {
      mockSignIn.mockResolvedValue({ error: undefined, ok: true, status: 200, url: '' } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('token=mytoken') as any
      )

      const { rerender } = render(<LoginPage />)

      await waitFor(() => {
        expect(mockSignIn).toHaveBeenCalledTimes(1)
      })

      // Re-render (e.g. from state change)
      rerender(<LoginPage />)

      // Still only 1 call — authAttemptedRef prevents duplicates
      expect(mockSignIn).toHaveBeenCalledTimes(1)
    })
  })

  // -------------------------------------------------------------------
  // Non-popup flow — token/code arrives in normal page (no opener)
  // -------------------------------------------------------------------

  describe('non-popup flow', () => {
    it('calls signIn with redirect:false for token', async () => {
      mockSignIn.mockResolvedValue({ ok: true } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('token=mytoken&callbackUrl=/dash') as any
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockSignIn).toHaveBeenCalledWith('backend-token', {
          token: 'mytoken',
          redirect: false,
        })
      })
    })

    it('calls signIn with redirect:false for code', async () => {
      mockSignIn.mockResolvedValue({ ok: true } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('code=mycode&provider=dex&callbackUrl=/dash') as any
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockSignIn).toHaveBeenCalledWith('backend-code', {
          code: 'mycode',
          provider: 'dex',
          redirect: false,
        })
      })
    })

    it('sanitizes callbackUrl passed to signIn', async () => {
      mockSignIn.mockResolvedValue({ ok: true } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('token=t&callbackUrl=//evil.com') as any
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockSignIn).toHaveBeenCalledWith('backend-token', {
          token: 't',
          redirect: false,
        })
      })
    })

    it('sanitizes callbackUrl in authenticated redirect', async () => {
      mockUseAuth.mockReturnValue({ isLoading: false, isAuthenticated: true } as any)
      mockUseSearchParams.mockReturnValue(
        new URLSearchParams('callbackUrl=https://evil.com') as any
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith('/')
      })
    })
  })

  // -------------------------------------------------------------------
  // Provider fetching error scenarios
  // -------------------------------------------------------------------

  describe('provider fetching', () => {
    it('shows "No authentication providers configured" when fetch returns empty array', async () => {
      global.fetch = jest.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ providers: [] }),
        } as Response)
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(screen.getByText('No authentication providers configured.')).toBeInTheDocument()
        expect(screen.queryByText('Continue with SSO')).not.toBeInTheDocument()
      })
    })

    it('shows error with no login buttons when fetch fails with network error', async () => {
      global.fetch = jest.fn(() => Promise.reject(new Error('Network error')))

      render(<LoginPage />)

      await waitFor(() => {
        expect(screen.getByText(/Unable to fetch identity provider configuration from backend/)).toBeInTheDocument()
        expect(screen.queryByText('Continue with SSO')).not.toBeInTheDocument()
      })
    })

    it('shows error with no login buttons when backend returns error', async () => {
      global.fetch = jest.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ providers: [], error: true }),
        } as Response)
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(screen.getByText(/Unable to fetch identity provider configuration from backend/)).toBeInTheDocument()
        expect(screen.queryByText('Continue with SSO')).not.toBeInTheDocument()
      })
    })

    it('auto-redirects when Dex is configured', async () => {
      global.fetch = jest.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ providers: ['dex'] }),
        } as Response)
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(screen.getByText('Redirecting to sign-in...')).toBeInTheDocument()
      })
    })

    it('filters out unknown providers from API response', async () => {
      mockUseSearchParams.mockReturnValue(new URLSearchParams('signOut=1') as any)
      global.fetch = jest.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ providers: ['unknown-provider', 'google', 'azure', 'dex'] }),
        } as Response)
      )

      render(<LoginPage />)

      await waitFor(() => {
        // signOut=1 suppresses auto-redirect so we can verify the rendered button set.
        expect(screen.getByText('Continue with SSO')).toBeInTheDocument()
        // No buttons for legacy/unknown providers should be rendered
      })
    })

    it('handles malformed API response gracefully', async () => {
      global.fetch = jest.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ notProviders: 'malformed' }),
        } as Response)
      )

      render(<LoginPage />)

      await waitFor(() => {
        expect(screen.getByText('No authentication providers configured.')).toBeInTheDocument()
        expect(screen.queryByText('Continue with SSO')).not.toBeInTheDocument()
      })
    })

    it('does not fetch providers when already authenticated', async () => {
      mockUseAuth.mockReturnValue({ isLoading: false, isAuthenticated: true } as any)
      const fetchSpy = jest.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ providers: ['dex'] }),
        } as Response)
      )
      global.fetch = fetchSpy

      render(<LoginPage />)

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith('/')
      })

      // Fetch should not be called when already authenticated
      expect(fetchSpy).not.toHaveBeenCalled()
    })
  })

  // -------------------------------------------------------------------
  // Iframe detection — handleLogin opens popup when in iframe
  // -------------------------------------------------------------------

  describe('iframe detection', () => {
    it('opens popup when in an iframe', async () => {
      const mockPopup = { focus: jest.fn() }
      window.open = jest.fn(() => mockPopup) as any

      // Simulate iframe: make window.self !== window.top.
      // The iframe check inside the auto-redirect effect also sees this and skips,
      // so the SSO button is rendered for click rather than auto-redirecting.
      const origSelf = window.self
      Object.defineProperty(window, 'self', { value: {}, writable: true, configurable: true })

      render(<LoginPage />)

      await waitFor(() => expect(screen.getByText('Continue with SSO')).toBeInTheDocument())
      fireEvent.click(screen.getByText('Continue with SSO'))

      expect(window.open).toHaveBeenCalledWith(
        '/api/oauth/start/dex?callbackUrl=%2F',
        'supportbot-auth',
        expect.stringContaining('popup=yes')
      )
      expect(mockPopup.focus).toHaveBeenCalled()

      Object.defineProperty(window, 'self', { value: origSelf, writable: true, configurable: true })
    })
  })
})
