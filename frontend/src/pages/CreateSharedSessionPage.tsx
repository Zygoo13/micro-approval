import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { canCreateSharedSession } from '../features/workspace/sharedSessionPermissions'
import { ApiError, api } from '../lib/api'
import type {
  CreateSharedReviewSessionRequest,
  SharedReviewSessionMode,
  WorkspaceDetail,
} from '../types'

const modes: Array<{
  value: SharedReviewSessionMode
  title: string
  hint: string
}> = [
  { value: 'RAW_SNIPPET', title: 'Raw Snippet', hint: 'Review một đoạn mã độc lập.' },
  { value: 'GIT_DIFF', title: 'Git Diff', hint: 'Review thay đổi từ commit hoặc pull request.' },
  { value: 'INTENT_MATCHING', title: 'Intent Matching', hint: 'Đối chiếu nội dung với ý định ban đầu.' },
]

type FieldErrors = Partial<Record<'title' | 'rawContent' | 'promptContent', string>>

export default function CreateSharedSessionPage() {
  const { workspaceId } = useParams()
  const navigate = useNavigate()
  const [workspace, setWorkspace] = useState<WorkspaceDetail>()
  const [workspaceError, setWorkspaceError] = useState<ApiError>()
  const [loadingWorkspace, setLoadingWorkspace] = useState(true)
  const [title, setTitle] = useState('')
  const [mode, setMode] = useState<SharedReviewSessionMode>('RAW_SNIPPET')
  const [rawContent, setRawContent] = useState('')
  const [promptContent, setPromptContent] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [apiError, setApiError] = useState<ApiError>()
  const [submitting, setSubmitting] = useState(false)

  const loadWorkspace = useCallback(async () => {
    if (!workspaceId) {
      setWorkspaceError(new ApiError('Không tìm thấy workspace.', 404))
      setLoadingWorkspace(false)
      return
    }
    setLoadingWorkspace(true)
    setWorkspaceError(undefined)
    try {
      setWorkspace(await api.getWorkspaceById(workspaceId))
    } catch (exception) {
      setWorkspaceError(exception instanceof ApiError
        ? exception
        : new ApiError('Không thể tải workspace.', null))
    } finally {
      setLoadingWorkspace(false)
    }
  }, [workspaceId])

  useEffect(() => {
    void loadWorkspace()
  }, [loadWorkspace])

  function validate(): FieldErrors {
    const errors: FieldErrors = {}
    const trimmedTitle = title.trim()
    if (!trimmedTitle) errors.title = 'Tiêu đề không được để trống.'
    else if (trimmedTitle.length > 255) errors.title = 'Tiêu đề tối đa 255 ký tự.'
    if (!rawContent.trim()) errors.rawContent = 'Nội dung cần review không được để trống.'
    else if (rawContent.length > 1_000_000) errors.rawContent = 'Nội dung tối đa 1.000.000 ký tự.'
    if (mode === 'INTENT_MATCHING') {
      if (!promptContent.trim()) errors.promptContent = 'Ý định / yêu cầu không được để trống.'
      else if (promptContent.length > 65_535) errors.promptContent = 'Ý định tối đa 65.535 ký tự.'
    }
    return errors
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting || !workspaceId || !workspace || !canCreateSharedSession(workspace)) return
    const errors = validate()
    setFieldErrors(errors)
    setApiError(undefined)
    if (Object.keys(errors).length > 0) return

    const payload: CreateSharedReviewSessionRequest = {
      title: title.trim(),
      mode,
      rawContent,
      ...(mode === 'INTENT_MATCHING' ? { promptContent: promptContent.trim() } : {}),
    }
    setSubmitting(true)
    try {
      const created = await api.createSharedReviewSession(workspaceId, payload)
      navigate(`/workspaces/${workspaceId}/sessions/${created.id}`, { replace: true })
    } catch (exception) {
      const error = exception instanceof ApiError
        ? exception
        : new ApiError('Không thể tạo Shared Review Session.', null)
      setApiError(error)
      setFieldErrors(current => ({
        ...current,
        ...(error.validationErrors.title ? { title: error.validationErrors.title } : {}),
        ...(error.validationErrors.rawContent
          ? { rawContent: error.validationErrors.rawContent }
          : {}),
        ...((error.validationErrors.promptContent || error.validationErrors.modeContentValid)
          ? { promptContent: error.validationErrors.promptContent
              ?? error.validationErrors.modeContentValid }
          : {}),
      }))
    } finally {
      setSubmitting(false)
    }
  }

  if (loadingWorkspace) return <p className="loading" role="status">Đang tải workspace…</p>
  if (!workspace || workspaceError) return <CreateUnavailable error={workspaceError} />
  if (!canCreateSharedSession(workspace)) return <section className="page-stack">
    <div className="empty" role="alert">
      <strong>Bạn không có quyền tạo session</strong>
      <span>Vai trò {workspace.currentUserRole} chỉ có thể xem Shared Review Sessions.</span>
      <Link className="button" to={`/workspaces/${workspace.id}/sessions`}>Xem Sessions</Link>
    </div>
  </section>

  const rawLabel = mode === 'GIT_DIFF' ? 'Git Diff' : mode === 'RAW_SNIPPET'
    ? 'Đoạn mã cần review'
    : 'Nội dung cần review'

  return <section className="form-page page-stack">
    <div>
      <Link className="back-link" to={`/workspaces/${workspace.id}/sessions`}>← Sessions của {workspace.name}</Link>
      <p className="eyebrow">SHARED ANALYSIS</p>
      <h1>Tạo Shared Review Session</h1>
      <p>Rule Engine chạy trước, sau đó AI phân tích phần nội dung còn lại.</p>
    </div>

    <form className="analysis-form" onSubmit={submit} noValidate>
      <label htmlFor="shared-session-title">Tiêu đề phiên
        <input
          id="shared-session-title"
          value={title}
          maxLength={255}
          autoFocus
          disabled={submitting}
          aria-invalid={Boolean(fieldErrors.title)}
          aria-describedby={fieldErrors.title ? 'shared-session-title-error' : undefined}
          onChange={event => {
            setTitle(event.target.value)
            setFieldErrors(current => ({ ...current, title: undefined }))
          }}
        />
      </label>
      {fieldErrors.title && <span id="shared-session-title-error" className="field-error">{fieldErrors.title}</span>}

      <fieldset disabled={submitting}>
        <legend>Chế độ phân tích</legend>
        <div className="mode-grid">{modes.map(item => <label
          className={`mode-option ${mode === item.value ? 'selected' : ''}`}
          key={item.value}
        >
          <input
            type="radio"
            name="mode"
            value={item.value}
            checked={mode === item.value}
            onChange={() => {
              setMode(item.value)
              if (item.value !== 'INTENT_MATCHING') setPromptContent('')
              setFieldErrors(current => ({ ...current, promptContent: undefined }))
            }}
          />
          <strong>{item.title}</strong>
          <span>{item.hint}</span>
        </label>)}</div>
      </fieldset>

      <label htmlFor="shared-session-content">{rawLabel}
        <textarea
          id="shared-session-content"
          rows={15}
          value={rawContent}
          maxLength={1_000_000}
          disabled={submitting}
          spellCheck={false}
          placeholder={mode === 'GIT_DIFF' ? 'diff --git ...' : 'Dán nội dung cần review…'}
          aria-invalid={Boolean(fieldErrors.rawContent)}
          aria-describedby={fieldErrors.rawContent ? 'shared-session-content-error' : 'shared-session-content-count'}
          onChange={event => {
            setRawContent(event.target.value)
            setFieldErrors(current => ({ ...current, rawContent: undefined }))
          }}
        />
      </label>
      {fieldErrors.rawContent && <span id="shared-session-content-error" className="field-error">{fieldErrors.rawContent}</span>}
      <span id="shared-session-content-count" className="character-count">{rawContent.length.toLocaleString('vi-VN')} / 1.000.000</span>

      {mode === 'INTENT_MATCHING' && <>
        <label htmlFor="shared-session-intent">Ý định / yêu cầu mong muốn
          <textarea
            id="shared-session-intent"
            rows={6}
            value={promptContent}
            maxLength={65_535}
            disabled={submitting}
            aria-invalid={Boolean(fieldErrors.promptContent)}
            aria-describedby={fieldErrors.promptContent ? 'shared-session-intent-error' : 'shared-session-intent-count'}
            onChange={event => {
              setPromptContent(event.target.value)
              setFieldErrors(current => ({ ...current, promptContent: undefined }))
            }}
          />
        </label>
        {fieldErrors.promptContent && <span id="shared-session-intent-error" className="field-error">{fieldErrors.promptContent}</span>}
        <span id="shared-session-intent-count" className="character-count">{promptContent.length.toLocaleString('vi-VN')} / 65.535</span>
      </>}

      {apiError && <div className="notice error" role="alert">{apiError.message}</div>}
      {submitting && <div className="analysis-progress" role="status">
        <strong>Đang phân tích mã và tạo Decision Cards…</strong>
        <span>Rule và AI chạy đồng bộ nên thao tác có thể mất một lúc.</span>
      </div>}
      <div className="form-actions">
        {submitting
          ? <button type="button" className="secondary" disabled>Hủy</button>
          : <Link className="button secondary" to={`/workspaces/${workspace.id}/sessions`}>Hủy</Link>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Đang phân tích…' : 'Tạo và phân tích'}
        </button>
      </div>
    </form>
  </section>
}

function CreateUnavailable({ error }: { error?: ApiError }) {
  return <section className="page-stack">
    <div className="empty" role="alert">
      <strong>{error?.status === 404 ? 'Không tìm thấy workspace' : 'Không thể mở form tạo session'}</strong>
      <span>{error?.message ?? 'Workspace không khả dụng.'}</span>
      <Link className="button secondary" to="/workspaces">Về danh sách</Link>
    </div>
  </section>
}
