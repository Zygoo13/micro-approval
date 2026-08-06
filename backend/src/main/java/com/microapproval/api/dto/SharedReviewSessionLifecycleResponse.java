package com.microapproval.api.dto;

import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.SessionStatus;

import java.time.LocalDateTime;

public record SharedReviewSessionLifecycleResponse(
        String sessionId,
        SessionStatus status,
        boolean closed,
        LocalDateTime closedAt,
        String closedByUserId,
        String closedByDisplayName,
        String closeReason,
        long lifecycleVersion
) {
    public static SharedReviewSessionLifecycleResponse from(ReviewSession session) {
        return new SharedReviewSessionLifecycleResponse(
                session.getId(),
                session.getStatus(),
                session.getClosedAt() != null,
                session.getClosedAt(),
                session.getClosedBy() == null ? null : session.getClosedBy().getId(),
                session.getClosedBy() == null ? null : session.getClosedBy().getFullName(),
                session.getCloseReason(),
                session.getLifecycleVersion()
        );
    }
}
