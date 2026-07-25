import type { AiConfiguration, AiProviderType, AnalysisMode, AuthResponse, DecisionStatus, MicroDecision, PersonalSession } from '../types'

const TOKEN_KEY = 'micro-approval-token'
const API_PREFIX = '/gateway/v1'

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
    if (response.status === 401) auth.clearToken()
    throw new Error(problem?.detail ?? 'Không thể kết nối đến máy chủ')
  }

  return response.status === 204 ? (undefined as T) : response.json() as Promise<T>
}

export const api = {
  register: (payload: { fullName: string; email: string; password: string }) =>
    request<AuthResponse>(`${API_PREFIX}/auth/register`, { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload: { email: string; password: string }) =>
    request<AuthResponse>(`${API_PREFIX}/auth/login`, { method: 'POST', body: JSON.stringify(payload) }),
  listSessions: () => request<PersonalSession[]>(`${API_PREFIX}/personal/sessions`),
  getSession: (id: string) => request<PersonalSession>(`${API_PREFIX}/personal/sessions/${id}`),
  createSession: (payload: { title: string; mode: AnalysisMode; rawContent: string; promptContent?: string }) =>
    request<PersonalSession>(`${API_PREFIX}/personal/sessions`, { method: 'POST', body: JSON.stringify(payload) }),
  vote: (decisionId: string, humanDecision: DecisionStatus, reviewerNote?: string) =>
    request<MicroDecision>(`${API_PREFIX}/personal/sessions/decisions/${decisionId}`, {
      method: 'PATCH', body: JSON.stringify({ humanDecision, reviewerNote }),
    }),
  deleteSession: (id: string) => request<void>(`${API_PREFIX}/personal/sessions/${id}`, { method: 'DELETE' }),
  getAiConfiguration: () => request<AiConfiguration>(`${API_PREFIX}/personal/ai-configuration`),
  saveAiConfiguration: (payload: { provider: AiProviderType; model: string; apiKey?: string; enabled: boolean }) => request<AiConfiguration>(`${API_PREFIX}/personal/ai-configuration`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteAiConfiguration: () => request<void>(`${API_PREFIX}/personal/ai-configuration`, { method: 'DELETE' }),
  testAiConfiguration: () => request<{ success: boolean; message: string }>(`${API_PREFIX}/personal/ai-configuration/test`, { method: 'POST' }),
}
