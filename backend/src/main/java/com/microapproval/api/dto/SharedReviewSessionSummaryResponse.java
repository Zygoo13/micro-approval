package com.microapproval.api.dto;

import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.WorkspaceType;

import java.time.LocalDateTime;

public record SharedReviewSessionSummaryResponse(
        String id,
        String workspaceId,
        WorkspaceType workspaceType,
        String title,
        AnalysisMode mode,
        SessionStatus status,
        AiAnalysisStatus aiAnalysisStatus,
        String createdByUserId,
        String createdByDisplayName,
        LocalDateTime createdAt
) {
    public static SharedReviewSessionSummaryResponse from(ReviewSession session) {
        return new SharedReviewSessionSummaryResponse(
                session.getId(),
                session.getWorkspace().getId(),
                session.getWorkspaceType(),
                session.getTitle(),
                session.getMode(),
                session.getStatus(),
                session.getAiAnalysisStatus(),
                session.getSubmittedBy().getId(),
                session.getSubmittedBy().getFullName(),
                session.getCreatedAt()
        );
    }
}
