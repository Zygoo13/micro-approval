package com.microapproval.api.service;

import com.microapproval.api.config.AiAnalysisProperties;
import com.microapproval.api.dto.CreatePersonalSessionRequest;
import com.microapproval.api.dto.PersonalSessionResponse;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AiProviderConfiguration;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.EngineType;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.User;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonalSessionAiAnalysisTest {

    @Test
    void appendsAiCardsAndRecordsTokenUsageAfterRuleScan() {
        TestContext context = context(true);
        when(context.aiClient.analyze(any(), org.mockito.ArgumentMatchers.eq("remaining business logic")))
                .thenReturn(new AiAnalysisResult(List.of(new AiDecisionCandidate(
                        RiskCategory.BUSINESS_LOGIC, RiskLevel.MEDIUM, "taxRate = 0.08", "Thuế suất mới đã được PM xác nhận chưa?"
                )), 42));

        PersonalSessionResponse response = context.service.createSession(request(), "user@example.com");

        List<MicroDecision> saved = capturedDecisions(context);
        assertEquals(2, saved.size());
        assertEquals(EngineType.RULE_BASED, saved.getFirst().getEngineType());
        assertEquals(EngineType.AI_BASED, saved.getLast().getEngineType());
        assertEquals(AiAnalysisStatus.SUCCEEDED, response.aiAnalysisStatus());
        assertEquals(42, response.aiTokenUsed());
    }

    @Test
    void preservesRuleCardsAndReturnsFallbackWarningWhenAiFails() {
        TestContext context = context(true);
        when(context.aiClient.analyze(any(), any())).thenThrow(new IllegalStateException("provider timeout"));

        PersonalSessionResponse response = context.service.createSession(request(), "user@example.com");

        List<MicroDecision> saved = capturedDecisions(context);
        assertEquals(1, saved.size());
        assertEquals(EngineType.RULE_BASED, saved.getFirst().getEngineType());
        assertEquals(AiAnalysisStatus.FALLBACK, response.aiAnalysisStatus());
        assertTrue(response.aiAnalysisError().contains("Rule Engine"));
    }

    @SuppressWarnings("unchecked")
    private List<MicroDecision> capturedDecisions(TestContext context) {
        ArgumentCaptor<List<MicroDecision>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(context.decisionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private TestContext context(boolean aiEnabled) {
        UserRepository users = mock(UserRepository.class);
        ReviewSessionRepository sessions = mock(ReviewSessionRepository.class);
        MicroDecisionRepository decisions = mock(MicroDecisionRepository.class);
        RuleEngineService ruleEngine = mock(RuleEngineService.class);
        AiAnalysisClient aiClient = mock(AiAnalysisClient.class);
        AiConfigurationService aiConfigurationService = mock(AiConfigurationService.class);
        AiAnalysisProperties properties = new AiAnalysisProperties();
        properties.setEnabled(aiEnabled);
        properties.setMaxCardsPerSession(10);

        User user = User.builder().id("user-1").email("user@example.com").fullName("Test User").passwordHash("hash").build();
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(sessions.save(any(ReviewSession.class))).thenAnswer(call -> {
            ReviewSession session = call.getArgument(0);
            session.setId("session-1");
            return session;
        });
        MicroDecision ruleDecision = MicroDecision.builder().engineType(EngineType.RULE_BASED)
                .riskCategory(RiskCategory.SECURITY).riskLevel(RiskLevel.HIGH)
                .codeSnippet("SELECT").questionText("Rule question").build();
        when(ruleEngine.analyzeWithRemainingContent(any())).thenAnswer(call -> {
            ruleDecision.setSession(call.getArgument(0));
            return new RuleAnalysisResult(List.of(ruleDecision), "remaining business logic");
        });
        AiProviderConfiguration configuration = AiProviderConfiguration.builder().id("ai-1").userId("user-1").enabled(true).build();
        when(aiConfigurationService.activeFor(user)).thenReturn(Optional.of(configuration));

        ReviewAnalysisPipeline pipeline = new ReviewAnalysisPipeline(
                ruleEngine,
                properties,
                aiClient,
                aiConfigurationService
        );
        return new TestContext(
                new PersonalSessionService(sessions, decisions, users, pipeline),
                decisions,
                aiClient
        );
    }

    private CreatePersonalSessionRequest request() {
        CreatePersonalSessionRequest request = new CreatePersonalSessionRequest();
        request.setTitle("AI analysis test");
        request.setMode(AnalysisMode.GIT_DIFF);
        request.setRawContent("original code");
        return request;
    }

    private record TestContext(PersonalSessionService service, MicroDecisionRepository decisionRepository, AiAnalysisClient aiClient) { }
}
