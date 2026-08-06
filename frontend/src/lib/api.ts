import type {
  AddWorkspaceMemberRequest,
  AssignSessionReviewerRequest,
  AiConfiguration,
  AiProviderType,
  AnalysisMode,
  ApiProblem,
  AuthResponse,
  CreateWorkspaceRequest,
  CreateWorkspaceInvitationRequest,
  CreateSharedReviewSessionRequest,
  DecisionStatus,
  MicroDecision,
  PersonalSession,
  SharedReviewSessionDetail,
  SharedReviewSessionSummary,
  MyWorkspaceInvitation,
  RemoveSessionReviewerRequest,
  SessionReviewer,
  SessionVoting,
  UpsertTeamVoteRequest,
  UpdateWorkspaceMemberRoleRequest,
  WorkspaceDetail,
  WorkspaceInvitation,
  WorkspaceMember,
  WorkspaceSummary,
} from '../types'

const TOKEN_KEY = 'micro-approval-token'
const API_PREFIX = '/gateway/v1'
const WORKSPACE_API_PREFIX = '/gateway/workspaces'

export const auth = {
  getToken: () => localStorage.getItem(TOKEN_KEY),
  setToken: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clearToken: () => localStorage.removeItem(TOKEN_KEY),
  isAuthenticated: () => Boolean(localStorage.getItem(TOKEN_KEY)),
  getCurrentUserEmail: () => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) return null
    try {
      const payload = token.split('.')[1]
      if (!payload) return null
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
      const normalized = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
      const decoded = JSON.parse(atob(normalized)) as unknown
      return isObject(decoded) && typeof decoded.sub === 'string' ? decoded.sub : null
    } catch {
      return null
    }
  },
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number | null,
    public readonly validationErrors: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function normalizeProblem(value: unknown): ApiProblem {
  if (!isObject(value)) return {}

  const errors = isObject(value.errors)
    ? Object.fromEntries(
        Object.entries(value.errors).filter(
          (entry): entry is [string, string] => typeof entry[1] === 'string',
        ),
      )
    : undefined

  return {
    detail: typeof value.detail === 'string' ? value.detail : undefined,
    errors,
  }
}

function fallbackMessage(status: number): string {
  if (status === 400) return 'Dữ liệu gửi lên không hợp lệ.'
  if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
  if (status === 404) return 'Không tìm thấy tài nguyên được yêu cầu.'
  if (status === 409) return 'Dữ liệu đang ở trạng thái xung đột.'
  if (status === 410) return 'Lời mời đã hết hạn.'
  if (status >= 500) return 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.'
  return 'Không thể hoàn tất yêu cầu.'
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = auth.getToken()
  let response: Response

  try {
    response = await fetch(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    })
  } catch {
    throw new ApiError('Không thể kết nối đến máy chủ. Hãy kiểm tra kết nối và thử lại.', null)
  }

  if (!response.ok) {
    const problem = normalizeProblem(await response.json().catch(() => null))
    if (response.status === 401) auth.clearToken()
    throw new ApiError(
      problem.detail ?? fallbackMessage(response.status),
      response.status,
      problem.errors,
    )
  }

  return response.status === 204 ? (undefined as T) : response.json() as Promise<T>
}

export const api = {
  register: (payload: { fullName: string; email: string; password: string }) =>
    request<AuthResponse>(`${API_PREFIX}/auth/register`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  login: (payload: { email: string; password: string }) =>
    request<AuthResponse>(`${API_PREFIX}/auth/login`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  listSessions: () => request<PersonalSession[]>(`${API_PREFIX}/personal/sessions`),
  getSession: (id: string) =>
    request<PersonalSession>(`${API_PREFIX}/personal/sessions/${id}`),
  createSession: (payload: {
    title: string
    mode: AnalysisMode
    rawContent: string
    promptContent?: string
  }) => request<PersonalSession>(`${API_PREFIX}/personal/sessions`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  vote: (
    decisionId: string,
    humanDecision: DecisionStatus,
    reviewerNote?: string,
  ) => request<MicroDecision>(`${API_PREFIX}/personal/sessions/decisions/${decisionId}`, {
    method: 'PATCH',
    body: JSON.stringify({ humanDecision, reviewerNote }),
  }),
  deleteSession: (id: string) =>
    request<void>(`${API_PREFIX}/personal/sessions/${id}`, { method: 'DELETE' }),
  getAiConfiguration: () =>
    request<AiConfiguration>(`${API_PREFIX}/personal/ai-configuration`),
  saveAiConfiguration: (payload: {
    provider: AiProviderType
    model: string
    apiKey?: string
    enabled: boolean
  }) => request<AiConfiguration>(`${API_PREFIX}/personal/ai-configuration`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  deleteAiConfiguration: () =>
    request<void>(`${API_PREFIX}/personal/ai-configuration`, { method: 'DELETE' }),
  testAiConfiguration: () =>
    request<{ success: boolean; message: string }>(
      `${API_PREFIX}/personal/ai-configuration/test`,
      { method: 'POST' },
    ),
  createWorkspace: (payload: CreateWorkspaceRequest) =>
    request<WorkspaceDetail>(WORKSPACE_API_PREFIX, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  getWorkspaces: () => request<WorkspaceSummary[]>(WORKSPACE_API_PREFIX),
  getWorkspaceById: (workspaceId: string) =>
    request<WorkspaceDetail>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}`,
    ),
  getWorkspaceMembers: (workspaceId: string) =>
    request<WorkspaceMember[]>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/members`,
    ),
  addWorkspaceMember: (
    workspaceId: string,
    payload: AddWorkspaceMemberRequest,
  ) => request<WorkspaceMember>(
    `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/members`,
    { method: 'POST', body: JSON.stringify(payload) },
  ),
  updateWorkspaceMemberRole: (
    workspaceId: string,
    memberId: string,
    payload: UpdateWorkspaceMemberRoleRequest,
  ) => request<WorkspaceMember>(
    `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(memberId)}/role`,
    { method: 'PATCH', body: JSON.stringify(payload) },
  ),
  removeWorkspaceMember: (workspaceId: string, memberId: string) =>
    request<void>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(memberId)}`,
      { method: 'DELETE' },
    ),
  getWorkspaceInvitations: (workspaceId: string) =>
    request<WorkspaceInvitation[]>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/invitations`,
    ),
  createWorkspaceInvitation: (
    workspaceId: string,
    payload: CreateWorkspaceInvitationRequest,
  ) => request<WorkspaceInvitation>(
    `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/invitations`,
    { method: 'POST', body: JSON.stringify(payload) },
  ),
  revokeWorkspaceInvitation: (workspaceId: string, invitationId: string) =>
    request<WorkspaceInvitation>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/invitations/${encodeURIComponent(invitationId)}/revoke`,
      { method: 'POST' },
    ),
  getMyWorkspaceInvitations: () =>
    request<MyWorkspaceInvitation[]>('/gateway/workspace-invitations/mine'),
  acceptWorkspaceInvitation: (invitationId: string) =>
    request<WorkspaceInvitation>(
      `/gateway/workspace-invitations/${encodeURIComponent(invitationId)}/accept`,
      { method: 'POST' },
    ),
  rejectWorkspaceInvitation: (invitationId: string) =>
    request<WorkspaceInvitation>(
      `/gateway/workspace-invitations/${encodeURIComponent(invitationId)}/reject`,
      { method: 'POST' },
    ),
  createSharedReviewSession: (
    workspaceId: string,
    payload: CreateSharedReviewSessionRequest,
  ) => request<SharedReviewSessionDetail>(
    `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions`,
    { method: 'POST', body: JSON.stringify(payload) },
  ),
  getSharedReviewSessions: (workspaceId: string) =>
    request<SharedReviewSessionSummary[]>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions`,
    ),
  getSharedReviewSession: (workspaceId: string, sessionId: string) =>
    request<SharedReviewSessionDetail>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions/${encodeURIComponent(sessionId)}`,
    ),
  getSessionReviewers: (workspaceId: string, sessionId: string) =>
    request<SessionReviewer[]>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions/${encodeURIComponent(sessionId)}/reviewers`,
    ),
  assignSessionReviewer: (
    workspaceId: string,
    sessionId: string,
    payload: AssignSessionReviewerRequest,
  ) => request<SessionReviewer>(
    `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions/${encodeURIComponent(sessionId)}/reviewers`,
    { method: 'POST', body: JSON.stringify(payload) },
  ),
  removeSessionReviewer: (
    workspaceId: string,
    sessionId: string,
    assignmentId: string,
    payload: RemoveSessionReviewerRequest,
  ) => request<SessionReviewer>(
    `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions/${encodeURIComponent(sessionId)}/reviewers/${encodeURIComponent(assignmentId)}/remove`,
    { method: 'POST', body: JSON.stringify(payload) },
  ),
  getSessionVoting: (workspaceId: string, sessionId: string) =>
    request<SessionVoting>(
      `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions/${encodeURIComponent(sessionId)}/votes`,
    ),
  upsertTeamVote: (
    workspaceId: string,
    sessionId: string,
    cardId: string,
    payload: UpsertTeamVoteRequest,
  ) => request<SessionVoting>(
    `${WORKSPACE_API_PREFIX}/${encodeURIComponent(workspaceId)}/sessions/${encodeURIComponent(sessionId)}/cards/${encodeURIComponent(cardId)}/vote`,
    { method: 'PUT', body: JSON.stringify(payload) },
  ),
}
