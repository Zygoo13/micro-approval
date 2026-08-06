import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import type { PersonalSession } from '../types'
import SessionDetailPage from './SessionDetailPage'

const personalSession: PersonalSession = {
  id: 'personal-session-1',
  title: 'Personal review',
  mode: 'GIT_DIFF',
  rawContent: 'diff --git ...',
  promptContent: null,
  status: 'PENDING',
  aiAnalysisStatus: 'NOT_REQUESTED',
  aiAnalysisError: null,
  aiTokenUsed: 0,
  createdAt: '2026-08-06T02:00:00',
  completedAt: null,
  decisions: [],
}

afterEach(() => vi.restoreAllMocks())

describe('SessionDetailPage', () => {
  it('keeps Personal Workspace isolated from Team Voting APIs', async () => {
    vi.spyOn(api, 'getSession').mockResolvedValue(personalSession)
    const getVoting = vi.spyOn(api, 'getSessionVoting')
    const close = vi.spyOn(api, 'closeSharedReviewSession')
    const reopen = vi.spyOn(api, 'reopenSharedReviewSession')

    render(
      <MemoryRouter initialEntries={['/sessions/personal-session-1']}>
        <Routes>
          <Route path="/sessions/:sessionId" element={<SessionDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Personal review' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Team Voting' })).not.toBeInTheDocument()
    expect(screen.queryByText('Session đang mở')).not.toBeInTheDocument()
    expect(getVoting).not.toHaveBeenCalled()
    expect(close).not.toHaveBeenCalled()
    expect(reopen).not.toHaveBeenCalled()
  })
})
