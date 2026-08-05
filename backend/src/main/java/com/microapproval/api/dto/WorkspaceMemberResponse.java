package com.microapproval.api.dto;

import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;

import java.time.LocalDateTime;

public record WorkspaceMemberResponse(
        String membershipId,
        String userId,
        String email,
        String displayName,
        WorkspaceRole role,
        MembershipStatus status,
        LocalDateTime joinedAt
) {
    public static WorkspaceMemberResponse from(WorkspaceMember membership) {
        return new WorkspaceMemberResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getFullName(),
                membership.getRole(),
                membership.getStatus(),
                membership.getJoinedAt()
        );
    }
}
