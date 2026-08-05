package com.microapproval.api.dto;

import com.microapproval.api.entity.WorkspaceInvitation;
import com.microapproval.api.entity.WorkspaceInvitationStatus;
import com.microapproval.api.entity.WorkspaceRole;

import java.time.LocalDateTime;

public record MyWorkspaceInvitationResponse(
        String id,
        String workspaceId,
        String workspaceName,
        WorkspaceRole role,
        WorkspaceInvitationStatus status,
        String invitedByDisplayName,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public static MyWorkspaceInvitationResponse from(
            WorkspaceInvitation invitation,
            WorkspaceInvitationStatus effectiveStatus
    ) {
        return new MyWorkspaceInvitationResponse(
                invitation.getId(),
                invitation.getWorkspace().getId(),
                invitation.getWorkspace().getName(),
                invitation.getRole(),
                effectiveStatus,
                invitation.getInvitedBy().getFullName(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt()
        );
    }
}
