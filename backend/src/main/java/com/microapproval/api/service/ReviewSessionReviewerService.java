package com.microapproval.api.service;

import com.microapproval.api.dto.AssignSessionReviewerRequest;
import com.microapproval.api.dto.RemoveSessionReviewerRequest;
import com.microapproval.api.dto.SessionReviewerResponse;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.ReviewSessionReviewer;
import com.microapproval.api.entity.ReviewSessionReviewerStatus;
import com.microapproval.api.entity.TeamReviewAuditEvent;
import com.microapproval.api.entity.TeamReviewAuditEventType;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.exception.InvalidOperationException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
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
public class ReviewSessionReviewerService {

    private static final EnumSet<WorkspaceRole> ELIGIBLE_ROLES =
            EnumSet.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.REVIEWER);

    private final ReviewSessionReviewerRepository reviewerRepository;
    private final TeamReviewAuditEventRepository auditEventRepository;
    private final ReviewSessionRepository sessionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final TeamReviewAggregationService aggregationService;

    @Transactional(readOnly = true)
    public List<SessionReviewerResponse> getReviewers(
            String workspaceId,
            String sessionId,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireActiveMembership(workspaceId, caller.getId());
        requireSharedSession(workspaceId, sessionId);

        return reviewerRepository
                .findAllWithPeopleBySessionIdAndStatus(
                        sessionId,
                        ReviewSessionReviewerStatus.ASSIGNED
                )
                .stream()
                .map(SessionReviewerResponse::from)
                .toList();
    }

    @Transactional
    public SessionReviewerResponse assignReviewer(
            String workspaceId,
            String sessionId,
            AssignSessionReviewerRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        ReviewSession session = requireSharedSessionForUpdate(workspaceId, sessionId);
        WorkspaceMember target = workspaceMemberRepository
                .findWithWorkspaceAndUserByIdForUpdate(workspaceId, request.workspaceMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên"));
        requireEligible(target);

        SessionReviewerResponse response = reviewerRepository
                .findBySessionAndMemberForUpdate(sessionId, target.getId())
                .map(existing -> reactivate(existing, caller))
                .orElseGet(() -> createAssignment(session, target, caller));
        aggregationService.recalculate(session);
        return response;
    }

    @Transactional
    public SessionReviewerResponse removeReviewer(
            String workspaceId,
            String sessionId,
            String assignmentId,
            RemoveSessionReviewerRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        ReviewSession session = requireSharedSessionForUpdate(workspaceId, sessionId);
        ReviewSessionReviewer assignment = reviewerRepository
                .findByIdAndSessionForUpdate(assignmentId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy reviewer assignment"
                ));

        if (assignment.getStatus() != ReviewSessionReviewerStatus.ASSIGNED) {
            throw new ConflictException("Reviewer assignment đã bị remove");
        }

        assignment.setStatus(ReviewSessionReviewerStatus.REMOVED);
        assignment.setRemovedAt(LocalDateTime.now());
        assignment.setRemovedBy(caller);
        assignment.setRemovalReason(request.reason());
        reviewerRepository.saveAndFlush(assignment);
        appendAudit(
                session,
                caller,
                assignment,
                TeamReviewAuditEventType.REVIEWER_REMOVED,
                "{\"status\":\"ASSIGNED\"}",
                "{\"status\":\"REMOVED\"}",
                request.reason()
        );
        aggregationService.recalculate(session);
        return SessionReviewerResponse.from(assignment);
    }

    /**
     * Soft-removes every active reviewer assignment when a membership ceases to be
     * eligible. The existing assignment and votes remain available for audit.
     */
    @Transactional
    public void removeAssignmentsForEligibilityLoss(
            WorkspaceMember membership,
            User actor,
            String reason
    ) {
        List<String> sessionIds = reviewerRepository
                .findAssignedSessionIdsByMembershipId(membership.getId());
        for (String sessionId : sessionIds) {
            ReviewSession session = sessionRepository
                    .findByWorkspaceAndTypeForUpdate(
                            sessionId,
                            membership.getWorkspace().getId(),
                            WorkspaceType.SHARED
                    )
                    .orElse(null);
            if (session == null) {
                continue;
            }
            ReviewSessionReviewer assignment = reviewerRepository
                    .findBySessionAndMemberForUpdate(sessionId, membership.getId())
                    .orElse(null);
            if (assignment == null
                    || assignment.getStatus() != ReviewSessionReviewerStatus.ASSIGNED) {
                continue;
            }

            assignment.setStatus(ReviewSessionReviewerStatus.REMOVED);
            assignment.setRemovedAt(LocalDateTime.now());
            assignment.setRemovedBy(actor);
            assignment.setRemovalReason(reason);
            reviewerRepository.saveAndFlush(assignment);
            appendAudit(
                    session,
                    actor,
                    assignment,
                    TeamReviewAuditEventType.REVIEWER_REMOVED,
                    "{\"status\":\"ASSIGNED\"}",
                    "{\"status\":\"REMOVED\"}",
                    reason
            );
            aggregationService.recalculate(session);
        }
    }

    private SessionReviewerResponse createAssignment(
            ReviewSession session,
            WorkspaceMember target,
            User caller
    ) {
        ReviewSessionReviewer assignment = ReviewSessionReviewer.builder()
                .session(session)
                .workspaceMember(target)
                .assignedBy(caller)
                .status(ReviewSessionReviewerStatus.ASSIGNED)
                .build();
        try {
            reviewerRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Reviewer đã được assign cho session");
        }
        appendAudit(
                session,
                caller,
                assignment,
                TeamReviewAuditEventType.REVIEWER_ASSIGNED,
                null,
                "{\"status\":\"ASSIGNED\"}",
                null
        );
        return SessionReviewerResponse.from(assignment);
    }

    private SessionReviewerResponse reactivate(
            ReviewSessionReviewer assignment,
            User caller
    ) {
        if (assignment.getStatus() == ReviewSessionReviewerStatus.ASSIGNED) {
            throw new ConflictException("Reviewer đã được assign cho session");
        }

        assignment.setStatus(ReviewSessionReviewerStatus.ASSIGNED);
        assignment.setAssignedBy(caller);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setRemovedAt(null);
        assignment.setRemovedBy(null);
        assignment.setRemovalReason(null);
        reviewerRepository.saveAndFlush(assignment);
        appendAudit(
                assignment.getSession(),
                caller,
                assignment,
                TeamReviewAuditEventType.REVIEWER_REACTIVATED,
                "{\"status\":\"REMOVED\"}",
                "{\"status\":\"ASSIGNED\"}",
                null
        );
        return SessionReviewerResponse.from(assignment);
    }

    private void appendAudit(
            ReviewSession session,
            User caller,
            ReviewSessionReviewer assignment,
            TeamReviewAuditEventType eventType,
            String oldValue,
            String newValue,
            String reason
    ) {
        auditEventRepository.save(TeamReviewAuditEvent.builder()
                .session(session)
                .actor(caller)
                .eventType(eventType)
                .targetUser(assignment.getWorkspaceMember().getUser())
                .targetAssignment(assignment)
                .oldValueJson(oldValue)
                .newValueJson(newValue)
                .reason(reason)
                .build());
    }

    private void requireEligible(WorkspaceMember membership) {
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new InvalidOperationException("Chỉ có thể assign thành viên ACTIVE");
        }
        if (!ELIGIBLE_ROLES.contains(membership.getRole())) {
            throw new InvalidOperationException(
                    "Chỉ OWNER, ADMIN hoặc REVIEWER có thể được assign"
            );
        }
    }

    private ReviewSession requireSharedSession(String workspaceId, String sessionId) {
        return sessionRepository
                .findWithSubmitterAndWorkspaceByIdAndWorkspaceIdAndType(
                        sessionId,
                        workspaceId,
                        WorkspaceType.SHARED
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Shared Review Session"
                ));
    }

    private ReviewSession requireSharedSessionForUpdate(String workspaceId, String sessionId) {
        return sessionRepository
                .findByWorkspaceAndTypeForUpdate(sessionId, workspaceId, WorkspaceType.SHARED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Shared Review Session"
                ));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }
}
