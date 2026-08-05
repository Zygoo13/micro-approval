package com.microapproval.api.service;

import com.microapproval.api.config.AiAnalysisProperties;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AiProviderConfiguration;
import com.microapproval.api.entity.EngineType;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.User;
import com.microapproval.api.exception.AiCredentialEncryptionUnavailableException;
import com.microapproval.api.exception.AiProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewAnalysisPipeline {

    private final RuleEngineService ruleEngineService;
    private final AiAnalysisProperties aiAnalysisProperties;
    private final AiAnalysisClient aiAnalysisClient;
    private final AiConfigurationService aiConfigurationService;

    public List<MicroDecision> analyze(ReviewSession session, User analysisUser) {
        RuleAnalysisResult ruleAnalysis = ruleEngineService.analyzeWithRemainingContent(session);
        List<MicroDecision> decisions = new ArrayList<>(ruleAnalysis.decisions());
        appendAiDecisions(session, analysisUser, ruleAnalysis.remainingContent(), decisions);
        for (int index = 0; index < decisions.size(); index++) {
            decisions.get(index).setDisplayOrder(index + 1);
        }
        return decisions;
    }

    private void appendAiDecisions(
            ReviewSession session,
            User analysisUser,
            String remainingContent,
            List<MicroDecision> decisions
    ) {
        if (!aiAnalysisProperties.isEnabled()) {
            session.setAiAnalysisStatus(AiAnalysisStatus.DISABLED);
            return;
        }
        if (remainingContent.isBlank()
                || decisions.size() >= aiAnalysisProperties.getMaxCardsPerSession()) {
            session.setAiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED);
            return;
        }

        AiProviderConfiguration configuration = aiConfigurationService
                .activeFor(analysisUser)
                .orElse(null);
        if (configuration == null) {
            session.setAiAnalysisStatus(AiAnalysisStatus.DISABLED);
            return;
        }

        try {
            AiAnalysisResult result = aiAnalysisClient.analyze(configuration, remainingContent);
            int remainingCapacity = aiAnalysisProperties.getMaxCardsPerSession() - decisions.size();
            result.decisions().stream()
                    .filter(this::isValidAiCandidate)
                    .limit(remainingCapacity)
                    .map(candidate -> MicroDecision.builder()
                            .session(session)
                            .engineType(EngineType.AI_BASED)
                            .riskCategory(candidate.riskCategory())
                            .riskLevel(candidate.riskLevel())
                            .codeSnippet(candidate.codeSnippet())
                            .questionText(candidate.questionText())
                            .isAiBypassed(false)
                            .build())
                    .forEach(decisions::add);
            session.setAiTokenUsed(result.totalTokens());
            session.setAiAnalysisStatus(AiAnalysisStatus.SUCCEEDED);
            session.setAiAnalysisError(null);
        } catch (AiProviderException | AiCredentialEncryptionUnavailableException exception) {
            markAiFallback(session, exception.getMessage());
        } catch (RuntimeException exception) {
            markAiFallback(
                    session,
                    "AI tạm thời không khả dụng; hệ thống tiếp tục với Rule Engine."
            );
        }
    }

    private boolean isValidAiCandidate(AiDecisionCandidate candidate) {
        return candidate != null
                && candidate.riskCategory() != null
                && candidate.riskLevel() != null
                && candidate.codeSnippet() != null
                && !candidate.codeSnippet().isBlank()
                && candidate.questionText() != null
                && !candidate.questionText().isBlank();
    }

    private void markAiFallback(ReviewSession session, String message) {
        session.setAiAnalysisStatus(AiAnalysisStatus.FALLBACK);
        session.setAiAnalysisError(message);
    }
}
