package com.microapproval.api.dto;

import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.WorkspaceType;

import java.time.LocalDateTime;
import java.util.List;

public record SharedReviewSessionDetailResponse(
        String id,
        String workspaceId,
        WorkspaceType workspaceType,
        String title,
        AnalysisMode mode,
        String rawContent,
        String promptContent,
        SessionStatus status,
        AiAnalysisStatus aiAnalysisStatus,
        String aiAnalysisError,
        int aiTokenUsed,
        String createdByUserId,
        String createdByDisplayName,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<MicroDecisionResponse> decisions
) {
    public static SharedReviewSessionDetailResponse from(
            ReviewSession session,
            List<MicroDecisionResponse> decisions
    ) {
        return new SharedReviewSessionDetailResponse(
                session.getId(),
                session.getWorkspace().getId(),
                session.getWorkspaceType(),
                session.getTitle(),
                session.getMode(),
                session.getRawContent(),
                session.getPromptContent(),
                session.getStatus(),
                session.getAiAnalysisStatus(),
                session.getAiAnalysisError(),
                session.getAiTokenUsed() == null ? 0 : session.getAiTokenUsed(),
                session.getSubmittedBy().getId(),
                session.getSubmittedBy().getFullName(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                decisions
        );
    }
}
