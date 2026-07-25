package com.microapproval.api.service;

import com.microapproval.api.entity.EngineType;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.RulePattern;
import com.microapproval.api.repository.RulePatternRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class RuleEngineService {

    private static final int MATCH_SNIPPET_MAX_LENGTH = 500;

    private final RulePatternRepository rulePatternRepository;
    private final int maxCardsPerSession;

    public RuleEngineService(
            RulePatternRepository rulePatternRepository,
            @Value("${rule-engine.max-cards-per-session:10}") int maxCardsPerSession
    ) {
        this.rulePatternRepository = rulePatternRepository;
        if (maxCardsPerSession < 1) {
            throw new IllegalArgumentException("rule-engine.max-cards-per-session phải lớn hơn 0");
        }
        this.maxCardsPerSession = maxCardsPerSession;
    }

    /** Runs configured system rules before any future AI analysis. */
    public List<MicroDecision> analyze(ReviewSession session) {
        return analyzeWithRemainingContent(session).decisions();
    }

    public RuleAnalysisResult analyzeWithRemainingContent(ReviewSession session) {
        List<MicroDecision> decisions = new ArrayList<>();
        List<MatchRange> matchedRanges = new ArrayList<>();
        for (RulePattern rule : rulePatternRepository.findAllByTeamIdIsNullAndIsActiveTrueOrderByPriorityAscNameAsc()) {
            if (decisions.size() >= maxCardsPerSession) {
                break;
            }
            findFirstMatch(rule, session.getRawContent()).ifPresent(match -> {
                decisions.add(card(session, rule, match.text()));
                matchedRanges.add(new MatchRange(match.start(), match.end()));
            });
        }

        for (int index = 0; index < decisions.size(); index++) {
            decisions.get(index).setDisplayOrder(index + 1);
        }
        return new RuleAnalysisResult(decisions, removeMatchedRanges(session.getRawContent(), matchedRanges));
    }

    private Optional<RuleMatch> findFirstMatch(RulePattern rule, String source) {
        try {
            Matcher matcher = Pattern.compile(rule.getPattern()).matcher(source);
            if (!matcher.find()) {
                return Optional.empty();
            }
            String match = matcher.group();
            String snippet = match.length() > MATCH_SNIPPET_MAX_LENGTH
                    ? match.substring(0, MATCH_SNIPPET_MAX_LENGTH) + "…"
                    : match;
            return Optional.of(new RuleMatch(snippet, matcher.start(), matcher.end()));
        } catch (PatternSyntaxException exception) {
            throw new IllegalStateException("Rule pattern không hợp lệ: " + rule.getName(), exception);
        }
    }

    private String removeMatchedRanges(String source, List<MatchRange> ranges) {
        StringBuilder remaining = new StringBuilder(source);
        ranges.stream().sorted((left, right) -> Integer.compare(right.start(), left.start()))
                .forEach(range -> remaining.delete(range.start(), range.end()));
        return remaining.toString();
    }

    private MicroDecision card(ReviewSession session, RulePattern rule, String matchedSnippet) {
        return MicroDecision.builder()
                .session(session)
                .engineType(EngineType.RULE_BASED)
                .riskCategory(rule.getRiskCategory())
                .riskLevel(rule.getRiskLevel())
                .codeSnippet(matchedSnippet)
                .questionText(rule.getQuestionTemplate())
                .isAiBypassed(false)
                .build();
    }

    private record RuleMatch(String text, int start, int end) { }
    private record MatchRange(int start, int end) { }
}
