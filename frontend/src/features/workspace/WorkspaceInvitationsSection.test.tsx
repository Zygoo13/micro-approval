import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../../lib/api'
import type {
  WorkspaceDetail,
  WorkspaceInvitation,
  WorkspaceInvitationStatus,
  WorkspaceRole,
} from '../../types'
import WorkspaceInvitationsSection from './WorkspaceInvitationsSection'

const workspace = (role: WorkspaceRole): WorkspaceDetail => ({
  id: 'workspace-1',
  name: 'Payments',
  description: null,
  ownerId: 'owner-1',
  currentUserRole: role,
  createdAt: '2026-07-25T14:00:00',
  updatedAt: '2026-07-25T14:05:00',
})

const invitation = (
  id: string,
  status: WorkspaceInvitationStatus = 'PENDING',
  role: WorkspaceInvitation['role'] = 'MEMBER',
): WorkspaceInvitation => ({
  id,
  workspaceId: 'workspace-1',
  email: `${id}@example.com`,
  role,
  status,
  invitedByUserId: 'owner-1',
  invitedByDisplayName: 'Owner User',
  createdAt: '2026-07-25T14:00:00',
  expiresAt: '2099-07-30T14:00:00',
  respondedAt: status === 'PENDING' ? null : '2026-07-26T14:00:00',
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('Workspace invitation section', () => {
  it('renders loading, empty and returned invitations', async () => {
    let resolveInvitations: (value: WorkspaceInvitation[]) => void = () => undefined
    vi.spyOn(api, 'getWorkspaceInvitations').mockReturnValue(new Promise(resolve => {
      resolveInvitations = resolve
    }))
    const view = render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)

    expect(screen.getByRole('status')).toHaveTextContent('Đang tải danh sách lời mời')
    resolveInvitations([])
    expect(await screen.findByText('Chưa có lời mời')).toBeInTheDocument()

    view.unmount()
    vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([invitation('reviewer')])
    render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)
    expect(await screen.findByText('reviewer@example.com')).toBeInTheDocument()
    expect(screen.getByText('Owner User')).toBeInTheDocument()
    expect(screen.getByText('Đang chờ')).toBeInTheDocument()
  })

  it('retries a failed load', async () => {
    const getInvitations = vi.spyOn(api, 'getWorkspaceInvitations')
      .mockRejectedValueOnce(new ApiError('Mất kết nối.', null))
      .mockResolvedValueOnce([])
    const user = userEvent.setup()
    render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối.')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))

    expect(await screen.findByText('Chưa có lời mời')).toBeInTheDocument()
    expect(getInvitations).toHaveBeenCalledTimes(2)
  })

  it('OWNER can invite ADMIN with a trimmed email', async () => {
    vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([])
    const created = invitation('new-admin', 'PENDING', 'ADMIN')
    const create = vi.spyOn(api, 'createWorkspaceInvitation').mockResolvedValue(created)
    const user = userEvent.setup()
    render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)

    await screen.findByText('Chưa có lời mời')
    await user.type(screen.getByLabelText('Email'), '  new-admin@example.com  ')
    await user.selectOptions(screen.getByLabelText('Vai trò'), 'ADMIN')
    await user.click(screen.getByRole('button', { name: 'Gửi lời mời' }))

    expect(await screen.findByText('new-admin@example.com')).toBeInTheDocument()
    expect(create).toHaveBeenCalledWith('workspace-1', {
      email: 'new-admin@example.com',
      role: 'ADMIN',
    })
    expect(screen.getByLabelText('Email')).toHaveValue('')
  })

  it('validates email and keeps API conflict details visible', async () => {
    vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([])
    const create = vi.spyOn(api, 'createWorkspaceInvitation')
      .mockRejectedValue(new ApiError('Đã có invitation PENDING cho email này', 409))
    const user = userEvent.setup()
    render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)

    await screen.findByText('Chưa có lời mời')
    await user.click(screen.getByRole('button', { name: 'Gửi lời mời' }))
    expect(screen.getByText('Email không được để trống.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'invalid')
    await user.click(screen.getByRole('button', { name: 'Gửi lời mời' }))
    expect(screen.getByText('Email không đúng định dạng.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Email'))
    await user.type(screen.getByLabelText('Email'), 'member@example.com')
    await user.click(screen.getByRole('button', { name: 'Gửi lời mời' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Đã có invitation PENDING')
  })

  it('shows an active-membership conflict without clearing the form', async () => {
    vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([])
    vi.spyOn(api, 'createWorkspaceInvitation').mockRejectedValue(
      new ApiError('User đã có membership trong workspace', 409),
    )
    const user = userEvent.setup()
    render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)

    await screen.findByText('Chưa có lời mời')
    await user.type(screen.getByLabelText('Email'), 'member@example.com')
    await user.click(screen.getByRole('button', { name: 'Gửi lời mời' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('đã có membership')
    expect(screen.getByLabelText('Email')).toHaveValue('member@example.com')
  })

  it('ADMIN cannot select ADMIN and MEMBER neither renders nor calls the API', async () => {
    const getInvitations = vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([])
    const adminView = render(<WorkspaceInvitationsSection workspace={workspace('ADMIN')} />)

    await screen.findByText('Chưa có lời mời')
    expect(within(screen.getByLabelText('Vai trò')).getAllByRole('option')
      .map(option => option.getAttribute('value'))).toEqual(['REVIEWER', 'MEMBER', 'AUDITOR'])

    adminView.unmount()
    getInvitations.mockClear()
    render(<WorkspaceInvitationsSection workspace={workspace('MEMBER')} />)
    await waitFor(() => expect(getInvitations).not.toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Invitations' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Gửi lời mời' })).not.toBeInTheDocument()
  })

  it('revokes after confirmation and keeps the row as REVOKED', async () => {
    vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([invitation('pending')])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const revoke = vi.spyOn(api, 'revokeWorkspaceInvitation').mockResolvedValue(
      invitation('pending', 'REVOKED'),
    )
    const user = userEvent.setup()
    render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)

    await user.click(await screen.findByRole('button', { name: 'Thu hồi' }))

    expect(await screen.findByText('Đã thu hồi')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Thu hồi' })).not.toBeInTheDocument()
    expect(revoke).toHaveBeenCalledWith('workspace-1', 'pending')
  })

  it('does not revoke when confirmation is cancelled', async () => {
    vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([invitation('pending')])
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const revoke = vi.spyOn(api, 'revokeWorkspaceInvitation')
    const user = userEvent.setup()
    render(<WorkspaceInvitationsSection workspace={workspace('OWNER')} />)

    await user.click(await screen.findByRole('button', { name: 'Thu hồi' }))
    expect(revoke).not.toHaveBeenCalled()
  })

  it('ADMIN cannot revoke ADMIN and terminal or expired invitations have no action', async () => {
    vi.spyOn(api, 'getWorkspaceInvitations').mockResolvedValue([
      invitation('admin', 'PENDING', 'ADMIN'),
      invitation('accepted', 'ACCEPTED'),
      invitation('rejected', 'REJECTED'),
      { ...invitation('expired'), expiresAt: '2020-01-01T00:00:00' },
    ])
    render(<WorkspaceInvitationsSection workspace={workspace('ADMIN')} />)

    await screen.findByText('admin@example.com')
    expect(screen.queryByRole('button', { name: 'Thu hồi' })).not.toBeInTheDocument()
    expect(screen.getByText('Đã chấp nhận')).toBeInTheDocument()
    expect(screen.getByText('Đã từ chối')).toBeInTheDocument()
    expect(screen.getByText('Đã hết hạn')).toBeInTheDocument()
  })
})
