import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, api, auth } from '../../lib/api'
import type {
  DecisionCardVoting,
  SessionReviewer,
  SessionVoting,
  SessionVotingStatus,
  SharedDecisionCard,
  TeamDecisionStatus,
  TeamVote,
  TeamVoteDecision,
  UpsertTeamVoteRequest,
  WorkspaceDetail,
} from '../../types'
import { canSubmitTeamVote } from './sharedSessionPermissions'

const NOTE_MAX_LENGTH = 2000

const sessionLabels: Record<SessionVotingStatus, string> = {
  PENDING: 'Chưa bắt đầu',
  IN_REVIEW: 'Đang review',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
}

const decisionLabels: Record<TeamDecisionStatus | TeamVoteDecision, string> = {
  PENDING: 'Chưa đủ quyết định',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
}

function normalizedError(exception: unknown, fallback: string) {
  return exception instanceof ApiError ? exception : new ApiError(fallback, null)
}

export default function TeamVotingSection({
  workspace,
  sessionId,
  decisionCards,
  refreshKey = 0,
  closed = false,
  onSessionStatusChange,
  onSessionVotingChange,
  onVoteChanged,
}: {
  workspace: WorkspaceDetail
  sessionId: string
  decisionCards: SharedDecisionCard[]
  refreshKey?: number
  closed?: boolean
  onSessionStatusChange?: (status: SessionVotingStatus) => void
  onSessionVotingChange?: (voting: SessionVoting) => void
  onVoteChanged?: () => void
}) {
  const [voting, setVoting] = useState<SessionVoting>()
  const [reviewers, setReviewers] = useState<SessionReviewer[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ApiError>()
  const [actionError, setActionError] = useState<ApiError>()
  const [conflictMessage, setConflictMessage] = useState('')

  const load = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true)
    setLoadError(undefined)
    try {
      const [votingResponse, reviewerResponse] = await Promise.all([
        api.getSessionVoting(workspace.id, sessionId),
        api.getSessionReviewers(workspace.id, sessionId),
      ])
      setVoting(votingResponse)
      setReviewers(reviewerResponse)
      onSessionStatusChange?.(votingResponse.sessionStatus)
      onSessionVotingChange?.(votingResponse)
    } catch (exception) {
      setLoadError(normalizedError(exception, 'Không thể tải dữ liệu Team Voting.'))
    } finally {
      if (showLoading) setLoading(false)
    }
  }, [onSessionStatusChange, onSessionVotingChange, sessionId, workspace.id])

  useEffect(() => {
    void load()
  }, [load, refreshKey])

  const currentAssignment = useMemo(() => {
    const currentEmail = auth.getCurrentUserEmail()?.toLocaleLowerCase()
    if (!currentEmail) return undefined
    return reviewers.find(reviewer => reviewer.status === 'ASSIGNED'
      && reviewer.email.toLocaleLowerCase() === currentEmail)
  }, [reviewers])
  const effectiveClosed = closed || Boolean(voting?.closed)
  const canVote = !effectiveClosed && canSubmitTeamVote(workspace, currentAssignment)

  async function submitVote(cardId: string, request: UpsertTeamVoteRequest) {
    setActionError(undefined)
    setConflictMessage('')
    try {
      const response = await api.upsertTeamVote(workspace.id, sessionId, cardId, request)
      setVoting(response)
      onSessionStatusChange?.(response.sessionStatus)
      onSessionVotingChange?.(response)
      onVoteChanged?.()
      return undefined
    } catch (exception) {
      const error = normalizedError(exception, 'Không thể lưu phiếu đánh giá.')
      if (error.status === 409) {
        setConflictMessage('Phiếu đã được thay đổi ở nơi khác. Dữ liệu mới nhất đang được tải lại; hệ thống sẽ không tự gửi lại phiếu.')
        await load(false)
      } else {
        setActionError(error)
      }
      return error
    }
  }

  return <section className="team-voting-section" aria-labelledby="team-voting-title">
    <div className="section-heading">
      <div>
        <h2 id="team-voting-title">Team Voting</h2>
        <p>Quyết định được tổng hợp từ toàn bộ reviewer đang được phân công.</p>
      </div>
      {voting && <span className={`status large ${voting.sessionStatus.toLowerCase()}`}>
        {sessionLabels[voting.sessionStatus]}
      </span>}
    </div>

    {conflictMessage && <div className="notice warning" role="alert">{conflictMessage}</div>}
    {actionError && <div className="notice error" role="alert">{actionError.message}</div>}
    {effectiveClosed && <div className="notice readonly" role="status">
      Session đã đóng. Các phiếu và ghi chú vẫn hiển thị nhưng My Vote ở chế độ chỉ đọc.
    </div>}

    {loading ? <p className="loading" role="status">Đang tải Team Voting…</p>
      : loadError ? <VotingUnavailable error={loadError} retry={load} />
        : voting && <>
          <dl className="voting-summary">
            <div><dt>Trạng thái session</dt><dd>{sessionLabels[voting.sessionStatus]}</dd></div>
            <div><dt>Reviewer đang tính quorum</dt><dd>{voting.reviewerCount}</dd></div>
            <div><dt>Decision Cards</dt><dd>{voting.cards.length}</dd></div>
          </dl>

          {voting.reviewerCount === 0 && <div className="empty compact">
            <strong>Chưa có reviewer được phân công.</strong>
            <span>OWNER hoặc ADMIN cần phân công reviewer trước khi bắt đầu vote.</span>
          </div>}

          {voting.cards.length === 0 ? <div className="empty safe-state">
            <strong>Session không có Decision Card — đã duyệt an toàn.</strong>
            <span>Trạng thái APPROVED được lấy trực tiếp từ backend.</span>
          </div> : <div className="team-voting-card-list">
            {voting.cards.map(cardVoting => <VotingCard
              key={cardVoting.cardId}
              cardVoting={cardVoting}
              decisionCard={decisionCards.find(card => card.id === cardVoting.cardId)}
              currentAssignment={currentAssignment}
              canVote={canVote}
              onSubmit={submitVote}
            />)}
          </div>}
        </>}
  </section>
}

function VotingCard({
  cardVoting,
  decisionCard,
  currentAssignment,
  canVote,
  onSubmit,
}: {
  cardVoting: DecisionCardVoting
  decisionCard?: SharedDecisionCard
  currentAssignment?: SessionReviewer
  canVote: boolean
  onSubmit: (cardId: string, request: UpsertTeamVoteRequest) => Promise<ApiError | undefined>
}) {
  const currentVote = currentAssignment
    ? cardVoting.votes.find(vote => vote.reviewerAssignmentId === currentAssignment.assignmentId)
    : undefined
  const source = decisionCard?.engineType === 'AI_BASED' ? 'AI' : 'RULE'

  return <article className={`team-voting-card ${decisionCard ? `risk-${decisionCard.riskLevel.toLowerCase()}` : ''}`}>
    <div className="team-voting-card-head">
      <div>
        {decisionCard && <div className="card-meta">
          <span className={`engine-tag ${decisionCard.engineType.toLowerCase()}`}>Nguồn: {source}</span>
          <span>Mức độ: {decisionCard.riskLevel}</span>
        </div>}
        <h3>{decisionCard?.questionText ?? 'Decision Card không còn trong session response'}</h3>
      </div>
      <span className={`status ${cardVoting.teamDecision.toLowerCase()}`}>
        {decisionLabels[cardVoting.teamDecision]}
      </span>
    </div>

    {decisionCard && <>
      <p className="decision-category">Danh mục: {decisionCard.riskCategory}</p>
      <pre className="snippet">{decisionCard.codeSnippet}</pre>
    </>}

    <p className="quorum-summary">
      <strong>{cardVoting.validVoteCount}/{cardVoting.assignedReviewerCount}</strong>
      {' '}phiếu hợp lệ trong quorum hiện tại
    </p>

    <div className="team-vote-list" aria-label="Danh sách phiếu đánh giá">
      {cardVoting.votes.length === 0 ? <div className="empty compact">
        <strong>Chưa có phiếu đánh giá.</strong>
        <span>Card vẫn ở trạng thái PENDING cho đến khi backend đủ quorum.</span>
      </div> : cardVoting.votes.map(vote => <VoteView key={vote.voteId} vote={vote} />)}
    </div>

    {canVote && currentAssignment && <MyVoteForm
      cardId={cardVoting.cardId}
      currentVote={currentVote}
      onSubmit={onSubmit}
    />}
  </article>
}

function VoteView({ vote }: { vote: TeamVote }) {
  return <div className={`team-vote ${vote.counted ? 'counted' : 'stale'}`}>
    <div className="team-vote-head">
      <strong>{vote.reviewerDisplayName}</strong>
      <span className={`status ${vote.decision.toLowerCase()}`}>
        {decisionLabels[vote.decision]}
      </span>
    </div>
    {vote.note && <p className="team-vote-note">Ghi chú: {vote.note}</p>}
    <div className="team-vote-time">
      <span>Tạo <time dateTime={vote.createdAt}>{new Date(vote.createdAt).toLocaleString('vi-VN')}</time></span>
      <span>Cập nhật <time dateTime={vote.updatedAt}>{new Date(vote.updatedAt).toLocaleString('vi-VN')}</time></span>
    </div>
    {!vote.counted && <div className="stale-vote-label">
      <strong>Không tính vào quorum</strong>
      <span>Cần xác nhận lại</span>
    </div>}
  </div>
}

function MyVoteForm({
  cardId,
  currentVote,
  onSubmit,
}: {
  cardId: string
  currentVote?: TeamVote
  onSubmit: (cardId: string, request: UpsertTeamVoteRequest) => Promise<ApiError | undefined>
}) {
  const [decision, setDecision] = useState<TeamVoteDecision>(currentVote?.decision ?? 'APPROVED')
  const [note, setNote] = useState(currentVote?.note ?? '')
  const [noteError, setNoteError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    setDecision(currentVote?.decision ?? 'APPROVED')
    setNote(currentVote?.note ?? '')
    setNoteError('')
  }, [currentVote?.decision, currentVote?.note, currentVote?.version])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    const normalizedNote = note.trim()
    if (decision === 'REJECTED' && !normalizedNote) {
      setNoteError('Ghi chú là bắt buộc khi từ chối.')
      return
    }

    setNoteError('')
    setSubmitting(true)
    const request: UpsertTeamVoteRequest = {
      decision,
      ...(normalizedNote ? { note: normalizedNote } : {}),
      ...(currentVote ? { version: currentVote.version } : {}),
    }
    await onSubmit(cardId, request)
    setSubmitting(false)
  }

  const stale = currentVote && !currentVote.counted
  return <form className="my-vote-form" onSubmit={submit} noValidate>
    <div>
      <h4>My Vote</h4>
      <p>{stale
        ? 'Phiếu cũ không còn được tính. Hãy xác nhận lại hoặc chỉnh sửa trước khi gửi.'
        : currentVote ? 'Bạn đang cập nhật phiếu hiện tại.' : 'Bạn chưa gửi phiếu cho card này.'}</p>
    </div>
    <fieldset disabled={submitting}>
      <legend>Quyết định</legend>
      <label>
        <input
          type="radio"
          name={`team-vote-${cardId}`}
          value="APPROVED"
          checked={decision === 'APPROVED'}
          onChange={() => {
            setDecision('APPROVED')
            setNoteError('')
          }}
        /> Đã duyệt
      </label>
      <label>
        <input
          type="radio"
          name={`team-vote-${cardId}`}
          value="REJECTED"
          checked={decision === 'REJECTED'}
          onChange={() => setDecision('REJECTED')}
        /> Từ chối
      </label>
    </fieldset>
    <label htmlFor={`team-vote-note-${cardId}`}>Ghi chú {decision === 'REJECTED' ? '(bắt buộc)' : '(tùy chọn)'}
      <textarea
        id={`team-vote-note-${cardId}`}
        rows={4}
        maxLength={NOTE_MAX_LENGTH}
        value={note}
        disabled={submitting}
        aria-invalid={Boolean(noteError)}
        aria-describedby={noteError
          ? `team-vote-note-error-${cardId}`
          : `team-vote-note-count-${cardId}`}
        onChange={event => {
          setNote(event.target.value)
          setNoteError('')
        }}
      />
    </label>
    {noteError && <span id={`team-vote-note-error-${cardId}`} className="field-error">
      {noteError}
    </span>}
    <span id={`team-vote-note-count-${cardId}`} className="character-count">
      {note.length}/{NOTE_MAX_LENGTH}
    </span>
    <button type="submit" disabled={submitting}>
      {submitting ? 'Đang lưu phiếu…'
        : stale ? 'Xác nhận lại phiếu'
          : currentVote ? 'Cập nhật phiếu' : 'Gửi phiếu'}
    </button>
  </form>
}

function VotingUnavailable({
  error,
  retry,
}: {
  error: ApiError
  retry: (showLoading?: boolean) => Promise<void>
}) {
  const terminal = error.status === 401 || error.status === 403 || error.status === 404
  return <div className="empty" role="alert">
    <strong>{error.status === 404 ? 'Team Voting không khả dụng' : 'Không thể tải Team Voting'}</strong>
    <span>{error.message}</span>
    {!terminal && <button type="button" onClick={() => void retry()}>Thử lại</button>}
  </div>
}
