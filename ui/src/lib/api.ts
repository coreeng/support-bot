const BASE_URL = '/api';  // now points to our Next.js proxy

// Helper to get headers for API requests
function getApiHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}

  // Add iframe detection header if running in iframe
  if (typeof window !== 'undefined') {
    try {
      if (window.self !== window.top) {
        headers['x-iframe-request'] = 'true'
      }
    } catch {
      // If access to window.top is blocked, assume iframe
      headers['x-iframe-request'] = 'true'
    }
  }

  return headers
}

export async function apiGet(path: string) {
  const headers = getApiHeaders()
  const res = await fetch(`${BASE_URL}${path}`, { headers })
  if (!res.ok) throw new Error(`Error fetching ${path}`)
  return res.json()
}

export async function apiPost(path: string, body: unknown) {
  const headers = {
    'Content-Type': 'application/json',
    ...getApiHeaders(),
  }
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`Error posting to ${path}`)
  return res.json()
}

export async function apiPut(path: string, body: unknown) {
  const headers = {
    'Content-Type': 'application/json',
    ...getApiHeaders(),
  }
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`Error updating ${path}`)
  return res.json()
}

export async function apiPatch(path: string, body: unknown) {
  const headers = {
    'Content-Type': 'application/json',
    ...getApiHeaders(),
  }
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const errorText = await res.text().catch(() => 'Unknown error')
    throw new Error(`Error updating ${path}: ${errorText}`)
  }
  return res.json()
}
