import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import type { AnalysisMode } from '../types'

const modeHelp: Record<AnalysisMode, { title: string; hint: string; placeholder: string }> = {
  RAW_SNIPPET: { title: 'Raw Snippet', hint: 'Kiểm tra nhanh một đoạn code độc lập.', placeholder: 'Dán đoạn code cần kiểm tra tại đây…' },
  INTENT_MATCHING: { title: 'Intent Matching', hint: 'Đối chiếu code AI tạo ra với prompt/yêu cầu gốc.', placeholder: 'Dán code AI đã sinh tại đây…' },
  GIT_DIFF: { title: 'Git Diff', hint: 'Phân tích thay đổi trong một pull request hoặc commit.', placeholder: 'Dán git diff tại đây…' },
}

export default function CreateSessionPage() {
  const navigate = useNavigate()
  const [mode, setMode] = useState<AnalysisMode>('RAW_SNIPPET')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setLoading(true); setError('')
    try {
      const session = await api.createSession({ title: String(form.get('title')), mode, rawContent: String(form.get('rawContent')), promptContent: String(form.get('promptContent') || '') })
      navigate(`/sessions/${session.id}`, { replace: true })
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Không thể tạo session') } finally { setLoading(false) }
  }

  return <section className="form-page page-stack"><div><p className="eyebrow">NEW ANALYSIS</p><h1>Tạo phiên phân tích</h1><p>Rule Engine luôn chạy trước. Nếu AI đã được server bật, phần code chưa khớp rule sẽ được gửi tiếp để tạo câu hỏi review.</p></div>
    <form onSubmit={submit} className="analysis-form"><label>Tiêu đề phiên<input name="title" required maxLength={255} autoFocus placeholder="Ví dụ: Kiểm tra thay đổi thuế VIP" /></label>
      <fieldset><legend>Chế độ phân tích</legend><div className="mode-grid">{(Object.keys(modeHelp) as AnalysisMode[]).map(item => <label className={`mode-option ${mode === item ? 'selected' : ''}`} key={item}><input type="radio" name="mode" value={item} checked={mode === item} onChange={() => setMode(item)} /><strong>{modeHelp[item].title}</strong><span>{modeHelp[item].hint}</span></label>)}</div></fieldset>
      {mode === 'INTENT_MATCHING' && <label>Prompt hoặc yêu cầu gốc<textarea name="promptContent" rows={5} required placeholder="Mô tả yêu cầu bạn đã đưa cho AI…" /></label>}
      <label>{mode === 'GIT_DIFF' ? 'Git diff' : 'Code cần phân tích'}<textarea name="rawContent" rows={15} required placeholder={modeHelp[mode].placeholder} spellCheck={false} /></label>
      {error && <p className="error">{error}</p>}<div className="form-actions"><button type="button" className="secondary" onClick={() => navigate('/sessions')}>Hủy</button><button disabled={loading}>{loading ? 'Đang phân tích…' : 'Phân tích code'}</button></div>
    </form>
    <aside className="info-panel"><strong>Thiết lập AI an toàn</strong><span>API key chỉ cấu hình ở backend qua biến môi trường. Ứng dụng web không lưu hoặc gửi API key của bạn.</span></aside>
  </section>
}
