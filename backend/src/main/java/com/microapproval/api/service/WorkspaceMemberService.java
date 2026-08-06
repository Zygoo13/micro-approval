package com.microapproval.api.service;

import com.microapproval.api.dto.AddWorkspaceMemberRequest;
import com.microapproval.api.dto.UpdateWorkspaceMemberRoleRequest;
import com.microapproval.api.dto.WorkspaceMemberResponse;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.exception.InvalidOperationException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceMemberService {

    private static final EnumSet<MembershipStatus> VISIBLE_STATUSES =
            EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.PENDING);
    private static final EnumSet<WorkspaceRole> REVIEWER_ELIGIBLE_ROLES =
            EnumSet.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.REVIEWER);

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ReviewSessionReviewerService reviewerService;

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> getMembers(String workspaceId, String callerEmail) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireActiveMembership(workspaceId, caller.getId());

        return workspaceMemberRepository
                .findAllWithUserByWorkspaceIdAndStatusIn(workspaceId, VISIBLE_STATUSES)
                .stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse addMember(
            String workspaceId,
            AddWorkspaceMemberRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        WorkspaceMember callerMembership =
                workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        workspaceAccessService.requireCanAssignRole(callerMembership.getRole(), request.getRole());

        User targetUser = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        return workspaceMemberRepository
                .findWithWorkspaceAndUserForUpdate(workspaceId, targetUser.getId())
                .map(existing -> reactivateOrReject(existing, request))
                .orElseGet(() -> createMembership(callerMembership, targetUser, request));
    }

    @Transactional
    public WorkspaceMemberResponse changeMemberRole(
            String workspaceId,
            String memberId,
            UpdateWorkspaceMemberRoleRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        WorkspaceMember callerMembership =
                workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        workspaceAccessService.requireCanAssignRole(callerMembership.getRole(), request.getRole());

        WorkspaceMember target = requireTargetForUpdate(workspaceId, memberId);
        if (target.getStatus() == MembershipStatus.REMOVED) {
            throw new ConflictException("Membership đã bị remove; hãy add lại user để kích hoạt");
        }
        workspaceAccessService.requireCanManageTarget(
                callerMembership.getRole(),
                target.getRole(),
                request.getRole()
        );

        WorkspaceRole previousRole = target.getRole();
        if (previousRole != request.getRole()) {
            target.setRole(request.getRole());
            if (REVIEWER_ELIGIBLE_ROLES.contains(previousRole)
                    && !REVIEWER_ELIGIBLE_ROLES.contains(request.getRole())) {
                reviewerService.removeAssignmentsForEligibilityLoss(
                        target,
                        caller,
                        "Workspace role is no longer eligible for reviewer assignment"
                );
            }
        }
        return WorkspaceMemberResponse.from(target);
    }

    @Transactional
    public void removeMember(String workspaceId, String memberId, String callerEmail) {
        User caller = requireUser(callerEmail);
        WorkspaceMember callerMembership =
                workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        WorkspaceMember target = requireTargetForUpdate(workspaceId, memberId);

        if (target.getUser().getId().equals(caller.getId())) {
            throw new InvalidOperationException("Không hỗ trợ tự rời workspace qua endpoint này");
        }
        if (target.getStatus() == MembershipStatus.REMOVED) {
            throw new ConflictException("Membership đã bị remove");
        }
        workspaceAccessService.requireCanManageTarget(
                callerMembership.getRole(),
                target.getRole(),
                null
        );

        target.setStatus(MembershipStatus.REMOVED);
        reviewerService.removeAssignmentsForEligibilityLoss(
                target,
                caller,
                "Workspace membership was removed"
        );
    }

    private WorkspaceMemberResponse reactivateOrReject(
            WorkspaceMember existing,
            AddWorkspaceMemberRequest request
    ) {
        if (existing.getStatus() != MembershipStatus.REMOVED) {
            throw new ConflictException("User đã có membership trong workspace");
        }

        existing.setRole(request.getRole());
        existing.setStatus(MembershipStatus.ACTIVE);
        existing.setJoinedAt(LocalDateTime.now());
        return WorkspaceMemberResponse.from(existing);
    }

    private WorkspaceMemberResponse createMembership(
            WorkspaceMember callerMembership,
            User targetUser,
            AddWorkspaceMemberRequest request
    ) {
        WorkspaceMember membership = WorkspaceMember.builder()
                .workspace(callerMembership.getWorkspace())
                .user(targetUser)
                .role(request.getRole())
                .status(MembershipStatus.ACTIVE)
                .build();
        try {
            workspaceMemberRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("User đã có membership trong workspace");
        }
        return WorkspaceMemberResponse.from(membership);
    }

    private WorkspaceMember requireTargetForUpdate(String workspaceId, String memberId) {
        return workspaceMemberRepository
                .findWithWorkspaceAndUserByIdForUpdate(workspaceId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên"));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }
}
