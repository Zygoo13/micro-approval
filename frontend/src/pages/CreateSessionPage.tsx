import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import type { AnalysisMode } from '../types'

export default function CreateSessionPage() {
  const navigate = useNavigate(); const [mode, setMode] = useState<AnalysisMode>('RAW_SNIPPET'); const [error, setError] = useState(''); const [loading, setLoading] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); setLoading(true); setError('')
    try { const session = await api.createSession({ title: String(form.get('title')), mode, rawContent: String(form.get('rawContent')), promptContent: String(form.get('promptContent') || '') }); navigate(`/sessions/${session.id}`) }
    catch (e) { setError(e instanceof Error ? e.message : 'Đã có lỗi') } finally { setLoading(false) }
  }
  return <section className="form-page"><h1>Tạo phiên phân tích</h1><form onSubmit={submit}><label>Tiêu đề<input name="title" required maxLength={255} placeholder="Ví dụ: Kiểm tra API thanh toán" /></label><label>Mode<select value={mode} onChange={e => setMode(e.target.value as AnalysisMode)}><option value="RAW_SNIPPET">Raw Snippet</option><option value="INTENT_MATCHING">Intent Matching</option><option value="GIT_DIFF">Git Diff</option></select></label>{mode === 'INTENT_MATCHING' && <label>Prompt gốc<textarea name="promptContent" rows={4} required /></label>}<label>Code / Git Diff<textarea name="rawContent" rows={14} required placeholder="Dán code hoặc git diff tại đây" /></label>{error && <p className="error">{error}</p>}<button disabled={loading}>{loading ? 'Đang phân tích…' : 'Phân tích'}</button></form></section>
}
