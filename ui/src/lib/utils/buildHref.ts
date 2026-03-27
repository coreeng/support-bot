/**
 * Builds a URL href by merging a base path with a set of query parameters.
 * Params whose value is null, undefined, or an empty string are omitted so the
 * URL stays clean when optional values are absent.
 *
 * @example
 * buildHref('/tickets', { team: 'platform' })   // '/tickets?team=platform'
 * buildHref('/tickets', { team: null })          // '/tickets'
 * buildHref('/tickets', { team: 'a', page: '2' }) // '/tickets?team=a&page=2'
 */
export function buildHref(
  path: string,
  params: Record<string, string | null | undefined>,
): string {
  const qs = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      qs.set(key, value)
    }
  }
  const queryString = qs.toString()
  return queryString ? `${path}?${queryString}` : path
}

