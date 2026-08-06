package com.microapproval.api.dto;

import com.microapproval.api.entity.DecisionCardVote;
import com.microapproval.api.entity.TeamVoteDecision;

import java.time.LocalDateTime;

public record TeamVoteResponse(
        String voteId,
        String cardId,
        String reviewerAssignmentId,
        String reviewerUserId,
        String reviewerDisplayName,
        TeamVoteDecision decision,
        String note,
        boolean counted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long version
) {
    public static TeamVoteResponse from(DecisionCardVote vote, boolean counted) {
        return new TeamVoteResponse(
                vote.getId(),
                vote.getDecisionCard().getId(),
                vote.getReviewerAssignment().getId(),
                vote.getReviewerAssignment().getWorkspaceMember().getUser().getId(),
                vote.getReviewerAssignment().getWorkspaceMember().getUser().getFullName(),
                vote.getDecision(),
                vote.getNote(),
                counted,
                vote.getCreatedAt(),
                vote.getUpdatedAt(),
                vote.getVersion() == null ? 0 : vote.getVersion()
        );
    }
}
