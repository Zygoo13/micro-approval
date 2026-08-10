import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, auth } from '../../lib/api'
import type {
  SessionReviewer,
  SessionVoting,
  SessionVotingStatus,
  SharedDecisionCard,
  TeamVote,
  WorkspaceDetail,
  WorkspaceRole,
} from '../../types'
import TeamVotingSection from './TeamVotingSection'

const workspace = (role: WorkspaceRole): WorkspaceDetail => ({
  id: 'workspace-1',
  name: 'Payments',
  description: null,
  ownerId: 'owner-user',
  currentUserRole: role,
  createdAt: '2026-08-06T01:00:00',
  updatedAt: '2026-08-06T01:00:00',
})

const card: SharedDecisionCard = {
  id: 'card-1',
  engineType: 'RULE_BASED',
  riskCategory: 'SECURITY',
  riskLevel: 'HIGH',
  codeSnippet: 'return repository.findById(id);',
  questionText: 'Đã kiểm tra quyền truy cập chưa?',
  humanDecision: 'PENDING',
  teamDecision: 'PENDING',
  reviewerNote: null,
  decidedByName: null,
  decidedAt: null,
  displayOrder: 0,
}

const reviewer = (role: WorkspaceRole = 'REVIEWER'): SessionReviewer => ({
  assignmentId: 'assignment-1',
  sessionId: 'session-1',
  workspaceMemberId: 'membership-1',
  userId: 'reviewer-user',
  displayName: 'Review User',
  email: 'reviewer@example.com',
  workspaceRole: role,
  status: 'ASSIGNED',
  assignedByUserId: 'owner-user',
  assignedByDisplayName: 'Owner User',
  assignedAt: '2026-08-06T02:00:00',
  removedAt: null,
  removedByUserId: null,
  removalReason: null,
  version: 0,
})

const vote = (overrides: Partial<TeamVote> = {}): TeamVote => ({
  voteId: 'vote-1',
  cardId: 'card-1',
  reviewerAssignmentId: 'assignment-1',
  reviewerUserId: 'reviewer-user',
  reviewerDisplayName: 'Review User',
  decision: 'APPROVED',
  note: null,
  counted: true,
  createdAt: '2026-08-06T03:00:00',
  updatedAt: '2026-08-06T03:00:00',
  version: 0,
  ...overrides,
})

function voting(
  overrides: Partial<SessionVoting> = {},
  votes: TeamVote[] = [],
): SessionVoting {
  return {
    sessionId: 'session-1',
    sessionStatus: 'PENDING',
    closed: false,
    closedAt: null,
    closedByUserId: null,
    closedByDisplayName: null,
    closeReason: null,
    lifecycleVersion: 0,
    reviewerCount: 1,
    cards: [{
      cardId: 'card-1',
      teamDecision: 'PENDING',
      assignedReviewerCount: 1,
      validVoteCount: votes.filter(item => item.counted).length,
      votes,
    }],
    ...overrides,
  }
}

afterEach(() => {
  vi.restoreAllMocks()
})

function renderSection({
  role = 'REVIEWER',
  roster = [reviewer(role)],
  response = voting(),
  onVoteChanged,
}: {
  role?: WorkspaceRole
  roster?: SessionReviewer[]
  response?: SessionVoting
  onVoteChanged?: () => void
} = {}) {
  vi.spyOn(auth, 'getCurrentUserEmail').mockReturnValue('reviewer@example.com')
  vi.spyOn(api, 'getSessionVoting').mockResolvedValue(response)
  vi.spyOn(api, 'getSessionReviewers').mockResolvedValue(roster)
  return render(<TeamVotingSection
    workspace={workspace(role)}
    sessionId="session-1"
    decisionCards={response.cards.length ? [card] : []}
    onVoteChanged={onVoteChanged}
  />)
}

describe('TeamVotingSection loading and read states', () => {
  it('renders loading, empty reviewer and zero-card states', async () => {
    vi.spyOn(api, 'getSessionVoting').mockReturnValue(new Promise(() => undefined))
    vi.spyOn(api, 'getSessionReviewers').mockReturnValue(new Promise(() => undefined))
    const loadingView = render(<TeamVotingSection
      workspace={workspace('MEMBER')}
      sessionId="session-1"
      decisionCards={[card]}
    />)
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải Team Voting')
    loadingView.unmount()
    vi.restoreAllMocks()

    const emptyReviewers = renderSection({ role: 'MEMBER', roster: [], response: voting({ reviewerCount: 0 }) })
    expect(await screen.findByText('Chưa có reviewer được phân công.')).toBeInTheDocument()
    emptyReviewers.unmount()
    vi.restoreAllMocks()

    renderSection({
      role: 'MEMBER',
      roster: [],
      response: voting({ sessionStatus: 'APPROVED', reviewerCount: 0, cards: [] }),
    })
    expect(await screen.findByText(/Session không có Decision Card/)).toBeInTheDocument()
    expect(screen.getAllByText('Đã duyệt').length).toBeGreaterThan(0)
  })

  it('renders error and retries without rendering stale voting data', async () => {
    const getVoting = vi.spyOn(api, 'getSessionVoting')
      .mockRejectedValueOnce(new ApiError('Mất kết nối.', null))
      .mockResolvedValueOnce(voting())
    vi.spyOn(api, 'getSessionReviewers').mockResolvedValue([])
    render(<TeamVotingSection workspace={workspace('MEMBER')} sessionId="session-1" decisionCards={[card]} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối.')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Đã kiểm tra quyền truy cập chưa?')).toBeInTheDocument()
    expect(getVoting).toHaveBeenCalledTimes(2)
  })

  it('renders aggregate, notes, timestamps and stale vote labels', async () => {
    renderSection({
      role: 'MEMBER',
      roster: [],
      response: voting({ sessionStatus: 'IN_REVIEW' }, [vote({
        decision: 'REJECTED',
        note: 'Thiếu kiểm tra owner',
        counted: false,
        updatedAt: '2026-08-06T04:00:00',
      })]),
    })
    expect(await screen.findAllByText('Đang review')).toHaveLength(2)
    expect(screen.getByText('Chưa đủ quyết định')).toBeInTheDocument()
    expect(screen.getByText(/0\/1/)).toBeInTheDocument()
    expect(screen.getByText('Ghi chú: Thiếu kiểm tra owner')).toBeInTheDocument()
    expect(screen.getByText('Không tính vào quorum')).toBeInTheDocument()
    expect(screen.getByText('Cần xác nhận lại')).toBeInTheDocument()
    expect(screen.getByText(/Tạo/)).toBeInTheDocument()
    expect(screen.getByText(/Cập nhật/)).toBeInTheDocument()
  })

  it.each<[SessionVotingStatus, string]>([
    ['PENDING', 'Chưa bắt đầu'],
    ['IN_REVIEW', 'Đang review'],
    ['APPROVED', 'Đã duyệt'],
    ['REJECTED', 'Bị từ chối'],
  ])('renders backend session aggregate %s as text', async (status, label) => {
    renderSection({ role: 'MEMBER', roster: [], response: voting({ sessionStatus: status }) })
    expect((await screen.findAllByText(label)).length).toBeGreaterThan(0)
  })
})

describe('TeamVotingSection permission-aware form', () => {
  it('keeps votes and notes visible but hides My Vote when the session is closed', async () => {
    renderSection({
      role: 'REVIEWER',
      response: voting({ closed: true }, [vote({ note: 'Frozen review note' })]),
    })
    expect(await screen.findByText('Ghi chú: Frozen review note')).toBeInTheDocument()
    expect(screen.getByText(/My Vote ở chế độ chỉ đọc/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'My Vote' })).not.toBeInTheDocument()
  })

  it.each<WorkspaceRole>(['OWNER', 'ADMIN', 'REVIEWER'])(
    'shows My Vote to assigned %s',
    async role => {
      renderSection({ role, roster: [reviewer(role)] })
      expect(await screen.findByRole('heading', { name: 'My Vote' })).toBeInTheDocument()
    },
  )

  it.each<WorkspaceRole>(['OWNER', 'ADMIN', 'REVIEWER', 'MEMBER', 'AUDITOR'])(
    'hides My Vote when role %s is not an eligible current assignment',
    async role => {
      const roster = role === 'MEMBER' || role === 'AUDITOR' ? [reviewer(role)] : []
      renderSection({ role, roster })
      expect(await screen.findByText('Đã kiểm tra quyền truy cập chưa?')).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'My Vote' })).not.toBeInTheDocument()
    },
  )
})

describe('TeamVotingSection create and update', () => {
  it('creates APPROVED without note or version and updates from authoritative response', async () => {
    const onVoteChanged = vi.fn()
    renderSection({ onVoteChanged })
    const result = voting({ sessionStatus: 'APPROVED' }, [vote()])
    result.cards[0].teamDecision = 'APPROVED'
    result.cards[0].validVoteCount = 1
    const upsert = vi.spyOn(api, 'upsertTeamVote').mockResolvedValue(result)

    await screen.findByRole('heading', { name: 'My Vote' })
    await userEvent.click(screen.getByRole('button', { name: 'Gửi phiếu' }))

    await waitFor(() => expect(upsert).toHaveBeenCalledWith(
      'workspace-1', 'session-1', 'card-1', { decision: 'APPROVED' },
    ))
    expect(await screen.findByRole('button', { name: 'Cập nhật phiếu' })).toBeInTheDocument()
    expect(screen.getAllByText('Đã duyệt').length).toBeGreaterThan(0)
    expect(onVoteChanged).toHaveBeenCalledTimes(1)
  })

  it('requires a trimmed note for REJECTED before calling the API', async () => {
    renderSection()
    const upsert = vi.spyOn(api, 'upsertTeamVote')
    await screen.findByRole('heading', { name: 'My Vote' })
    await userEvent.click(screen.getByRole('radio', { name: 'Từ chối' }))
    await userEvent.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
    expect(screen.getByText('Ghi chú là bắt buộc khi từ chối.')).toBeInTheDocument()
    expect(upsert).not.toHaveBeenCalled()
  })

  it.each([
    ['APPROVED', 'REJECTED', 'Lý do mới'],
    ['REJECTED', 'APPROVED', 'Ghi chú đã cập nhật'],
  ] as const)('updates %s to %s with current version', async (from, to, note) => {
    const current = vote({ decision: from, note: from === 'REJECTED' ? 'Lý do cũ' : null, version: 4 })
    renderSection({ response: voting({}, [current]) })
    const updated = vote({ decision: to, note, version: 5 })
    const result = voting({}, [updated])
    const upsert = vi.spyOn(api, 'upsertTeamVote').mockResolvedValue(result)
    await screen.findByRole('button', { name: 'Cập nhật phiếu' })

    await userEvent.click(screen.getByRole('radio', { name: to === 'REJECTED' ? 'Từ chối' : 'Đã duyệt' }))
    const noteInput = screen.getByRole('textbox', { name: /Ghi chú/ })
    await userEvent.clear(noteInput)
    await userEvent.type(noteInput, `  ${note}  `)
    await userEvent.click(screen.getByRole('button', { name: 'Cập nhật phiếu' }))

    await waitFor(() => expect(upsert).toHaveBeenCalledWith(
      'workspace-1', 'session-1', 'card-1',
      { decision: to, note, version: 4 },
    ))
    expect(await screen.findByText(`Ghi chú: ${note}`)).toBeInTheDocument()
    expect(screen.getByText('Review User')).toBeInTheDocument()
  })

  it('reconfirms counted=false vote using its current version', async () => {
    const stale = vote({ decision: 'REJECTED', note: 'Giữ nguyên', counted: false, version: 7 })
    renderSection({ response: voting({}, [stale]) })
    const upsert = vi.spyOn(api, 'upsertTeamVote').mockResolvedValue(voting({}, [
      vote({ decision: 'REJECTED', note: 'Giữ nguyên', counted: true, version: 8 }),
    ]))
    await screen.findByRole('button', { name: 'Xác nhận lại phiếu' })
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận lại phiếu' }))
    await waitFor(() => expect(upsert).toHaveBeenCalledWith(
      'workspace-1', 'session-1', 'card-1',
      { decision: 'REJECTED', note: 'Giữ nguyên', version: 7 },
    ))
  })

  it('preserves the form after a generic API failure', async () => {
    renderSection()
    vi.spyOn(api, 'upsertTeamVote').mockRejectedValue(new ApiError('Máy chủ lỗi.', 500))
    await screen.findByRole('heading', { name: 'My Vote' })
    await userEvent.click(screen.getByRole('radio', { name: 'Từ chối' }))
    await userEvent.type(screen.getByRole('textbox', { name: /Ghi chú/ }), 'Nội dung cần giữ')
    await userEvent.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Máy chủ lỗi.')
    expect(screen.getByRole('textbox', { name: /Ghi chú/ })).toHaveValue('Nội dung cần giữ')
  })
})

describe('TeamVotingSection stale conflict', () => {
  it('shows 409 guidance, refetches authoritative vote/version and does not resubmit', async () => {
    const initial = voting({}, [vote({ note: 'Bản cũ', version: 2 })])
    const authoritative = voting({}, [vote({ decision: 'REJECTED', note: 'Bản mới nhất', version: 3 })])
    vi.spyOn(auth, 'getCurrentUserEmail').mockReturnValue('reviewer@example.com')
    const getVoting = vi.spyOn(api, 'getSessionVoting')
      .mockResolvedValueOnce(initial)
      .mockResolvedValueOnce(authoritative)
    vi.spyOn(api, 'getSessionReviewers').mockResolvedValue([reviewer()])
    const upsert = vi.spyOn(api, 'upsertTeamVote')
      .mockRejectedValue(new ApiError('Vote đã thay đổi', 409))
    render(<TeamVotingSection workspace={workspace('REVIEWER')} sessionId="session-1" decisionCards={[card]} />)

    await screen.findByRole('button', { name: 'Cập nhật phiếu' })
    await userEvent.click(screen.getByRole('button', { name: 'Cập nhật phiếu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('được thay đổi ở nơi khác')
    await waitFor(() => expect(getVoting).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('textbox', { name: /Ghi chú/ })).toHaveValue('Bản mới nhất')
    expect(screen.getByRole('radio', { name: 'Từ chối' })).toBeChecked()
    expect(upsert).toHaveBeenCalledTimes(1)

    const form = screen.getByRole('heading', { name: 'My Vote' }).closest('form')
    expect(form).not.toBeNull()
    expect(within(form!).getByRole('button', { name: 'Cập nhật phiếu' })).toBeEnabled()
  })
})
