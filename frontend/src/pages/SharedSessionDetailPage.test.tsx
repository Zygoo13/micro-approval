import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../lib/api'
import type { SharedReviewSessionDetail, WorkspaceDetail } from '../types'
import SharedSessionDetailPage from './SharedSessionDetailPage'

const workspace: WorkspaceDetail = {
  id: 'workspace-1', name: 'Payments', description: null, ownerId: 'owner-1',
  currentUserRole: 'MEMBER', createdAt: '2026-08-06T01:00:00', updatedAt: '2026-08-06T01:00:00',
}

const detail: SharedReviewSessionDetail = {
  id: 'session-1', workspaceId: 'workspace-1', workspaceType: 'SHARED',
  title: 'Payment review', mode: 'GIT_DIFF', rawContent: 'diff --git ...', promptContent: null,
  status: 'PENDING', aiAnalysisStatus: 'SUCCEEDED', aiAnalysisError: null, aiTokenUsed: 27,
  createdByUserId: 'owner-1', createdByDisplayName: 'Owner User', createdAt: '2026-08-06T02:00:00',
  completedAt: null,
  decisions: [
    { id: 'rule-card', engineType: 'RULE_BASED', riskCategory: 'SECURITY', riskLevel: 'HIGH', codeSnippet: 'delete user', questionText: 'Có nên xóa kiểm tra quyền?', humanDecision: 'PENDING', reviewerNote: null, decidedByName: null, decidedAt: null, displayOrder: 1 },
    { id: 'ai-card', engineType: 'AI_BASED', riskCategory: 'BUSINESS_LOGIC', riskLevel: 'MEDIUM', codeSnippet: 'amount', questionText: 'Giá trị âm đã được kiểm tra chưa?', humanDecision: 'APPROVED', reviewerNote: 'Đã kiểm tra', decidedByName: 'Reviewer', decidedAt: '2026-08-06T03:00:00', displayOrder: 2 },
  ],
}

afterEach(() => vi.restoreAllMocks())

function renderPage(response: SharedReviewSessionDetail = detail) {
  vi.spyOn(api, 'getWorkspaceById').mockResolvedValue(workspace)
  vi.spyOn(api, 'getSharedReviewSession').mockResolvedValue(response)
  return render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/session-1']}>
    <Routes><Route path="/workspaces/:workspaceId/sessions/:sessionId" element={<SharedSessionDetailPage />} /></Routes>
  </MemoryRouter>)
}

describe('SharedSessionDetailPage', () => {
  it('renders loading then metadata and Rule/AI cards', async () => {
    vi.spyOn(api, 'getWorkspaceById').mockReturnValue(new Promise(() => undefined))
    vi.spyOn(api, 'getSharedReviewSession').mockReturnValue(new Promise(() => undefined))
    const view = render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/session-1']}>
      <Routes><Route path="/workspaces/:workspaceId/sessions/:sessionId" element={<SharedSessionDetailPage />} /></Routes>
    </MemoryRouter>)
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải Shared Review Session')
    view.unmount()
    vi.restoreAllMocks()

    renderPage()
    expect(await screen.findByRole('heading', { name: 'Payment review' })).toBeInTheDocument()
    expect(screen.getByText('Owner User', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('Nguồn: RULE')).toBeInTheDocument()
    expect(screen.getByText('Nguồn: AI')).toBeInTheDocument()
    expect(screen.getByText('Có nên xóa kiểm tra quyền?')).toBeInTheDocument()
    expect(screen.getByText('Giá trị âm đã được kiểm tra chưa?')).toBeInTheDocument()
  })

  it('treats AI fallback as a successful session with Rule cards', async () => {
    renderPage({
      ...detail,
      aiAnalysisStatus: 'FALLBACK',
      aiAnalysisError: 'provider detail must not be displayed',
      decisions: [detail.decisions[0]],
    })
    expect(await screen.findByText('Rule fallback')).toBeInTheDocument()
    expect(screen.getByText(/Rule Engine vẫn được giữ lại/)).toBeInTheDocument()
    expect(screen.queryByText('provider detail must not be displayed')).not.toBeInTheDocument()
    expect(screen.getByText('Nguồn: RULE')).toBeInTheDocument()
  })

  it('renders an empty safe state when there are no cards', async () => {
    renderPage({ ...detail, status: 'APPROVED', decisions: [] })
    expect(await screen.findByText('Không phát hiện rủi ro cần quyết định')).toBeInTheDocument()
    expect(screen.getByText('Đã duyệt')).toBeInTheDocument()
  })

  it.each([
    ['missing session', new ApiError('Không tìm thấy.', 404)],
    ['session from another workspace', new ApiError('Không tìm thấy.', 404)],
  ])('does not render %s after the API hides it', async (_name, apiError) => {
    vi.spyOn(api, 'getWorkspaceById').mockResolvedValue(workspace)
    vi.spyOn(api, 'getSharedReviewSession').mockRejectedValue(apiError)
    render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/missing']}>
      <Routes><Route path="/workspaces/:workspaceId/sessions/:sessionId" element={<SharedSessionDetailPage />} /></Routes>
    </MemoryRouter>)
    expect(await screen.findByText('Không tìm thấy Shared Review Session')).toBeInTheDocument()
    expect(screen.queryByText('Payment review')).not.toBeInTheDocument()
  })
})
