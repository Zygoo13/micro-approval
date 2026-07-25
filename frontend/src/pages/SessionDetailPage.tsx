import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import type { DecisionStatus, PersonalSession } from '../types'

export default function SessionDetailPage() {
  const { sessionId } = useParams(); const navigate = useNavigate(); const [session, setSession] = useState<PersonalSession>(); const [error, setError] = useState('')
  const load = () => { if (sessionId) api.getSession(sessionId).then(setSession).catch(e => setError(e.message)) }
  useEffect(load, [sessionId])
  async function vote(decisionId: string, decision: DecisionStatus) { try { await api.vote(decisionId, decision); load() } catch (e) { setError(e instanceof Error ? e.message : 'Đã có lỗi') } }
  async function remove() { if (sessionId && confirm('Xóa session này?')) { await api.deleteSession(sessionId); navigate('/sessions') } }
  if (error) return <p className="error">{error}</p>; if (!session) return <p>Đang tải…</p>
  return <section><div className="page-title"><div><h1>{session.title}</h1><p>{session.mode} · <span className={`status ${session.status.toLowerCase()}`}>{session.status}</span></p></div><button className="danger" onClick={remove}>Xóa</button></div><pre className="code">{session.rawContent}</pre><h2>Decision Cards ({session.decisions.length})</h2>{session.decisions.length === 0 && <p className="empty">Không phát hiện rủi ro theo các rule hiện có.</p>}<div className="card-list">{session.decisions.map(card => <article className="decision-card" key={card.id}><div className="card-meta"><span>{card.engineType}</span><span>{card.riskCategory} · {card.riskLevel}</span></div><p>{card.questionText}</p><small>{card.codeSnippet}</small>{card.humanDecision === 'PENDING' ? <div className="card-actions"><button onClick={() => vote(card.id, 'APPROVED')}>Đồng ý</button><button className="danger" onClick={() => vote(card.id, 'REJECTED')}>Từ chối</button></div> : <strong className={`status ${card.humanDecision.toLowerCase()}`}>{card.humanDecision}</strong>}</article>)}</div></section>
}
