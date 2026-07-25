package com.microapproval.api.service;

import com.microapproval.api.entity.MicroDecision;

import java.util.List;

public record RuleAnalysisResult(List<MicroDecision> decisions, String remainingContent) { }
