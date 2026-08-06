package com.microapproval.api.service;

import com.microapproval.api.dto.DecisionCardVotingResponse;
import com.microapproval.api.dto.SessionVotingResponse;
import com.microapproval.api.dto.TeamVoteResponse;
import com.microapproval.api.entity.DecisionCardVote;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.ReviewSessionReviewer;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.TeamDecisionStatus;
import com.microapproval.api.entity.TeamVoteDecision;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.repository.DecisionCardVoteRepository;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeamReviewAggregationService {

    private static final Set<WorkspaceRole> ELIGIBLE_ROLES =
            EnumSet.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.REVIEWER);

    private final MicroDecisionRepository decisionRepository;
    private final ReviewSessionReviewerRepository reviewerRepository;
    private final DecisionCardVoteRepository voteRepository;

    public SessionVotingResponse recalculate(ReviewSession session) {
        if (session.getClosedAt() != null) {
            return currentResponse(session);
        }
        List<MicroDecision> cards = decisionRepository
                .findAllBySessionIdForUpdate(session.getId());
        VotingData data = loadVotingDataForUpdate(session.getId(), cards);
        Map<String, List<DecisionCardVote>> validVotesByCard = validVotesByCard(data);

        for (MicroDecision card : cards) {
            card.setTeamDecision(calculateCardDecision(
                    data.eligibleAssignments().size(),
                    validVotesByCard.getOrDefault(card.getId(), List.of())
            ));
        }
        session.setStatus(calculateSessionStatus(
                cards,
                data.eligibleAssignments().size(),
                validVotesByCard
        ));
        return toResponse(session, data);
    }

    public SessionVotingResponse currentResponse(ReviewSession session) {
        List<MicroDecision> cards = decisionRepository
                .findBySessionIdOrderByDisplayOrderAsc(session.getId());
        return toResponse(session, loadVotingData(session.getId(), cards));
    }

    private VotingData loadVotingData(String sessionId, List<MicroDecision> cards) {
        return new VotingData(
                cards,
                reviewerRepository.findEligibleAssignedWithMemberBySessionId(
                        sessionId,
                        ELIGIBLE_ROLES
                ),
                voteRepository.findAllWithReviewerBySessionId(sessionId)
        );
    }

    private VotingData loadVotingDataForUpdate(String sessionId, List<MicroDecision> cards) {
        return new VotingData(
                cards,
                reviewerRepository.findEligibleAssignedWithMemberBySessionIdForUpdate(
                        sessionId,
                        ELIGIBLE_ROLES
                ),
                voteRepository.findAllWithReviewerBySessionIdForUpdate(sessionId)
        );
    }

    private Map<String, List<DecisionCardVote>> validVotesByCard(VotingData data) {
        Map<String, ReviewSessionReviewer> eligibleById = new HashMap<>();
        for (ReviewSessionReviewer assignment : data.eligibleAssignments()) {
            eligibleById.put(assignment.getId(), assignment);
        }

        Map<String, List<DecisionCardVote>> votesByCard = new HashMap<>();
        for (DecisionCardVote vote : data.votes()) {
            if (isCounted(vote, eligibleById)) {
                votesByCard.computeIfAbsent(vote.getDecisionCard().getId(), ignored ->
                        new java.util.ArrayList<>()).add(vote);
            }
        }
        return votesByCard;
    }

    private boolean isCounted(
            DecisionCardVote vote,
            Map<String, ReviewSessionReviewer> eligibleById
    ) {
        ReviewSessionReviewer current = eligibleById.get(vote.getReviewerAssignment().getId());
        return current != null
                && vote.getAssignmentVersion().equals(current.getVersion());
    }

    private TeamDecisionStatus calculateCardDecision(
            int reviewerCount,
            List<DecisionCardVote> validVotes
    ) {
        if (reviewerCount == 0) {
            return TeamDecisionStatus.PENDING;
        }
        if (validVotes.stream().anyMatch(vote -> vote.getDecision() == TeamVoteDecision.REJECTED)) {
            return TeamDecisionStatus.REJECTED;
        }
        Set<String> approvingAssignments = new HashSet<>();
        validVotes.stream()
                .filter(vote -> vote.getDecision() == TeamVoteDecision.APPROVED)
                .forEach(vote -> approvingAssignments.add(vote.getReviewerAssignment().getId()));
        return approvingAssignments.size() == reviewerCount
                ? TeamDecisionStatus.APPROVED
                : TeamDecisionStatus.PENDING;
    }

    private SessionStatus calculateSessionStatus(
            List<MicroDecision> cards,
            int reviewerCount,
            Map<String, List<DecisionCardVote>> validVotesByCard
    ) {
        if (cards.isEmpty()) {
            return SessionStatus.APPROVED;
        }
        if (reviewerCount == 0) {
            return SessionStatus.PENDING;
        }
        if (cards.stream().anyMatch(card -> card.getTeamDecision() == TeamDecisionStatus.REJECTED)) {
            return SessionStatus.REJECTED;
        }
        if (cards.stream().allMatch(card -> card.getTeamDecision() == TeamDecisionStatus.APPROVED)) {
            return SessionStatus.APPROVED;
        }
        boolean anyValidVote = validVotesByCard.values().stream().anyMatch(votes -> !votes.isEmpty());
        return anyValidVote ? SessionStatus.IN_REVIEW : SessionStatus.PENDING;
    }

    private SessionVotingResponse toResponse(ReviewSession session, VotingData data) {
        Map<String, ReviewSessionReviewer> eligibleById = new HashMap<>();
        for (ReviewSessionReviewer assignment : data.eligibleAssignments()) {
            eligibleById.put(assignment.getId(), assignment);
        }
        Map<String, List<DecisionCardVote>> votesByCard = new HashMap<>();
        for (DecisionCardVote vote : data.votes()) {
            votesByCard.computeIfAbsent(vote.getDecisionCard().getId(), ignored ->
                    new java.util.ArrayList<>()).add(vote);
        }

        List<DecisionCardVotingResponse> cardResponses = data.cards().stream()
                .map(card -> {
                    List<DecisionCardVote> cardVotes = votesByCard.getOrDefault(
                            card.getId(),
                            List.of()
                    );
                    List<TeamVoteResponse> voteResponses = cardVotes.stream()
                            .map(vote -> TeamVoteResponse.from(
                                    vote,
                                    isCounted(vote, eligibleById)
                            ))
                            .toList();
                    int validVoteCount = (int) voteResponses.stream()
                            .filter(TeamVoteResponse::counted)
                            .count();
                    return new DecisionCardVotingResponse(
                            card.getId(),
                            card.getTeamDecision(),
                            data.eligibleAssignments().size(),
                            validVoteCount,
                            voteResponses
                    );
                })
                .toList();
        return new SessionVotingResponse(
                session.getId(),
                session.getStatus(),
                session.getClosedAt() != null,
                session.getClosedAt(),
                session.getClosedBy() == null ? null : session.getClosedBy().getId(),
                session.getClosedBy() == null ? null : session.getClosedBy().getFullName(),
                session.getCloseReason(),
                session.getLifecycleVersion(),
                data.eligibleAssignments().size(),
                cardResponses
        );
    }

    private record VotingData(
            List<MicroDecision> cards,
            List<ReviewSessionReviewer> eligibleAssignments,
            List<DecisionCardVote> votes
    ) {
    }
}
