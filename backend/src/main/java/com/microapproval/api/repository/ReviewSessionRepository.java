package com.microapproval.api.repository;


import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.WorkspaceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewSessionRepository extends JpaRepository<ReviewSession, String> {
    // Tìm các session thuộc Personal Workspace của một user cụ thể (Bảo mật quyền riêng tư - BR-04, BR-10 trong thiet-ke-ui-va-chuan-bi-code)
    List<ReviewSession> findBySubmittedByIdAndWorkspaceTypeOrderByCreatedAtDesc(String userId, WorkspaceType workspaceType);
    Optional<ReviewSession> findByIdAndSubmittedByIdAndWorkspaceType(String id, String userId, WorkspaceType workspaceType);
    List<ReviewSession> findAllBySubmittedByIdAndWorkspaceTypeOrderByCreatedAtDesc(String userId, WorkspaceType workspaceType);

    @Query("""
            SELECT session
            FROM ReviewSession session
            JOIN FETCH session.submittedBy
            LEFT JOIN FETCH session.closedBy
            WHERE session.workspace.id = :workspaceId
              AND session.workspaceType = :workspaceType
            ORDER BY session.createdAt DESC
            """)
    List<ReviewSession> findAllWithSubmitterByWorkspaceIdAndType(
            @Param("workspaceId") String workspaceId,
            @Param("workspaceType") WorkspaceType workspaceType
    );

    @Query("""
            SELECT session
            FROM ReviewSession session
            JOIN FETCH session.submittedBy
            JOIN FETCH session.workspace
            LEFT JOIN FETCH session.closedBy
            WHERE session.id = :sessionId
              AND session.workspace.id = :workspaceId
              AND session.workspaceType = :workspaceType
            """)
    Optional<ReviewSession> findWithSubmitterAndWorkspaceByIdAndWorkspaceIdAndType(
            @Param("sessionId") String sessionId,
            @Param("workspaceId") String workspaceId,
            @Param("workspaceType") WorkspaceType workspaceType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM ReviewSession session
            JOIN FETCH session.workspace
            WHERE session.id = :sessionId
              AND session.workspace.id = :workspaceId
              AND session.workspaceType = :workspaceType
            """)
    Optional<ReviewSession> findByWorkspaceAndTypeForUpdate(
            @Param("sessionId") String sessionId,
            @Param("workspaceId") String workspaceId,
            @Param("workspaceType") WorkspaceType workspaceType
    );

}
