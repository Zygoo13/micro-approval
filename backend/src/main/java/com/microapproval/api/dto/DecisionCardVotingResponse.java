package com.microapproval.api.dto;

import com.microapproval.api.entity.TeamDecisionStatus;

import java.util.List;

public record DecisionCardVotingResponse(
        String cardId,
        TeamDecisionStatus teamDecision,
        int assignedReviewerCount,
        int validVoteCount,
        List<TeamVoteResponse> votes
) {
}
