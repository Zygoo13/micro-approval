package com.microapproval.api.service;

import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.exception.ForbiddenOperationException;
import com.microapproval.api.exception.InvalidOperationException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public WorkspaceMember requireActiveMembership(String workspaceId, String userId) {
        return workspaceMemberRepository
                .findWithWorkspaceByWorkspaceIdAndUserIdAndStatus(
                        workspaceId,
                        userId,
                        MembershipStatus.ACTIVE
                )
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy workspace"));
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(String workspaceId, String userId) {
        return workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndStatus(
                workspaceId,
                userId,
                MembershipStatus.ACTIVE
        );
    }

    public WorkspaceMember requireOwnerOrAdminForUpdate(String workspaceId, String userId) {
        WorkspaceMember membership = workspaceMemberRepository
                .findWithWorkspaceAndUserForUpdate(workspaceId, userId)
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy workspace"));

        if (membership.getRole() != WorkspaceRole.OWNER
                && membership.getRole() != WorkspaceRole.ADMIN) {
            throw new ForbiddenOperationException("Bạn không có quyền quản lý thành viên");
        }
        return membership;
    }

    public WorkspaceMember requireOwnerOrAdmin(String workspaceId, String userId) {
        WorkspaceMember membership = requireActiveMembership(workspaceId, userId);
        if (membership.getRole() != WorkspaceRole.OWNER
                && membership.getRole() != WorkspaceRole.ADMIN) {
            throw new ForbiddenOperationException("Bạn không có quyền quản lý workspace");
        }
        return membership;
    }

    public WorkspaceMember requireSharedSessionCreator(String workspaceId, String userId) {
        WorkspaceMember membership = requireActiveMembership(workspaceId, userId);
        if (membership.getRole() != WorkspaceRole.OWNER
                && membership.getRole() != WorkspaceRole.ADMIN
                && membership.getRole() != WorkspaceRole.REVIEWER) {
            throw new ForbiddenOperationException("Bạn không có quyền tạo Shared Review Session");
        }
        return membership;
    }

    public void requireCanAssignRole(WorkspaceRole callerRole, WorkspaceRole requestedRole) {
        if (requestedRole == WorkspaceRole.OWNER) {
            throw new InvalidOperationException("Không thể tạo OWNER thứ hai");
        }
        if (callerRole == WorkspaceRole.ADMIN && !isStandardManagedRole(requestedRole)) {
            throw new ForbiddenOperationException("ADMIN không thể gán role này");
        }
    }

    public void requireCanManageTarget(
            WorkspaceRole callerRole,
            WorkspaceRole targetRole,
            WorkspaceRole requestedRole
    ) {
        if (targetRole == WorkspaceRole.OWNER) {
            throw new InvalidOperationException("Không thể thay đổi OWNER");
        }
        if (callerRole == WorkspaceRole.ADMIN
                && (!isStandardManagedRole(targetRole)
                || (requestedRole != null && !isStandardManagedRole(requestedRole)))) {
            throw new ForbiddenOperationException("ADMIN không thể quản lý membership này");
        }
    }

    private boolean isStandardManagedRole(WorkspaceRole role) {
        return role == WorkspaceRole.REVIEWER
                || role == WorkspaceRole.MEMBER
                || role == WorkspaceRole.AUDITOR;
    }
}
