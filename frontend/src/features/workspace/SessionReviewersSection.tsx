import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, api, auth } from '../../lib/api'
import type {
  SessionReviewer,
  WorkspaceDetail,
  WorkspaceMember,
  WorkspaceRole,
} from '../../types'
import { canManageSessionReviewers } from './sharedSessionPermissions'

const ELIGIBLE_ROLES = new Set<WorkspaceRole>(['OWNER', 'ADMIN', 'REVIEWER'])
const REMOVE_REASON_MAX_LENGTH = 1000

const roleLabels: Record<WorkspaceRole, string> = {
  OWNER: 'Chủ sở hữu',
  ADMIN: 'Quản trị viên',
  REVIEWER: 'Người kiểm duyệt',
  MEMBER: 'Thành viên',
  AUDITOR: 'Kiểm toán viên',
}

function normalizedError(exception: unknown, fallback: string) {
  return exception instanceof ApiError ? exception : new ApiError(fallback, null)
}

function byAssignedAt(left: SessionReviewer, right: SessionReviewer) {
  return left.assignedAt.localeCompare(right.assignedAt)
}

export default function SessionReviewersSection({
  workspace,
  sessionId,
  closed = false,
  refreshKey = 0,
  onRosterChanged,
}: {
  workspace: WorkspaceDetail
  sessionId: string
  closed?: boolean
  refreshKey?: number
  onRosterChanged?: () => void
}) {
  const [reviewers, setReviewers] = useState<SessionReviewer[]>([])
  const [members, setMembers] = useState<WorkspaceMember[]>([])
  const [loading, setLoading] = useState(true)
  const [membersLoading, setMembersLoading] = useState(false)
  const [loadError, setLoadError] = useState<ApiError>()
  const [membersError, setMembersError] = useState<ApiError>()
  const [actionError, setActionError] = useState<ApiError>()
  const [selectedMemberId, setSelectedMemberId] = useState('')
  const [selectionError, setSelectionError] = useState('')
  const [assigning, setAssigning] = useState(false)
  const [removingAssignmentId, setRemovingAssignmentId] = useState<string>()
  const [removeTarget, setRemoveTarget] = useState<SessionReviewer>()
  const [removeReason, setRemoveReason] = useState('')
  const [removeReasonError, setRemoveReasonError] = useState('')
  const hasManagePermission = canManageSessionReviewers(workspace)
  const canManage = hasManagePermission && !closed
  const currentUserEmail = auth.getCurrentUserEmail()?.toLocaleLowerCase()
  const assignControlsDisabled = assigning
    || loading
    || membersLoading
    || Boolean(loadError)
    || Boolean(membersError)

  const loadReviewers = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true)
    setLoadError(undefined)
    try {
      setReviewers(await api.getSessionReviewers(workspace.id, sessionId))
    } catch (exception) {
      setLoadError(normalizedError(exception, 'Không thể tải reviewer roster.'))
    } finally {
      if (showLoading) setLoading(false)
    }
  }, [sessionId, workspace.id])

  const loadMembers = useCallback(async () => {
    if (!canManage) return
    setMembersLoading(true)
    setMembersError(undefined)
    try {
      setMembers(await api.getWorkspaceMembers(workspace.id))
    } catch (exception) {
      setMembersError(normalizedError(exception, 'Không thể tải ứng viên reviewer.'))
    } finally {
      setMembersLoading(false)
    }
  }, [canManage, workspace.id])

  useEffect(() => {
    void loadReviewers()
    if (canManage) void loadMembers()
  }, [canManage, loadMembers, loadReviewers, refreshKey])

  useEffect(() => {
    if (!closed) return
    setRemoveTarget(undefined)
    setRemoveReason('')
    setRemoveReasonError('')
  }, [closed])

  const candidates = useMemo(() => {
    const assignedMembershipIds = new Set(reviewers.map(item => item.workspaceMemberId))
    return members.filter(member => member.status === 'ACTIVE'
      && ELIGIBLE_ROLES.has(member.role)
      && !assignedMembershipIds.has(member.membershipId))
  }, [members, reviewers])

  async function submitAssign(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (assigning) return
    if (!selectedMemberId) {
      setSelectionError('Hãy chọn một reviewer.')
      return
    }

    setAssigning(true)
    setSelectionError('')
    setActionError(undefined)
    try {
      const assignment = await api.assignSessionReviewer(workspace.id, sessionId, {
        workspaceMemberId: selectedMemberId,
      })
      setReviewers(current => [
        ...current.filter(item => item.assignmentId !== assignment.assignmentId),
        assignment,
      ].sort(byAssignedAt))
      setSelectedMemberId('')
      onRosterChanged?.()
    } catch (exception) {
      const error = normalizedError(exception, 'Không thể phân công reviewer.')
      setActionError(error)
      if (error.status === 409) {
        setSelectedMemberId('')
        void loadReviewers(false)
      }
    } finally {
      setAssigning(false)
    }
  }

  function beginRemove(reviewer: SessionReviewer) {
    setRemoveTarget(reviewer)
    setRemoveReason('')
    setRemoveReasonError('')
    setActionError(undefined)
  }

  function cancelRemove() {
    if (removingAssignmentId) return
    setRemoveTarget(undefined)
    setRemoveReason('')
    setRemoveReasonError('')
  }

  async function submitRemove(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!removeTarget || removingAssignmentId) return
    const reason = removeReason.trim()
    if (!reason) {
      setRemoveReasonError('Lý do gỡ reviewer là bắt buộc.')
      return
    }

    setRemovingAssignmentId(removeTarget.assignmentId)
    setRemoveReasonError('')
    setActionError(undefined)
    try {
      await api.removeSessionReviewer(
        workspace.id,
        sessionId,
        removeTarget.assignmentId,
        { reason },
      )
      setReviewers(current => current.filter(
        item => item.assignmentId !== removeTarget.assignmentId,
      ))
      setRemoveTarget(undefined)
      setRemoveReason('')
      onRosterChanged?.()
    } catch (exception) {
      const error = normalizedError(exception, 'Không thể gỡ reviewer.')
      setActionError(error)
      if (error.status === 404 || error.status === 409) {
        setRemoveTarget(undefined)
        setRemoveReason('')
        void loadReviewers(false)
      }
    } finally {
      setRemovingAssignmentId(undefined)
    }
  }

  return <section className="session-reviewers-section" aria-labelledby="reviewers-title">
    <div className="section-heading">
      <div>
        <h2 id="reviewers-title">Reviewers</h2>
        <p>Reviewer được phân công cho toàn bộ Decision Card của session.</p>
      </div>
    </div>

    {closed && <div className="notice readonly" role="status">
      Session đã đóng. Reviewer roster hiện ở chế độ chỉ đọc.
    </div>}

    {canManage && <form className="reviewer-assign-form" onSubmit={submitAssign} noValidate>
      <label htmlFor="reviewer-candidate">Reviewer
        <select
          id="reviewer-candidate"
          value={selectedMemberId}
          disabled={assignControlsDisabled}
          aria-invalid={Boolean(selectionError)}
          aria-describedby={selectionError ? 'reviewer-candidate-error' : undefined}
          onChange={event => {
            setSelectedMemberId(event.target.value)
            setSelectionError('')
          }}
        >
          <option value="">Chọn thành viên phù hợp</option>
          {candidates.map(member => <option key={member.membershipId} value={member.membershipId}>
            {member.displayName || member.email} — {member.email} — {roleLabels[member.role]}
          </option>)}
        </select>
      </label>
      <button type="submit" disabled={assignControlsDisabled}>
        {assigning ? 'Đang phân công…' : 'Phân công'}
      </button>
      {selectionError && <span id="reviewer-candidate-error" className="field-error">
        {selectionError}
      </span>}
      {!loading && !loadError && !membersLoading && !membersError && candidates.length === 0 && <span className="form-hint">
        Không còn thành viên phù hợp để phân công.
      </span>}
    </form>}

    {canManage && membersLoading && <p className="loading" role="status">
      Đang tải ứng viên reviewer…
    </p>}
    {canManage && membersError && <div className="notice error" role="alert">
      <span>{membersError.message}</span>
      <button type="button" className="secondary" onClick={() => void loadMembers()}>Thử lại</button>
    </div>}
    {actionError && <div className="notice error" role="alert">{actionError.message}</div>}

    {loading ? <p className="loading" role="status">Đang tải reviewer roster…</p>
      : loadError ? <div className="empty" role="alert">
        <strong>Không thể tải reviewer roster</strong>
        <span>{loadError.message}</span>
        <button type="button" onClick={() => void loadReviewers()}>Thử lại</button>
      </div>
        : reviewers.length === 0 ? <div className="empty">
          <strong>Chưa có reviewer nào được phân công.</strong>
          <span>OWNER hoặc ADMIN có thể thêm reviewer phù hợp.</span>
        </div>
          : <div className="reviewer-list">
            {reviewers.map(reviewer => <article className="reviewer-card" key={reviewer.assignmentId}>
              <div className="reviewer-identity">
                <strong>{reviewer.displayName || reviewer.email}</strong>
                <span>{reviewer.email}</span>
              </div>
              <dl className="reviewer-metadata">
                <div><dt>Vai trò</dt><dd><span className="role-tag">{roleLabels[reviewer.workspaceRole]}</span></dd></div>
                <div><dt>Phân công lúc</dt><dd><time dateTime={reviewer.assignedAt}>
                  {new Date(reviewer.assignedAt).toLocaleString('vi-VN')}
                </time></dd></div>
                <div><dt>Người phân công</dt><dd>{reviewer.assignedByDisplayName || 'Không rõ'}</dd></div>
                <div><dt>Trạng thái</dt><dd><span className="membership-status active">Đã phân công</span></dd></div>
              </dl>
              {canManage && <button
                type="button"
                className="danger"
                disabled={Boolean(removingAssignmentId)}
                onClick={() => beginRemove(reviewer)}
              >Gỡ reviewer</button>}
            </article>)}
          </div>}

    {removeTarget && <form
      className="reviewer-remove-form"
      role="dialog"
      aria-modal="true"
      aria-labelledby="remove-reviewer-title"
      onSubmit={submitRemove}
      noValidate
    >
      <h3 id="remove-reviewer-title">Gỡ {removeTarget.displayName || removeTarget.email} khỏi reviewer roster?</h3>
      <p>Assignment sẽ được soft-remove và có thể được phân công lại sau.</p>
      {currentUserEmail === removeTarget.email.toLocaleLowerCase() && <p className="self-remove-warning">
        Bạn đang gỡ chính mình khỏi reviewer roster. Quyền quản trị workspace của bạn không thay đổi.
      </p>}
      <label htmlFor="reviewer-remove-reason">Lý do
        <textarea
          id="reviewer-remove-reason"
          rows={3}
          maxLength={REMOVE_REASON_MAX_LENGTH}
          value={removeReason}
          disabled={Boolean(removingAssignmentId)}
          aria-invalid={Boolean(removeReasonError)}
          aria-describedby={removeReasonError ? 'reviewer-remove-reason-error' : 'reviewer-remove-reason-count'}
          onChange={event => {
            setRemoveReason(event.target.value)
            setRemoveReasonError('')
          }}
        />
      </label>
      {removeReasonError && <span id="reviewer-remove-reason-error" className="field-error">
        {removeReasonError}
      </span>}
      <span id="reviewer-remove-reason-count" className="character-count">
        {removeReason.length}/{REMOVE_REASON_MAX_LENGTH}
      </span>
      <div className="form-actions">
        <button type="button" className="secondary" disabled={Boolean(removingAssignmentId)} onClick={cancelRemove}>
          Hủy
        </button>
        <button type="submit" className="danger" disabled={Boolean(removingAssignmentId)}>
          {removingAssignmentId ? 'Đang gỡ…' : 'Xác nhận gỡ'}
        </button>
      </div>
    </form>}
  </section>
}
