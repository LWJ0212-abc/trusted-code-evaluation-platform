import { afterEach, describe, expect, it, vi } from 'vitest'
import { logout } from './auth'

describe('auth transport', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  })

  it('sends the cookie CSRF token using the documented header name', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value; path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await logout()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({
      method: 'POST',
      credentials: 'same-origin',
      headers: expect.objectContaining({ 'X-CSRF-Token': 'csrf-value' }),
    }))
  })
})
