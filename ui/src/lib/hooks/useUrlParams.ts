'use client'

import { useCallback, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'

type StringRecord = Record<string, string>

/**
 * Reads and writes URL search parameters for a given set of keys.
 *
 * Usage
 * -----
 * ```ts
 * const [params, setParams] = useUrlParams({
 *   dateFilter: 'lastWeek',
 *   dateFrom: '',
 *   dateTo: '',
 * })
 *
 * // Read:  params.dateFilter          => 'lastWeek' (from URL, or the default)
 * // Write: setParams({ dateFilter: 'custom', dateFrom: '2025-01-01' })
 * ```
 *
 * Notes
 * -----
 * - Only keys present in `defaults` are managed by this hook; all other search
 *   params (e.g. the global `team` param owned by TeamFilterContext) are left
 *   untouched when the setter is called.
 * - Empty-string values are **removed** from the URL to keep it clean.
 * - Uses `router.replace()` so filter changes do not add browser-history entries.
 * - Requires the component tree to be inside a `<Suspense>` boundary because
 *   Next.js App Router requires it for `useSearchParams`.
 * - Pass `defaults` as a stable reference (a module-level constant or `useMemo`)
 *   to avoid unnecessary recomputation. The hook captures the initial value via
 *   `useState` so inline object literals are safe too.
 */
export function useUrlParams<T extends StringRecord>(
  defaults: T,
): [T, (updates: Partial<T>) => void] {
  // Capture defaults once on mount via useState so callers can pass inline
  // object literals without causing the memoised params to recompute every render.
  // useState's initialiser only runs on the first render (unlike useMemo).
  const [stableDefaults] = useState<T>(defaults)

  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const params = useMemo<T>(() => {
    // Widen to Record<string, string> for the write loop — TypeScript cannot
    // verify that T[keyof T] is assignable to result[specificKey] through a
    // generic indexed write. The cast is safe because T extends Record<string, string>.
    const result: Record<string, string> = { ...stableDefaults }
    for (const key of Object.keys(stableDefaults)) {
      const value = searchParams.get(key)
      if (value !== null) {
        result[key] = value
      }
    }
    return result as T
  }, [searchParams, stableDefaults])

  const setParams = useCallback(
    (updates: Partial<T>) => {
      // Start from the *full* current search string so params owned by other
      // hooks (e.g. `team`) are preserved.
      const next = new URLSearchParams(searchParams.toString())

      for (const [key, value] of Object.entries(updates)) {
        if (value === '' || value === undefined || value === null) {
          // Remove the param entirely when the value is empty/unset.
          next.delete(key)
        } else {
          next.set(key, value as string)
        }
      }

      const qs = next.toString()
      router.replace(qs ? `?${qs}` : pathname)
    },
    [router, pathname, searchParams],
  )

  return [params, setParams]
}

