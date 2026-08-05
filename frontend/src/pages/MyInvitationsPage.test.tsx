import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../lib/api'
import type {
  MyWorkspaceInvitation,
  WorkspaceInvitation,
  WorkspaceInvitationStatus,
} from '../types'
import MyInvitationsPage from './MyInvitationsPage'

const myInvitation = (
  id: string,
  status: WorkspaceInvitationStatus = 'PENDING',
): MyWorkspaceInvitation => ({
  id,
  workspaceId: `workspace-${id}`,
  workspaceName: `Workspace ${id}`,
  role: 'REVIEWER',
  status,
  invitedByDisplayName: 'Owner User',
  createdAt: '2026-07-25T14:00:00',
  expiresAt: '2099-07-30T14:00:00',
})

const mutationResponse = (
  source: MyWorkspaceInvitation,
  status: WorkspaceInvitationStatus,
): WorkspaceInvitation => ({
  id: source.id,
  workspaceId: source.workspaceId,
  email: 'recipient@example.com',
  role: source.role,
  status,
  invitedByUserId: 'owner-1',
  invitedByDisplayName: source.invitedByDisplayName,
  createdAt: source.createdAt,
  expiresAt: source.expiresAt,
  respondedAt: '2026-07-26T14:00:00',
})

function renderPage() {
  return render(<MemoryRouter><MyInvitationsPage /></MemoryRouter>)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('My invitations', () => {
  it('renders loading, empty and returned invitation details', async () => {
    let resolveInvitations: (value: MyWorkspaceInvitation[]) => void = () => undefined
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockReturnValue(new Promise(resolve => {
      resolveInvitations = resolve
    }))
    const view = renderPage()

    expect(screen.getByRole('status')).toHaveTextContent('Đang tải lời mời của bạn')
    resolveInvitations([])
    expect(await screen.findByText('Bạn chưa có lời mời nào')).toBeInTheDocument()

    view.unmount()
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([myInvitation('one')])
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Workspace one' })).toBeInTheDocument()
    expect(screen.getByText('Người kiểm duyệt')).toBeInTheDocument()
    expect(screen.getByText('Owner User')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chấp nhận' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Từ chối' })).toBeInTheDocument()
  })

  it('retries a failed load', async () => {
    const getMine = vi.spyOn(api, 'getMyWorkspaceInvitations')
      .mockRejectedValueOnce(new ApiError('Mất kết nối.', null))
      .mockResolvedValueOnce([])
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối.')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Bạn chưa có lời mời nào')).toBeInTheDocument()
    expect(getMine).toHaveBeenCalledTimes(2)
  })

  it('hides actions for ACCEPTED and client-expired invitations', async () => {
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([
      myInvitation('accepted', 'ACCEPTED'),
      { ...myInvitation('expired'), expiresAt: '2020-01-01T00:00:00' },
    ])
    renderPage()

    await screen.findByText('Đã chấp nhận')
    expect(screen.getByText('Đã hết hạn')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Chấp nhận' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Từ chối' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đi tới workspace' })).toHaveAttribute(
      'href',
      '/workspaces/workspace-accepted',
    )
  })

  it('accepts after confirmation and exposes the workspace action', async () => {
    const pending = myInvitation('pending')
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([pending])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const accept = vi.spyOn(api, 'acceptWorkspaceInvitation').mockResolvedValue(
      mutationResponse(pending, 'ACCEPTED'),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Chấp nhận' }))

    expect(await screen.findByText('Đã chấp nhận')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đi tới workspace' })).toHaveAttribute(
      'href', '/workspaces/workspace-pending',
    )
    expect(accept).toHaveBeenCalledWith('pending')
  })

  it('rejects after confirmation without removing the invitation', async () => {
    const pending = myInvitation('pending')
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([pending])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const reject = vi.spyOn(api, 'rejectWorkspaceInvitation').mockResolvedValue(
      mutationResponse(pending, 'REJECTED'),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Từ chối' }))

    expect(await screen.findByText('Đã từ chối')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Workspace pending' })).toBeInTheDocument()
    expect(reject).toHaveBeenCalledWith('pending')
  })

  it('turns a 410 response into EXPIRED and keeps the error visible', async () => {
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([myInvitation('pending')])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.spyOn(api, 'acceptWorkspaceInvitation').mockRejectedValue(
      new ApiError('Invitation đã hết hạn', 410),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Chấp nhận' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('đã hết hạn')
    expect(screen.getByText('Đã hết hạn')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Chấp nhận' })).not.toBeInTheDocument()
  })

  it.each([
    [404, 'Không tìm thấy invitation.'],
    [409, 'Invitation đã được xử lý.'],
  ])('keeps PENDING actions stable for error %s', async (status, message) => {
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([myInvitation('pending')])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.spyOn(api, 'acceptWorkspaceInvitation').mockRejectedValue(new ApiError(message, status))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Chấp nhận' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Chấp nhận' })).toBeEnabled()
  })

  it('does not call an action when confirmation is cancelled', async () => {
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([myInvitation('pending')])
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const accept = vi.spyOn(api, 'acceptWorkspaceInvitation')
    const reject = vi.spyOn(api, 'rejectWorkspaceInvitation')
    const user = userEvent.setup()
    renderPage()

    const card = (await screen.findByRole('heading', { name: 'Workspace pending' })).closest('article')!
    await user.click(within(card).getByRole('button', { name: 'Chấp nhận' }))
    await user.click(within(card).getByRole('button', { name: 'Từ chối' }))

    await waitFor(() => {
      expect(accept).not.toHaveBeenCalled()
      expect(reject).not.toHaveBeenCalled()
    })
  })
})
