import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import type { WorkspaceSummary } from '../types'

const roleLabels: Record<WorkspaceSummary['currentUserRole'], string> = {
  OWNER: 'Chủ sở hữu',
  ADMIN: 'Quản trị viên',
  REVIEWER: 'Người kiểm duyệt',
  MEMBER: 'Thành viên',
  AUDITOR: 'Kiểm toán viên',
}

export default function WorkspaceListPage() {
  const [workspaces, setWorkspaces] = useState<WorkspaceSummary[]>([])
  const [error, setError] = useState<ApiError>()
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      setWorkspaces(await api.getWorkspaces())
    } catch (exception) {
      setError(
        exception instanceof ApiError
          ? exception
          : new ApiError('Không thể tải danh sách workspace.', null),
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return <section className="page-stack">
    <div className="page-title">
      <div>
        <p className="eyebrow">SHARED WORKSPACE</p>
        <h1>Workspaces của bạn</h1>
        <p>Các không gian làm việc mà membership của bạn đang hoạt động.</p>
      </div>
      <Link className="button" to="/workspaces/new">+ Tạo workspace</Link>
    </div>

    {loading && <p className="loading" role="status">Đang tải danh sách workspace…</p>}

    {!loading && error && <div className="notice error" role="alert">
      <span>{error.message}</span>
      {error.status === 401
        ? <Link className="button" to="/login">Đăng nhập lại</Link>
        : <button className="secondary" onClick={() => void load()}>Thử lại</button>}
    </div>}

    {!loading && !error && workspaces.length === 0 && <div className="empty">
      <strong>Bạn chưa có workspace nào</strong>
      <span>Tạo workspace đầu tiên để bắt đầu không gian làm việc nhóm.</span>
      <Link className="button" to="/workspaces/new">Tạo workspace</Link>
    </div>}

    {!loading && !error && workspaces.length > 0 && <div className="workspace-grid">
      {workspaces.map(workspace => <Link
        className="workspace-card"
        key={workspace.id}
        to={`/workspaces/${workspace.id}`}
      >
        <div className="workspace-card-head">
          <h2>{workspace.name}</h2>
          <span className="role-tag">{roleLabels[workspace.currentUserRole]}</span>
        </div>
        {workspace.description && <p>{workspace.description}</p>}
        <span className="workspace-time">
          Tạo lúc {new Date(workspace.createdAt).toLocaleString('vi-VN')}
        </span>
      </Link>)}
    </div>}
  </section>
}
