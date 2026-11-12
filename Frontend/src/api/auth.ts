export interface Membership {
  course_id: string
  role: string
}

export interface CurrentUser {
  user_id: string
  memberships: Membership[]
  trace_id: string
  platform_roles: string[]
}

export interface ApiFailure {
  status?: number
  message?: string
  trace_id?: string
}

async function readFailure(response: Response): Promise<ApiFailure> {
  let body: Partial<ApiFailure> = {}
  try {
    body = await response.json()
  } catch {
    // A proxy/network error may return no JSON body.
  }
  return {
    status: response.status,
    message: body.message,
    trace_id: body.trace_id,
  }
}

export async function getCurrentUser(): Promise<CurrentUser> {
  let response: Response
  try {
    response = await fetch('/api/v1/auth/me', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw { message: '无法连接到服务端。' } satisfies ApiFailure
  }
  if (!response.ok) throw await readFailure(response)
  return response.json() as Promise<CurrentUser>
}

function csrfToken(): string | undefined {
  const cookie = document.cookie
    .split('; ')
    .find((value) => value.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : undefined
}

export async function logout(): Promise<void> {
  const token = csrfToken()
  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(token ? { 'X-CSRF-Token': token } : {}),
    },
  })
  if (!response.ok) throw await readFailure(response)
}
