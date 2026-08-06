import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, auth } from '../../lib/api'
import type {
  SessionReviewer,
  WorkspaceDetail,
  WorkspaceMember,
  WorkspaceRole,
} from '../../types'
import SessionReviewersSection from './SessionReviewersSection'

const workspace = (role: WorkspaceRole): WorkspaceDetail => ({
  id: 'workspace-1',
  name: 'Payments',
  description: null,
  ownerId: 'owner-user',
  currentUserRole: role,
  createdAt: '2026-08-06T01:00:00',
  updatedAt: '2026-08-06T01:00:00',
})

const reviewer: SessionReviewer = {
  assignmentId: 'assignment-1',
  sessionId: 'session-1',
  workspaceMemberId: 'member-reviewer',
  userId: 'reviewer-user',
  displayName: 'Review User',
  email: 'reviewer@example.com',
  workspaceRole: 'REVIEWER',
  status: 'ASSIGNED',
  assignedByUserId: 'owner-user',
  assignedByDisplayName: 'Owner User',
  assignedAt: '2026-08-06T02:00:00',
  removedAt: null,
  removedByUserId: null,
  removalReason: null,
  version: 0,
}

const members: WorkspaceMember[] = [
  { membershipId: 'member-owner', userId: 'owner-user', email: 'owner@example.com', displayName: 'Owner User', role: 'OWNER', status: 'ACTIVE', joinedAt: '2026-08-01T01:00:00' },
  { membershipId: 'member-admin', userId: 'admin-user', email: 'admin@example.com', displayName: 'Admin User', role: 'ADMIN', status: 'ACTIVE', joinedAt: '2026-08-01T01:00:00' },
  { membershipId: 'member-reviewer', userId: 'reviewer-user', email: 'reviewer@example.com', displayName: 'Review User', role: 'REVIEWER', status: 'ACTIVE', joinedAt: '2026-08-01T01:00:00' },
  { membershipId: 'member-reviewer-2', userId: 'reviewer-user-2', email: 'reviewer2@example.com', displayName: 'Second Reviewer', role: 'REVIEWER', status: 'ACTIVE', joinedAt: '2026-08-01T01:00:00' },
  { membershipId: 'member-member', userId: 'member-user', email: 'member@example.com', displayName: 'Member User', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2026-08-01T01:00:00' },
  { membershipId: 'member-auditor', userId: 'auditor-user', email: 'auditor@example.com', displayName: 'Auditor User', role: 'AUDITOR', status: 'ACTIVE', joinedAt: '2026-08-01T01:00:00' },
  { membershipId: 'member-pending', userId: 'pending-user', email: 'pending@example.com', displayName: 'Pending Reviewer', role: 'REVIEWER', status: 'PENDING', joinedAt: '2026-08-01T01:00:00' },
]

afterEach(() => {
  auth.clearToken()
  vi.restoreAllMocks()
})

function renderSection(
  role: WorkspaceRole,
  roster: SessionReviewer[] = [],
  memberList: WorkspaceMember[] = members,
) {
  vi.spyOn(api, 'getSessionReviewers').mockResolvedValue(roster)
  vi.spyOn(api, 'getWorkspaceMembers').mockResolvedValue(memberList)
  return render(<SessionReviewersSection workspace={workspace(role)} sessionId="session-1" />)
}

describe('SessionReviewersSection roster and permissions', () => {
  it('renders loading, empty, reviewer metadata, error and retry states', async () => {
    vi.spyOn(api, 'getSessionReviewers').mockReturnValue(new Promise(() => undefined))
    const loadingView = render(<SessionReviewersSection workspace={workspace('MEMBER')} sessionId="session-1" />)
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải reviewer roster')
    loadingView.unmount()
    vi.restoreAllMocks()

    const emptyView = renderSection('MEMBER')
    expect(await screen.findByText('Chưa có reviewer nào được phân công.')).toBeInTheDocument()
    emptyView.unmount()
    vi.restoreAllMocks()

    const rosterView = renderSection('MEMBER', [reviewer])
    expect(await screen.findByText('Review User')).toBeInTheDocument()
    expect(screen.getByText('reviewer@example.com')).toBeInTheDocument()
    expect(screen.getByText('Owner User')).toBeInTheDocument()
    rosterView.unmount()
    vi.restoreAllMocks()

    const getReviewers = vi.spyOn(api, 'getSessionReviewers')
      .mockRejectedValueOnce(new ApiError('Mất kết nối.', null))
      .mockResolvedValueOnce([reviewer])
    render(<SessionReviewersSection workspace={workspace('AUDITOR')} sessionId="session-1" />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối.')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Review User')).toBeInTheDocument()
    expect(getReviewers).toHaveBeenCalledTimes(2)
  })

  it.each(['OWNER', 'ADMIN'] as WorkspaceRole[])('%s sees assign and remove controls', async role => {
    renderSection(role, [reviewer])
    expect(await screen.findByRole('button', { name: 'Phân công' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Gỡ reviewer' })).toBeInTheDocument()
  })

  it.each(['REVIEWER', 'MEMBER', 'AUDITOR'] as WorkspaceRole[])('%s has a read-only roster', async role => {
    const getMembers = vi.spyOn(api, 'getWorkspaceMembers')
    vi.spyOn(api, 'getSessionReviewers').mockResolvedValue([reviewer])
    render(<SessionReviewersSection workspace={workspace(role)} sessionId="session-1" />)
    expect(await screen.findByText('Review User')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Phân công' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Gỡ reviewer' })).not.toBeInTheDocument()
    expect(getMembers).not.toHaveBeenCalled()
  })

  it('filters candidates by ACTIVE status, eligible role and current assignment', async () => {
    renderSection('OWNER', [reviewer])
    const select = await screen.findByLabelText('Reviewer')
    const options = within(select).getAllByRole('option').map(option => option.textContent)
    expect(options).toEqual(expect.arrayContaining([
      expect.stringContaining('Owner User'),
      expect.stringContaining('Admin User'),
      expect.stringContaining('Second Reviewer'),
    ]))
    expect(options.join(' ')).not.toContain('Member User')
    expect(options.join(' ')).not.toContain('Auditor User')
    expect(options.join(' ')).not.toContain('Pending Reviewer')
    expect(options.join(' ')).not.toContain('Review User — reviewer@example.com')
  })
})

describe('SessionReviewersSection assignment', () => {
  it('validates selection and updates the roster after assign/reactivate response', async () => {
    const assign = vi.spyOn(api, 'assignSessionReviewer').mockResolvedValue(reviewer)
    const user = userEvent.setup()
    renderSection('OWNER', [], [members[2]])
    await screen.findByRole('button', { name: 'Phân công' })

    await user.click(screen.getByRole('button', { name: 'Phân công' }))
    expect(screen.getByText('Hãy chọn một reviewer.')).toBeInTheDocument()
    expect(assign).not.toHaveBeenCalled()

    await user.selectOptions(screen.getByLabelText('Reviewer'), 'member-reviewer')
    await user.click(screen.getByRole('button', { name: 'Phân công' }))
    expect(assign).toHaveBeenCalledWith('workspace-1', 'session-1', {
      workspaceMemberId: 'member-reviewer',
    })
    expect(await screen.findByText('Review User')).toBeInTheDocument()
    expect(screen.getByLabelText('Reviewer')).toHaveValue('')
  })

  it('disables submit while assigning', async () => {
    vi.spyOn(api, 'assignSessionReviewer').mockReturnValue(new Promise(() => undefined))
    const user = userEvent.setup()
    renderSection('ADMIN', [], [members[3]])
    await user.selectOptions(await screen.findByLabelText('Reviewer'), 'member-reviewer-2')
    await user.click(screen.getByRole('button', { name: 'Phân công' }))
    expect(screen.getByRole('button', { name: 'Đang phân công…' })).toBeDisabled()
  })

  it('keeps a stable roster and refreshes after duplicate conflict or stale role denial', async () => {
    const getReviewers = vi.spyOn(api, 'getSessionReviewers')
      .mockResolvedValueOnce([reviewer])
      .mockResolvedValueOnce([reviewer])
    vi.spyOn(api, 'getWorkspaceMembers').mockResolvedValue(members)
    vi.spyOn(api, 'assignSessionReviewer').mockRejectedValue(
      new ApiError('Reviewer đã được assign cho session', 409),
    )
    const user = userEvent.setup()
    render(<SessionReviewersSection workspace={workspace('OWNER')} sessionId="session-1" />)
    await user.selectOptions(await screen.findByLabelText('Reviewer'), 'member-reviewer-2')
    await user.click(screen.getByRole('button', { name: 'Phân công' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Reviewer đã được assign')
    expect(screen.getByText('Review User')).toBeInTheDocument()
    await waitFor(() => expect(getReviewers).toHaveBeenCalledTimes(2))

    vi.mocked(api.assignSessionReviewer).mockRejectedValueOnce(
      new ApiError('Bạn không có quyền thực hiện thao tác này.', 403),
    )
    await user.selectOptions(screen.getByLabelText('Reviewer'), 'member-reviewer-2')
    await user.click(screen.getByRole('button', { name: 'Phân công' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('không có quyền')
    expect(screen.getByText('Review User')).toBeInTheDocument()
  })
})

describe('SessionReviewersSection removal', () => {
  it('requires a trimmed reason and cancel does not call the API', async () => {
    const remove = vi.spyOn(api, 'removeSessionReviewer')
    const user = userEvent.setup()
    renderSection('OWNER', [reviewer])
    await user.click(await screen.findByRole('button', { name: 'Gỡ reviewer' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Xác nhận gỡ' }))
    expect(screen.getByText('Lý do gỡ reviewer là bắt buộc.')).toBeInTheDocument()
    expect(remove).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(remove).not.toHaveBeenCalled()
  })

  it('soft-removes through the API and only removes the row after success', async () => {
    let resolveRemove: ((value: SessionReviewer) => void) | undefined
    vi.spyOn(api, 'removeSessionReviewer').mockReturnValue(new Promise(resolve => {
      resolveRemove = resolve
    }))
    const user = userEvent.setup()
    renderSection('ADMIN', [reviewer])
    await user.click(await screen.findByRole('button', { name: 'Gỡ reviewer' }))
    await user.type(screen.getByLabelText('Lý do'), '  Chuyển reviewer khác  ')
    await user.click(screen.getByRole('button', { name: 'Xác nhận gỡ' }))
    expect(api.removeSessionReviewer).toHaveBeenCalledWith(
      'workspace-1', 'session-1', 'assignment-1', { reason: 'Chuyển reviewer khác' },
    )
    expect(screen.getByText('Review User')).toBeInTheDocument()
    resolveRemove?.({
      ...reviewer,
      status: 'REMOVED',
      removedAt: '2026-08-06T04:00:00',
      removedByUserId: 'admin-user',
      removalReason: 'Chuyển reviewer khác',
      version: 1,
    })
    expect(await screen.findByText('Chưa có reviewer nào được phân công.')).toBeInTheDocument()
  })

  it('keeps UI stable and refreshes after repeated remove conflict', async () => {
    const getReviewers = vi.spyOn(api, 'getSessionReviewers')
      .mockResolvedValueOnce([reviewer])
      .mockResolvedValueOnce([])
    vi.spyOn(api, 'getWorkspaceMembers').mockResolvedValue(members)
    vi.spyOn(api, 'removeSessionReviewer').mockRejectedValue(
      new ApiError('Reviewer assignment đã bị remove', 409),
    )
    const user = userEvent.setup()
    render(<SessionReviewersSection workspace={workspace('OWNER')} sessionId="session-1" />)
    await user.click(await screen.findByRole('button', { name: 'Gỡ reviewer' }))
    await user.type(screen.getByLabelText('Lý do'), 'Đã gỡ ở tab khác')
    await user.click(screen.getByRole('button', { name: 'Xác nhận gỡ' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('đã bị remove')
    await waitFor(() => expect(getReviewers).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Chưa có reviewer nào được phân công.')).toBeInTheDocument()
  })

  it('shows the explicit self-remove warning without changing workspace authority', async () => {
    vi.spyOn(auth, 'getCurrentUserEmail').mockReturnValue('reviewer@example.com')
    const user = userEvent.setup()
    renderSection('OWNER', [{ ...reviewer, workspaceRole: 'OWNER' }])
    await user.click(await screen.findByRole('button', { name: 'Gỡ reviewer' }))
    expect(screen.getByText(/Bạn đang gỡ chính mình khỏi reviewer roster/)).toBeInTheDocument()
    expect(screen.getByText(/Quyền quản trị workspace của bạn không thay đổi/)).toBeInTheDocument()
  })
})
