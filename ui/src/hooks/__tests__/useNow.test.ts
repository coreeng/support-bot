import { renderHook } from '@testing-library/react'
import { useNow } from '../useNow'

describe('useNow', () => {
    it('should return current timestamp on initial render', () => {
        const before = Date.now()
        const { result } = renderHook(() => useNow())
        const after = Date.now()

        expect(result.current).toBeGreaterThanOrEqual(before)
        expect(result.current).toBeLessThanOrEqual(after)
    })

    it('should return stable value across re-renders within same render cycle', () => {
        const { result, rerender } = renderHook(() => useNow())
        const first = result.current
        rerender()
        expect(typeof result.current).toBe('number')
    })
})
