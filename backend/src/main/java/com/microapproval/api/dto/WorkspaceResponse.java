package com.microapproval.api.dto;

import com.microapproval.api.entity.WorkspaceRole;

import java.time.LocalDateTime;

public record WorkspaceResponse(
        String id,
        String name,
        String description,
        String ownerId,
        WorkspaceRole currentUserRole,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
