package com.microapproval.api.service;

import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineServiceTest {

    private final RuleEngineService ruleEngineService = new RuleEngineService();

    @Test
    void createsOrderedCardsForDetectedRisks() {
        ReviewSession session = ReviewSession.builder()
                .rawContent("SELECT * FROM users WHERE id = '" + " + input + " + "';\nDROP TABLE audit_log;\napi_key=abc")
                .build();

        List<MicroDecision> decisions = ruleEngineService.analyze(session);

        assertEquals(3, decisions.size());
        assertEquals(1, decisions.getFirst().getDisplayOrder());
        assertEquals(3, decisions.getLast().getDisplayOrder());
        assertTrue(decisions.stream().allMatch(decision -> decision.getSession() == session));
    }

    @Test
    void returnsNoCardsWhenNoKnownPatternExists() {
        ReviewSession session = ReviewSession.builder().rawContent("int total = quantity * price;").build();

        assertTrue(ruleEngineService.analyze(session).isEmpty());
    }
}
