import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import type { DecisionStatus, PersonalSession } from '../types'
import { aiLabel, modeLabel, statusLabel } from '../lib/labels'

export default function SessionDetailPage() {
  const { sessionId } = useParams(); const navigate = useNavigate()
  const [session, setSession] = useState<PersonalSession>(); const [error, setError] = useState(''); const [notes, setNotes] = useState<Record<string, string>>({}); const [voting, setVoting] = useState<string>()
  const load = useCallback(async () => { if (!sessionId) return; setError(''); try { setSession(await api.getSession(sessionId)) } catch (exception) { setError(exception instanceof Error ? exception.message : 'Không thể tải session') } }, [sessionId])
  useEffect(() => { void load() }, [load])
  async function vote(decisionId: string, decision: DecisionStatus) { setVoting(decisionId); setError(''); try { await api.vote(decisionId, decision, notes[decisionId]?.trim() || undefined); await load() } catch (exception) { setError(exception instanceof Error ? exception.message : 'Không thể lưu quyết định') } finally { setVoting(undefined) } }
  async function remove() { if (sessionId && confirm('Xóa session này? Thao tác không thể hoàn tác.')) { try { await api.deleteSession(sessionId); navigate('/sessions') } catch (exception) { setError(exception instanceof Error ? exception.message : 'Không thể xóa session') } } }
  if (!session && !error) return <p className="loading">Đang tải session…</p>
  if (!session) return <section className="page-stack"><div className="notice error">{error}</div><Link className="button" to="/sessions">Quay lại lịch sử</Link></section>
  const pending = session.decisions.filter(card => card.humanDecision === 'PENDING').length
  return <section className="page-stack"><div className="detail-head"><div><Link className="back-link" to="/sessions">← Lịch sử</Link><p className="eyebrow">{modeLabel(session.mode)}</p><h1>{session.title}</h1><p>Tạo lúc {new Date(session.createdAt).toLocaleString('vi-VN')}</p></div><div className="head-actions"><span className={`status large ${session.status.toLowerCase()}`}>{statusLabel(session.status)}</span><button className="secondary danger-text" onClick={() => void remove()}>Xóa session</button></div></div>
    <div className={`ai-banner ${session.aiAnalysisStatus.toLowerCase()}`}><div><strong>{aiLabel(session.aiAnalysisStatus)}</strong><span>{session.aiAnalysisStatus === 'SUCCEEDED' ? `Đã dùng ${session.aiTokenUsed} token AI.` : session.aiAnalysisError ?? 'Không có AI card nào được tạo cho phiên này.'}</span></div></div>
    {error && <div className="notice error">{error}</div>}
    <details className="source-panel"><summary>Xem nội dung đã gửi để phân tích</summary><pre className="code">{session.rawContent}</pre>{session.promptContent && <><h3>Prompt gốc</h3><pre className="code">{session.promptContent}</pre></>}</details>
    <div className="section-heading"><div><h2>Decision Cards</h2><p>{pending ? `Còn ${pending} thẻ cần xử lý. Mỗi quyết định chỉ được thực hiện một lần.` : 'Tất cả thẻ đã được xử lý.'}</p></div></div>
    {session.decisions.length === 0 && <div className="empty"><strong>Không phát hiện rủi ro</strong><span>Phiên này không có Decision Card cần xử lý.</span></div>}
    <div className="card-list">{session.decisions.map(card => <article className="decision-card" key={card.id}><div className="card-meta"><span className="engine-tag">{card.engineType === 'AI_BASED' ? 'AI' : 'RULE'}</span><span>{card.riskCategory} · {card.riskLevel}</span></div><h3>{card.questionText}</h3><pre className="snippet">{card.codeSnippet}</pre>{card.humanDecision === 'PENDING' ? <div className="vote-area"><label>Ghi chú của bạn <span>(không bắt buộc)</span><textarea rows={3} value={notes[card.id] ?? ''} onChange={event => setNotes(current => ({ ...current, [card.id]: event.target.value }))} placeholder="Ví dụ: Đã kiểm tra với PM…" /></label><div className="card-actions"><button disabled={voting === card.id} onClick={() => void vote(card.id, 'APPROVED')}>{voting === card.id ? 'Đang lưu…' : 'Đồng ý'}</button><button className="danger" disabled={voting === card.id} onClick={() => void vote(card.id, 'REJECTED')}>Từ chối</button></div></div> : <div className="decision-result"><strong className={`status large ${card.humanDecision.toLowerCase()}`}>{statusLabel(card.humanDecision)}</strong>{card.reviewerNote && <p>Ghi chú: {card.reviewerNote}</p>}{card.decidedAt && <small>Đã xử lý {new Date(card.decidedAt).toLocaleString('vi-VN')}</small>}</div>}</article>)}</div>
  </section>
}
