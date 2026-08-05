import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../lib/api'
import type { SharedReviewSessionDetail, WorkspaceDetail, WorkspaceRole } from '../types'
import CreateSharedSessionPage from './CreateSharedSessionPage'

const workspace = (role: WorkspaceRole = 'OWNER'): WorkspaceDetail => ({
  id: 'workspace-1', name: 'Payments', description: null, ownerId: 'owner-1',
  currentUserRole: role, createdAt: '2026-08-06T01:00:00', updatedAt: '2026-08-06T01:00:00',
})

const created: SharedReviewSessionDetail = {
  id: 'session-1', workspaceId: 'workspace-1', workspaceType: 'SHARED',
  title: 'Review', mode: 'RAW_SNIPPET', rawContent: 'delete user', promptContent: null,
  status: 'PENDING', aiAnalysisStatus: 'DISABLED', aiAnalysisError: null,
  aiTokenUsed: 0, createdByUserId: 'owner-1', createdByDisplayName: 'Owner',
  createdAt: '2026-08-06T02:00:00', completedAt: null, decisions: [],
}

afterEach(() => vi.restoreAllMocks())

function renderPage(role: WorkspaceRole = 'OWNER') {
  vi.spyOn(api, 'getWorkspaceById').mockResolvedValue(workspace(role))
  return render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/new']}>
    <Routes>
      <Route path="/workspaces/:workspaceId/sessions/new" element={<CreateSharedSessionPage />} />
      <Route path="/workspaces/:workspaceId/sessions/:sessionId" element={<div>Session detail opened</div>} />
    </Routes>
  </MemoryRouter>)
}

async function fillRequired(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('Tiêu đề phiên'), '  Review  ')
  await user.type(screen.getByLabelText('Đoạn mã cần review'), 'delete user')
}

describe('CreateSharedSessionPage', () => {
  it('renders all three modes and validates title/raw content', async () => {
    const create = vi.spyOn(api, 'createSharedReviewSession')
    const user = userEvent.setup()
    renderPage()
    expect(await screen.findByRole('radio', { name: /Raw Snippet/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Git Diff/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Intent Matching/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tạo và phân tích' }))
    expect(screen.getByText('Tiêu đề không được để trống.')).toBeInTheDocument()
    expect(screen.getByText('Nội dung cần review không được để trống.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('requires raw content for Git Diff', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('radio', { name: /Git Diff/ }))
    await user.type(screen.getByLabelText('Tiêu đề phiên'), 'Review diff')
    await user.click(screen.getByRole('button', { name: 'Tạo và phân tích' }))
    expect(screen.getByText('Nội dung cần review không được để trống.')).toBeInTheDocument()
  })

  it('requires content and intent for Intent Matching', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('radio', { name: /Intent Matching/ }))
    await user.type(screen.getByLabelText('Tiêu đề phiên'), 'Intent review')
    await user.click(screen.getByRole('button', { name: 'Tạo và phân tích' }))
    expect(screen.getByText('Nội dung cần review không được để trống.')).toBeInTheDocument()
    expect(screen.getByText('Ý định / yêu cầu không được để trống.')).toBeInTheDocument()
  })

  it('omits promptContent for non-intent mode and navigates after success', async () => {
    const create = vi.spyOn(api, 'createSharedReviewSession').mockResolvedValue(created)
    const user = userEvent.setup()
    renderPage()
    await fillRequired(user)
    await user.click(screen.getByRole('button', { name: 'Tạo và phân tích' }))
    expect(await screen.findByText('Session detail opened')).toBeInTheDocument()
    expect(create).toHaveBeenCalledWith('workspace-1', {
      title: 'Review', mode: 'RAW_SNIPPET', rawContent: 'delete user',
    })
  })

  it('keeps form values and displays the API error after failed submit', async () => {
    vi.spyOn(api, 'createSharedReviewSession').mockRejectedValue(
      new ApiError('Không đủ quyền.', 403),
    )
    const user = userEvent.setup()
    renderPage()
    await fillRequired(user)
    await user.click(screen.getByRole('button', { name: 'Tạo và phân tích' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Không đủ quyền.')
    expect(screen.getByLabelText('Tiêu đề phiên')).toHaveValue('  Review  ')
    expect(screen.getByLabelText('Đoạn mã cần review')).toHaveValue('delete user')
  })

  it('disables submission while synchronous analysis is running', async () => {
    vi.spyOn(api, 'createSharedReviewSession').mockReturnValue(new Promise(() => undefined))
    const user = userEvent.setup()
    renderPage()
    await fillRequired(user)
    await user.click(screen.getByRole('button', { name: 'Tạo và phân tích' }))
    expect(screen.getByRole('button', { name: 'Đang phân tích…' })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('Đang phân tích mã')
  })

  it.each(['MEMBER', 'AUDITOR'] as WorkspaceRole[])(
    'does not render create form for %s',
    async role => {
      renderPage(role)
      expect(await screen.findByText('Bạn không có quyền tạo session')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Tạo và phân tích' })).not.toBeInTheDocument()
    },
  )
})
