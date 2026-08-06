export type AnalysisMode = 'RAW_SNIPPET' | 'INTENT_MATCHING' | 'GIT_DIFF'
export type DecisionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type SessionStatus = 'PENDING' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'COMPLETED'

export interface AuthResponse {
  token: string
  userId: string
  fullName: string
  email: string
}

export interface MicroDecision {
  id: string
  engineType: 'RULE_BASED' | 'AI_BASED'
  riskCategory: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  codeSnippet: string
  questionText: string
  humanDecision: DecisionStatus
  reviewerNote: string | null
  decidedByName: string | null
  decidedAt: string | null
  displayOrder: number
}

export interface PersonalSession {
  id: string
  title: string
  mode: AnalysisMode
  rawContent: string
  promptContent: string | null
  status: SessionStatus
  aiAnalysisStatus: 'NOT_REQUESTED' | 'SUCCEEDED' | 'FALLBACK' | 'DISABLED'
  aiAnalysisError: string | null
  aiTokenUsed: number
  createdAt: string
  completedAt: string | null
  decisions: MicroDecision[]
}

export type SharedReviewSessionMode = AnalysisMode
export type SharedReviewSessionStatus = SessionStatus
export type AiAnalysisStatus = 'NOT_REQUESTED' | 'SUCCEEDED' | 'FALLBACK' | 'DISABLED'
export type SharedDecisionCard = MicroDecision

export interface CreateSharedReviewSessionRequest {
  title: string
  mode: SharedReviewSessionMode
  rawContent: string
  promptContent?: string
}

export interface SharedReviewSessionSummary {
  id: string
  workspaceId: string
  workspaceType: 'SHARED'
  title: string
  mode: SharedReviewSessionMode
  status: SharedReviewSessionStatus
  aiAnalysisStatus: AiAnalysisStatus
  createdByUserId: string
  createdByDisplayName: string
  createdAt: string
}

export interface SharedReviewSessionDetail extends SharedReviewSessionSummary {
  rawContent: string
  promptContent: string | null
  aiAnalysisError: string | null
  aiTokenUsed: number
  completedAt: string | null
  decisions: SharedDecisionCard[]
}

export type ReviewSessionReviewerStatus = 'ASSIGNED' | 'REMOVED'

export interface SessionReviewer {
  assignmentId: string
  sessionId: string
  workspaceMemberId: string
  userId: string
  displayName: string
  email: string
  workspaceRole: WorkspaceRole
  status: ReviewSessionReviewerStatus
  assignedByUserId: string
  assignedByDisplayName: string
  assignedAt: string
  removedAt: string | null
  removedByUserId: string | null
  removalReason: string | null
  version: number
}

export interface AssignSessionReviewerRequest {
  workspaceMemberId: string
}

export interface RemoveSessionReviewerRequest {
  reason: string
}

export type AiProviderType = 'OPENAI' | 'GOOGLE_GENAI'
export interface AiConfiguration { configured: boolean; provider: AiProviderType | null; model: string | null; enabled: boolean; keySuffix: string | null; updatedAt: string | null }

export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'REVIEWER' | 'MEMBER' | 'AUDITOR'
export type WorkspaceMemberRole = WorkspaceRole
export type WorkspaceMembershipStatus = 'PENDING' | 'ACTIVE' | 'REMOVED'
export type WorkspaceInvitationRole = Exclude<WorkspaceRole, 'OWNER'>
export type WorkspaceInvitationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'REVOKED'
  | 'EXPIRED'

export interface WorkspaceMember {
  membershipId: string
  userId: string
  email: string
  displayName: string
  role: WorkspaceMemberRole
  status: WorkspaceMembershipStatus
  joinedAt: string
}

export interface AddWorkspaceMemberRequest {
  email: string
  role: Exclude<WorkspaceMemberRole, 'OWNER'>
}

export interface UpdateWorkspaceMemberRoleRequest {
  role: Exclude<WorkspaceMemberRole, 'OWNER'>
}

export interface CreateWorkspaceInvitationRequest {
  email: string
  role: WorkspaceInvitationRole
}

export interface WorkspaceInvitation {
  id: string
  workspaceId: string
  email: string
  role: WorkspaceInvitationRole
  status: WorkspaceInvitationStatus
  invitedByUserId: string
  invitedByDisplayName: string
  createdAt: string
  expiresAt: string
  respondedAt: string | null
}

export interface MyWorkspaceInvitation {
  id: string
  workspaceId: string
  workspaceName: string
  role: WorkspaceInvitationRole
  status: WorkspaceInvitationStatus
  invitedByDisplayName: string
  createdAt: string
  expiresAt: string
}

export interface CreateWorkspaceRequest {
  name: string
  description?: string
}

export interface WorkspaceSummary {
  id: string
  name: string
  description: string | null
  ownerId: string
  currentUserRole: WorkspaceRole
  createdAt: string
}

export interface WorkspaceDetail extends WorkspaceSummary {
  updatedAt: string
}

export interface ApiProblem {
  detail?: string
  errors?: Record<string, string>
}
