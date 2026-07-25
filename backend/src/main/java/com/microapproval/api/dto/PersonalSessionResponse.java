package com.microapproval.api.dto;

import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.SessionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PersonalSessionResponse(
        String id,
        String title,
        AnalysisMode mode,
        String rawContent,
        String promptContent,
        SessionStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<MicroDecisionResponse> decisions
) {
    public static PersonalSessionResponse from(ReviewSession session, List<MicroDecisionResponse> decisions) {
        return new PersonalSessionResponse(
                session.getId(), session.getTitle(), session.getMode(), session.getRawContent(), session.getPromptContent(),
                session.getStatus(), session.getCreatedAt(), session.getCompletedAt(), decisions
        );
    }
}
