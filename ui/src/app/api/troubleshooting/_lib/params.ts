const DIGITS = /^\d{1,18}$/;

/** Path segments are copied into the backend URL, so only plain positive integers are let through. */
export function parseId(value: string): number | null {
  return DIGITS.test(value) ? Number(value) : null;
}

export function badRequest(message: string) {
  return Response.json({ error: message }, { status: 400 });
}
