import { render, screen, waitFor, act } from '@testing-library/react'
import { AuthProvider, useAuth } from '../AuthContext'
import * as tokenModule from '../../lib/auth/token'

jest.mock('../../lib/auth/token', () => ({
  isAuthenticated: jest.fn(),
  fetchCurrentUser: jest.fn(),
  logout: jest.fn(),
  clearToken: jest.fn(),
  setToken: jest.fn(),
}))

const TestComponent = () => {
  const auth = useAuth()
  return (
    <div>
      <div data-testid="loading">{auth.isLoading ? 'loading' : 'ready'}</div>
      <div data-testid="authenticated">{auth.isAuthenticated ? 'yes' : 'no'}</div>
      <div data-testid="user">{auth.user?.email || 'none'}</div>
      <button onClick={() => auth.logout()}>Logout</button>
      <button onClick={() => auth.refreshUser()}>Refresh</button>
    </div>
  )
}

describe('AuthContext', () => {
  const mockIsAuthenticated = tokenModule.isAuthenticated as jest.MockedFunction<typeof tokenModule.isAuthenticated>
  const mockFetchCurrentUser = tokenModule.fetchCurrentUser as jest.MockedFunction<typeof tokenModule.fetchCurrentUser>
  const mockLogout = tokenModule.logout as jest.MockedFunction<typeof tokenModule.logout>
  const mockClearToken = tokenModule.clearToken as jest.MockedFunction<typeof tokenModule.clearToken>
  const mockSetToken = tokenModule.setToken as jest.MockedFunction<typeof tokenModule.setToken>

  beforeEach(() => {
    jest.clearAllMocks()
    
    Storage.prototype.getItem = jest.fn()
    Storage.prototype.removeItem = jest.fn()
  })

  afterEach(() => {
    jest.restoreAllMocks()
  })

  it('loads user when authenticated', async () => {
    const mockUser = {
      name: 'Test User',
      email: 'user@example.com',
      isLeadership: false,
      isSupportEngineer: true,
      isEscalation: false,
      teams: [],
    }

    mockIsAuthenticated.mockReturnValue(true)
    mockFetchCurrentUser.mockResolvedValue(mockUser)

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('ready')
      expect(screen.getByTestId('authenticated')).toHaveTextContent('yes')
      expect(screen.getByTestId('user')).toHaveTextContent('user@example.com')
    })
  })

  it('sets user to null when not authenticated', async () => {
    mockIsAuthenticated.mockReturnValue(false)

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('ready')
      expect(screen.getByTestId('authenticated')).toHaveTextContent('no')
      expect(screen.getByTestId('user')).toHaveTextContent('none')
    })
  })

  it('clears token on fetch error', async () => {
    mockIsAuthenticated.mockReturnValue(true)
    mockFetchCurrentUser.mockRejectedValue(new Error('Unauthorized'))

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(mockClearToken).toHaveBeenCalled()
      expect(screen.getByTestId('authenticated')).toHaveTextContent('no')
    })
  })

  it('logs out and clears user', async () => {
    const mockUser = {
      name: 'Test User',
      email: 'user@example.com',
      isLeadership: false,
      isSupportEngineer: true,
      isEscalation: false,
      teams: [],
    }

    mockIsAuthenticated.mockReturnValue(true)
    mockFetchCurrentUser.mockResolvedValue(mockUser)
    mockLogout.mockResolvedValue()

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('user@example.com')
    })

    act(() => {
      screen.getByText('Logout').click()
    })

    await waitFor(() => {
      expect(mockLogout).toHaveBeenCalled()
      expect(screen.getByTestId('user')).toHaveTextContent('none')
    })
  })

  it('receives auth token via postMessage from popup', async () => {
    mockIsAuthenticated.mockReturnValue(false)
    mockFetchCurrentUser.mockResolvedValue({
      name: 'Popup User',
      email: 'popup@example.com',
      isLeadership: false,
      isSupportEngineer: true,
      isEscalation: false,
      teams: [],
    })

    jest.spyOn(Storage.prototype, 'getItem').mockReturnValue('/')

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('authenticated')).toHaveTextContent('no')
    })

    // Use the actual window.location.origin from the test environment
    act(() => {
      window.dispatchEvent(
        new MessageEvent('message', {
          data: { type: 'auth:success', token: 'new_jwt_token' },
          origin: window.location.origin,
        })
      )
    })

    await waitFor(() => {
      expect(mockSetToken).toHaveBeenCalledWith('new_jwt_token')
    })
  })

  it('rejects postMessage from different origin', async () => {
    mockIsAuthenticated.mockReturnValue(false)

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('ready')
    })

    act(() => {
      window.dispatchEvent(
        new MessageEvent('message', {
          data: { type: 'auth:success', token: 'malicious_token' },
          origin: 'https://evil.example.com',
        })
      )
    })

    await waitFor(() => {
      expect(mockSetToken).not.toHaveBeenCalled()
      expect(screen.getByTestId('authenticated')).toHaveTextContent('no')
    })
  })

  it('ignores non-auth messages', async () => {
    mockIsAuthenticated.mockReturnValue(false)

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('ready')
    })

    act(() => {
      window.dispatchEvent(
        new MessageEvent('message', {
          data: { type: 'other:event', token: 'some_token' },
          origin: window.location.origin,
        })
      )
    })

    await waitFor(() => {
      expect(mockSetToken).not.toHaveBeenCalled()
    })
  })
})
