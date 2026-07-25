import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import type { PersonalSession } from '../types'

export default function SessionsPage() {
  const [sessions, setSessions] = useState<PersonalSession[]>([])
  const [error, setError] = useState('')
  useEffect(() => { api.listSessions().then(setSessions).catch(e => setError(e.message)) }, [])
  return <section><div className="page-title"><div><h1>Phiên kiểm duyệt cá nhân</h1><p>Lịch sử các phiên code review của bạn.</p></div><Link className="button" to="/sessions/new">Tạo phiên</Link></div>
    {error && <p className="error">{error}</p>}
    <div className="session-list">{sessions.map(session => <Link className="session-row" key={session.id} to={`/sessions/${session.id}`}><div><strong>{session.title}</strong><span>{session.mode} · {new Date(session.createdAt).toLocaleString('vi-VN')}</span></div><span className={`status ${session.status.toLowerCase()}`}>{session.status}</span></Link>)}
      {!error && sessions.length === 0 && <p className="empty">Chưa có session nào. Hãy tạo phiên đầu tiên.</p>}</div>
  </section>
}
