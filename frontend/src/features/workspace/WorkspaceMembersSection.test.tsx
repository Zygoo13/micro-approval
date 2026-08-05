import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, auth } from '../../lib/api'
import type { WorkspaceDetail, WorkspaceMember, WorkspaceRole } from '../../types'
import WorkspaceMembersSection from './WorkspaceMembersSection'

const workspace = (role: WorkspaceRole): WorkspaceDetail => ({
  id: 'workspace-1',
  name: 'Payments',
  description: null,
  ownerId: 'user-owner',
  currentUserRole: role,
  createdAt: '2026-07-25T14:00:00',
  updatedAt: '2026-07-25T14:05:00',
})

const member = (
  membershipId: string,
  role: WorkspaceRole,
  email = `${membershipId}@example.com`,
): WorkspaceMember => ({
  membershipId,
  userId: `user-${membershipId}`,
  email,
  displayName: membershipId === 'owner' ? 'Owner User' : `${membershipId} User`,
  role,
  status: 'ACTIVE',
  joinedAt: '2026-07-25T14:00:00',
})

const owner = member('owner', 'OWNER', 'owner@example.com')
const admin = member('admin', 'ADMIN', 'admin@example.com')
const regularMember = member('member', 'MEMBER', 'member@example.com')

function mockIdentity(email: string) {
  vi.spyOn(auth, 'getCurrentUserEmail').mockReturnValue(email)
}

function renderSection(role: WorkspaceRole, members = [owner, admin, regularMember]) {
  vi.spyOn(api, 'getWorkspaceMembers').mockResolvedValue(members)
  return render(<WorkspaceMembersSection workspace={workspace(role)} />)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('Workspace member list', () => {
  it('renders loading state, members, OWNER and status returned by the API', async () => {
    mockIdentity('member@example.com')
    let resolveMembers: (value: WorkspaceMember[]) => void = () => undefined
    vi.spyOn(api, 'getWorkspaceMembers').mockReturnValue(new Promise(resolve => {
      resolveMembers = resolve
    }))
    render(<WorkspaceMembersSection workspace={workspace('MEMBER')} />)

    expect(screen.getByRole('status')).toHaveTextContent('Đang tải danh sách thành viên')
    resolveMembers([owner, { ...regularMember, status: 'PENDING' }])

    expect(await screen.findByText('Owner User')).toBeInTheDocument()
    expect(screen.getByText('Chủ sở hữu')).toBeInTheDocument()
    expect(screen.getByText('Đang chờ')).toBeInTheDocument()
  })

  it('renders an error and retries member loading', async () => {
    mockIdentity('member@example.com')
    const getMembers = vi.spyOn(api, 'getWorkspaceMembers')
      .mockRejectedValueOnce(new ApiError('Mất kết nối.', null))
      .mockResolvedValueOnce([owner])
    const user = userEvent.setup()
    render(<WorkspaceMembersSection workspace={workspace('MEMBER')} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối.')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))

    expect(await screen.findByText('Owner User')).toBeInTheDocument()
    expect(getMembers).toHaveBeenCalledTimes(2)
  })

  it('keeps ordinary members in read-only mode', async () => {
    mockIdentity('member@example.com')
    renderSection('MEMBER')

    await screen.findByText('Owner User')
    expect(screen.queryByRole('button', { name: 'Thêm thành viên' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Xóa' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })
})

describe('Add workspace member', () => {
  it('lets OWNER choose ADMIN and submits a trimmed valid email', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    const added = member('new-admin', 'ADMIN', 'new@example.com')
    const add = vi.spyOn(api, 'addWorkspaceMember').mockResolvedValue(added)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Thêm thành viên' }))
    await user.type(screen.getByLabelText('Email'), '  new@example.com  ')
    await user.selectOptions(screen.getByLabelText('Vai trò'), 'ADMIN')
    await user.click(screen.getByRole('button', { name: 'Thêm thành viên' }))

    expect(await screen.findByText('new-admin User')).toBeInTheDocument()
    expect(add).toHaveBeenCalledWith('workspace-1', { email: 'new@example.com', role: 'ADMIN' })
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
  })

  it('validates blank and malformed email before calling the API', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    const add = vi.spyOn(api, 'addWorkspaceMember')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Thêm thành viên' }))
    await user.click(screen.getByRole('button', { name: 'Thêm thành viên' }))
    expect(await screen.findByText('Email không được để trống.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Email'), 'invalid-email')
    await user.click(screen.getByRole('button', { name: 'Thêm thành viên' }))
    expect(await screen.findByText('Email không đúng định dạng.')).toBeInTheDocument()
    expect(add).not.toHaveBeenCalled()
  })

  it('does not offer ADMIN or OWNER when caller is ADMIN', async () => {
    mockIdentity('admin@example.com')
    renderSection('ADMIN')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Thêm thành viên' }))
    const options = within(screen.getByLabelText('Vai trò')).getAllByRole('option')
    expect(options.map(option => option.getAttribute('value'))).toEqual([
      'REVIEWER', 'MEMBER', 'AUDITOR',
    ])
  })

  it.each([
    [409, 'Membership đã tồn tại.'],
    [404, 'Không tìm thấy người dùng.'],
  ])('shows API error %s and keeps the form open', async (status, message) => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    vi.spyOn(api, 'addWorkspaceMember').mockRejectedValue(new ApiError(message, status))
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Thêm thành viên' }))
    await user.type(screen.getByLabelText('Email'), 'new@example.com')
    await user.click(screen.getByRole('button', { name: 'Thêm thành viên' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByLabelText('Email')).toHaveValue('new@example.com')
  })
})

describe('Change member role', () => {
  it('lets OWNER promote MEMBER to ADMIN after confirmation', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const update = vi.spyOn(api, 'updateWorkspaceMemberRole').mockResolvedValue({
      ...regularMember,
      role: 'ADMIN',
    })
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Vai trò của member User'), 'ADMIN')

    await waitFor(() => expect(update).toHaveBeenCalledWith(
      'workspace-1', 'member', { role: 'ADMIN' },
    ))
    expect(screen.getByLabelText('Vai trò của member User')).toHaveValue('ADMIN')
  })

  it('lets ADMIN change MEMBER to REVIEWER but cannot manage another ADMIN', async () => {
    mockIdentity('admin@example.com')
    renderSection('ADMIN')
    const update = vi.spyOn(api, 'updateWorkspaceMemberRole').mockResolvedValue({
      ...regularMember,
      role: 'REVIEWER',
    })
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Vai trò của member User'), 'REVIEWER')

    await waitFor(() => expect(update).toHaveBeenCalled())
    expect(screen.queryByLabelText('Vai trò của admin User')).not.toBeInTheDocument()
    expect(within(screen.getByText('admin User').closest('tr')!).queryByRole('button', { name: 'Xóa' })).not.toBeInTheDocument()
  })

  it('never exposes role controls for OWNER', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')

    const ownerRow = (await screen.findByText('Owner User')).closest('tr')!
    expect(within(ownerRow).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(ownerRow).queryByRole('button', { name: 'Xóa' })).not.toBeInTheDocument()
  })

  it('keeps the original role when role update fails', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    vi.spyOn(api, 'updateWorkspaceMemberRole').mockRejectedValue(
      new ApiError('Không đủ quyền.', 403),
    )
    const user = userEvent.setup()

    const select = await screen.findByLabelText('Vai trò của member User')
    await user.selectOptions(select, 'REVIEWER')

    expect(await screen.findByRole('alert')).toHaveTextContent('Không đủ quyền.')
    expect(select).toHaveValue('MEMBER')
  })
})

describe('Remove workspace member', () => {
  it('lets OWNER remove ADMIN and removes it from the list after 204', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const remove = vi.spyOn(api, 'removeWorkspaceMember').mockResolvedValue(undefined)
    const user = userEvent.setup()

    const adminRow = (await screen.findByText('admin User')).closest('tr')!
    await user.click(within(adminRow).getByRole('button', { name: 'Xóa' }))

    await waitFor(() => expect(screen.queryByText('admin User')).not.toBeInTheDocument())
    expect(remove).toHaveBeenCalledWith('workspace-1', 'admin')
  })

  it('lets ADMIN remove MEMBER but hides actions for self, OWNER and ADMIN', async () => {
    mockIdentity('admin@example.com')
    renderSection('ADMIN')
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.spyOn(api, 'removeWorkspaceMember').mockResolvedValue(undefined)
    const user = userEvent.setup()

    await user.click(within((await screen.findByText('member User')).closest('tr')!)
      .getByRole('button', { name: 'Xóa' }))

    await waitFor(() => expect(screen.queryByText('member User')).not.toBeInTheDocument())
    expect(within(screen.getByText('Owner User').closest('tr')!).queryByRole('button', { name: 'Xóa' })).not.toBeInTheDocument()
    expect(within(screen.getByText('admin User').closest('tr')!).queryByRole('button', { name: 'Xóa' })).not.toBeInTheDocument()
  })

  it('does not call remove when confirmation is cancelled', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const remove = vi.spyOn(api, 'removeWorkspaceMember')
    const user = userEvent.setup()

    await user.click(within((await screen.findByText('member User')).closest('tr')!)
      .getByRole('button', { name: 'Xóa' }))

    expect(remove).not.toHaveBeenCalled()
    expect(screen.getByText('member User')).toBeInTheDocument()
  })

  it('keeps member visible when remove fails', async () => {
    mockIdentity('owner@example.com')
    renderSection('OWNER')
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.spyOn(api, 'removeWorkspaceMember').mockRejectedValue(
      new ApiError('Membership xung đột.', 409),
    )
    const user = userEvent.setup()

    await user.click(within((await screen.findByText('member User')).closest('tr')!)
      .getByRole('button', { name: 'Xóa' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Membership xung đột.')
    expect(screen.getByText('member User')).toBeInTheDocument()
  })
})
