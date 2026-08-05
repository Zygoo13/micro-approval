package com.microapproval.api.service;

import com.microapproval.api.dto.CreateWorkspaceInvitationRequest;
import com.microapproval.api.dto.MyWorkspaceInvitationResponse;
import com.microapproval.api.dto.WorkspaceInvitationResponse;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.WorkspaceInvitation;
import com.microapproval.api.entity.WorkspaceInvitationStatus;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.exception.GoneException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceInvitationRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceInvitationService {

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final Clock clock;

    @Value("${app.workspace-invitations.expiration-days}")
    private long expirationDays;

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> getWorkspaceInvitations(
            String workspaceId,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireOwnerOrAdmin(workspaceId, caller.getId());
        LocalDateTime now = now();

        return invitationRepository
                .findAllWithWorkspaceAndInviterByWorkspaceId(workspaceId)
                .stream()
                .map(invitation -> WorkspaceInvitationResponse.from(
                        invitation,
                        effectiveStatus(invitation, now)
                ))
                .toList();
    }

    @Transactional
    public WorkspaceInvitationResponse createInvitation(
            String workspaceId,
            CreateWorkspaceInvitationRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        WorkspaceMember callerMembership =
                workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        workspaceAccessService.requireCanAssignRole(callerMembership.getRole(), request.role());

        String normalizedEmail = normalizeEmail(request.email());
        LocalDateTime now = now();
        invitationRepository.findByWorkspaceIdAndEmailAndStatusForUpdate(
                        workspaceId,
                        normalizedEmail,
                        WorkspaceInvitationStatus.PENDING
                )
                .ifPresent(existing -> expireOrRejectDuplicate(existing, now));
        invitationRepository.flush();
        rejectExistingMembership(workspaceId, normalizedEmail);

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .id(UUID.randomUUID().toString())
                .workspace(callerMembership.getWorkspace())
                .email(normalizedEmail)
                .role(request.role())
                .status(WorkspaceInvitationStatus.PENDING)
                .invitedBy(caller)
                .expiresAt(now.plusDays(expirationDays))
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Đã có invitation PENDING cho email này");
        }
        return WorkspaceInvitationResponse.from(invitation, invitation.getStatus());
    }

    @Transactional(noRollbackFor = GoneException.class)
    public WorkspaceInvitationResponse revokeInvitation(
            String workspaceId,
            String invitationId,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        WorkspaceMember callerMembership =
                workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        WorkspaceInvitation invitation = invitationRepository
                .findWithWorkspaceAndInviterByWorkspaceIdAndIdForUpdate(workspaceId, invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy invitation"));

        ensurePendingAndNotExpired(invitation);
        workspaceAccessService.requireCanManageTarget(
                callerMembership.getRole(),
                invitation.getRole(),
                null
        );
        transition(invitation, WorkspaceInvitationStatus.REVOKED);
        return WorkspaceInvitationResponse.from(invitation, invitation.getStatus());
    }

    @Transactional(readOnly = true)
    public List<MyWorkspaceInvitationResponse> getMyInvitations(String callerEmail) {
        String normalizedEmail = normalizeEmail(callerEmail);
        LocalDateTime now = now();
        return invitationRepository.findAllWithWorkspaceAndInviterByEmail(normalizedEmail)
                .stream()
                .map(invitation -> MyWorkspaceInvitationResponse.from(
                        invitation,
                        effectiveStatus(invitation, now)
                ))
                .toList();
    }

    @Transactional(noRollbackFor = GoneException.class)
    public WorkspaceInvitationResponse acceptInvitation(
            String invitationId,
            String callerEmail
    ) {
        User recipient = requireUser(callerEmail);
        WorkspaceInvitation invitation = requireRecipientInvitationForUpdate(
                invitationId,
                recipient.getEmail()
        );
        ensurePendingAndNotExpired(invitation);

        WorkspaceMember membership = memberRepository
                .findWithWorkspaceAndUserForUpdate(
                        invitation.getWorkspace().getId(),
                        recipient.getId()
                )
                .map(existing -> reactivateOrReject(existing, invitation))
                .orElseGet(() -> createMembership(invitation, recipient));

        membership.setRole(invitation.getRole());
        transition(invitation, WorkspaceInvitationStatus.ACCEPTED);
        return WorkspaceInvitationResponse.from(invitation, invitation.getStatus());
    }

    @Transactional(noRollbackFor = GoneException.class)
    public WorkspaceInvitationResponse rejectInvitation(
            String invitationId,
            String callerEmail
    ) {
        WorkspaceInvitation invitation = requireRecipientInvitationForUpdate(
                invitationId,
                callerEmail
        );
        ensurePendingAndNotExpired(invitation);
        transition(invitation, WorkspaceInvitationStatus.REJECTED);
        return WorkspaceInvitationResponse.from(invitation, invitation.getStatus());
    }

    private void rejectExistingMembership(String workspaceId, String normalizedEmail) {
        userRepository.findByEmail(normalizedEmail)
                .flatMap(user -> memberRepository.findWithWorkspaceAndUserForUpdate(
                        workspaceId,
                        user.getId()
                ))
                .filter(member -> member.getStatus() != MembershipStatus.REMOVED)
                .ifPresent(member -> {
                    throw new ConflictException("User đã có membership trong workspace");
                });
    }

    private void expireOrRejectDuplicate(WorkspaceInvitation invitation, LocalDateTime now) {
        if (isExpired(invitation, now)) {
            invitation.setStatus(WorkspaceInvitationStatus.EXPIRED);
            invitation.setUpdatedAt(now);
            return;
        }
        throw new ConflictException("Đã có invitation PENDING cho email này");
    }

    private WorkspaceInvitation requireRecipientInvitationForUpdate(
            String invitationId,
            String callerEmail
    ) {
        WorkspaceInvitation invitation = invitationRepository
                .findWithWorkspaceAndInviterByIdForUpdate(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy invitation"));
        if (!invitation.getEmail().equals(normalizeEmail(callerEmail))) {
            throw new ResourceNotFoundException("Không tìm thấy invitation");
        }
        return invitation;
    }

    private WorkspaceMember reactivateOrReject(
            WorkspaceMember membership,
            WorkspaceInvitation invitation
    ) {
        if (membership.getStatus() != MembershipStatus.REMOVED) {
            throw new ConflictException("User đã có membership trong workspace");
        }
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setRole(invitation.getRole());
        membership.setJoinedAt(now());
        return membership;
    }

    private WorkspaceMember createMembership(WorkspaceInvitation invitation, User recipient) {
        WorkspaceMember membership = WorkspaceMember.builder()
                .workspace(invitation.getWorkspace())
                .user(recipient)
                .role(invitation.getRole())
                .status(MembershipStatus.ACTIVE)
                .joinedAt(now())
                .build();
        try {
            return memberRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("User đã có membership trong workspace");
        }
    }

    private void ensurePendingAndNotExpired(WorkspaceInvitation invitation) {
        if (invitation.getStatus() != WorkspaceInvitationStatus.PENDING) {
            throw new ConflictException("Invitation đã được xử lý");
        }
        LocalDateTime now = now();
        if (isExpired(invitation, now)) {
            invitation.setStatus(WorkspaceInvitationStatus.EXPIRED);
            invitation.setUpdatedAt(now);
            throw new GoneException("Invitation đã hết hạn");
        }
    }

    private void transition(
            WorkspaceInvitation invitation,
            WorkspaceInvitationStatus status
    ) {
        LocalDateTime now = now();
        invitation.setStatus(status);
        invitation.setRespondedAt(now);
        invitation.setUpdatedAt(now);
    }

    private WorkspaceInvitationStatus effectiveStatus(
            WorkspaceInvitation invitation,
            LocalDateTime now
    ) {
        return invitation.getStatus() == WorkspaceInvitationStatus.PENDING
                && isExpired(invitation, now)
                ? WorkspaceInvitationStatus.EXPIRED
                : invitation.getStatus();
    }

    private boolean isExpired(WorkspaceInvitation invitation, LocalDateTime now) {
        return !invitation.getExpiresAt().isAfter(now);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
