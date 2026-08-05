package com.microapproval.api.service;

import com.microapproval.api.dto.CreateWorkspaceRequest;
import com.microapproval.api.dto.WorkspaceResponse;
import com.microapproval.api.dto.WorkspaceSummaryResponse;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, String userEmail) {
        User currentUser = requireUser(userEmail);

        Workspace workspace = Workspace.builder()
                .name(request.getName().trim())
                .description(normalizeNullableText(request.getDescription()))
                .owner(currentUser)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember ownerMembership = WorkspaceMember.builder()
                .workspace(workspace)
                .user(currentUser)
                .role(WorkspaceRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
        ownerMembership = workspaceMemberRepository.save(ownerMembership);

        return toResponse(workspace, ownerMembership);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSummaryResponse> getMyWorkspaces(String userEmail) {
        User currentUser = requireUser(userEmail);

        return workspaceMemberRepository
                .findAllWithWorkspaceByUserIdAndStatus(currentUser.getId(), MembershipStatus.ACTIVE)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(String workspaceId, String userEmail) {
        User currentUser = requireUser(userEmail);
        WorkspaceMember activeMembership =
                workspaceAccessService.requireActiveMembership(workspaceId, currentUser.getId());
        return toResponse(activeMembership.getWorkspace(), activeMembership);
    }

    private User requireUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private WorkspaceResponse toResponse(Workspace workspace, WorkspaceMember membership) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getOwner().getId(),
                membership.getRole(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt()
        );
    }

    private WorkspaceSummaryResponse toSummaryResponse(WorkspaceMember membership) {
        Workspace workspace = membership.getWorkspace();
        return new WorkspaceSummaryResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getOwner().getId(),
                membership.getRole(),
                workspace.getCreatedAt()
        );
    }
}
