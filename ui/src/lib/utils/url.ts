/**
 * Sanitize a callback URL to prevent open-redirect attacks.
 * Only relative paths (starting with "/" but not "//") are allowed.
 * Anything else (absolute URLs, protocol-relative, javascript: etc.) falls back to "/".
 */
export function sanitizeCallbackUrl(url: string | null | undefined): string {
  if (typeof url === "string" && url.startsWith("/") && !url.startsWith("//")) {
    return url;
  }
  return "/";
}
