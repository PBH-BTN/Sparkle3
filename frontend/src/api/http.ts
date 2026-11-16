import { Message } from '@arco-design/web-vue'
import i18n from '@/i18n'

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

function readCookie(name: string) {
  const prefix = `${name}=`
  const entry = document.cookie.split('; ').find((cookie) => cookie.startsWith(prefix))
  return entry ? entry.slice(prefix.length) : null
}

async function performRequest<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const method = (init.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'TRACE', 'OPTIONS'].includes(method)) {
    const csrfToken = readCookie('XSRF-TOKEN')
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', decodeURIComponent(csrfToken))
    }
  }

  const response = await fetch(url, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (response.status === 401) {
    window.dispatchEvent(new CustomEvent('sparkle:unauthorized'))
  }

  let payload: unknown = null
  if (response.status !== 204 && isJsonResponse(response)) {
    try {
      payload = await response.json()
    } catch {
      throw new ApiError(i18n.global.t('common.invalidResponse'), response.status)
    }
  }
  if (!response.ok) {
    const message =
      payload && typeof payload === 'object' && 'message' in payload
        && typeof payload.message === 'string' && payload.message.trim()
        ? String(payload.message)
        : i18n.global.t('common.requestFailed')
    const translated = message === 'content_moderation_rejected'
      ? i18n.global.t('common.contentModerationRejected')
      : message === 'user_roles_changed'
        ? i18n.global.t('admin.users.rolesChanged')
        : message
    throw new ApiError(translated, response.status, payload)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return payload as T
}

export async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  try {
    return await performRequest<T>(url, init)
  } catch (error) {
    const failure = error instanceof ApiError
      ? error
      : new ApiError(i18n.global.t('common.networkError'), 0)
    Message.error({
      id: 'api-request-error',
      content: failure.message,
      duration: 5000,
      closable: true,
    })
    throw failure
  }
}
