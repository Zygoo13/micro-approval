import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import type {
  MyWorkspaceInvitation,
  WorkspaceInvitationRole,
  WorkspaceInvitationStatus,
} from '../types'

const roleLabels: Record<WorkspaceInvitationRole, string> = {
  ADMIN: 'Quản trị viên',
  REVIEWER: 'Người kiểm duyệt',
  MEMBER: 'Thành viên',
  AUDITOR: 'Kiểm toán viên',
}

const statusLabels: Record<WorkspaceInvitationStatus, string> = {
  PENDING: 'Đang chờ',
  ACCEPTED: 'Đã chấp nhận',
  REJECTED: 'Đã từ chối',
  REVOKED: 'Đã thu hồi',
  EXPIRED: 'Đã hết hạn',
}

function normalizedError(exception: unknown, fallback: string) {
  return exception instanceof ApiError ? exception : new ApiError(fallback, null)
}

function effectiveStatus(invitation: MyWorkspaceInvitation): WorkspaceInvitationStatus {
  return invitation.status === 'PENDING'
    && new Date(invitation.expiresAt).getTime() <= Date.now()
    ? 'EXPIRED'
    : invitation.status
}

export default function MyInvitationsPage() {
  const [invitations, setInvitations] = useState<MyWorkspaceInvitation[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ApiError>()
  const [actionError, setActionError] = useState<ApiError>()
  const [busyInvitationId, setBusyInvitationId] = useState<string>()

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(undefined)
    try {
      setInvitations(await api.getMyWorkspaceInvitations())
    } catch (exception) {
      setLoadError(normalizedError(exception, 'Không thể tải lời mời của bạn.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function respond(invitation: MyWorkspaceInvitation, action: 'accept' | 'reject') {
    if (busyInvitationId) return
    const verb = action === 'accept' ? 'chấp nhận' : 'từ chối'
    if (!window.confirm(`Bạn có chắc muốn ${verb} lời mời vào ${invitation.workspaceName}?`)) {
      return
    }

    setBusyInvitationId(invitation.id)
    setActionError(undefined)
    try {
      const updated = action === 'accept'
        ? await api.acceptWorkspaceInvitation(invitation.id)
        : await api.rejectWorkspaceInvitation(invitation.id)
      setInvitations(current => current.map(item => item.id === invitation.id
        ? { ...item, status: updated.status }
        : item))
    } catch (exception) {
      const error = normalizedError(exception, `Không thể ${verb} lời mời.`)
      if (error.status === 410) {
        setInvitations(current => current.map(item => item.id === invitation.id
          ? { ...item, status: 'EXPIRED' }
          : item))
      }
      setActionError(error)
    } finally {
      setBusyInvitationId(undefined)
    }
  }

  return <section className="page-stack">
    <div className="page-title">
      <div>
        <p className="eyebrow">TEAM WORKSPACE</p>
        <h1>Lời mời của tôi</h1>
        <p>Chấp nhận hoặc từ chối lời mời được gửi tới email đăng nhập của bạn.</p>
      </div>
    </div>

    {actionError && <div className="notice error" role="alert">{actionError.message}</div>}

    {loading ? <p className="loading" role="status">Đang tải lời mời của bạn…</p>
      : loadError ? <div className="empty" role="alert">
        <strong>Không thể tải lời mời</strong>
        <span>{loadError.message}</span>
        {loadError.status === 401
          ? <Link className="button" to="/login">Đăng nhập lại</Link>
          : <button type="button" onClick={() => void load()}>Thử lại</button>}
      </div>
        : invitations.length === 0 ? <div className="empty">
          <strong>Bạn chưa có lời mời nào</strong>
          <span>Các lời mời workspace gửi tới bạn sẽ xuất hiện tại đây.</span>
        </div>
          : <div className="my-invitation-list">{invitations.map(invitation => {
            const status = effectiveStatus(invitation)
            const actionable = status === 'PENDING'
            const busy = busyInvitationId === invitation.id
            return <article className="my-invitation-card" key={invitation.id}>
              <div className="workspace-card-head">
                <div>
                  <span className="invitation-caption">Workspace</span>
                  <h2>{invitation.workspaceName}</h2>
                </div>
                <span className={`invitation-status ${status.toLowerCase()}`}>{statusLabels[status]}</span>
              </div>
              <dl className="invitation-details">
                <div><dt>Vai trò</dt><dd>{roleLabels[invitation.role]}</dd></div>
                <div><dt>Người mời</dt><dd>{invitation.invitedByDisplayName}</dd></div>
                <div><dt>Ngày gửi</dt><dd><time dateTime={invitation.createdAt}>{new Date(invitation.createdAt).toLocaleString('vi-VN')}</time></dd></div>
                <div><dt>Hạn chấp nhận</dt><dd><time dateTime={invitation.expiresAt}>{new Date(invitation.expiresAt).toLocaleString('vi-VN')}</time></dd></div>
              </dl>
              <div className="card-actions">
                {status === 'ACCEPTED' && <Link className="button secondary" to={`/workspaces/${invitation.workspaceId}`}>Đi tới workspace</Link>}
                {actionable && <>
                  <button type="button" className="secondary" disabled={busy} onClick={() => void respond(invitation, 'reject')}>
                    {busy ? 'Đang xử lý…' : 'Từ chối'}
                  </button>
                  <button type="button" disabled={busy} onClick={() => void respond(invitation, 'accept')}>
                    {busy ? 'Đang xử lý…' : 'Chấp nhận'}
                  </button>
                </>}
              </div>
            </article>
          })}</div>}
  </section>
}
