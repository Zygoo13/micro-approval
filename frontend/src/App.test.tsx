import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { api, auth } from './lib/api'

afterEach(() => {
  auth.clearToken()
  vi.restoreAllMocks()
})

describe('Invitation route and navigation', () => {
  it('protects /invitations from unauthenticated access', async () => {
    auth.clearToken()
    render(<MemoryRouter initialEntries={['/invitations']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
  })

  it('renders My Invitations and its navigation link for an authenticated user', async () => {
    auth.setToken('test-token')
    vi.spyOn(api, 'getMyWorkspaceInvitations').mockResolvedValue([])
    render(<MemoryRouter initialEntries={['/invitations']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Lời mời của tôi' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Lời mời của tôi' })).toHaveAttribute(
      'href',
      '/invitations',
    )
  })
})

describe('Shared Session routes', () => {
  it('protects the direct create route from unauthenticated access', async () => {
    auth.clearToken()
    render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions/new']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
  })

  it('renders the direct session list route inside the authenticated layout', async () => {
    auth.setToken('test-token')
    vi.spyOn(api, 'getWorkspaceById').mockResolvedValue({
      id: 'workspace-1', name: 'Payments', description: null, ownerId: 'owner-1',
      currentUserRole: 'MEMBER', createdAt: '2026-08-06T01:00:00', updatedAt: '2026-08-06T01:00:00',
    })
    vi.spyOn(api, 'getSharedReviewSessions').mockResolvedValue([])
    render(<MemoryRouter initialEntries={['/workspaces/workspace-1/sessions']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Sessions của Payments' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Workspaces' })).toHaveAttribute('href', '/workspaces')
  })
})
