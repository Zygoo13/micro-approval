import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../lib/api'

const NAME_MAX_LENGTH = 100
const DESCRIPTION_MAX_LENGTH = 1000

interface WorkspaceFormErrors {
  name?: string
  description?: string
}

export default function CreateWorkspacePage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [errors, setErrors] = useState<WorkspaceFormErrors>({})
  const [apiError, setApiError] = useState<ApiError>()
  const [submitting, setSubmitting] = useState(false)

  function validate(): WorkspaceFormErrors {
    const nextErrors: WorkspaceFormErrors = {}
    if (!name.trim()) nextErrors.name = 'Tên workspace không được để trống.'
    if (name.trim().length > NAME_MAX_LENGTH) {
      nextErrors.name = `Tên workspace không được vượt quá ${NAME_MAX_LENGTH} ký tự.`
    }
    if (description.length > DESCRIPTION_MAX_LENGTH) {
      nextErrors.description =
        `Mô tả workspace không được vượt quá ${DESCRIPTION_MAX_LENGTH} ký tự.`
    }
    return nextErrors
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return

    const nextErrors = validate()
    setErrors(nextErrors)
    setApiError(undefined)
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    try {
      const workspace = await api.createWorkspace({
        name: name.trim(),
        ...(description.trim() ? { description: description.trim() } : {}),
      })
      navigate(`/workspaces/${workspace.id}`, { replace: true })
    } catch (exception) {
      if (exception instanceof ApiError) {
        setApiError(exception)
        setErrors({
          name: exception.validationErrors.name,
          description: exception.validationErrors.description,
        })
      } else {
        setApiError(new ApiError('Không thể tạo workspace. Vui lòng thử lại.', null))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return <section className="form-page page-stack">
    <div>
      <Link className="back-link" to="/workspaces">← Danh sách workspace</Link>
      <p className="eyebrow">SHARED WORKSPACE</p>
      <h1>Tạo workspace</h1>
      <p>Tạo không gian làm việc nhóm. Bạn sẽ trở thành chủ sở hữu workspace.</p>
    </div>

    <form className="analysis-form" onSubmit={submit} noValidate>
      <label htmlFor="workspace-name">
        Tên workspace
        <input
          id="workspace-name"
          value={name}
          onChange={event => {
            setName(event.target.value)
            setErrors(current => ({ ...current, name: undefined }))
          }}
          required
          maxLength={NAME_MAX_LENGTH}
          autoFocus
          aria-invalid={Boolean(errors.name)}
          aria-describedby={errors.name ? 'workspace-name-error' : undefined}
          placeholder="Ví dụ: Payments"
        />
      </label>
      {errors.name && <span className="field-error" id="workspace-name-error">
        {errors.name}
      </span>}

      <label htmlFor="workspace-description">
        Mô tả <span>(không bắt buộc)</span>
        <textarea
          id="workspace-description"
          rows={6}
          value={description}
          onChange={event => {
            setDescription(event.target.value)
            setErrors(current => ({ ...current, description: undefined }))
          }}
          maxLength={DESCRIPTION_MAX_LENGTH}
          aria-invalid={Boolean(errors.description)}
          aria-describedby={errors.description ? 'workspace-description-error' : undefined}
          placeholder="Mục đích và phạm vi của workspace…"
        />
      </label>
      <span className="character-count">
        {description.length}/{DESCRIPTION_MAX_LENGTH}
      </span>
      {errors.description && <span className="field-error" id="workspace-description-error">
        {errors.description}
      </span>}

      {apiError && <div className="notice error" role="alert">
        <span>{apiError.message}</span>
        {apiError.status === 401 &&
          <Link className="button" to="/login">Đăng nhập lại</Link>}
      </div>}

      <div className="form-actions">
        <Link className="button secondary" to="/workspaces">Hủy</Link>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Đang tạo…' : 'Tạo workspace'}
        </button>
      </div>
    </form>
  </section>
}
