import { getToken, clearToken } from './auth/token'

// Custom error class for API errors
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public statusText: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

// Helper to get headers for API requests
function getApiHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}

  // Add auth token if available
  const token = getToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

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

// Handle API response
async function handleResponse(res: Response, path: string) {
  if (res.status === 401) {
    // Token expired or invalid - clear it and redirect to login
    clearToken()
    if (typeof window !== 'undefined') {
      sessionStorage.setItem('auth_return_to', window.location.pathname)
      window.location.href = '/login'
    }
    throw new ApiError('Unauthorized', res.status, res.statusText)
  }

  if (!res.ok) {
    const errorText = await res.text().catch(() => 'Unknown error')
    throw new ApiError(`Error with ${path}: ${errorText}`, res.status, res.statusText)
  }

  return res.json()
}

export async function apiGet(path: string) {
  const headers = getApiHeaders()
  const res = await fetch(`/backend${path}`, { headers })
  return handleResponse(res, path)
}

export async function apiPost(path: string, body: unknown) {
  const headers = {
    'Content-Type': 'application/json',
    ...getApiHeaders(),
  }
  const res = await fetch(`/backend${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })
  return handleResponse(res, path)
}

export async function apiPut(path: string, body: unknown) {
  const headers = {
    'Content-Type': 'application/json',
    ...getApiHeaders(),
  }
  const res = await fetch(`/backend${path}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(body),
  })
  return handleResponse(res, path)
}

export async function apiPatch(path: string, body: unknown) {
  const headers = {
    'Content-Type': 'application/json',
    ...getApiHeaders(),
  }
  const res = await fetch(`/backend${path}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(body),
  })
  return handleResponse(res, path)
}

export async function apiDelete(path: string) {
  const headers = getApiHeaders()
  const res = await fetch(`/backend${path}`, {
    method: 'DELETE',
    headers,
  })
  return handleResponse(res, path)
}
