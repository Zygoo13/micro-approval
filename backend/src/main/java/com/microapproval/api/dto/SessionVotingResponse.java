package com.microapproval.api.dto;

import com.microapproval.api.entity.SessionStatus;

import java.util.List;

public record SessionVotingResponse(
        String sessionId,
        SessionStatus sessionStatus,
        int reviewerCount,
        List<DecisionCardVotingResponse> cards
) {
}
