package com.microapproval.api.dto;

import com.microapproval.api.entity.SessionStatus;

import java.util.List;
import java.time.LocalDateTime;

public record SessionVotingResponse(
        String sessionId,
        SessionStatus sessionStatus,
        boolean closed,
        LocalDateTime closedAt,
        String closedByUserId,
        String closedByDisplayName,
        String closeReason,
        long lifecycleVersion,
        int reviewerCount,
        List<DecisionCardVotingResponse> cards
) {
}
