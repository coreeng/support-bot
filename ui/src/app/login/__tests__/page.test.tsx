import { render, screen, waitFor } from '@testing-library/react'
import { useRouter } from 'next/navigation'
import LoginPage from '../page'
import { useAuth } from '../../../contexts/AuthContext'

jest.mock('next/navigation', () => ({
  useRouter: jest.fn(),
}))

jest.mock('../../../contexts/AuthContext', () => ({
  useAuth: jest.fn(),
}))

describe('LoginPage', () => {
  const mockReplace = jest.fn()
  const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>
  const mockUseRouter = useRouter as jest.MockedFunction<typeof useRouter>

  beforeEach(() => {
    jest.clearAllMocks()
    mockUseRouter.mockReturnValue({
      replace: mockReplace,
    } as any)
  })

  it('shows login form when not authenticated', () => {
    mockUseAuth.mockReturnValue({
      isLoading: false,
      isAuthenticated: false,
    } as any)

    render(<LoginPage />)

    expect(screen.getByText('Sign in')).toBeInTheDocument()
    expect(screen.getByText('Continue with Google')).toBeInTheDocument()
    expect(screen.getByText('Continue with Microsoft')).toBeInTheDocument()
  })

  it('redirects to home if already authenticated', async () => {
    mockUseAuth.mockReturnValue({
      isLoading: false,
      isAuthenticated: true,
    } as any)

    render(<LoginPage />)

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/')
    })
  })

  it('shows loading state while checking auth', () => {
    mockUseAuth.mockReturnValue({
      isLoading: true,
      isAuthenticated: false,
    } as any)

    render(<LoginPage />)

    expect(screen.getByText((content, element) => {
      return element?.className?.includes('animate-spin') ?? false
    })).toBeInTheDocument()
  })
})
