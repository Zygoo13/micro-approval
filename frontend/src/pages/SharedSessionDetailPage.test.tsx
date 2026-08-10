import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, auth } from '../lib/api'
import type { SessionReviewer, SessionVoting, SharedReviewSessionDetail, WorkspaceDetail } from '../types'
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
  closed: false, closedAt: null, closedByUserId: null, closedByDisplayName: null,
  closeReason: null, lifecycleVersion: 0,
  decisions: [
    { id: 'rule-card', engineType: 'RULE_BASED', riskCategory: 'SECURITY', riskLevel: 'HIGH', codeSnippet: 'delete user', questionText: 'Có nên xóa kiểm tra quyền?', humanDecision: 'PENDING', teamDecision: 'PENDING', reviewerNote: null, decidedByName: null, decidedAt: null, displayOrder: 1 },
    { id: 'ai-card', engineType: 'AI_BASED', riskCategory: 'BUSINESS_LOGIC', riskLevel: 'MEDIUM', codeSnippet: 'amount', questionText: 'Giá trị âm đã được kiểm tra chưa?', humanDecision: 'APPROVED', teamDecision: 'APPROVED', reviewerNote: 'Đã kiểm tra', decidedByName: 'Reviewer', decidedAt: '2026-08-06T03:00:00', displayOrder: 2 },
  ],
}

const ownerReviewer: SessionReviewer = {
  assignmentId: 'owner-assignment', sessionId: 'session-1', workspaceMemberId: 'owner-member',
  userId: 'owner-1', displayName: 'Owner User', email: 'owner@example.com', workspaceRole: 'OWNER',
  status: 'ASSIGNED', assignedByUserId: 'owner-1', assignedByDisplayName: 'Owner User',
  assignedAt: '2026-08-06T02:30:00', removedAt: null, removedByUserId: null,
  removalReason: null, version: 0,
}

function votingState(closed: boolean): SessionVoting {
  return {
    sessionId: 'session-1', sessionStatus: 'APPROVED', closed,
    closedAt: closed ? '2026-08-06T04:00:00' : null,
    closedByUserId: closed ? 'owner-1' : null,
    closedByDisplayName: closed ? 'Owner User' : null,
    closeReason: closed ? 'Ready to release' : null,
    lifecycleVersion: closed ? 4 : 3,
    reviewerCount: 1,
    cards: detail.decisions.map(card => ({
      cardId: card.id, teamDecision: 'APPROVED', assignedReviewerCount: 1,
      validVoteCount: 0, votes: [],
    })),
  }
}

afterEach(() => vi.restoreAllMocks())

beforeEach(() => {
  vi.spyOn(api, 'getSessionAuditTimeline').mockResolvedValue({
    sessionId: 'session-1', events: [], page: 0, size: 20,
    totalElements: 0, totalPages: 0, hasNext: false,
  })
})

function renderPage(response: SharedReviewSessionDetail = detail) {
  vi.spyOn(api, 'getWorkspaceById').mockResolvedValue(workspace)
  vi.spyOn(api, 'getSharedReviewSession').mockResolvedValue(response)
  vi.spyOn(api, 'getSessionReviewers').mockResolvedValue([])
  vi.spyOn(api, 'getSessionVoting').mockResolvedValue({
    sessionId: response.id,
    sessionStatus: response.status === 'COMPLETED' ? 'APPROVED' : response.status,
    closed: response.closed,
    closedAt: response.closedAt,
    closedByUserId: response.closedByUserId,
    closedByDisplayName: response.closedByDisplayName,
    closeReason: response.closeReason,
    lifecycleVersion: response.lifecycleVersion,
    reviewerCount: 0,
    cards: response.decisions.map(card => ({
      cardId: card.id,
      teamDecision: card.teamDecision,
      assignedReviewerCount: 0,
      validVoteCount: 0,
      votes: [],
    })),
  } satisfies SessionVoting)
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
    expect(await screen.findByText('Nguồn: RULE')).toBeInTheDocument()
    expect(screen.getByText('Nguồn: AI')).toBeInTheDocument()
    expect(screen.getByText('Có nên xóa kiểm tra quyền?')).toBeInTheDocument()
    expect(screen.getByText('Giá trị âm đã được kiểm tra chưa?')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Reviewers' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Team Voting' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Audit Timeline' })).toBeInTheDocument()
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
    expect(await screen.findByText('Nguồn: RULE')).toBeInTheDocument()
  })

  it('renders an empty safe state when there are no cards', async () => {
    renderPage({ ...detail, status: 'APPROVED', decisions: [] })
    expect(await screen.findByText('Session không có Decision Card — đã duyệt an toàn.')).toBeInTheDocument()
    expect(screen.getAllByText('Đã duyệt')).toHaveLength(4)
  })

  it('close refreshes detail, voting and roster into one authoritative read-only UI', async () => {
    const ownerWorkspace = { ...workspace, currentUserRole: 'OWNER' as const }
    const open = { ...detail, status: 'APPROVED' as const }
    const closed = {
      ...open,
      closed: true,
      closedAt: '2026-08-06T04:00:00',
      closedByUserId: 'owner-1',
      closedByDisplayName: 'Owner User',
      closeReason: 'Ready to release',
      lifecycleVersion: 4,
    }
    vi.spyOn(auth, 'getCurrentUserEmail').mockReturnValue('owner@example.com')
    vi.spyOn(api, 'getWorkspaceById').mockResolvedValue(ownerWorkspace)
    const getDetail = vi.spyOn(api, 'getSharedReviewSession')
      .mockResolvedValueOnce(open)
      .mockResolvedValueOnce(closed)
    const getVoting = vi.spyOn(api, 'getSessionVoting')
      .mockResolvedValueOnce(votingState(false))
      .mockResolvedValue(votingState(true))
    const getReviewers = vi.spyOn(api, 'getSessionReviewers').mockResolvedValue([ownerReviewer])
    vi.spyOn(api, 'getWorkspaceMembers').mockResolvedValue([])
    const close = vi.spyOn(api, 'closeSharedReviewSession').mockResolvedValue({
      sessionId: 'session-1', status: 'APPROVED', closed: true,
      closedAt: closed.closedAt, closedByUserId: 'owner-1',
      closedByDisplayName: 'Owner User', closeReason: 'Ready to release',
      lifecycleVersion: 4,
    })
    const getAudit = vi.mocked(api.getSessionAuditTimeline)
    render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/session-1']}>
      <Routes><Route path="/workspaces/:workspaceId/sessions/:sessionId" element={<SharedSessionDetailPage />} /></Routes>
    </MemoryRouter>)

    expect((await screen.findAllByRole('heading', { name: 'My Vote' })).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Gỡ reviewer' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Đóng session' }))
    await userEvent.type(screen.getByRole('textbox', { name: /Lý do/ }), '  Ready to release  ')
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận đóng' }))

    expect(await screen.findByText('Session đã đóng')).toBeInTheDocument()
    expect(screen.getByText('Ready to release')).toBeInTheDocument()
    expect(screen.queryAllByRole('heading', { name: 'My Vote' })).toHaveLength(0)
    expect(screen.queryByRole('button', { name: 'Phân công' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Gỡ reviewer' })).not.toBeInTheDocument()
    expect(close).toHaveBeenCalledTimes(1)
    expect(getDetail).toHaveBeenCalledTimes(2)
    await waitFor(() => expect(getVoting).toHaveBeenCalledTimes(2))
    expect(getReviewers.mock.calls.length).toBeGreaterThanOrEqual(4)
    await waitFor(() => expect(getAudit).toHaveBeenCalledTimes(2))
  })

  it('reopen refresh restores voting and reviewer controls for ADMIN', async () => {
    const adminWorkspace = { ...workspace, currentUserRole: 'ADMIN' as const }
    const closed = {
      ...detail, status: 'APPROVED' as const, closed: true,
      closedAt: '2026-08-06T04:00:00', closedByUserId: 'owner-1',
      closedByDisplayName: 'Owner User', closeReason: 'Done', lifecycleVersion: 4,
    }
    const reopened = {
      ...closed, closed: false, closedAt: null, closedByUserId: null,
      closedByDisplayName: null, closeReason: null, lifecycleVersion: 5,
    }
    vi.spyOn(auth, 'getCurrentUserEmail').mockReturnValue('owner@example.com')
    vi.spyOn(api, 'getWorkspaceById').mockResolvedValue(adminWorkspace)
    vi.spyOn(api, 'getSharedReviewSession')
      .mockResolvedValueOnce(closed)
      .mockResolvedValueOnce(reopened)
    vi.spyOn(api, 'getSessionVoting')
      .mockResolvedValueOnce(votingState(true))
      .mockResolvedValue(votingState(false))
    vi.spyOn(api, 'getSessionReviewers').mockResolvedValue([ownerReviewer])
    vi.spyOn(api, 'getWorkspaceMembers').mockResolvedValue([])
    const reopen = vi.spyOn(api, 'reopenSharedReviewSession').mockResolvedValue({
      sessionId: 'session-1', status: 'APPROVED', closed: false, closedAt: null,
      closedByUserId: null, closedByDisplayName: null, closeReason: null, lifecycleVersion: 5,
    })
    const getAudit = vi.mocked(api.getSessionAuditTimeline)
    render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/session-1']}>
      <Routes><Route path="/workspaces/:workspaceId/sessions/:sessionId" element={<SharedSessionDetailPage />} /></Routes>
    </MemoryRouter>)

    expect(await screen.findByText('Session đã đóng')).toBeInTheDocument()
    expect(screen.queryAllByRole('heading', { name: 'My Vote' })).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: 'Mở lại session' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận mở lại' }))

    expect(await screen.findByText('Session đang mở')).toBeInTheDocument()
    expect((await screen.findAllByRole('heading', { name: 'My Vote' })).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Phân công' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Gỡ reviewer' })).toBeInTheDocument()
    expect(reopen).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(getAudit).toHaveBeenCalledTimes(2))
  })

  it.each([
    ['missing session', new ApiError('Không tìm thấy.', 404)],
    ['session from another workspace', new ApiError('Không tìm thấy.', 404)],
  ])('does not render %s after the API hides it', async (_name, apiError) => {
    const getReviewers = vi.spyOn(api, 'getSessionReviewers')
    const getVoting = vi.spyOn(api, 'getSessionVoting')
    const getAudit = vi.mocked(api.getSessionAuditTimeline)
    vi.spyOn(api, 'getWorkspaceById').mockResolvedValue(workspace)
    vi.spyOn(api, 'getSharedReviewSession').mockRejectedValue(apiError)
    render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/missing']}>
      <Routes><Route path="/workspaces/:workspaceId/sessions/:sessionId" element={<SharedSessionDetailPage />} /></Routes>
    </MemoryRouter>)
    expect(await screen.findByText('Không tìm thấy Shared Review Session')).toBeInTheDocument()
    expect(screen.queryByText('Payment review')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Reviewers' })).not.toBeInTheDocument()
    expect(getReviewers).not.toHaveBeenCalled()
    expect(getVoting).not.toHaveBeenCalled()
    expect(getAudit).not.toHaveBeenCalled()
  })
})
