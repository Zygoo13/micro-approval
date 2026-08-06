package com.microapproval.api.repository;

import com.microapproval.api.entity.ReviewSessionReviewer;
import com.microapproval.api.entity.ReviewSessionReviewerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewSessionReviewerRepository
        extends JpaRepository<ReviewSessionReviewer, String> {

    long countBySessionId(String sessionId);

    @Query("""
            SELECT assignment
            FROM ReviewSessionReviewer assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            JOIN FETCH assignment.assignedBy
            LEFT JOIN FETCH assignment.removedBy
            WHERE assignment.session.id = :sessionId
              AND assignment.status = :status
            ORDER BY assignment.assignedAt ASC, assignment.id ASC
            """)
    List<ReviewSessionReviewer> findAllWithPeopleBySessionIdAndStatus(
            @Param("sessionId") String sessionId,
            @Param("status") ReviewSessionReviewerStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM ReviewSessionReviewer assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            JOIN FETCH assignment.assignedBy
            LEFT JOIN FETCH assignment.removedBy
            WHERE assignment.session.id = :sessionId
              AND member.id = :workspaceMemberId
            """)
    Optional<ReviewSessionReviewer> findBySessionAndMemberForUpdate(
            @Param("sessionId") String sessionId,
            @Param("workspaceMemberId") String workspaceMemberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM ReviewSessionReviewer assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            JOIN FETCH assignment.assignedBy
            LEFT JOIN FETCH assignment.removedBy
            WHERE assignment.id = :assignmentId
              AND assignment.session.id = :sessionId
            """)
    Optional<ReviewSessionReviewer> findByIdAndSessionForUpdate(
            @Param("assignmentId") String assignmentId,
            @Param("sessionId") String sessionId
    );
}
