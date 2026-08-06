import type { SessionReviewer, WorkspaceDetail } from '../../types'

const creatorRoles = new Set(['OWNER', 'ADMIN', 'REVIEWER'])

export function canCreateSharedSession(workspace: WorkspaceDetail) {
  return creatorRoles.has(workspace.currentUserRole)
}

export function canManageSessionReviewers(workspace: WorkspaceDetail) {
  return workspace.currentUserRole === 'OWNER' || workspace.currentUserRole === 'ADMIN'
}

export function canSubmitTeamVote(
  workspace: WorkspaceDetail,
  assignment?: SessionReviewer,
) {
  return Boolean(assignment
    && assignment.status === 'ASSIGNED'
    && creatorRoles.has(workspace.currentUserRole)
    && creatorRoles.has(assignment.workspaceRole))
}
