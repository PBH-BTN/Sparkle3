export class ApiError extends Error {
  status: number
  payload: unknown

  constructor(message: string, status: number, payload?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

function isJsonResponse(response: Response) {
  return response.headers.get('content-type')?.includes('application/json')
}

export async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(url, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  const payload = isJsonResponse(response) ? await response.json() : null
  if (!response.ok) {
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('sparkle:unauthorized'))
    }
    const message =
      payload && typeof payload === 'object' && 'message' in payload
        ? String(payload.message)
        : `HTTP ${response.status}`
    throw new ApiError(message, response.status, payload)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return payload as T
}
