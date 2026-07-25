package com.microapproval.api.service;

import java.util.List;

public record AiAnalysisResult(List<AiDecisionCandidate> decisions, int totalTokens) { }
