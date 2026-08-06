package com.microapproval.api.dto;

import java.time.LocalDateTime;

public record SessionAuditValueResponse(
        String status,
        String decision,
        String note,
        Long assignmentVersion,
        Long voteVersion,
        Boolean closed,
        LocalDateTime closedAt,
        String closedByUserId,
        String closeReason,
        Long lifecycleVersion,
        LocalDateTime reopenedAt
) {
}
