import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import type { WorkspaceDetail } from '../types'
import WorkspaceMembersSection from '../features/workspace/WorkspaceMembersSection'
import WorkspaceInvitationsSection from '../features/workspace/WorkspaceInvitationsSection'

const roleLabels: Record<WorkspaceDetail['currentUserRole'], string> = {
  OWNER: 'Chủ sở hữu',
  ADMIN: 'Quản trị viên',
  REVIEWER: 'Người kiểm duyệt',
  MEMBER: 'Thành viên',
  AUDITOR: 'Kiểm toán viên',
}

export default function WorkspaceDetailPage() {
  const { workspaceId } = useParams()
  const [workspace, setWorkspace] = useState<WorkspaceDetail>()
  const [error, setError] = useState<ApiError>()
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    if (!workspaceId) {
      setError(new ApiError('Không tìm thấy workspace.', 404))
      setLoading(false)
      return
    }

    setLoading(true)
    setError(undefined)
    try {
      setWorkspace(await api.getWorkspaceById(workspaceId))
    } catch (exception) {
      setError(
        exception instanceof ApiError
          ? exception
          : new ApiError('Không thể tải workspace.', null),
      )
    } finally {
      setLoading(false)
    }
  }, [workspaceId])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return <p className="loading" role="status">Đang tải workspace…</p>
  }

  if (!workspace || error) {
    const isNotFound = error?.status === 404
    const isUnauthorized = error?.status === 401 || error?.status === 403
    return <section className="page-stack">
      <div className="empty" role="alert">
        <strong>{isNotFound
          ? 'Không tìm thấy workspace'
          : isUnauthorized
            ? 'Không thể truy cập workspace'
            : 'Không thể tải workspace'}</strong>
        <span>{error?.message ?? 'Workspace không khả dụng.'}</span>
        <div className="form-actions">
          <Link className="button secondary" to="/workspaces">Về danh sách</Link>
          {!isNotFound && !isUnauthorized &&
            <button onClick={() => void load()}>Thử lại</button>}
          {error?.status === 401 &&
            <Link className="button" to="/login">Đăng nhập lại</Link>}
        </div>
      </div>
    </section>
  }

  const canManageInvitations = workspace.currentUserRole === 'OWNER'
    || workspace.currentUserRole === 'ADMIN'

  return <section className="page-stack">
    <div className="detail-head">
      <div>
        <Link className="back-link" to="/workspaces">← Danh sách workspace</Link>
        <p className="eyebrow">SHARED WORKSPACE</p>
        <h1>{workspace.name}</h1>
        <p>{workspace.description ?? 'Workspace chưa có mô tả.'}</p>
      </div>
      <span className="role-tag large">{roleLabels[workspace.currentUserRole]}</span>
    </div>

    <nav className="workspace-sections" aria-label="Khu vực workspace">
      <a href="#overview">Tổng quan</a>
      <a href="#members">Members</a>
      {canManageInvitations && <a href="#invitations">Invitations</a>}
      <a href="#team-sessions">Team Sessions</a>
    </nav>

    <dl id="overview" className="workspace-metadata">
      <div>
        <dt>ID chủ sở hữu</dt>
        <dd>{workspace.ownerId}</dd>
      </div>
      <div>
        <dt>Ngày tạo</dt>
        <dd>{new Date(workspace.createdAt).toLocaleString('vi-VN')}</dd>
      </div>
      <div>
        <dt>Cập nhật lần cuối</dt>
        <dd>{new Date(workspace.updatedAt).toLocaleString('vi-VN')}</dd>
      </div>
    </dl>

    <WorkspaceMembersSection workspace={workspace} />

    {canManageInvitations && <WorkspaceInvitationsSection workspace={workspace} />}

    <div className="placeholder-grid single">
      <section id="team-sessions" className="placeholder-card">
        <h2>Workspace Sessions</h2>
        <p>Phiên kiểm duyệt nhóm sẽ được bổ sung trong một vertical slice riêng.</p>
      </section>
    </div>
  </section>
}
