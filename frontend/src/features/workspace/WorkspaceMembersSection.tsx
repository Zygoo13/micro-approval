import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError, api, auth } from '../../lib/api'
import type {
  WorkspaceDetail,
  WorkspaceMember,
  WorkspaceMemberRole,
  WorkspaceMembershipStatus,
} from '../../types'

const managedRoles = ['REVIEWER', 'MEMBER', 'AUDITOR'] as const
const ownerAssignableRoles = ['ADMIN', ...managedRoles] as const

const roleLabels: Record<WorkspaceMemberRole, string> = {
  OWNER: 'Chủ sở hữu',
  ADMIN: 'Quản trị viên',
  REVIEWER: 'Người kiểm duyệt',
  MEMBER: 'Thành viên',
  AUDITOR: 'Kiểm toán viên',
}

const statusLabels: Record<WorkspaceMembershipStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  PENDING: 'Đang chờ',
  REMOVED: 'Đã xóa',
}

function normalizedError(exception: unknown, fallback: string) {
  return exception instanceof ApiError ? exception : new ApiError(fallback, null)
}

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

export default function WorkspaceMembersSection({ workspace }: { workspace: WorkspaceDetail }) {
  const [members, setMembers] = useState<WorkspaceMember[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ApiError>()
  const [actionError, setActionError] = useState<ApiError>()
  const [showAddForm, setShowAddForm] = useState(false)
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')
  const [addRole, setAddRole] = useState<Exclude<WorkspaceMemberRole, 'OWNER'>>('MEMBER')
  const [adding, setAdding] = useState(false)
  const [busyMemberId, setBusyMemberId] = useState<string>()
  const currentUserEmail = auth.getCurrentUserEmail()?.toLocaleLowerCase()
  const canAdminister = workspace.currentUserRole === 'OWNER' || workspace.currentUserRole === 'ADMIN'
  const assignableRoles = workspace.currentUserRole === 'OWNER'
    ? ownerAssignableRoles
    : managedRoles

  const loadMembers = useCallback(async () => {
    setLoading(true)
    setLoadError(undefined)
    try {
      setMembers(await api.getWorkspaceMembers(workspace.id))
    } catch (exception) {
      setLoadError(normalizedError(exception, 'Không thể tải danh sách thành viên.'))
    } finally {
      setLoading(false)
    }
  }, [workspace.id])

  useEffect(() => {
    void loadMembers()
  }, [loadMembers])

  function canManage(member: WorkspaceMember) {
    if (!canAdminister || member.role === 'OWNER') return false
    if (currentUserEmail && member.email.toLocaleLowerCase() === currentUserEmail) return false
    if (workspace.currentUserRole === 'ADMIN') {
      return managedRoles.includes(member.role as typeof managedRoles[number])
    }
    return true
  }

  async function submitAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (adding) return
    const trimmedEmail = email.trim()
    if (!trimmedEmail) {
      setEmailError('Email không được để trống.')
      return
    }
    if (!isValidEmail(trimmedEmail)) {
      setEmailError('Email không đúng định dạng.')
      return
    }

    setAdding(true)
    setEmailError('')
    setActionError(undefined)
    try {
      const member = await api.addWorkspaceMember(workspace.id, {
        email: trimmedEmail,
        role: addRole,
      })
      setMembers(current => {
        if (!current.some(item => item.membershipId === member.membershipId)) {
          return [...current, member]
        }
        return current.map(item => item.membershipId === member.membershipId ? member : item)
      })
      setEmail('')
      setAddRole('MEMBER')
      setShowAddForm(false)
    } catch (exception) {
      setActionError(normalizedError(exception, 'Không thể thêm thành viên.'))
    } finally {
      setAdding(false)
    }
  }

  async function changeRole(member: WorkspaceMember, role: Exclude<WorkspaceMemberRole, 'OWNER'>) {
    if (role === member.role || busyMemberId) return
    if (role === 'ADMIN'
      && !window.confirm('Xác nhận cấp quyền quản trị cho thành viên này?')) return

    setBusyMemberId(member.membershipId)
    setActionError(undefined)
    try {
      const updated = await api.updateWorkspaceMemberRole(
        workspace.id,
        member.membershipId,
        { role },
      )
      setMembers(current => current.map(item =>
        item.membershipId === updated.membershipId ? updated : item,
      ))
    } catch (exception) {
      setActionError(normalizedError(exception, 'Không thể đổi vai trò thành viên.'))
    } finally {
      setBusyMemberId(undefined)
    }
  }

  async function removeMember(member: WorkspaceMember) {
    if (busyMemberId
      || !window.confirm(`Xóa ${member.displayName || member.email} khỏi workspace?`)) return

    setBusyMemberId(member.membershipId)
    setActionError(undefined)
    try {
      await api.removeWorkspaceMember(workspace.id, member.membershipId)
      setMembers(current => current.filter(item => item.membershipId !== member.membershipId))
    } catch (exception) {
      setActionError(normalizedError(exception, 'Không thể xóa thành viên.'))
    } finally {
      setBusyMemberId(undefined)
    }
  }

  return <section id="members" className="members-section" aria-labelledby="members-title">
    <div className="section-heading">
      <div>
        <h2 id="members-title">Members</h2>
        <p>Thành viên ACTIVE và PENDING trong workspace.</p>
      </div>
      {canAdminister && <button type="button" onClick={() => {
        setShowAddForm(value => !value)
        setActionError(undefined)
      }}>{showAddForm ? 'Đóng form' : 'Thêm thành viên'}</button>}
    </div>

    {showAddForm && <form className="member-add-form" onSubmit={submitAdd} noValidate>
      <label htmlFor="member-email">Email
        <input
          id="member-email"
          type="email"
          value={email}
          maxLength={255}
          aria-invalid={Boolean(emailError)}
          aria-describedby={emailError ? 'member-email-error' : undefined}
          onChange={event => setEmail(event.target.value)}
          disabled={adding}
        />
      </label>
      {emailError && <span id="member-email-error" className="field-error">{emailError}</span>}
      <label htmlFor="member-role">Vai trò
        <select
          id="member-role"
          value={addRole}
          onChange={event => setAddRole(
            event.target.value as Exclude<WorkspaceMemberRole, 'OWNER'>,
          )}
          disabled={adding}
        >
          {assignableRoles.map(role => <option key={role} value={role}>{roleLabels[role]}</option>)}
        </select>
      </label>
      <div className="form-actions">
        <button type="button" className="secondary" disabled={adding} onClick={() => setShowAddForm(false)}>Hủy</button>
        <button type="submit" disabled={adding}>{adding ? 'Đang thêm…' : 'Thêm thành viên'}</button>
      </div>
    </form>}

    {actionError && <div className="notice error" role="alert">{actionError.message}</div>}

    {loading ? <p className="loading" role="status">Đang tải danh sách thành viên…</p>
      : loadError ? <div className="empty" role="alert">
        <strong>Không thể tải danh sách thành viên</strong>
        <span>{loadError.message}</span>
        <button type="button" onClick={() => void loadMembers()}>Thử lại</button>
      </div>
        : members.length === 0 ? <div className="empty">
          <strong>Chưa có thành viên</strong>
          <span>Danh sách workspace hiện đang trống.</span>
        </div>
          : <div className="member-table-wrap">
            <table className="member-table">
              <thead><tr><th>Thành viên</th><th>Vai trò</th><th>Trạng thái</th><th>Tham gia</th><th>Thao tác</th></tr></thead>
              <tbody>{members.map(member => {
                const manageable = canManage(member)
                const busy = busyMemberId === member.membershipId
                return <tr key={member.membershipId}>
                  <td data-label="Thành viên"><strong>{member.displayName || member.email}</strong><span>{member.email}</span></td>
                  <td data-label="Vai trò">{manageable ? <select
                    aria-label={`Vai trò của ${member.displayName || member.email}`}
                    value={member.role}
                    disabled={busy}
                    onChange={event => void changeRole(
                      member,
                      event.target.value as Exclude<WorkspaceMemberRole, 'OWNER'>,
                    )}
                  >{assignableRoles.map(role => <option key={role} value={role}>{roleLabels[role]}</option>)}</select>
                    : <span className="role-tag">{roleLabels[member.role]}</span>}</td>
                  <td data-label="Trạng thái"><span className={`membership-status ${member.status.toLocaleLowerCase()}`}>{statusLabels[member.status]}</span></td>
                  <td data-label="Tham gia"><time dateTime={member.joinedAt}>{new Date(member.joinedAt).toLocaleDateString('vi-VN')}</time></td>
                  <td data-label="Thao tác">{manageable
                    ? <button type="button" className="danger" disabled={busy} onClick={() => void removeMember(member)}>{busy ? 'Đang xử lý…' : 'Xóa'}</button>
                    : <span className="muted-action">Chỉ xem</span>}</td>
                </tr>
              })}</tbody>
            </table>
          </div>}
  </section>
}
