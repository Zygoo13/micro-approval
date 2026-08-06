import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../../lib/api'
import type {
  SharedReviewSessionDetail,
  SharedSessionLifecycleResponse,
  WorkspaceDetail,
  WorkspaceRole,
} from '../../types'
import SharedSessionLifecycleControls from './SharedSessionLifecycleControls'

const workspace = (role: WorkspaceRole): WorkspaceDetail => ({
  id: 'workspace-1',
  name: 'Payments',
  description: null,
  ownerId: 'owner-1',
  currentUserRole: role,
  createdAt: '2026-08-06T01:00:00',
  updatedAt: '2026-08-06T01:00:00',
})

const session = (
  overrides: Partial<SharedReviewSessionDetail> = {},
): SharedReviewSessionDetail => ({
  id: 'session-1',
  workspaceId: 'workspace-1',
  workspaceType: 'SHARED',
  title: 'Payment review',
  mode: 'GIT_DIFF',
  rawContent: 'diff --git ...',
  promptContent: null,
  status: 'APPROVED',
  aiAnalysisStatus: 'SUCCEEDED',
  aiAnalysisError: null,
  aiTokenUsed: 12,
  createdByUserId: 'owner-1',
  createdByDisplayName: 'Owner User',
  createdAt: '2026-08-06T02:00:00',
  completedAt: null,
  closed: false,
  closedAt: null,
  closedByUserId: null,
  closedByDisplayName: null,
  closeReason: null,
  lifecycleVersion: 3,
  decisions: [],
  ...overrides,
})

const lifecycleResponse = (
  overrides: Partial<SharedSessionLifecycleResponse> = {},
): SharedSessionLifecycleResponse => ({
  sessionId: 'session-1',
  status: 'APPROVED',
  closed: true,
  closedAt: '2026-08-06T04:00:00',
  closedByUserId: 'owner-1',
  closedByDisplayName: 'Owner User',
  closeReason: null,
  lifecycleVersion: 4,
  ...overrides,
})

afterEach(() => vi.restoreAllMocks())

function renderControls({
  role = 'OWNER',
  value = session(),
  onRefresh = vi.fn().mockResolvedValue(undefined),
}: {
  role?: WorkspaceRole
  value?: SharedReviewSessionDetail
  onRefresh?: () => Promise<void>
} = {}) {
  render(<SharedSessionLifecycleControls
    workspace={workspace(role)}
    session={value}
    onRefresh={onRefresh}
  />)
  return { onRefresh }
}

describe('SharedSessionLifecycleControls display and permissions', () => {
  it('renders open and closed states with result and close metadata as text', () => {
    renderControls()
    expect(screen.getByText('Session đang mở')).toBeInTheDocument()
    expect(screen.getByText('Open')).toBeInTheDocument()
    expect(screen.getByText('Đã duyệt')).toBeInTheDocument()
  })

  it.each(['APPROVED', 'REJECTED'] as const)(
    'allows OWNER to start close for terminal %s',
    status => {
      renderControls({ value: session({ status }) })
      expect(screen.getByRole('button', { name: 'Đóng session' })).toBeInTheDocument()
    },
  )

  it.each(['PENDING', 'IN_REVIEW'] as const)(
    'explains why %s cannot close and does not render an enabled close action',
    status => {
      renderControls({ value: session({ status }) })
      expect(screen.queryByRole('button', { name: 'Đóng session' })).not.toBeInTheDocument()
      expect(screen.getByText(/chưa đủ điều kiện để đóng/)).toBeInTheDocument()
    },
  )

  it.each(['REVIEWER', 'MEMBER', 'AUDITOR'] as WorkspaceRole[])(
    'keeps %s lifecycle read-only',
    role => {
      renderControls({ role })
      expect(screen.getByText('Session đang mở')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Đóng session' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Mở lại session' })).not.toBeInTheDocument()
    },
  )

  it.each(['OWNER', 'ADMIN'] as WorkspaceRole[])(
    'shows closed metadata and Reopen to %s',
    role => {
      renderControls({
        role,
        value: session({
          status: 'REJECTED',
          closed: true,
          closedAt: '2026-08-06T04:00:00',
          closedByUserId: 'admin-1',
          closedByDisplayName: 'Admin User',
          closeReason: 'Còn rủi ro truy cập',
          lifecycleVersion: 8,
        }),
      })
      expect(screen.getByText('Session đã đóng')).toBeInTheDocument()
      expect(screen.getByText('Closed')).toBeInTheDocument()
      expect(screen.getByText('Đã từ chối')).toBeInTheDocument()
      expect(screen.getByText('Admin User')).toBeInTheDocument()
      expect(screen.getByText('Còn rủi ro truy cập')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Mở lại session' })).toBeInTheDocument()
    },
  )
})

describe('SharedSessionLifecycleControls close and reopen', () => {
  it('opens a semantic dialog, trims a supplied reason and refreshes once', async () => {
    const close = vi.spyOn(api, 'closeSharedReviewSession')
      .mockResolvedValue(lifecycleResponse({ closeReason: 'Release accepted' }))
    const onRefresh = vi.fn().mockResolvedValue(undefined)
    renderControls({ onRefresh })

    await userEvent.click(screen.getByRole('button', { name: 'Đóng session' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Vote sẽ không thể chỉnh sửa')
    const reason = screen.getByRole('textbox', { name: /Lý do/ })
    expect(reason).toHaveAttribute('maxlength', '1000')
    expect(reason).toHaveFocus()
    await userEvent.type(reason, '  Release accepted  ')
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận đóng' }))

    await waitFor(() => expect(close).toHaveBeenCalledWith(
      'workspace-1', 'session-1', { reason: 'Release accepted' },
    ))
    expect(onRefresh).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('omits a whitespace-only optional reason', async () => {
    const close = vi.spyOn(api, 'closeSharedReviewSession')
      .mockResolvedValue(lifecycleResponse())
    renderControls()
    await userEvent.click(screen.getByRole('button', { name: 'Đóng session' }))
    await userEvent.type(screen.getByRole('textbox', { name: /Lý do/ }), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận đóng' }))
    await waitFor(() => expect(close).toHaveBeenCalledWith('workspace-1', 'session-1', {}))
  })

  it('blocks duplicate submit while close is pending', async () => {
    vi.spyOn(api, 'closeSharedReviewSession').mockReturnValue(new Promise(() => undefined))
    renderControls()
    await userEvent.click(screen.getByRole('button', { name: 'Đóng session' }))
    const submit = screen.getByRole('button', { name: 'Xác nhận đóng' })
    await userEvent.click(submit)
    expect(screen.getByRole('button', { name: 'Đang đóng…' })).toBeDisabled()
    expect(api.closeSharedReviewSession).toHaveBeenCalledTimes(1)
  })

  it('keeps close reason and dialog after an API error', async () => {
    vi.spyOn(api, 'closeSharedReviewSession')
      .mockRejectedValue(new ApiError('Máy chủ lỗi.', 500))
    renderControls()
    await userEvent.click(screen.getByRole('button', { name: 'Đóng session' }))
    await userEvent.type(screen.getByRole('textbox', { name: /Lý do/ }), 'Giữ lại lý do này')
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận đóng' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Máy chủ lỗi.')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Lý do/ })).toHaveValue('Giữ lại lý do này')
  })

  it('confirms reopen without a body and refreshes authoritative state', async () => {
    const reopen = vi.spyOn(api, 'reopenSharedReviewSession')
      .mockResolvedValue(lifecycleResponse({
        closed: false,
        closedAt: null,
        closedByUserId: null,
        closedByDisplayName: null,
        closeReason: null,
        lifecycleVersion: 5,
      }))
    const onRefresh = vi.fn().mockResolvedValue(undefined)
    renderControls({ value: session({ closed: true }), onRefresh })
    await userEvent.click(screen.getByRole('button', { name: 'Mở lại session' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('cho phép reviewer chỉnh sửa phiếu')
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận mở lại' }))
    await waitFor(() => expect(reopen).toHaveBeenCalledWith('workspace-1', 'session-1'))
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })
})

describe('SharedSessionLifecycleControls conflicts', () => {
  it.each(['close', 'reopen'] as const)(
    '%s conflict refetches once, does not retry and shows targeted guidance',
    async action => {
      const close = vi.spyOn(api, 'closeSharedReviewSession')
        .mockRejectedValue(new ApiError('Lifecycle conflict', 409))
      const reopen = vi.spyOn(api, 'reopenSharedReviewSession')
        .mockRejectedValue(new ApiError('Lifecycle conflict', 409))
      const onRefresh = vi.fn().mockResolvedValue(undefined)
      renderControls({
        value: session({ closed: action === 'reopen' }),
        onRefresh,
      })

      await userEvent.click(screen.getByRole('button', {
        name: action === 'close' ? 'Đóng session' : 'Mở lại session',
      }))
      await userEvent.click(screen.getByRole('button', {
        name: action === 'close' ? 'Xác nhận đóng' : 'Xác nhận mở lại',
      }))

      expect(await screen.findByRole('alert')).toHaveTextContent('Dữ liệu mới nhất đã được tải lại')
      expect(onRefresh).toHaveBeenCalledTimes(1)
      expect(action === 'close' ? close : reopen).toHaveBeenCalledTimes(1)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    },
  )
})
