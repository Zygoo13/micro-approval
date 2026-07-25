import type { AnalysisMode, AuthResponse, DecisionStatus, MicroDecision, PersonalSession } from '../types'

const TOKEN_KEY = 'micro-approval-token'

export const auth = {
  getToken: () => localStorage.getItem(TOKEN_KEY),
  setToken: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clearToken: () => localStorage.removeItem(TOKEN_KEY),
  isAuthenticated: () => Boolean(localStorage.getItem(TOKEN_KEY)),
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = auth.getToken()
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail ?? 'Không thể kết nối đến máy chủ')
  }

  return response.status === 204 ? (undefined as T) : response.json() as Promise<T>
}

export const api = {
  register: (payload: { fullName: string; email: string; password: string }) =>
    request<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload: { email: string; password: string }) =>
    request<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  listSessions: () => request<PersonalSession[]>('/api/v1/personal/sessions'),
  getSession: (id: string) => request<PersonalSession>(`/api/v1/personal/sessions/${id}`),
  createSession: (payload: { title: string; mode: AnalysisMode; rawContent: string; promptContent?: string }) =>
    request<PersonalSession>('/api/v1/personal/sessions', { method: 'POST', body: JSON.stringify(payload) }),
  vote: (decisionId: string, humanDecision: DecisionStatus, reviewerNote?: string) =>
    request<MicroDecision>(`/api/v1/personal/sessions/decisions/${decisionId}`, {
      method: 'PATCH', body: JSON.stringify({ humanDecision, reviewerNote }),
    }),
  deleteSession: (id: string) => request<void>(`/api/v1/personal/sessions/${id}`, { method: 'DELETE' }),
}
