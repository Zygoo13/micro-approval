package com.microapproval.api.dto;

import com.microapproval.api.entity.ReviewSessionReviewer;
import com.microapproval.api.entity.ReviewSessionReviewerStatus;
import com.microapproval.api.entity.WorkspaceRole;

import java.time.LocalDateTime;

public record SessionReviewerResponse(
        String assignmentId,
        String sessionId,
        String workspaceMemberId,
        String userId,
        String displayName,
        String email,
        WorkspaceRole workspaceRole,
        ReviewSessionReviewerStatus status,
        String assignedByUserId,
        String assignedByDisplayName,
        LocalDateTime assignedAt,
        LocalDateTime removedAt,
        String removedByUserId,
        String removalReason,
        long version
) {
    public static SessionReviewerResponse from(ReviewSessionReviewer assignment) {
        return new SessionReviewerResponse(
                assignment.getId(),
                assignment.getSession().getId(),
                assignment.getWorkspaceMember().getId(),
                assignment.getWorkspaceMember().getUser().getId(),
                assignment.getWorkspaceMember().getUser().getFullName(),
                assignment.getWorkspaceMember().getUser().getEmail(),
                assignment.getWorkspaceMember().getRole(),
                assignment.getStatus(),
                assignment.getAssignedBy().getId(),
                assignment.getAssignedBy().getFullName(),
                assignment.getAssignedAt(),
                assignment.getRemovedAt(),
                assignment.getRemovedBy() == null ? null : assignment.getRemovedBy().getId(),
                assignment.getRemovalReason(),
                assignment.getVersion() == null ? 0 : assignment.getVersion()
        );
    }
}
