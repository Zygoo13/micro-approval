import { Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom'
import { auth } from './lib/api'
import AiSettingsPage from './pages/AiSettingsPage'
import CreateSessionPage from './pages/CreateSessionPage'
import CreateWorkspacePage from './pages/CreateWorkspacePage'
import LoginPage from './pages/LoginPage'
import MyInvitationsPage from './pages/MyInvitationsPage'
import RegisterPage from './pages/RegisterPage'
import SessionDetailPage from './pages/SessionDetailPage'
import SessionsPage from './pages/SessionsPage'
import CreateSharedSessionPage from './pages/CreateSharedSessionPage'
import SharedSessionDetailPage from './pages/SharedSessionDetailPage'
import SharedSessionsPage from './pages/SharedSessionsPage'
import WorkspaceDetailPage from './pages/WorkspaceDetailPage'
import WorkspaceListPage from './pages/WorkspaceListPage'

function Protected({ children }: { children: React.ReactNode }) {
  return auth.isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

function Layout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate()

  return <main className="app-shell">
    <header className="topbar">
      <NavLink to="/sessions" className="brand">
        <span className="brand-mark">✓</span>
        <span>Micro Approval</span>
      </NavLink>
      <nav aria-label="Điều hướng chính">
        <NavLink to="/sessions">Lịch sử</NavLink>
        <NavLink to="/sessions/new">Phân tích mới</NavLink>
        <NavLink to="/workspaces">Workspaces</NavLink>
        <NavLink to="/invitations">Lời mời của tôi</NavLink>
        <NavLink to="/settings/ai">Thiết lập AI</NavLink>
        <button
          className="link-button"
          onClick={() => {
            auth.clearToken()
            navigate('/login')
          }}
        >
          Đăng xuất
        </button>
      </nav>
    </header>
    {children}
  </main>
}

export default function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route path="/sessions" element={
      <Protected><Layout><SessionsPage /></Layout></Protected>
    } />
    <Route path="/sessions/new" element={
      <Protected><Layout><CreateSessionPage /></Layout></Protected>
    } />
    <Route path="/sessions/:sessionId" element={
      <Protected><Layout><SessionDetailPage /></Layout></Protected>
    } />
    <Route path="/workspaces" element={
      <Protected><Layout><WorkspaceListPage /></Layout></Protected>
    } />
    <Route path="/workspaces/new" element={
      <Protected><Layout><CreateWorkspacePage /></Layout></Protected>
    } />
    <Route path="/workspaces/:workspaceId" element={
      <Protected><Layout><WorkspaceDetailPage /></Layout></Protected>
    } />
    <Route path="/workspaces/:workspaceId/sessions" element={
      <Protected><Layout><SharedSessionsPage /></Layout></Protected>
    } />
    <Route path="/workspaces/:workspaceId/sessions/new" element={
      <Protected><Layout><CreateSharedSessionPage /></Layout></Protected>
    } />
    <Route path="/workspaces/:workspaceId/sessions/:sessionId" element={
      <Protected><Layout><SharedSessionDetailPage /></Layout></Protected>
    } />
    <Route path="/invitations" element={
      <Protected><Layout><MyInvitationsPage /></Layout></Protected>
    } />
    <Route path="/settings/ai" element={
      <Protected><Layout><AiSettingsPage /></Layout></Protected>
    } />
    <Route path="*" element={<Navigate to="/sessions" replace />} />
  </Routes>
}
