package com.microapproval.api.dto;

import com.microapproval.api.entity.TeamReviewAuditEventType;

import java.time.LocalDateTime;

public record SessionAuditEventResponse(
        String eventId,
        TeamReviewAuditEventType eventType,
        String actorUserId,
        String actorDisplayName,
        String actorEmail,
        String targetUserId,
        String targetDisplayName,
        String targetAssignmentId,
        String decisionCardId,
        String decisionCardSummary,
        String reason,
        SessionAuditChangeResponse change,
        LocalDateTime createdAt
) {
}
