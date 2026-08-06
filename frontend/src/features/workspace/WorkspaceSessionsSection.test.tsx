import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../../lib/api'
import type { SharedReviewSessionSummary, WorkspaceDetail, WorkspaceRole } from '../../types'
import WorkspaceSessionsSection from './WorkspaceSessionsSection'

const workspace = (role: WorkspaceRole): WorkspaceDetail => ({
  id: 'workspace-1',
  name: 'Payments',
  description: null,
  ownerId: 'owner-1',
  currentUserRole: role,
  createdAt: '2026-08-06T01:00:00',
  updatedAt: '2026-08-06T01:00:00',
})

const session: SharedReviewSessionSummary = {
  id: 'session-1',
  workspaceId: 'workspace-1',
  workspaceType: 'SHARED',
  title: 'Payment authorization',
  mode: 'GIT_DIFF',
  status: 'PENDING',
  aiAnalysisStatus: 'SUCCEEDED',
  createdByUserId: 'owner-1',
  createdByDisplayName: 'Owner User',
  createdAt: '2026-08-06T02:00:00',
  closed: false,
  closedAt: null,
  closedByUserId: null,
  closedByDisplayName: null,
  closeReason: null,
  lifecycleVersion: 0,
}

afterEach(() => vi.restoreAllMocks())

function renderSection(role: WorkspaceRole) {
  return render(<MemoryRouter><WorkspaceSessionsSection workspace={workspace(role)} /></MemoryRouter>)
}

describe('WorkspaceSessionsSection', () => {
  it('renders loading and empty states', async () => {
    vi.spyOn(api, 'getSharedReviewSessions').mockReturnValue(new Promise(() => undefined))
    const view = renderSection('OWNER')
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải Shared Review Sessions')

    view.unmount()
    vi.restoreAllMocks()
    vi.spyOn(api, 'getSharedReviewSessions').mockResolvedValue([])
    renderSection('OWNER')
    expect(await screen.findByText('Chưa có Shared Review Session')).toBeInTheDocument()
  })

  it('renders the backend list and metadata without inventing a card count', async () => {
    vi.spyOn(api, 'getSharedReviewSessions').mockResolvedValue([session])
    renderSection('REVIEWER')
    expect(await screen.findByText('Payment authorization')).toBeInTheDocument()
    expect(screen.getByText(/Owner User/)).toBeInTheDocument()
    expect(screen.getByText('AI đã phân tích')).toBeInTheDocument()
    expect(screen.queryByText(/\d+ Decision Card/)).not.toBeInTheDocument()
    expect(screen.getByText('Open')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Payment authorization/ })).toHaveAttribute(
      'href',
      '/workspaces/workspace-1/sessions/session-1',
    )
  })

  it('renders the lightweight Closed badge from the backend summary contract', async () => {
    vi.spyOn(api, 'getSharedReviewSessions').mockResolvedValue([{
      ...session,
      closed: true,
      closedAt: '2026-08-06T04:00:00',
      closedByUserId: 'owner-1',
      closedByDisplayName: 'Owner User',
      closeReason: 'Done',
      lifecycleVersion: 4,
    }])
    renderSection('MEMBER')
    expect(await screen.findByText('Closed')).toBeInTheDocument()
  })

  it('renders an error and retries', async () => {
    const getSessions = vi.spyOn(api, 'getSharedReviewSessions')
      .mockRejectedValueOnce(new ApiError('Mất kết nối.', null))
      .mockResolvedValueOnce([session])
    const user = userEvent.setup()
    renderSection('OWNER')
    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối.')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Payment authorization')).toBeInTheDocument()
    expect(getSessions).toHaveBeenCalledTimes(2)
  })

  it.each(['OWNER', 'ADMIN', 'REVIEWER'] as WorkspaceRole[])(
    '%s can see Create Session',
    async role => {
      vi.spyOn(api, 'getSharedReviewSessions').mockResolvedValue([])
      renderSection(role)
      expect(await screen.findByRole('link', { name: 'Tạo session' })).toBeInTheDocument()
    },
  )

  it.each(['MEMBER', 'AUDITOR'] as WorkspaceRole[])(
    '%s cannot see Create Session',
    async role => {
      vi.spyOn(api, 'getSharedReviewSessions').mockResolvedValue([])
      renderSection(role)
      await screen.findByText('Chưa có Shared Review Session')
      expect(screen.queryByRole('link', { name: /Tạo session/ })).not.toBeInTheDocument()
    },
  )
})
