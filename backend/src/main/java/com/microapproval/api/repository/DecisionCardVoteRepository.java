package com.microapproval.api.repository;

import com.microapproval.api.entity.DecisionCardVote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DecisionCardVoteRepository extends JpaRepository<DecisionCardVote, String> {

    long countByDecisionCardSessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT vote
            FROM DecisionCardVote vote
            WHERE vote.decisionCard.id = :cardId
              AND vote.reviewerAssignment.id = :assignmentId
            """)
    Optional<DecisionCardVote> findByCardAndAssignmentForUpdate(
            @Param("cardId") String cardId,
            @Param("assignmentId") String assignmentId
    );

    @Query("""
            SELECT vote
            FROM DecisionCardVote vote
            JOIN FETCH vote.decisionCard card
            JOIN FETCH vote.reviewerAssignment assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            WHERE card.session.id = :sessionId
            ORDER BY card.displayOrder ASC, vote.createdAt ASC, vote.id ASC
            """)
    List<DecisionCardVote> findAllWithReviewerBySessionId(
            @Param("sessionId") String sessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT vote
            FROM DecisionCardVote vote
            JOIN FETCH vote.decisionCard card
            JOIN FETCH vote.reviewerAssignment assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            WHERE card.session.id = :sessionId
            ORDER BY card.displayOrder ASC, vote.createdAt ASC, vote.id ASC
            """)
    List<DecisionCardVote> findAllWithReviewerBySessionIdForUpdate(
            @Param("sessionId") String sessionId
    );
}
