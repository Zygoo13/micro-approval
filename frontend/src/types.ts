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

export type AiProviderType = 'OPENAI' | 'GOOGLE_GENAI'
export interface AiConfiguration { configured: boolean; provider: AiProviderType | null; model: string | null; enabled: boolean; keySuffix: string | null; updatedAt: string | null }
