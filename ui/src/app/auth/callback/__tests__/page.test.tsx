import { render, screen, waitFor } from '@testing-library/react'
import { useRouter, useSearchParams } from 'next/navigation'
import AuthCallbackPage from '../page'
import { useAuth } from '../../../../contexts/AuthContext'
import { exchangeCodeForToken } from '../../../../lib/auth/token'

jest.mock('next/navigation', () => ({
  useRouter: jest.fn(),
  useSearchParams: jest.fn(),
}))

jest.mock('../../../../contexts/AuthContext', () => ({
  useAuth: jest.fn(),
}))

jest.mock('../../../../lib/auth/token', () => ({
  exchangeCodeForToken: jest.fn(),
}))

describe('AuthCallbackPage', () => {
  const mockReplace = jest.fn()
  const mockGet = jest.fn()
  const mockRefreshUser = jest.fn()
  const mockExchangeCodeForToken = exchangeCodeForToken as jest.MockedFunction<typeof exchangeCodeForToken>
  const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>
  const mockUseRouter = useRouter as jest.MockedFunction<typeof useRouter>
  const mockUseSearchParams = useSearchParams as jest.MockedFunction<typeof useSearchParams>

  beforeEach(() => {
    jest.clearAllMocks()
    
    mockUseRouter.mockReturnValue({
      replace: mockReplace,
    } as any)

    mockUseSearchParams.mockReturnValue({
      get: mockGet,
    } as any)

    mockUseAuth.mockReturnValue({
      refreshUser: mockRefreshUser,
    } as any)

    mockRefreshUser.mockResolvedValue(undefined)

    Storage.prototype.getItem = jest.fn()
    Storage.prototype.removeItem = jest.fn()
    Object.defineProperty(window, 'opener', { value: null, writable: true, configurable: true })
    window.close = jest.fn()
  })

  afterEach(() => {
    jest.restoreAllMocks()
  })

  it('shows error when no authorization code is provided', () => {
    mockGet.mockReturnValue(null)

    render(<AuthCallbackPage />)

    expect(screen.getByText('Authentication Error')).toBeInTheDocument()
    expect(screen.getByText('No authorization code received')).toBeInTheDocument()
  })

  it('exchanges code for token and redirects to home', async () => {
    const uniqueCode = `code_${Date.now()}`
    mockGet.mockReturnValue(uniqueCode)
    mockExchangeCodeForToken.mockResolvedValue('jwt_token')
    jest.spyOn(Storage.prototype, 'getItem').mockReturnValue(null)

    render(<AuthCallbackPage />)

    await waitFor(() => {
      expect(mockExchangeCodeForToken).toHaveBeenCalledWith(uniqueCode)
    }, { timeout: 3000 })
    
    await waitFor(() => {
      expect(mockRefreshUser).toHaveBeenCalled()
      expect(mockReplace).toHaveBeenCalledWith('/')
    }, { timeout: 3000 })
  })

  it('shows error when code exchange fails', async () => {
    const uniqueCode = `code_${Date.now()}`
    mockGet.mockReturnValue(uniqueCode)
    mockExchangeCodeForToken.mockRejectedValue(new Error('Invalid code'))

    render(<AuthCallbackPage />)

    await waitFor(() => {
      expect(screen.getByText('Authentication Error')).toBeInTheDocument()
      expect(screen.getByText('Authentication failed. Please try again.')).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  it('sends token to opener window and closes popup', async () => {
    const uniqueCode = `code_${Date.now()}`
    mockGet.mockReturnValue(uniqueCode)
    mockExchangeCodeForToken.mockResolvedValue('jwt_token')
    
    const mockOpener = { postMessage: jest.fn(), closed: false }
    Object.defineProperty(window, 'opener', { value: mockOpener, writable: true, configurable: true })
    const mockClose = jest.fn()
    window.close = mockClose

    render(<AuthCallbackPage />)

    await waitFor(() => {
      expect(mockExchangeCodeForToken).toHaveBeenCalledWith(uniqueCode)
    }, { timeout: 3000 })

    await waitFor(() => {
      expect(mockOpener.postMessage).toHaveBeenCalledWith(
        { type: 'auth:success', token: 'jwt_token' },
        window.location.origin
      )
      expect(mockClose).toHaveBeenCalled()
    }, { timeout: 3000 })
  })

  it('redirects to stored return path after authentication', async () => {
    const uniqueCode = `code_${Date.now()}`
    mockGet.mockReturnValue(uniqueCode)
    mockExchangeCodeForToken.mockResolvedValue('jwt_token')
    jest.spyOn(Storage.prototype, 'getItem').mockReturnValue('/tickets')
    const mockRemoveItem = jest.spyOn(Storage.prototype, 'removeItem')

    render(<AuthCallbackPage />)

    await waitFor(() => {
      expect(mockExchangeCodeForToken).toHaveBeenCalledWith(uniqueCode)
    }, { timeout: 3000 })

    await waitFor(() => {
      expect(mockRemoveItem).toHaveBeenCalledWith('auth_return_to')
      expect(mockReplace).toHaveBeenCalledWith('/tickets')
    }, { timeout: 3000 })
  })
})
