package com.microapproval.api.repository;

import com.microapproval.api.entity.WorkspaceInvitation;
import com.microapproval.api.entity.WorkspaceInvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, String> {

    @Query("""
            SELECT invitation
            FROM WorkspaceInvitation invitation
            JOIN FETCH invitation.workspace
            JOIN FETCH invitation.invitedBy
            WHERE invitation.workspace.id = :workspaceId
            ORDER BY invitation.createdAt DESC
            """)
    List<WorkspaceInvitation> findAllWithWorkspaceAndInviterByWorkspaceId(
            @Param("workspaceId") String workspaceId
    );

    @Query("""
            SELECT invitation
            FROM WorkspaceInvitation invitation
            JOIN FETCH invitation.workspace
            JOIN FETCH invitation.invitedBy
            WHERE invitation.email = :email
            ORDER BY invitation.createdAt DESC
            """)
    List<WorkspaceInvitation> findAllWithWorkspaceAndInviterByEmail(
            @Param("email") String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM WorkspaceInvitation invitation
            JOIN FETCH invitation.workspace
            JOIN FETCH invitation.invitedBy
            WHERE invitation.id = :invitationId
            """)
    Optional<WorkspaceInvitation> findWithWorkspaceAndInviterByIdForUpdate(
            @Param("invitationId") String invitationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM WorkspaceInvitation invitation
            JOIN FETCH invitation.workspace
            JOIN FETCH invitation.invitedBy
            WHERE invitation.workspace.id = :workspaceId
              AND invitation.id = :invitationId
            """)
    Optional<WorkspaceInvitation> findWithWorkspaceAndInviterByWorkspaceIdAndIdForUpdate(
            @Param("workspaceId") String workspaceId,
            @Param("invitationId") String invitationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM WorkspaceInvitation invitation
            JOIN FETCH invitation.workspace
            JOIN FETCH invitation.invitedBy
            WHERE invitation.workspace.id = :workspaceId
              AND invitation.email = :email
              AND invitation.status = :status
            """)
    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailAndStatusForUpdate(
            @Param("workspaceId") String workspaceId,
            @Param("email") String email,
            @Param("status") WorkspaceInvitationStatus status
    );
}
