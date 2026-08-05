import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../../lib/api'
import { aiLabel, modeLabel, statusLabel } from '../../lib/labels'
import type { SharedReviewSessionSummary, WorkspaceDetail } from '../../types'
import { canCreateSharedSession } from './sharedSessionPermissions'

function normalizedError(exception: unknown) {
  return exception instanceof ApiError
    ? exception
    : new ApiError('Không thể tải Shared Review Sessions.', null)
}

export default function WorkspaceSessionsSection({ workspace }: { workspace: WorkspaceDetail }) {
  const [sessions, setSessions] = useState<SharedReviewSessionSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError>()
  const canCreate = canCreateSharedSession(workspace)

  const load = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      setSessions(await api.getSharedReviewSessions(workspace.id))
    } catch (exception) {
      setError(normalizedError(exception))
    } finally {
      setLoading(false)
    }
  }, [workspace.id])

  useEffect(() => {
    void load()
  }, [load])

  return <section id="sessions" className="workspace-sessions-section" aria-labelledby="workspace-sessions-title">
    <div className="section-heading">
      <div>
        <h2 id="workspace-sessions-title">Shared Review Sessions</h2>
        <p>Rule Engine chạy trước AI để tạo các Decision Card cho workspace.</p>
      </div>
      {canCreate && <Link className="button" to={`/workspaces/${workspace.id}/sessions/new`}>
        Tạo session
      </Link>}
    </div>

    {loading ? <p className="loading" role="status">Đang tải Shared Review Sessions…</p>
      : error ? <div className="empty" role="alert">
        <strong>Không thể tải Shared Review Sessions</strong>
        <span>{error.message}</span>
        <button type="button" onClick={() => void load()}>Thử lại</button>
      </div>
        : sessions.length === 0 ? <div className="empty">
          <strong>Chưa có Shared Review Session</strong>
          <span>{canCreate
            ? 'Tạo session đầu tiên để review code cùng workspace.'
            : 'Workspace chưa có phiên review nào để xem.'}</span>
          {canCreate && <Link className="button" to={`/workspaces/${workspace.id}/sessions/new`}>
            Tạo session đầu tiên
          </Link>}
        </div>
          : <div className="session-list">
            {sessions.map(session => <SessionSummaryRow
              key={session.id}
              workspaceId={workspace.id}
              session={session}
            />)}
          </div>}

    <Link className="text-link" to={`/workspaces/${workspace.id}/sessions`}>
      Mở trang danh sách Sessions
    </Link>
  </section>
}

function SessionSummaryRow({
  workspaceId,
  session,
}: {
  workspaceId: string
  session: SharedReviewSessionSummary
}) {
  return <Link
    className="session-row"
    to={`/workspaces/${workspaceId}/sessions/${session.id}`}
  >
    <div>
      <strong>{session.title}</strong>
      <span>{modeLabel(session.mode)} · {session.createdByDisplayName}</span>
      <time dateTime={session.createdAt}>
        {new Date(session.createdAt).toLocaleString('vi-VN')}
      </time>
    </div>
    <div className="row-status">
      <span className={`ai-status ${session.aiAnalysisStatus.toLowerCase()}`}>
        {aiLabel(session.aiAnalysisStatus)}
      </span>
      <span className={`status ${session.status.toLowerCase()}`}>
        {statusLabel(session.status)}
      </span>
    </div>
  </Link>
}
