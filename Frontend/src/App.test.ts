import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'
import { getCurrentUser, logout } from './api/auth'

vi.mock('./api/auth', () => ({
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
}))

const currentUser = {
  user_id: '00000000-0000-4000-8000-000000000001',
  memberships: [
    {
      course_id: '10000000-0000-4000-8000-000000000001',
      role: 'STUDENT',
    },
  ],
  trace_id: 'trace-current-user',
  platform_roles: ['AUDITOR'],
}

describe('identity session screen', () => {
  beforeEach(() => {
    vi.mocked(getCurrentUser).mockReset()
    vi.mocked(logout).mockReset()
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    window.history.replaceState({}, '', '/')
  })

  it('shows platform identity and memberships without exposing OIDC details', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(currentUser)

    render(App)

    expect(await screen.findByText(currentUser.user_id)).toBeTruthy()
    expect(screen.getByText(currentUser.memberships[0].course_id)).toBeTruthy()
    expect(screen.getByText('STUDENT')).toBeTruthy()
    expect(screen.getByText('AUDITOR')).toBeTruthy()
    expect(screen.queryByText(/issuer|subject|token/i)).toBeNull()
  })

  it('shows the login action when the session is unauthenticated', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue({ status: 401 })

    render(App)

    const loginLink = await screen.findByRole('link', { name: '校园身份登录' })
    expect(loginLink.getAttribute('href')).toBe('/api/v1/auth/login')
  })

  it('shows a retry action and trace ID when loading fails', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue({
      status: 503,
      message: 'Service unavailable',
      trace_id: 'trace-network-error',
    })

    render(App)

    expect(await screen.findByText('trace-network-error')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重试' })).toBeTruthy()
  })

  it('returns to the signed-out screen after successful logout', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(currentUser)
    vi.mocked(logout).mockResolvedValue(undefined)

    render(App)

    await screen.findByText(currentUser.user_id)
    await fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    expect(await screen.findByRole('link', { name: '校园身份登录' })).toBeTruthy()
    expect(logout).toHaveBeenCalledOnce()
  })

  it('shows a generic authentication failure without rendering provider details', async () => {
    window.history.replaceState({}, '', '/?auth_error=1&error_description=private')
    vi.mocked(getCurrentUser).mockRejectedValue({ status: 401 })

    render(App)

    expect(await screen.findByText('身份认证未完成，请重试。')).toBeTruthy()
    expect(screen.queryByText('private')).toBeNull()
  })

  it('retries loading the current user from the error state', async () => {
    vi.mocked(getCurrentUser)
      .mockRejectedValueOnce({ status: 503, message: 'unavailable' })
      .mockResolvedValueOnce(currentUser)

    render(App)
    await screen.findByRole('button', { name: '重试' })
    await fireEvent.click(screen.getByRole('button', { name: '重试' }))

    await waitFor(() => expect(screen.getByText(currentUser.user_id)).toBeTruthy())
  })
})
