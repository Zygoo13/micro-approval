package com.microapproval.api.dto;

import com.microapproval.api.entity.WorkspaceRole;

import java.time.LocalDateTime;

public record WorkspaceSummaryResponse(
        String id,
        String name,
        String description,
        String ownerId,
        WorkspaceRole currentUserRole,
        LocalDateTime createdAt
) {
}
