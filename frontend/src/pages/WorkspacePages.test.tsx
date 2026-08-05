import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../lib/api'
import type { WorkspaceDetail, WorkspaceSummary } from '../types'
import CreateWorkspacePage from './CreateWorkspacePage'
import WorkspaceDetailPage from './WorkspaceDetailPage'
import WorkspaceListPage from './WorkspaceListPage'

const workspaceSummary: WorkspaceSummary = {
  id: 'workspace-1',
  name: 'Payments',
  description: 'Không gian kiểm duyệt thanh toán',
  ownerId: 'owner-1',
  currentUserRole: 'REVIEWER',
  createdAt: '2026-07-25T14:00:00',
}

const workspaceDetail: WorkspaceDetail = {
  ...workspaceSummary,
  currentUserRole: 'OWNER',
  updatedAt: '2026-07-25T14:05:00',
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('Workspace list', () => {
  it('renders the loading state', () => {
    vi.spyOn(api, 'getWorkspaces').mockReturnValue(new Promise(() => undefined))

    render(<MemoryRouter><WorkspaceListPage /></MemoryRouter>)

    expect(screen.getByRole('status')).toHaveTextContent('Đang tải danh sách workspace')
  })

  it('renders the empty state', async () => {
    vi.spyOn(api, 'getWorkspaces').mockResolvedValue([])

    render(<MemoryRouter><WorkspaceListPage /></MemoryRouter>)

    expect(await screen.findByText('Bạn chưa có workspace nào')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tạo workspace' })).toHaveAttribute(
      'href',
      '/workspaces/new',
    )
  })

  it('renders active workspace data returned by the API', async () => {
    vi.spyOn(api, 'getWorkspaces').mockResolvedValue([workspaceSummary])

    render(<MemoryRouter><WorkspaceListPage /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Payments' })).toBeInTheDocument()
    expect(screen.getByText('Không gian kiểm duyệt thanh toán')).toBeInTheDocument()
    expect(screen.getByText('Người kiểm duyệt')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Payments/ })).toHaveAttribute(
      'href',
      '/workspaces/workspace-1',
    )
  })
})

describe('Create workspace', () => {
  function renderCreatePage() {
    render(
      <MemoryRouter initialEntries={['/workspaces/new']}>
        <Routes>
          <Route path="/workspaces/new" element={<CreateWorkspacePage />} />
          <Route path="/workspaces/:workspaceId" element={<div>Chi tiết đã mở</div>} />
        </Routes>
      </MemoryRouter>,
    )
  }

  it('validates a blank name before calling the API', async () => {
    const create = vi.spyOn(api, 'createWorkspace')
    const user = userEvent.setup()
    renderCreatePage()

    await user.click(screen.getByRole('button', { name: 'Tạo workspace' }))

    expect(await screen.findByText('Tên workspace không được để trống.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('creates a trimmed workspace and navigates to its detail', async () => {
    const create = vi.spyOn(api, 'createWorkspace').mockResolvedValue(workspaceDetail)
    const user = userEvent.setup()
    renderCreatePage()

    await user.type(screen.getByLabelText('Tên workspace'), '  Payments  ')
    await user.type(screen.getByLabelText(/Mô tả/), '  Nhóm thanh toán  ')
    await user.click(screen.getByRole('button', { name: 'Tạo workspace' }))

    expect(await screen.findByText('Chi tiết đã mở')).toBeInTheDocument()
    expect(create).toHaveBeenCalledTimes(1)
    expect(create).toHaveBeenCalledWith({
      name: 'Payments',
      description: 'Nhóm thanh toán',
    })
  })

  it('shows normalized API and field errors when creation fails', async () => {
    vi.spyOn(api, 'createWorkspace').mockRejectedValue(
      new ApiError('Dữ liệu gửi lên không hợp lệ.', 400, {
        name: 'Tên workspace không hợp lệ',
      }),
    )
    const user = userEvent.setup()
    renderCreatePage()

    await user.type(screen.getByLabelText('Tên workspace'), 'Payments')
    await user.click(screen.getByRole('button', { name: 'Tạo workspace' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Dữ liệu gửi lên không hợp lệ.',
    )
    expect(screen.getByText('Tên workspace không hợp lệ')).toBeInTheDocument()
  })
})

describe('Workspace detail', () => {
  it('renders a not-found state for an unavailable workspace', async () => {
    vi.spyOn(api, 'getWorkspaceById').mockRejectedValue(
      new ApiError('Không tìm thấy workspace.', 404),
    )

    render(
      <MemoryRouter initialEntries={['/workspaces/missing']}>
        <Routes>
          <Route path="/workspaces/:workspaceId" element={<WorkspaceDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText(
      'Không tìm thấy workspace',
      { selector: 'strong' },
    )).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về danh sách' })).toHaveAttribute(
      'href',
      '/workspaces',
    )
  })
})
