package com.microapproval.api.dto;

import com.microapproval.api.entity.WorkspaceInvitation;
import com.microapproval.api.entity.WorkspaceInvitationStatus;
import com.microapproval.api.entity.WorkspaceRole;

import java.time.LocalDateTime;

public record WorkspaceInvitationResponse(
        String id,
        String workspaceId,
        String email,
        WorkspaceRole role,
        WorkspaceInvitationStatus status,
        String invitedByUserId,
        String invitedByDisplayName,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime respondedAt
) {
    public static WorkspaceInvitationResponse from(
            WorkspaceInvitation invitation,
            WorkspaceInvitationStatus effectiveStatus
    ) {
        return new WorkspaceInvitationResponse(
                invitation.getId(),
                invitation.getWorkspace().getId(),
                invitation.getEmail(),
                invitation.getRole(),
                effectiveStatus,
                invitation.getInvitedBy().getId(),
                invitation.getInvitedBy().getFullName(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getRespondedAt()
        );
    }
}
