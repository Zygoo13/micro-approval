import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { aiLabel, modeLabel, statusLabel } from '../lib/labels'
import type { PersonalSession } from '../types'

export default function SessionsPage() {
  const [sessions, setSessions] = useState<PersonalSession[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setSessions(await api.listSessions()) } catch (exception) { setError(exception instanceof Error ? exception.message : 'Không thể tải lịch sử') } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])
  const summary = useMemo(() => ({ total: sessions.length, approved: sessions.filter(s => s.status === 'APPROVED').length, pending: sessions.filter(s => s.status === 'PENDING').length }), [sessions])
  return <section className="page-stack">
    <div className="page-title"><div><p className="eyebrow">PERSONAL WORKSPACE</p><h1>Phiên kiểm duyệt cá nhân</h1><p>Lịch sử review chỉ bạn mới có thể xem và xử lý.</p></div><Link className="button" to="/sessions/new">+ Phân tích code</Link></div>
    <div className="metrics"><div><strong>{summary.total}</strong><span>Tổng phiên</span></div><div><strong>{summary.pending}</strong><span>Đang xử lý</span></div><div><strong>{summary.approved}</strong><span>Đã duyệt</span></div></div>
    <div className="section-heading"><h2>Lịch sử</h2><button className="secondary" onClick={() => void load()} disabled={loading}>Làm mới</button></div>
    {error && <div className="notice error"><span>{error}</span><button className="secondary" onClick={() => void load()}>Thử lại</button></div>}
    {loading ? <p className="loading">Đang tải các phiên kiểm duyệt…</p> : <div className="session-list">{sessions.map(session => <Link className="session-row" key={session.id} to={`/sessions/${session.id}`}><div><strong>{session.title}</strong><span>{modeLabel(session.mode)} · {new Date(session.createdAt).toLocaleString('vi-VN')}</span></div><div className="row-status"><span className={`ai-status ${session.aiAnalysisStatus.toLowerCase()}`}>{aiLabel(session.aiAnalysisStatus)}</span><span className={`status ${session.status.toLowerCase()}`}>{statusLabel(session.status)}</span></div></Link>)}
    {!error && sessions.length === 0 && <div className="empty"><strong>Chưa có session nào</strong><span>Tạo phiên đầu tiên để kiểm tra code hoặc Git diff.</span><Link className="button" to="/sessions/new">Tạo phiên</Link></div>}</div>}
  </section>
}
