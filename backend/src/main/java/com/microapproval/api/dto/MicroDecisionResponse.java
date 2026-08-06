package com.microapproval.api.dto;

import com.microapproval.api.entity.DecisionStatus;
import com.microapproval.api.entity.EngineType;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;
import com.microapproval.api.entity.TeamDecisionStatus;

import java.time.LocalDateTime;

public record MicroDecisionResponse(
        String id,
        EngineType engineType,
        RiskCategory riskCategory,
        RiskLevel riskLevel,
        String codeSnippet,
        String questionText,
        DecisionStatus humanDecision,
        TeamDecisionStatus teamDecision,
        String reviewerNote,
        String decidedByName,
        LocalDateTime decidedAt,
        int displayOrder
) {
    public static MicroDecisionResponse from(MicroDecision decision) {
        return new MicroDecisionResponse(
                decision.getId(), decision.getEngineType(), decision.getRiskCategory(), decision.getRiskLevel(),
                decision.getCodeSnippet(), decision.getQuestionText(), decision.getHumanDecision(), decision.getTeamDecision(), decision.getReviewerNote(),
                decision.getDecidedBy() == null ? null : decision.getDecidedBy().getFullName(),
                decision.getDecidedAt(), decision.getDisplayOrder()
        );
    }
}
