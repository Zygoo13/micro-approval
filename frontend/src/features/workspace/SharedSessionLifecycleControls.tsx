import { FormEvent, useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../../lib/api'
import { statusLabel } from '../../lib/labels'
import type { SharedReviewSessionDetail, WorkspaceDetail } from '../../types'
import { canManageSharedSessionLifecycle } from './sharedSessionPermissions'

const REASON_MAX_LENGTH = 1000
const FINAL_STATUSES = new Set(['APPROVED', 'REJECTED'])
const CONFLICT_MESSAGE = 'Trạng thái session đã thay đổi ở nơi khác. Dữ liệu mới nhất đã được tải lại.'

function normalizeError(exception: unknown, fallback: string) {
  return exception instanceof ApiError ? exception : new ApiError(fallback, null)
}

export default function SharedSessionLifecycleControls({
  workspace,
  session,
  onRefresh,
}: {
  workspace: WorkspaceDetail
  session: SharedReviewSessionDetail
  onRefresh: () => Promise<void>
}) {
  const [dialog, setDialog] = useState<'close' | 'reopen'>()
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [actionError, setActionError] = useState<ApiError>()
  const [conflictMessage, setConflictMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const reasonRef = useRef<HTMLTextAreaElement>(null)
  const canManage = canManageSharedSessionLifecycle(workspace)
  const canClose = canManage && !session.closed && FINAL_STATUSES.has(session.status)

  useEffect(() => {
    if (dialog === 'close') reasonRef.current?.focus()
  }, [dialog])

  function openDialog(type: 'close' | 'reopen') {
    setDialog(type)
    setReason('')
    setReasonError('')
    setActionError(undefined)
    setConflictMessage('')
  }

  function cancelDialog() {
    if (submitting) return
    setDialog(undefined)
    setReasonError('')
    setActionError(undefined)
  }

  async function handleFailure(exception: unknown, fallback: string) {
    const error = normalizeError(exception, fallback)
    if (error.status === 409) {
      setConflictMessage(CONFLICT_MESSAGE)
      setDialog(undefined)
      await onRefresh()
      return
    }
    setActionError(error)
    const backendReasonError = error.validationErrors.reason
      ?? error.validationErrors.reasonValid
    if (backendReasonError) setReasonError(backendReasonError)
  }

  async function submitClose(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    const normalizedReason = reason.trim()
    if (normalizedReason.length > REASON_MAX_LENGTH) {
      setReasonError('Lý do đóng session tối đa 1.000 ký tự.')
      return
    }
    setSubmitting(true)
    setReasonError('')
    setActionError(undefined)
    setConflictMessage('')
    try {
      await api.closeSharedReviewSession(workspace.id, session.id, normalizedReason
        ? { reason: normalizedReason }
        : {})
      setDialog(undefined)
      setReason('')
      await onRefresh()
    } catch (exception) {
      await handleFailure(exception, 'Không thể đóng Shared Review Session.')
    } finally {
      setSubmitting(false)
    }
  }

  async function submitReopen() {
    if (submitting) return
    setSubmitting(true)
    setActionError(undefined)
    setConflictMessage('')
    try {
      await api.reopenSharedReviewSession(workspace.id, session.id)
      setDialog(undefined)
      await onRefresh()
    } catch (exception) {
      await handleFailure(exception, 'Không thể mở lại Shared Review Session.')
    } finally {
      setSubmitting(false)
    }
  }

  return <section className="session-lifecycle-section" aria-labelledby="session-lifecycle-title">
    <div className={`session-lifecycle-banner ${session.closed ? 'closed' : 'open'}`}>
      <div>
        <p className="eyebrow" id="session-lifecycle-title">VÒNG ĐỜI SESSION</p>
        <h2>{session.closed ? 'Session đã đóng' : 'Session đang mở'}</h2>
        <p>{session.closed
          ? 'Kết quả đã được chốt. Phiếu và reviewer hiện ở chế độ chỉ đọc.'
          : 'Phiếu và reviewer có thể thay đổi theo quyền hiện tại.'}</p>
      </div>
      <span className={`lifecycle-state ${session.closed ? 'closed' : 'open'}`}>
        {session.closed ? 'Closed' : 'Open'}
      </span>
    </div>

    <dl className="lifecycle-metadata">
      <div><dt>Kết quả</dt><dd>{statusLabel(session.status)}</dd></div>
      {session.closed && <>
        <div><dt>Đóng lúc</dt><dd>{session.closedAt
          ? <time dateTime={session.closedAt}>{new Date(session.closedAt).toLocaleString('vi-VN')}</time>
          : 'Không rõ'}</dd></div>
        <div><dt>Người đóng</dt><dd>{session.closedByDisplayName ?? 'Không rõ'}</dd></div>
        {session.closeReason && <div className="lifecycle-reason"><dt>Lý do</dt><dd>{session.closeReason}</dd></div>}
      </>}
    </dl>

    {canManage && !session.closed && !canClose && <div className="notice warning" role="status">
      Session chưa đủ điều kiện để đóng. Kết quả phải là APPROVED hoặc REJECTED.
    </div>}
    {conflictMessage && <div className="notice warning" role="alert">{conflictMessage}</div>}
    {actionError && <div className="notice error" role="alert">{actionError.message}</div>}

    {canClose && <button type="button" className="danger" onClick={() => openDialog('close')}>
      Đóng session
    </button>}
    {canManage && session.closed && <button type="button" onClick={() => openDialog('reopen')}>
      Mở lại session
    </button>}

    {dialog === 'close' && <div
      className="lifecycle-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="close-session-title"
    >
      <form onSubmit={submitClose} noValidate>
        <h3 id="close-session-title">Đóng Shared Review Session?</h3>
        <p>Kết quả hiện tại: <strong>{statusLabel(session.status)}</strong>.</p>
        <ul>
          <li>Vote sẽ không thể chỉnh sửa.</li>
          <li>Reviewer roster sẽ chuyển sang chế độ chỉ đọc.</li>
          <li>Session vẫn xem được và OWNER/ADMIN có thể mở lại.</li>
        </ul>
        <label htmlFor="close-session-reason">Lý do (tùy chọn)
          <textarea
            ref={reasonRef}
            id="close-session-reason"
            rows={4}
            maxLength={REASON_MAX_LENGTH}
            value={reason}
            disabled={submitting}
            aria-invalid={Boolean(reasonError)}
            aria-describedby={reasonError ? 'close-session-reason-error' : 'close-session-reason-count'}
            onChange={event => {
              setReason(event.target.value)
              setReasonError('')
            }}
          />
        </label>
        {reasonError && <span id="close-session-reason-error" className="field-error">{reasonError}</span>}
        <span id="close-session-reason-count" className="character-count">{reason.length}/{REASON_MAX_LENGTH}</span>
        <div className="form-actions">
          <button type="button" className="secondary" disabled={submitting} onClick={cancelDialog}>Hủy</button>
          <button type="submit" className="danger" disabled={submitting}>
            {submitting ? 'Đang đóng…' : 'Xác nhận đóng'}
          </button>
        </div>
      </form>
    </div>}

    {dialog === 'reopen' && <div
      className="lifecycle-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reopen-session-title"
    >
      <div>
        <h3 id="reopen-session-title">Mở lại Shared Review Session?</h3>
        <p>Mở lại session sẽ cho phép reviewer chỉnh sửa phiếu và quản lý reviewer trở lại.</p>
        <p>Aggregate sẽ được tính lại từ dữ liệu authoritative hiện tại.</p>
        <div className="form-actions">
          <button type="button" className="secondary" disabled={submitting} onClick={cancelDialog}>Hủy</button>
          <button type="button" disabled={submitting} onClick={() => void submitReopen()}>
            {submitting ? 'Đang mở lại…' : 'Xác nhận mở lại'}
          </button>
        </div>
      </div>
    </div>}
  </section>
}
