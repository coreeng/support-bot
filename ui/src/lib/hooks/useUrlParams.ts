'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'

type StringRecord = Record<string, string>

/**
 * A validator for a single URL parameter.
 * Receives the raw string from the URL and the parameter's default value.
 * Returns the sanitised value to use. Return `defaultValue` to fall back.
 */
export type ParamValidator = (raw: string, defaultValue: string) => string

/** Per-key validator map, keyed by parameter name. */
export type ParamValidators<T extends StringRecord> = Partial<{
  [K in keyof T]: ParamValidator
}>

/**
 * Returns a validator that accepts only the listed enum values, falling back
 * to `fallback` when provided, or the parameter's own default otherwise.
 *
 * @example
 * enumValidator(['lastWeek', 'lastMonth', 'custom'] as const, 'lastWeek')
 */
export function enumValidator<T extends string>(
  validValues: readonly T[],
  fallback?: T,
): ParamValidator {
  return (raw: string, defaultValue: string): string =>
    (validValues as readonly string[]).includes(raw)
      ? raw
      : (fallback ?? defaultValue)
}

/**
 * Validates a URL parameter as a non-negative integer string.
 * Malformed, negative, or non-numeric values fall back to the parameter's default. Fractional strings are truncated to their integer part by parseInt.
 *
 * @example
 * // In useUrlParams validators: { page: nonNegativeIntValidator }
 */
export const nonNegativeIntValidator: ParamValidator = (
  raw: string,
  defaultValue: string,
): string => {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) && n >= 0 ? String(n) : defaultValue
}

/**
 * Accepts only well-formed calendar dates in `YYYY-MM-DD` format that
 * produce a valid `Date` object.  Anything else (e.g. `'AAAA'`, `'ZZZZ'`,
 * `'2024-99-99'`, `'2024-02-30'`) falls back to the parameter's default
 * (typically an empty string, which removes the param from the URL).
 *
 * @example
 * // In useUrlParams validators: { dateFrom: isoDateValidator, dateTo: isoDateValidator }
 */
export const isoDateValidator: ParamValidator = (
  raw: string,
  defaultValue: string,
): string => {
  if (!raw) return defaultValue
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return defaultValue
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return defaultValue
  // Round-trip check: V8 silently overflows invalid calendar dates
  // (e.g. new Date('2024-02-30') → 2024-03-01).  If the formatted
  // UTC date doesn't match the input, the date was not a real calendar day.
  return d.toISOString().slice(0, 10) === raw ? raw : defaultValue
}

/**
 * Reads and writes URL search parameters for a given set of keys.
 *
 * Usage
 * -----
 * ```ts
 * const [params, setParams] = useUrlParams(
 *   { dateFilter: 'lastWeek', dateFrom: '', dateTo: '', page: '0' },
 *   {
 *     dateFilter: enumValidator(['lastWeek', 'lastMonth', 'custom'] as const),
 *     dateFrom: isoDateValidator,
 *     dateTo: isoDateValidator,
 *     page: nonNegativeIntValidator,
 *   },
 * )
 * ```
 *
 * Notes
 * -----
 * - Only keys present in `defaults` are managed; all other search params
 *   (e.g. the global `team` param) are left untouched when the setter is called.
 * - Empty-string values are **removed** from the URL to keep it clean.
 * - Uses `router.replace()` so filter changes do not add browser-history entries.
 * - When `validators` are provided, any URL param whose raw value fails
 *   validation is silently corrected in the URL (via a `router.replace` in a
 *   `useEffect`). The corrected `params` object is returned immediately so the
 *   page renders correctly even before the URL is updated.
 * - Requires the component tree to be inside a `<Suspense>` boundary because
 *   Next.js App Router requires it for `useSearchParams`.
 * - Pass `defaults` (and `validators`) as stable references to avoid
 *   unnecessary recomputation. The hook captures both via `useState` so inline
 *   object literals are safe too.
 */
export function useUrlParams<T extends StringRecord>(
  defaults: T,
  validators?: ParamValidators<T>,
): [T, (updates: Partial<T>) => void] {
  // Capture defaults and validators once on mount so callers can pass inline
  // object literals without causing memoised values to recompute every render.
  const [stableDefaults] = useState<T>(defaults)
  const [stableValidators] = useState<ParamValidators<T> | undefined>(validators)

  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const params = useMemo<T>(() => {
    // Widen to Record<string, string> for the write loop — TypeScript cannot
    // verify that T[keyof T] is assignable to result[specificKey] through a
    // generic indexed write. The cast is safe because T extends Record<string, string>.
    const result: Record<string, string> = { ...stableDefaults }
    for (const key of Object.keys(stableDefaults)) {
      const raw = searchParams.get(key)
      if (raw !== null) {
        const validator = stableValidators?.[key as keyof T]
        result[key] = validator ? validator(raw, stableDefaults[key]) : raw
      }
    }
    return result as T
  }, [searchParams, stableDefaults, stableValidators])

  // Auto-correct the URL when validators produce a different value than what
  // is in the raw URL. This ensures stale / invalid params are cleaned up
  // silently on page load without crashing or showing wrong data.
  //
  // Loop safety: after correction, the new URL's raw values match the
  // validated values, so `hasCorrection` will be false on the next run.
  useEffect(() => {
    if (!stableValidators) return

    let hasCorrection = false
    const next = new URLSearchParams(searchParams.toString())

    for (const key of Object.keys(stableDefaults)) {
      const validator = stableValidators?.[key as keyof T]
      if (!validator) continue
      const raw = searchParams.get(key)
      if (raw === null) continue
      const validated = params[key]
      if (raw !== validated) {
        hasCorrection = true
        // Remove the param when the corrected value is empty or equals the
        // default (keeps the URL clean). Otherwise write the corrected value.
        if (validated === '' || validated === stableDefaults[key]) {
          next.delete(key)
        } else {
          next.set(key, validated)
        }
      }
    }

    if (!hasCorrection) return
    const qs = next.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname)
  }, [params, searchParams, stableDefaults, stableValidators, router, pathname])

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
      router.replace(qs ? `${pathname}?${qs}` : pathname)
    },
    [router, pathname, searchParams],
  )

  return [params, setParams]
}

