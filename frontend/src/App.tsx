import { Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom'
import { auth } from './lib/api'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SessionsPage from './pages/SessionsPage'
import CreateSessionPage from './pages/CreateSessionPage'
import SessionDetailPage from './pages/SessionDetailPage'

function Protected({ children }: { children: React.ReactNode }) {
  return auth.isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

function Layout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate()
  return <main className="app-shell"><header><NavLink to="/sessions" className="brand">Micro Approval</NavLink><nav><NavLink to="/sessions">Lịch sử</NavLink><NavLink to="/sessions/new">Tạo phiên</NavLink><button className="link-button" onClick={() => { auth.clearToken(); navigate('/login') }}>Đăng xuất</button></nav></header>{children}</main>
}

export default function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route path="/sessions" element={<Protected><Layout><SessionsPage /></Layout></Protected>} />
    <Route path="/sessions/new" element={<Protected><Layout><CreateSessionPage /></Layout></Protected>} />
    <Route path="/sessions/:sessionId" element={<Protected><Layout><SessionDetailPage /></Layout></Protected>} />
    <Route path="*" element={<Navigate to="/sessions" replace />} />
  </Routes>
}
