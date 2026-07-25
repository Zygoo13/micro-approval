package com.microapproval.api.service;

import com.microapproval.api.entity.AiProviderConfiguration;

public interface AiAnalysisClient {
    AiAnalysisResult analyze(AiProviderConfiguration configuration, String remainingContent);
    void verify(AiProviderConfiguration configuration);
}
