import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import WorkspaceSessionsSection from '../features/workspace/WorkspaceSessionsSection'
import { ApiError, api } from '../lib/api'
import type { WorkspaceDetail } from '../types'

export default function SharedSessionsPage() {
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
      setError(exception instanceof ApiError
        ? exception
        : new ApiError('Không thể tải workspace.', null))
    } finally {
      setLoading(false)
    }
  }, [workspaceId])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) return <p className="loading" role="status">Đang tải workspace…</p>
  if (!workspace || error) return <UnavailableState error={error} retry={load} />

  return <section className="page-stack">
    <div>
      <Link className="back-link" to={`/workspaces/${workspace.id}`}>← {workspace.name}</Link>
      <p className="eyebrow">SHARED WORKSPACE</p>
      <h1>Sessions của {workspace.name}</h1>
      <p>Danh sách được sắp xếp mới nhất trước theo kết quả từ backend.</p>
    </div>
    <WorkspaceSessionsSection workspace={workspace} />
  </section>
}

function UnavailableState({ error, retry }: { error?: ApiError; retry: () => Promise<void> }) {
  const unavailable = error?.status === 401 || error?.status === 403 || error?.status === 404
  return <section className="page-stack">
    <div className="empty" role="alert">
      <strong>{error?.status === 404 ? 'Không tìm thấy workspace' : 'Không thể tải workspace'}</strong>
      <span>{error?.message ?? 'Workspace không khả dụng.'}</span>
      <div className="form-actions">
        <Link className="button secondary" to="/workspaces">Về danh sách</Link>
        {!unavailable && <button type="button" onClick={() => void retry()}>Thử lại</button>}
        {error?.status === 401 && <Link className="button" to="/login">Đăng nhập lại</Link>}
      </div>
    </div>
  </section>
}
