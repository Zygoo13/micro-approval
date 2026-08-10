import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, auth } from './api'

const authResponse = {
  token: 'new-token',
  userId: 'user-id',
  email: 'user@example.test',
  fullName: 'User',
}

describe('public authentication requests', () => {
  afterEach(() => {
    auth.clearToken()
    vi.unstubAllGlobals()
  })

  it.each([
    ['login', () => api.login({ email: 'user@example.test', password: 'Password123!' })],
    ['register', () => api.register({ fullName: 'User', email: 'user@example.test', password: 'Password123!' })],
  ])('does not send a stale Authorization header when calling %s', async (_name, call) => {
    auth.setToken('stale-token')
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(authResponse), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await call()

    expect(fetchMock).toHaveBeenCalledOnce()
    const requestOptions = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(requestOptions.headers).not.toHaveProperty('Authorization')
  })
})
