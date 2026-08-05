import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { aiLabel, modeLabel, statusLabel } from '../lib/labels'
import type { SharedDecisionCard, SharedReviewSessionDetail, WorkspaceDetail } from '../types'

export default function SharedSessionDetailPage() {
  const { workspaceId, sessionId } = useParams()
  const [workspace, setWorkspace] = useState<WorkspaceDetail>()
  const [session, setSession] = useState<SharedReviewSessionDetail>()
  const [error, setError] = useState<ApiError>()
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    if (!workspaceId || !sessionId) {
      setError(new ApiError('Không tìm thấy Shared Review Session.', 404))
      setLoading(false)
      return
    }
    setLoading(true)
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
      setLoading(false)
    }
  }, [sessionId, workspaceId])

  useEffect(() => {
    void load()
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

    <details className="source-panel">
      <summary>Xem nội dung đã gửi để phân tích</summary>
      <pre className="code">{session.rawContent}</pre>
      {session.promptContent && <><h3>Ý định / yêu cầu mong muốn</h3>
        <pre className="code">{session.promptContent}</pre></>}
    </details>

    <div className="section-heading">
      <div>
        <h2>Decision Cards</h2>
        <p>{session.decisions.length > 0
          ? `${session.decisions.length} thẻ được tạo bởi Rule Engine và AI.`
          : 'Không có Decision Card trong session này.'}</p>
      </div>
    </div>
    {session.decisions.length === 0 ? <div className="empty safe-state">
      <strong>Không phát hiện rủi ro cần quyết định</strong>
      <span>Backend đã hoàn tất phân tích và không tạo Decision Card.</span>
    </div> : <div className="card-list">
      {session.decisions.map(card => <SharedDecisionCardView key={card.id} card={card} />)}
    </div>}
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

function SharedDecisionCardView({ card }: { card: SharedDecisionCard }) {
  const source = card.engineType === 'AI_BASED' ? 'AI' : 'RULE'
  return <article className={`decision-card shared-decision-card risk-${card.riskLevel.toLowerCase()}`}>
    <div className="card-meta">
      <span className={`engine-tag ${card.engineType.toLowerCase()}`}>
        Nguồn: {source}
      </span>
      <span>Mức độ: {card.riskLevel}</span>
    </div>
    <p className="decision-category">Danh mục: {card.riskCategory}</p>
    <h3>{card.questionText}</h3>
    <pre className="snippet">{card.codeSnippet}</pre>
    <div className="decision-readonly-status">
      <span>Quyết định hiện tại</span>
      <strong className={`status ${card.humanDecision.toLowerCase()}`}>
        {statusLabel(card.humanDecision)}
      </strong>
      {card.reviewerNote && <p>Ghi chú: {card.reviewerNote}</p>}
      {card.decidedByName && <small>Người xử lý: {card.decidedByName}</small>}
    </div>
  </article>
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
