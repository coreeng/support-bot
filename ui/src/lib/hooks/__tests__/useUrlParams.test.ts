import { renderHook, act } from '@testing-library/react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useUrlParams, enumValidator, nonNegativeIntValidator, isoDateValidator } from '../useUrlParams'

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
      expect(mockReplace).toHaveBeenCalledWith('/test?dateFilter=custom')
    })

    it('merges updates with existing search params not in defaults', () => {
      // `team` is an existing param that this hook does not own — it must be preserved.
      setupMocks({ team: 'platform', dateFilter: 'lastWeek' })
      const { result } = renderHook(() => useUrlParams(DEFAULTS))
      act(() => {
        result.current[1]({ dateFilter: 'lastMonth' })
      })
      const called = mockReplace.mock.calls[0][0] as string
      const resultParams = new URLSearchParams(called.split('?')[1] ?? '')
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
      const resultParams = new URLSearchParams(called.split('?')[1] ?? '')
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
      const resultParams = new URLSearchParams(called.split('?')[1] ?? '')
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

// ---------------------------------------------------------------------------
// enumValidator helper
// ---------------------------------------------------------------------------
describe('enumValidator', () => {
  const validator = enumValidator(['lastWeek', 'lastMonth', 'custom'] as const, 'lastWeek')

  it('accepts a valid value', () => {
    expect(validator('lastMonth', 'lastWeek')).toBe('lastMonth')
  })

  it('rejects an unknown value and returns the explicit fallback', () => {
    expect(validator('garbage', 'lastWeek')).toBe('lastWeek')
  })

  it('uses the defaultValue when no explicit fallback is provided', () => {
    const noFallback = enumValidator(['a', 'b'] as const)
    expect(noFallback('unknown', 'a')).toBe('a')
  })

  it('accepts an empty string when it is listed as a valid value', () => {
    const withEmpty = enumValidator(['', 'lastWeek'] as const)
    expect(withEmpty('', 'lastWeek')).toBe('')
  })
})

// ---------------------------------------------------------------------------
// nonNegativeIntValidator helper
// ---------------------------------------------------------------------------
describe('nonNegativeIntValidator', () => {
  it('passes through a valid non-negative integer string', () => {
    expect(nonNegativeIntValidator('3', '0')).toBe('3')
    expect(nonNegativeIntValidator('0', '0')).toBe('0')
  })

  it('falls back to the default for a negative value', () => {
    expect(nonNegativeIntValidator('-1', '0')).toBe('0')
  })

  it('falls back to the default for NaN (non-numeric string)', () => {
    expect(nonNegativeIntValidator('abc', '0')).toBe('0')
  })

  it('truncates floats', () => {
    expect(nonNegativeIntValidator('2.9', '0')).toBe('2')
  })

  it('falls back to the default for a negative float', () => {
    expect(nonNegativeIntValidator('-1.5', '0')).toBe('0')
  })
})

// ---------------------------------------------------------------------------
// isoDateValidator helper
// ---------------------------------------------------------------------------
describe('isoDateValidator', () => {
  it('passes through a valid YYYY-MM-DD date', () => {
    expect(isoDateValidator('2024-06-15', '')).toBe('2024-06-15')
  })

  it('returns default for an empty string', () => {
    expect(isoDateValidator('', '')).toBe('')
  })

  it('returns default for a plain word (fails regex)', () => {
    expect(isoDateValidator('AAAA', '')).toBe('')
    expect(isoDateValidator('ZZZZ', '')).toBe('')
  })

  it('returns default for a string that passes the regex but is an invalid calendar date', () => {
    expect(isoDateValidator('2024-99-99', '')).toBe('')
    expect(isoDateValidator('2024-02-30', '')).toBe('')
  })

  it('returns default for a datetime string with time component', () => {
    expect(isoDateValidator('2024-06-15T12:00:00Z', '')).toBe('')
  })

  it('returns default for partial date strings', () => {
    expect(isoDateValidator('2024-06', '')).toBe('')
    expect(isoDateValidator('2024', '')).toBe('')
  })
})

// ---------------------------------------------------------------------------
// useUrlParams with validators
// ---------------------------------------------------------------------------
describe('useUrlParams with validators', () => {
  const DEFAULTS_WITH_PAGE = { dateFilter: 'lastWeek', page: '0' }
  const VALIDATORS = {
    dateFilter: enumValidator(['lastWeek', 'lastMonth', 'custom'] as const, 'lastWeek'),
    page: nonNegativeIntValidator,
  }

  it('returns the validated value when the raw URL value is valid', () => {
    setupMocks({ dateFilter: 'lastMonth', page: '2' })
    const { result } = renderHook(() =>
      useUrlParams(DEFAULTS_WITH_PAGE, VALIDATORS),
    )
    expect(result.current[0].dateFilter).toBe('lastMonth')
    expect(result.current[0].page).toBe('2')
  })

  it('falls back to the default for an unrecognised enum value', () => {
    setupMocks({ dateFilter: 'garbage' })
    const { result } = renderHook(() =>
      useUrlParams(DEFAULTS_WITH_PAGE, VALIDATORS),
    )
    expect(result.current[0].dateFilter).toBe('lastWeek')
  })

  it('clamps a negative page to 0 (the default)', () => {
    setupMocks({ page: '-5' })
    const { result } = renderHook(() =>
      useUrlParams(DEFAULTS_WITH_PAGE, VALIDATORS),
    )
    expect(result.current[0].page).toBe('0')
  })

  it('auto-corrects the URL via router.replace when a validator rejects a value', () => {
    setupMocks({ dateFilter: 'garbage', page: '2' })
    renderHook(() => useUrlParams(DEFAULTS_WITH_PAGE, VALIDATORS))
    // The effect runs after render; mockReplace should be called to clean the URL.
    // 'garbage' → default 'lastWeek' which equals the default → removed from URL.
    // 'page=2' is valid so it stays.
    expect(mockReplace).toHaveBeenCalledWith('/test?page=2')
  })

  it('auto-corrects an invalid page by removing it (equals default 0)', () => {
    setupMocks({ page: '-3' })
    renderHook(() => useUrlParams(DEFAULTS_WITH_PAGE, VALIDATORS))
    // -3 → '0' which is the default → param removed → empty URL → pathname
    expect(mockReplace).toHaveBeenCalledWith('/test')
  })

  it('does NOT call router.replace when all URL params are already valid', () => {
    setupMocks({ dateFilter: 'lastMonth', page: '1' })
    renderHook(() => useUrlParams(DEFAULTS_WITH_PAGE, VALIDATORS))
    expect(mockReplace).not.toHaveBeenCalled()
  })

  it('does NOT call router.replace when no validators are provided', () => {
    setupMocks({ dateFilter: 'garbage' })
    renderHook(() => useUrlParams(DEFAULTS))
    expect(mockReplace).not.toHaveBeenCalled()
  })
})

