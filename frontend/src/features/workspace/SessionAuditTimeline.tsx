import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../../lib/api'
import { statusLabel } from '../../lib/labels'
import type { SessionAuditEvent, SessionAuditTimeline as SessionAuditTimelineResponse } from '../../types'

const PAGE_SIZE = 20

function normalizeError(exception: unknown) {
  return exception instanceof ApiError
    ? exception
    : new ApiError('Không thể tải Audit Timeline.', null)
}

function uniqueEvents(events: SessionAuditEvent[]) {
  const ids = new Set<string>()
  return events.filter(event => {
    if (ids.has(event.eventId)) return false
    ids.add(event.eventId)
    return true
  })
}

function appendUniqueEvents(current: SessionAuditEvent[], next: SessionAuditEvent[]) {
  const ids = new Set(current.map(event => event.eventId))
  return [...current, ...next.filter(event => {
    if (ids.has(event.eventId)) return false
    ids.add(event.eventId)
    return true
  })]
}

export default function SessionAuditTimeline({
  workspaceId,
  sessionId,
  refreshKey = 0,
}: {
  workspaceId: string
  sessionId: string
  refreshKey?: number
}) {
  const [events, setEvents] = useState<SessionAuditEvent[]>([])
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ApiError>()
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState<ApiError>()
  const requestSequence = useRef(0)

  const loadFirstPage = useCallback(async () => {
    const sequence = ++requestSequence.current
    setLoading(true)
    setLoadError(undefined)
    setLoadMoreError(undefined)
    try {
      const response = await api.getSessionAuditTimeline(workspaceId, sessionId, 0, PAGE_SIZE)
      if (sequence !== requestSequence.current) return
      applyFirstPage(response)
    } catch (exception) {
      if (sequence !== requestSequence.current) return
      setLoadError(normalizeError(exception))
    } finally {
      if (sequence === requestSequence.current) setLoading(false)
    }
  }, [sessionId, workspaceId])

  useEffect(() => {
    void loadFirstPage()
    return () => {
      requestSequence.current += 1
    }
  }, [loadFirstPage, refreshKey])

  function applyFirstPage(response: SessionAuditTimelineResponse) {
    setEvents(uniqueEvents(response.events))
    setPage(response.page)
    setHasNext(response.hasNext)
  }

  async function loadNextPage() {
    if (loadingMore || !hasNext) return
    const nextPage = page + 1
    setLoadingMore(true)
    setLoadMoreError(undefined)
    try {
      const response = await api.getSessionAuditTimeline(
        workspaceId,
        sessionId,
        nextPage,
        PAGE_SIZE,
      )
      setEvents(current => appendUniqueEvents(current, response.events))
      setPage(response.page)
      setHasNext(response.hasNext)
    } catch (exception) {
      setLoadMoreError(normalizeError(exception))
    } finally {
      setLoadingMore(false)
    }
  }

  return <section className="session-audit-section" aria-labelledby="session-audit-title">
    <div className="section-heading">
      <div>
        <h2 id="session-audit-title">Audit Timeline</h2>
        <p>Lịch sử reviewer, phiếu đánh giá và vòng đời session — mới nhất trước.</p>
      </div>
    </div>

    {loading ? <p className="loading" role="status">Đang tải Audit Timeline…</p>
      : loadError ? <AuditUnavailable error={loadError} retry={loadFirstPage} />
        : events.length === 0 ? <div className="empty">
          <strong>Chưa có hoạt động nào.</strong>
          <span>Các thay đổi reviewer, vote và vòng đời session sẽ xuất hiện tại đây.</span>
        </div>
          : <>
            <ol className="audit-event-list" aria-label="Lịch sử hoạt động của session">
              {events.map(event => <AuditEventItem key={event.eventId} event={event} />)}
            </ol>
            {loadMoreError && <div className="notice error audit-load-more-error" role="alert">
              <span>{loadMoreError.message}</span>
              <button type="button" className="secondary" onClick={() => void loadNextPage()}>
                Thử tải lại
              </button>
            </div>}
            {hasNext && !loadMoreError && <button
              type="button"
              className="secondary audit-load-more"
              disabled={loadingMore}
              onClick={() => void loadNextPage()}
            >{loadingMore ? 'Đang tải thêm…' : 'Tải thêm'}</button>}
          </>}
  </section>
}

function AuditEventItem({ event }: { event: SessionAuditEvent }) {
  const group = eventGroup(event.eventType)
  return <li className={`audit-event audit-${group.key}`}>
    <span className="audit-marker" aria-hidden="true" />
    <article>
      <span className="audit-group-label">{group.label}</span>
      <h3>{eventSentence(event)}</h3>
      <AuditDetails event={event} />
      <AuditTime value={event.createdAt} />
    </article>
  </li>
}

function eventGroup(eventType: string) {
  if (eventType.startsWith('REVIEWER_')) return { key: 'reviewer', label: 'Reviewer' }
  if (eventType.startsWith('VOTE_')) return { key: 'vote', label: 'Vote' }
  if (eventType.startsWith('SESSION_')) return { key: 'lifecycle', label: 'Vòng đời' }
  return { key: 'other', label: 'Hoạt động' }
}

function eventSentence(event: SessionAuditEvent) {
  const actor = event.actorDisplayName?.trim() || 'Người dùng không xác định'
  const target = event.targetDisplayName?.trim() || 'reviewer không xác định'
  const card = event.decisionCardSummary?.trim() || 'Decision Card'
  const oldDecision = event.change?.oldValue?.decision
  const newDecision = event.change?.newValue?.decision
  const result = event.change?.newValue?.status ?? event.change?.oldValue?.status

  switch (event.eventType) {
    case 'REVIEWER_ASSIGNED':
      return `${actor} đã phân công ${target} làm reviewer.`
    case 'REVIEWER_REMOVED':
      return `${actor} đã gỡ ${target} khỏi reviewer roster.`
    case 'REVIEWER_REACTIVATED':
      return `${actor} đã phân công lại ${target}.`
    case 'VOTE_CREATED':
      return newDecision
        ? `${actor} đã bỏ phiếu ${statusLabel(newDecision)} cho ${card}.`
        : `${actor} đã bỏ phiếu cho ${card}.`
    case 'VOTE_UPDATED':
      return oldDecision && newDecision
        ? `${actor} đã đổi phiếu từ ${statusLabel(oldDecision)} sang ${statusLabel(newDecision)} cho ${card}.`
        : `${actor} đã cập nhật phiếu cho ${card}.`
    case 'SESSION_CLOSED':
      return result
        ? `${actor} đã đóng session với kết quả ${statusLabel(result)}.`
        : `${actor} đã đóng session.`
    case 'SESSION_REOPENED':
      return `${actor} đã mở lại session.`
    default:
      return 'Hoạt động trong session đã được cập nhật.'
  }
}

function AuditDetails({ event }: { event: SessionAuditEvent }) {
  const oldNote = event.change?.oldValue?.note
  const newNote = event.change?.newValue?.note
  const noteChanged = event.eventType === 'VOTE_UPDATED' && oldNote !== newNote
  const createdNote = event.eventType === 'VOTE_CREATED' ? newNote : null
  const previousCloseReason = event.eventType === 'SESSION_REOPENED'
    ? event.change?.oldValue?.closeReason
    : null

  return <div className="audit-details">
    {event.reason && <p><strong>Lý do:</strong> {event.reason}</p>}
    {createdNote && <p><strong>Ghi chú:</strong> {createdNote}</p>}
    {noteChanged && <>
      <p><strong>Ghi chú trước:</strong> {oldNote || 'Không có'}</p>
      <p><strong>Ghi chú mới:</strong> {newNote || 'Không có'}</p>
    </>}
    {previousCloseReason && <p><strong>Lý do đóng trước đó:</strong> {previousCloseReason}</p>}
  </div>
}

function AuditTime({ value }: { value: string }) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return <span className="audit-time">Không xác định thời gian</span>
  return <time className="audit-time" dateTime={value} title={date.toISOString()}>
    {date.toLocaleString('vi-VN')}
  </time>
}

function AuditUnavailable({ error, retry }: { error: ApiError; retry: () => Promise<void> }) {
  const terminal = error.status === 401 || error.status === 404
  return <div className="empty" role="alert">
    <strong>{error.status === 404 ? 'Audit Timeline không khả dụng' : 'Không thể tải Audit Timeline'}</strong>
    <span>{error.message}</span>
    {!terminal && <button type="button" onClick={() => void retry()}>Thử lại</button>}
  </div>
}
