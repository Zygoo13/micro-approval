package com.microapproval.api.service;

import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;

public record AiDecisionCandidate(
        RiskCategory riskCategory,
        RiskLevel riskLevel,
        String codeSnippet,
        String questionText
) { }
