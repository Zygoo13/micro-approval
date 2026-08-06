import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { aiLabel, modeLabel, statusLabel } from '../lib/labels'
import type { SessionVoting, SessionVotingStatus, SharedReviewSessionDetail, WorkspaceDetail } from '../types'
import SessionReviewersSection from '../features/workspace/SessionReviewersSection'
import TeamVotingSection from '../features/workspace/TeamVotingSection'
import SharedSessionLifecycleControls from '../features/workspace/SharedSessionLifecycleControls'

export default function SharedSessionDetailPage() {
  const { workspaceId, sessionId } = useParams()
  const [workspace, setWorkspace] = useState<WorkspaceDetail>()
  const [session, setSession] = useState<SharedReviewSessionDetail>()
  const [error, setError] = useState<ApiError>()
  const [loading, setLoading] = useState(true)
  const [sectionsRefreshKey, setSectionsRefreshKey] = useState(0)

  const updateVotingStatus = useCallback((status: SessionVotingStatus) => {
    setSession(current => current && current.status !== status
      ? { ...current, status }
      : current)
  }, [])

  const updateVotingState = useCallback((voting: SessionVoting) => {
    setSession(current => current ? {
      ...current,
      status: voting.sessionStatus,
      closed: voting.closed,
      closedAt: voting.closedAt,
      closedByUserId: voting.closedByUserId,
      closedByDisplayName: voting.closedByDisplayName,
      closeReason: voting.closeReason,
      lifecycleVersion: voting.lifecycleVersion,
    } : current)
  }, [])

  const load = useCallback(async (showLoading = true) => {
    if (!workspaceId || !sessionId) {
      setError(new ApiError('Không tìm thấy Shared Review Session.', 404))
      setLoading(false)
      return
    }
    if (showLoading) setLoading(true)
    setError(undefined)
    try {
      const [workspaceResponse, sessionResponse] = await Promise.all([
        api.getWorkspaceById(workspaceId),
        api.getSharedReviewSession(workspaceId, sessionId),
      ])
      setWorkspace(workspaceResponse)
      setSession(sessionResponse)
    } catch (exception) {
      setError(exception instanceof ApiError
        ? exception
        : new ApiError('Không thể tải Shared Review Session.', null))
    } finally {
      if (showLoading) setLoading(false)
    }
  }, [sessionId, workspaceId])

  const refreshAuthoritativeState = useCallback(async () => {
    await load(false)
    setSectionsRefreshKey(current => current + 1)
  }, [load])

  useEffect(() => {
    void load(true)
  }, [load])

  if (loading) return <p className="loading" role="status">Đang tải Shared Review Session…</p>
  if (!workspace || !session || error) return <DetailUnavailable
    workspaceId={workspaceId}
    error={error}
    retry={load}
  />

  return <section className="page-stack">
    <div className="detail-head">
      <div>
        <Link className="back-link" to={`/workspaces/${workspace.id}/sessions`}>← Sessions của {workspace.name}</Link>
        <p className="eyebrow">{modeLabel(session.mode)}</p>
        <h1>{session.title}</h1>
        <p>Tạo bởi {session.createdByDisplayName} lúc <time dateTime={session.createdAt}>
          {new Date(session.createdAt).toLocaleString('vi-VN')}
        </time></p>
      </div>
      <span className={`status large ${session.status.toLowerCase()}`}>
        {statusLabel(session.status)}
      </span>
    </div>

    <dl className="workspace-metadata session-metadata">
      <div><dt>Workspace</dt><dd>{workspace.name}</dd></div>
      <div><dt>Chế độ</dt><dd>{modeLabel(session.mode)}</dd></div>
      <div><dt>Hoàn tất</dt><dd>{session.completedAt
        ? <time dateTime={session.completedAt}>{new Date(session.completedAt).toLocaleString('vi-VN')}</time>
        : 'Chưa hoàn tất'}</dd></div>
    </dl>

    <AiOutcome session={session} />

    <SharedSessionLifecycleControls
      workspace={workspace}
      session={session}
      onRefresh={refreshAuthoritativeState}
    />

    <SessionReviewersSection
      workspace={workspace}
      sessionId={session.id}
      closed={session.closed}
      refreshKey={sectionsRefreshKey}
      onRosterChanged={() => setSectionsRefreshKey(current => current + 1)}
    />

    <details className="source-panel">
      <summary>Xem nội dung đã gửi để phân tích</summary>
      <pre className="code">{session.rawContent}</pre>
      {session.promptContent && <><h3>Ý định / yêu cầu mong muốn</h3>
        <pre className="code">{session.promptContent}</pre></>}
    </details>

    <TeamVotingSection
      workspace={workspace}
      sessionId={session.id}
      decisionCards={session.decisions}
      closed={session.closed}
      refreshKey={sectionsRefreshKey}
      onSessionStatusChange={updateVotingStatus}
      onSessionVotingChange={updateVotingState}
    />
  </section>
}

function AiOutcome({ session }: { session: SharedReviewSessionDetail }) {
  const fallback = session.aiAnalysisStatus === 'FALLBACK'
  return <div className={`ai-banner ${session.aiAnalysisStatus.toLowerCase()}`}>
    <div>
      <strong>{aiLabel(session.aiAnalysisStatus)}</strong>
      <span>{fallback
        ? 'AI không hoàn tất; kết quả từ Rule Engine vẫn được giữ lại.'
        : session.aiAnalysisStatus === 'SUCCEEDED'
          ? `AI đã hoàn tất với ${session.aiTokenUsed} token.`
          : 'Session được xử lý bằng Rule Engine theo cấu hình hiện tại.'}</span>
    </div>
  </div>
}

function DetailUnavailable({
  workspaceId,
  error,
  retry,
}: {
  workspaceId?: string
  error?: ApiError
  retry: () => Promise<void>
}) {
  const terminal = error?.status === 401 || error?.status === 403 || error?.status === 404
  return <section className="page-stack">
    <div className="empty" role="alert">
      <strong>{error?.status === 404
        ? 'Không tìm thấy Shared Review Session'
        : 'Không thể tải Shared Review Session'}</strong>
      <span>{error?.message ?? 'Session không khả dụng.'}</span>
      <div className="form-actions">
        <Link className="button secondary" to={workspaceId
          ? `/workspaces/${workspaceId}/sessions`
          : '/workspaces'}>Quay lại</Link>
        {!terminal && <button type="button" onClick={() => void retry()}>Thử lại</button>}
        {error?.status === 401 && <Link className="button" to="/login">Đăng nhập lại</Link>}
      </div>
    </div>
  </section>
}
