package com.microapproval.api.service;

import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;
import com.microapproval.api.entity.RulePattern;
import com.microapproval.api.repository.RulePatternRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleEngineServiceTest {

    private final RulePatternRepository rulePatternRepository = mock(RulePatternRepository.class);

    @Test
    void detectsSqlStringInterpolation() {
        RuleEngineService engine = engineWith(10, List.of(sqlInterpolationRule()));

        List<MicroDecision> decisions = engine.analyze(session("SELECT * FROM users WHERE name = '" + " + input + " + "'"));

        assertEquals(1, decisions.size());
        assertEquals(RiskCategory.SECURITY, decisions.getFirst().getRiskCategory());
    }

    @Test
    void detectsDestructiveSql() {
        RuleEngineService engine = engineWith(10, List.of(destructiveSqlRule()));

        List<MicroDecision> decisions = engine.analyze(session("DELETE FROM audit_events WHERE created_at < NOW()"));

        assertEquals(1, decisions.size());
        assertEquals(RiskCategory.DATABASE, decisions.getFirst().getRiskCategory());
    }

    @Test
    void detectsHardcodedCredential() {
        RuleEngineService engine = engineWith(10, List.of(hardcodedCredentialRule()));

        List<MicroDecision> decisions = engine.analyze(session("const api_key = \"value-from-source\";"));

        assertEquals(1, decisions.size());
        assertEquals(RiskLevel.HIGH, decisions.getFirst().getRiskLevel());
    }

    @Test
    void detectsDependencyChange() {
        RuleEngineService engine = engineWith(10, List.of(dependencyRule()));

        List<MicroDecision> decisions = engine.analyze(session("import fastJsonParser from 'fast-json-parser';"));

        assertEquals(1, decisions.size());
        assertEquals(RiskCategory.DEPENDENCY, decisions.getFirst().getRiskCategory());
    }

    @Test
    void keepsConfiguredPriorityAndCapsCardsPerSession() {
        RuleEngineService engine = engineWith(2, List.of(sqlInterpolationRule(), destructiveSqlRule(), hardcodedCredentialRule()));

        List<MicroDecision> decisions = engine.analyze(session("SELECT * FROM users + input; DELETE FROM users; secret='abc'"));

        assertEquals(2, decisions.size());
        assertEquals("SQL interpolation", decisions.getFirst().getQuestionText());
        assertEquals("Destructive SQL", decisions.getLast().getQuestionText());
        assertEquals(1, decisions.getFirst().getDisplayOrder());
        assertEquals(2, decisions.getLast().getDisplayOrder());
    }

    @Test
    void returnsNoCardsWhenNoRuleMatches() {
        RuleEngineService engine = engineWith(10, configuredRules());

        assertTrue(engine.analyze(session("int total = quantity * price;")).isEmpty());
    }

    private RuleEngineService engineWith(int limit, List<RulePattern> rules) {
        when(rulePatternRepository.findAllByWorkspaceIdIsNullAndIsActiveTrueOrderByPriorityAscNameAsc()).thenReturn(rules);
        return new RuleEngineService(rulePatternRepository, limit);
    }

    private ReviewSession session(String source) {
        return ReviewSession.builder().rawContent(source).build();
    }

    private List<RulePattern> configuredRules() {
        return List.of(sqlInterpolationRule(), destructiveSqlRule(), hardcodedCredentialRule(), dependencyRule());
    }

    private RulePattern sqlInterpolationRule() {
        return rule("SQL interpolation", 10, "(?is)\\bselect\\b[\\s\\S]{0,500}?(?:\\+|\\$\\{)", RiskCategory.SECURITY, RiskLevel.HIGH);
    }

    private RulePattern destructiveSqlRule() {
        return rule("Destructive SQL", 20, "(?i)\\b(?:drop\\s+table|delete\\s+from)\\b", RiskCategory.DATABASE, RiskLevel.HIGH);
    }

    private RulePattern hardcodedCredentialRule() {
        return rule("Hardcoded credential", 30, "(?i)\\b(?:password|api[_-]?key|secret)\\s*[:=]\\s*[\"'][^\"']+", RiskCategory.SECURITY, RiskLevel.HIGH);
    }

    private RulePattern dependencyRule() {
        return rule("Dependency change", 40, "(?im)^\\s*(?:import\\s+|(?:\"dependencies\"|'dependencies')\\s*:|require\\s*\\()", RiskCategory.DEPENDENCY, RiskLevel.MEDIUM);
    }

    private RulePattern rule(String name, int priority, String pattern, RiskCategory category, RiskLevel level) {
        return RulePattern.builder().name(name).priority(priority).pattern(pattern).riskCategory(category).riskLevel(level)
                .questionTemplate(name).isActive(true).build();
    }
}
