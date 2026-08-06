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
import java.util.Set;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM ReviewSessionReviewer assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            WHERE assignment.session.id = :sessionId
              AND member.user.id = :userId
            """)
    Optional<ReviewSessionReviewer> findBySessionAndUserForUpdate(
            @Param("sessionId") String sessionId,
            @Param("userId") String userId
    );

    @Query("""
            SELECT assignment
            FROM ReviewSessionReviewer assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            WHERE assignment.session.id = :sessionId
              AND assignment.status = com.microapproval.api.entity.ReviewSessionReviewerStatus.ASSIGNED
              AND member.status = com.microapproval.api.entity.MembershipStatus.ACTIVE
              AND member.role IN :roles
            ORDER BY assignment.assignedAt ASC, assignment.id ASC
            """)
    List<ReviewSessionReviewer> findEligibleAssignedWithMemberBySessionId(
            @Param("sessionId") String sessionId,
            @Param("roles") Set<com.microapproval.api.entity.WorkspaceRole> roles
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM ReviewSessionReviewer assignment
            JOIN FETCH assignment.workspaceMember member
            JOIN FETCH member.user
            WHERE assignment.session.id = :sessionId
              AND assignment.status = com.microapproval.api.entity.ReviewSessionReviewerStatus.ASSIGNED
              AND member.status = com.microapproval.api.entity.MembershipStatus.ACTIVE
              AND member.role IN :roles
            ORDER BY assignment.assignedAt ASC, assignment.id ASC
            """)
    List<ReviewSessionReviewer> findEligibleAssignedWithMemberBySessionIdForUpdate(
            @Param("sessionId") String sessionId,
            @Param("roles") Set<com.microapproval.api.entity.WorkspaceRole> roles
    );

    @Query("""
            SELECT assignment.session.id
            FROM ReviewSessionReviewer assignment
            WHERE assignment.workspaceMember.id = :membershipId
              AND assignment.status = com.microapproval.api.entity.ReviewSessionReviewerStatus.ASSIGNED
            ORDER BY assignment.session.id ASC
            """)
    List<String> findAssignedSessionIdsByMembershipId(
            @Param("membershipId") String membershipId
    );
}
