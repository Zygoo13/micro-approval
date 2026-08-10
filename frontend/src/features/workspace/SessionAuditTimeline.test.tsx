import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../../lib/api'
import type { SessionAuditEvent, SessionAuditTimeline as TimelineResponse } from '../../types'
import SessionAuditTimeline from './SessionAuditTimeline'

const emptyValue = {
  status: null, decision: null, note: null, assignmentVersion: null,
  voteVersion: null, closed: null, closedAt: null, closedByUserId: null,
  closeReason: null, lifecycleVersion: null, reopenedAt: null,
}

function event(
  eventId: string,
  eventType: SessionAuditEvent['eventType'],
  overrides: Partial<SessionAuditEvent> = {},
): SessionAuditEvent {
  return {
    eventId,
    eventType,
    actorUserId: 'actor-1',
    actorDisplayName: 'Nguyễn An',
    actorEmail: 'an@example.com',
    targetUserId: 'reviewer-1',
    targetDisplayName: 'Trần Bình',
    targetAssignmentId: 'assignment-1',
    decisionCardId: 'card-1',
    decisionCardSummary: 'Decision Card #1',
    reason: null,
    change: null,
    createdAt: '2026-08-07T09:30:00',
    ...overrides,
  }
}

function timeline(events: SessionAuditEvent[], overrides: Partial<TimelineResponse> = {}): TimelineResponse {
  return {
    sessionId: 'session-1', events, page: 0, size: 20,
    totalElements: events.length, totalPages: events.length ? 1 : 0, hasNext: false,
    ...overrides,
  }
}

afterEach(() => vi.restoreAllMocks())

describe('SessionAuditTimeline', () => {
  it('renders loading then an empty state', async () => {
    let resolveRequest!: (value: TimelineResponse) => void
    vi.spyOn(api, 'getSessionAuditTimeline').mockReturnValue(new Promise(resolve => {
      resolveRequest = resolve
    }))
    render(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" />)
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải Audit Timeline')

    resolveRequest(timeline([]))
    expect(await screen.findByText('Chưa có hoạt động nào.')).toBeInTheDocument()
  })

  it('renders a safe error and retries the first page', async () => {
    const request = vi.spyOn(api, 'getSessionAuditTimeline')
      .mockRejectedValueOnce(new ApiError('Mạng tạm thời gián đoạn.', null))
      .mockResolvedValueOnce(timeline([]))
    render(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" />)

    expect(await screen.findByText('Mạng tạm thời gián đoạn.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có hoạt động nào.')).toBeInTheDocument()
    expect(request).toHaveBeenCalledTimes(2)
  })

  it('maps reviewer, voting and lifecycle events with optional details', async () => {
    const events = [
      event('reopened', 'SESSION_REOPENED', {
        change: { oldValue: { ...emptyValue, closed: true, closeReason: 'Cần sửa thêm' }, newValue: { ...emptyValue, status: 'REJECTED', closed: false } },
      }),
      event('closed', 'SESSION_CLOSED', {
        reason: 'Đủ điều kiện phát hành',
        change: { oldValue: null, newValue: { ...emptyValue, status: 'APPROVED', closed: true } },
      }),
      event('updated', 'VOTE_UPDATED', {
        change: { oldValue: { ...emptyValue, decision: 'APPROVED', note: 'Ổn' }, newValue: { ...emptyValue, decision: 'REJECTED', note: 'Thiếu quyền' } },
      }),
      event('created', 'VOTE_CREATED', {
        change: { oldValue: null, newValue: { ...emptyValue, decision: 'APPROVED', note: 'Đã kiểm tra' } },
      }),
      event('reactivated', 'REVIEWER_REACTIVATED'),
      event('removed', 'REVIEWER_REMOVED', { reason: 'Đổi người review' }),
      event('assigned', 'REVIEWER_ASSIGNED'),
    ]
    vi.spyOn(api, 'getSessionAuditTimeline').mockResolvedValue(timeline(events))
    render(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" />)

    expect(await screen.findByText('Nguyễn An đã phân công Trần Bình làm reviewer.')).toBeInTheDocument()
    expect(screen.getByText('Nguyễn An đã gỡ Trần Bình khỏi reviewer roster.')).toBeInTheDocument()
    expect(screen.getByText('Nguyễn An đã phân công lại Trần Bình.')).toBeInTheDocument()
    expect(screen.getByText(/đã bỏ phiếu Đã duyệt cho Decision Card #1/)).toBeInTheDocument()
    expect(screen.getByText(/đã đổi phiếu từ Đã duyệt sang Đã từ chối/)).toBeInTheDocument()
    expect(screen.getByText(/đã đóng session với kết quả Đã duyệt/)).toBeInTheDocument()
    expect(screen.getByText('Nguyễn An đã mở lại session.')).toBeInTheDocument()
    expect(screen.getByText('Đổi người review')).toBeInTheDocument()
    expect(screen.getByText('Đủ điều kiện phát hành')).toBeInTheDocument()
    expect(screen.getByText('Ghi chú trước:', { exact: false }).closest('p')).toHaveTextContent('Ổn')
    expect(screen.getByText('Ghi chú mới:', { exact: false }).closest('p')).toHaveTextContent('Thiếu quyền')
    expect(screen.getByText('Lý do đóng trước đó:', { exact: false }).closest('p')).toHaveTextContent('Cần sửa thêm')
    expect(screen.getAllByText('Reviewer')).toHaveLength(3)
    expect(screen.getAllByText('Vote')).toHaveLength(2)
    expect(screen.getAllByText('Vòng đời')).toHaveLength(2)
  })

  it('uses safe fallbacks for partial, unknown and invalid events', async () => {
    const partial = event('partial', 'REVIEWER_ASSIGNED', {
      actorUserId: null, actorDisplayName: null, actorEmail: null,
      targetUserId: null, targetDisplayName: null, targetAssignmentId: null,
      decisionCardId: null, decisionCardSummary: null,
      change: { oldValue: null, newValue: null }, reason: null,
      createdAt: 'not-a-date',
    })
    const unknown = { ...partial, eventId: 'unknown', eventType: 'LEGACY_EVENT' } as unknown as SessionAuditEvent
    vi.spyOn(api, 'getSessionAuditTimeline').mockResolvedValue(timeline([partial, unknown]))
    render(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" />)

    expect(await screen.findByText('Người dùng không xác định đã phân công reviewer không xác định làm reviewer.')).toBeInTheDocument()
    expect(screen.getByText('Hoạt động trong session đã được cập nhật.')).toBeInTheDocument()
    expect(screen.getAllByText('Không xác định thời gian')).toHaveLength(2)
    expect(screen.queryByText(/undefined|null|\[object Object\]/)).not.toBeInTheDocument()
  })

  it('loads more in backend order, deduplicates IDs and hides the button at the end', async () => {
    const newest = event('event-3', 'SESSION_REOPENED')
    const middle = event('event-2', 'VOTE_UPDATED')
    const oldest = event('event-1', 'REVIEWER_ASSIGNED')
    const request = vi.spyOn(api, 'getSessionAuditTimeline')
      .mockResolvedValueOnce(timeline([newest, middle], { totalElements: 3, totalPages: 2, hasNext: true }))
      .mockResolvedValueOnce(timeline([middle, oldest], { page: 1, totalElements: 3, totalPages: 2 }))
    render(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" />)

    await userEvent.click(await screen.findByRole('button', { name: 'Tải thêm' }))
    const list = screen.getByRole('list', { name: 'Lịch sử hoạt động của session' })
    await waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(3))
    expect(within(list).getAllByRole('listitem').map(item => item.textContent)).toEqual([
      expect.stringContaining('mở lại session'),
      expect.stringContaining('cập nhật phiếu'),
      expect.stringContaining('phân công'),
    ])
    expect(request).toHaveBeenNthCalledWith(2, 'workspace-1', 'session-1', 1, 20)
    expect(screen.queryByRole('button', { name: 'Tải thêm' })).not.toBeInTheDocument()
  })

  it('keeps existing events when load-more fails and retries the same page', async () => {
    const first = event('event-2', 'VOTE_CREATED')
    const second = event('event-1', 'REVIEWER_ASSIGNED')
    const request = vi.spyOn(api, 'getSessionAuditTimeline')
      .mockResolvedValueOnce(timeline([first], { totalElements: 2, totalPages: 2, hasNext: true }))
      .mockRejectedValueOnce(new ApiError('Không tải được trang tiếp theo.', 500))
      .mockResolvedValueOnce(timeline([second], { page: 1, totalElements: 2, totalPages: 2 }))
    render(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" />)

    await userEvent.click(await screen.findByRole('button', { name: 'Tải thêm' }))
    expect(await screen.findByText('Không tải được trang tiếp theo.')).toBeInTheDocument()
    expect(screen.getByText(/đã bỏ phiếu cho Decision Card/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử tải lại' }))
    expect(await screen.findByText(/đã phân công Trần Bình/)).toBeInTheDocument()
    expect(request).toHaveBeenNthCalledWith(3, 'workspace-1', 'session-1', 1, 20)
  })

  it('refreshes from page zero and safely renders HTML-like notes as text', async () => {
    const unsafe = '<img src=x onerror=alert(1)>'
    const request = vi.spyOn(api, 'getSessionAuditTimeline')
      .mockResolvedValueOnce(timeline([event('old', 'VOTE_CREATED', {
        change: { oldValue: null, newValue: { ...emptyValue, decision: 'REJECTED', note: unsafe } },
      })]))
      .mockResolvedValueOnce(timeline([event('new', 'SESSION_REOPENED')]))
    const view = render(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" />)

    expect((await screen.findByText('Ghi chú:', { exact: false })).closest('p')).toHaveTextContent(unsafe)
    expect(document.querySelector('img')).toBeNull()
    view.rerender(<SessionAuditTimeline workspaceId="workspace-1" sessionId="session-1" refreshKey={1} />)
    expect(await screen.findByText(/đã mở lại session/)).toBeInTheDocument()
    expect(screen.queryByText(unsafe)).not.toBeInTheDocument()
    expect(request).toHaveBeenNthCalledWith(2, 'workspace-1', 'session-1', 0, 20)
  })
})
