import type { WorkspaceDetail } from '../../types'

const creatorRoles = new Set(['OWNER', 'ADMIN', 'REVIEWER'])

export function canCreateSharedSession(workspace: WorkspaceDetail) {
  return creatorRoles.has(workspace.currentUserRole)
}
