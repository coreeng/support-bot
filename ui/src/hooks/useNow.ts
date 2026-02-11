import { useRef } from 'react'

/**
 * Returns a stable timestamp that is captured once per render cycle.
 * This avoids calling Date.now() multiple times during render, which
 * violates React's purity rules.
 */
export function useNow(): number {
    const ref = useRef<number | null>(null)

    if (ref.current === null) {
        // eslint-disable-next-line react-hooks/purity -- Intentional: this is the single controlled point of impurity
        ref.current = Date.now()
    }

    const now = ref.current
    ref.current = null

    return now
}
