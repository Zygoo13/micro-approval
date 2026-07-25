import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, auth } from '../lib/api'

export default function AuthForm({ register = false }: { register?: boolean }) {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setLoading(true); setError('')
    try {
      const result = register
        ? await api.register({ fullName: String(form.get('fullName')), email: String(form.get('email')), password: String(form.get('password')) })
        : await api.login({ email: String(form.get('email')), password: String(form.get('password')) })
      auth.setToken(result.token)
      navigate('/sessions')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Đã có lỗi xảy ra')
    } finally { setLoading(false) }
  }

  return <section className="auth-card"><h1>{register ? 'Tạo tài khoản' : 'Đăng nhập'}</h1><p>Không gian tự review code cá nhân.</p><form onSubmit={submit}>
    {register && <label>Họ tên<input name="fullName" required maxLength={100} /></label>}
    <label>Email<input name="email" type="email" required /></label>
    <label>Mật khẩu<input name="password" type="password" required minLength={8} /></label>
    {error && <p className="error">{error}</p>}
    <button disabled={loading}>{loading ? 'Đang xử lý…' : register ? 'Đăng ký' : 'Đăng nhập'}</button>
  </form><p>{register ? 'Đã có tài khoản?' : 'Chưa có tài khoản?'} <Link to={register ? '/login' : '/register'}>{register ? 'Đăng nhập' : 'Đăng ký'}</Link></p></section>
}
