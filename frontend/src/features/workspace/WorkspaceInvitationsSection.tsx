import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../../lib/api'
import type {
  WorkspaceDetail,
  WorkspaceInvitation,
  WorkspaceInvitationRole,
  WorkspaceInvitationStatus,
} from '../../types'

const standardRoles = ['REVIEWER', 'MEMBER', 'AUDITOR'] as const
const ownerRoles = ['ADMIN', ...standardRoles] as const

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

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function isPast(expiresAt: string) {
  return new Date(expiresAt).getTime() <= Date.now()
}

function effectiveStatus(invitation: WorkspaceInvitation): WorkspaceInvitationStatus {
  return invitation.status === 'PENDING' && isPast(invitation.expiresAt)
    ? 'EXPIRED'
    : invitation.status
}

export default function WorkspaceInvitationsSection({ workspace }: { workspace: WorkspaceDetail }) {
  const [invitations, setInvitations] = useState<WorkspaceInvitation[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ApiError>()
  const [actionError, setActionError] = useState<ApiError>()
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')
  const [role, setRole] = useState<WorkspaceInvitationRole>('MEMBER')
  const [creating, setCreating] = useState(false)
  const [busyInvitationId, setBusyInvitationId] = useState<string>()
  const canAdminister = workspace.currentUserRole === 'OWNER'
    || workspace.currentUserRole === 'ADMIN'
  const assignableRoles = workspace.currentUserRole === 'OWNER' ? ownerRoles : standardRoles

  const loadInvitations = useCallback(async () => {
    if (!canAdminister) {
      setLoading(false)
      return
    }
    setLoading(true)
    setLoadError(undefined)
    try {
      setInvitations(await api.getWorkspaceInvitations(workspace.id))
    } catch (exception) {
      setLoadError(normalizedError(exception, 'Không thể tải danh sách lời mời.'))
    } finally {
      setLoading(false)
    }
  }, [canAdminister, workspace.id])

  useEffect(() => {
    void loadInvitations()
  }, [loadInvitations])

  if (!canAdminister) return null

  async function submitInvitation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (creating) return

    const normalizedEmail = email.trim()
    if (!normalizedEmail) {
      setEmailError('Email không được để trống.')
      return
    }
    if (!isValidEmail(normalizedEmail)) {
      setEmailError('Email không đúng định dạng.')
      return
    }

    setCreating(true)
    setEmailError('')
    setActionError(undefined)
    try {
      const created = await api.createWorkspaceInvitation(workspace.id, {
        email: normalizedEmail,
        role,
      })
      setInvitations(current => [created, ...current])
      setEmail('')
      setRole('MEMBER')
    } catch (exception) {
      const error = normalizedError(exception, 'Không thể tạo lời mời.')
      setActionError(error)
      if (error.validationErrors.email) setEmailError(error.validationErrors.email)
    } finally {
      setCreating(false)
    }
  }

  function canRevoke(invitation: WorkspaceInvitation) {
    if (effectiveStatus(invitation) !== 'PENDING') return false
    return workspace.currentUserRole === 'OWNER' || invitation.role !== 'ADMIN'
  }

  async function revoke(invitation: WorkspaceInvitation) {
    if (busyInvitationId || !window.confirm(`Thu hồi lời mời gửi tới ${invitation.email}?`)) {
      return
    }

    setBusyInvitationId(invitation.id)
    setActionError(undefined)
    try {
      const updated = await api.revokeWorkspaceInvitation(workspace.id, invitation.id)
      setInvitations(current => current.map(item => item.id === updated.id ? updated : item))
    } catch (exception) {
      const error = normalizedError(exception, 'Không thể thu hồi lời mời.')
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

  return <section id="invitations" className="invitations-section" aria-labelledby="invitations-title">
    <div className="section-heading">
      <div>
        <h2 id="invitations-title">Invitations</h2>
        <p>Mời thành viên bằng email và theo dõi toàn bộ lịch sử lời mời.</p>
      </div>
    </div>

    <form className="invitation-form" onSubmit={submitInvitation} noValidate>
      <label htmlFor="invitation-email">Email
        <input
          id="invitation-email"
          type="email"
          value={email}
          maxLength={255}
          disabled={creating}
          aria-invalid={Boolean(emailError)}
          aria-describedby={emailError ? 'invitation-email-error' : undefined}
          onChange={event => {
            setEmail(event.target.value)
            setEmailError('')
          }}
        />
      </label>
      {emailError && <span id="invitation-email-error" className="field-error">{emailError}</span>}
      <label htmlFor="invitation-role">Vai trò
        <select
          id="invitation-role"
          value={role}
          disabled={creating}
          onChange={event => setRole(event.target.value as WorkspaceInvitationRole)}
        >
          {assignableRoles.map(item => <option key={item} value={item}>{roleLabels[item]}</option>)}
        </select>
      </label>
      <div className="form-actions">
        <button type="submit" disabled={creating}>
          {creating ? 'Đang gửi…' : 'Gửi lời mời'}
        </button>
      </div>
    </form>

    {actionError && <div className="notice error" role="alert">{actionError.message}</div>}

    {loading ? <p className="loading" role="status">Đang tải danh sách lời mời…</p>
      : loadError ? <div className="empty" role="alert">
        <strong>Không thể tải danh sách lời mời</strong>
        <span>{loadError.message}</span>
        <button type="button" onClick={() => void loadInvitations()}>Thử lại</button>
      </div>
        : invitations.length === 0 ? <div className="empty">
          <strong>Chưa có lời mời</strong>
          <span>Lời mời được tạo sẽ xuất hiện tại đây.</span>
        </div>
          : <div className="invitation-table-wrap">
            <table className="invitation-table">
              <thead><tr><th>Người nhận</th><th>Vai trò</th><th>Trạng thái</th><th>Người mời</th><th>Thời gian</th><th>Phản hồi</th><th>Thao tác</th></tr></thead>
              <tbody>{invitations.map(invitation => {
                const status = effectiveStatus(invitation)
                const busy = busyInvitationId === invitation.id
                return <tr key={invitation.id}>
                  <td data-label="Người nhận"><strong>{invitation.email}</strong></td>
                  <td data-label="Vai trò"><span className="role-tag">{roleLabels[invitation.role]}</span></td>
                  <td data-label="Trạng thái"><span className={`invitation-status ${status.toLowerCase()}`}>{statusLabels[status]}</span></td>
                  <td data-label="Người mời">{invitation.invitedByDisplayName}</td>
                  <td data-label="Thời gian" className="invitation-dates">
                    <span>Tạo: <time dateTime={invitation.createdAt}>{new Date(invitation.createdAt).toLocaleString('vi-VN')}</time></span>
                    <span>Hạn: <time dateTime={invitation.expiresAt}>{new Date(invitation.expiresAt).toLocaleString('vi-VN')}</time></span>
                  </td>
                  <td data-label="Phản hồi">{invitation.respondedAt
                    ? <time dateTime={invitation.respondedAt}>{new Date(invitation.respondedAt).toLocaleString('vi-VN')}</time>
                    : <span className="muted-action">Chưa có</span>}</td>
                  <td data-label="Thao tác">{canRevoke(invitation)
                    ? <button type="button" className="danger" disabled={busy} onClick={() => void revoke(invitation)}>
                      {busy ? 'Đang thu hồi…' : 'Thu hồi'}
                    </button>
                    : <span className="muted-action">Không có</span>}</td>
                </tr>
              })}</tbody>
            </table>
          </div>}
  </section>
}
