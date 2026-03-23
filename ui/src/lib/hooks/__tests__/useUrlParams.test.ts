import { renderHook, act } from '@testing-library/react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useUrlParams } from '../useUrlParams'

jest.mock('next/navigation', () => ({
  useRouter: jest.fn(),
  useSearchParams: jest.fn(),
  usePathname: jest.fn(),
}))

const mockReplace = jest.fn()

function makeSearchParams(init: Record<string, string> = {}): URLSearchParams {
  return new URLSearchParams(init)
}

function setupMocks(searchParamInit: Record<string, string> = {}) {
  ;(useRouter as jest.Mock).mockReturnValue({ replace: mockReplace })
  ;(useSearchParams as jest.Mock).mockReturnValue(makeSearchParams(searchParamInit))
  ;(usePathname as jest.Mock).mockReturnValue('/test')
}

beforeEach(() => {
  jest.clearAllMocks()
})

const DEFAULTS = { dateFilter: 'lastWeek', dateFrom: '', dateTo: '' }

describe('useUrlParams', () => {
  describe('reading params', () => {
    it('returns defaults when search params are empty', () => {
      setupMocks()
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      expect(result.current[0]).toEqual(DEFAULTS)
    })

    it('reads values from search params, overriding defaults', () => {
      setupMocks({ dateFilter: 'lastMonth', dateFrom: '2025-01-01', dateTo: '2025-01-31' })
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      expect(result.current[0]).toEqual({
        dateFilter: 'lastMonth',
        dateFrom: '2025-01-01',
        dateTo: '2025-01-31',
      })
    })

    it('ignores URL params that are not in the defaults', () => {
      setupMocks({ dateFilter: 'lastMonth', team: 'engineering', unrelated: 'value' })
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      // Only keys in DEFAULTS should appear
      expect(result.current[0]).toEqual({
        dateFilter: 'lastMonth',
        dateFrom: '',
        dateTo: '',
      })
      expect(result.current[0]).not.toHaveProperty('team')
      expect(result.current[0]).not.toHaveProperty('unrelated')
    })

    it('uses the default for any key absent from the URL', () => {
      setupMocks({ dateFilter: 'custom' }) // dateFrom and dateTo are absent
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      expect(result.current[0].dateFrom).toBe('')
      expect(result.current[0].dateTo).toBe('')
    })
  })

  describe('setting params', () => {
    it('calls router.replace with the updated search string', () => {
      setupMocks()
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      act(() => {
        result.current[1]({ dateFilter: 'custom' })
      })
      expect(mockReplace).toHaveBeenCalledWith('?dateFilter=custom')
    })

    it('merges updates with existing search params not in defaults', () => {
      // `team` is an existing param that this hook does not own — it must be preserved.
      setupMocks({ team: 'platform', dateFilter: 'lastWeek' })
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      act(() => {
        result.current[1]({ dateFilter: 'lastMonth' })
      })
      const called = mockReplace.mock.calls[0][0] as string
      const resultParams = new URLSearchParams(called.slice(1))
      expect(resultParams.get('team')).toBe('platform')
      expect(resultParams.get('dateFilter')).toBe('lastMonth')
    })

    it('removes a param from the URL when its value is set to an empty string', () => {
      setupMocks({ dateFilter: 'custom', dateFrom: '2025-01-01', dateTo: '2025-01-31' })
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      act(() => {
        result.current[1]({ dateFilter: 'lastWeek', dateFrom: '', dateTo: '' })
      })
      const called = mockReplace.mock.calls[0][0] as string
      const resultParams = new URLSearchParams(called.slice(1))
      expect(resultParams.has('dateFrom')).toBe(false)
      expect(resultParams.has('dateTo')).toBe(false)
      expect(resultParams.get('dateFilter')).toBe('lastWeek')
    })

    it('calls router.replace with just the pathname when all params are cleared', () => {
      setupMocks({ dateFilter: 'lastWeek' })
      const { result } = renderHook(() => useUrlParams({ dateFilter: 'lastWeek' }))
      act(() => {
        result.current[1]({ dateFilter: '' })
      })
      expect(mockReplace).toHaveBeenCalledWith('/test')
    })

    it('can set multiple params in a single call', () => {
      setupMocks()
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      act(() => {
        result.current[1]({ dateFilter: 'custom', dateFrom: '2025-03-01', dateTo: '2025-03-31' })
      })
      const called = mockReplace.mock.calls[0][0] as string
      const resultParams = new URLSearchParams(called.slice(1))
      expect(resultParams.get('dateFilter')).toBe('custom')
      expect(resultParams.get('dateFrom')).toBe('2025-03-01')
      expect(resultParams.get('dateTo')).toBe('2025-03-31')
    })
  })

  describe('stability', () => {
    it('returns a stable setter reference across re-renders when params do not change', () => {
      setupMocks({ dateFilter: 'lastWeek' })
      const { result, rerender } = renderHook(() => useUrlParams(DEFAULTS))
      const firstSetter = result.current[1]
      rerender()
      expect(result.current[1]).toBe(firstSetter)
    })
  })
})

