package com.microapproval.api.repository;

import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.WorkspaceMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, String> {

    boolean existsByWorkspaceIdAndUserId(String workspaceId, String userId);

    boolean existsByWorkspaceIdAndUserIdAndStatus(
            String workspaceId,
            String userId,
            MembershipStatus status
    );

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, String userId);

    @Query("""
            SELECT member
            FROM WorkspaceMember member
            JOIN FETCH member.user
            WHERE member.workspace.id = :workspaceId
              AND member.status IN :statuses
            ORDER BY
              CASE WHEN member.role = com.microapproval.api.entity.WorkspaceRole.OWNER
                   THEN 0 ELSE 1 END,
              member.user.fullName ASC
            """)
    List<WorkspaceMember> findAllWithUserByWorkspaceIdAndStatusIn(
            @Param("workspaceId") String workspaceId,
            @Param("statuses") Set<MembershipStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT member
            FROM WorkspaceMember member
            JOIN FETCH member.workspace workspace
            JOIN FETCH member.user
            WHERE workspace.id = :workspaceId
              AND member.user.id = :userId
            """)
    Optional<WorkspaceMember> findWithWorkspaceAndUserForUpdate(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT member
            FROM WorkspaceMember member
            JOIN FETCH member.workspace workspace
            JOIN FETCH member.user
            WHERE workspace.id = :workspaceId
              AND member.id = :memberId
            """)
    Optional<WorkspaceMember> findWithWorkspaceAndUserByIdForUpdate(
            @Param("workspaceId") String workspaceId,
            @Param("memberId") String memberId
    );

    @Query("""
            SELECT member
            FROM WorkspaceMember member
            JOIN FETCH member.workspace workspace
            JOIN FETCH workspace.owner
            WHERE member.user.id = :userId
              AND member.status = :status
            ORDER BY workspace.createdAt DESC
            """)
    List<WorkspaceMember> findAllWithWorkspaceByUserIdAndStatus(
            @Param("userId") String userId,
            @Param("status") MembershipStatus status
    );

    @Query("""
            SELECT member
            FROM WorkspaceMember member
            JOIN FETCH member.workspace workspace
            JOIN FETCH workspace.owner
            WHERE workspace.id = :workspaceId
              AND member.user.id = :userId
              AND member.status = :status
            """)
    Optional<WorkspaceMember> findWithWorkspaceByWorkspaceIdAndUserIdAndStatus(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId,
            @Param("status") MembershipStatus status
    );
}
