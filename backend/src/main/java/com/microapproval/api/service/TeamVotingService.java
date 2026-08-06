package com.microapproval.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microapproval.api.dto.SessionVotingResponse;
import com.microapproval.api.dto.UpsertTeamVoteRequest;
import com.microapproval.api.entity.DecisionCardVote;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.ReviewSessionReviewer;
import com.microapproval.api.entity.ReviewSessionReviewerStatus;
import com.microapproval.api.entity.TeamReviewAuditEvent;
import com.microapproval.api.entity.TeamReviewAuditEventType;
import com.microapproval.api.entity.TeamVoteDecision;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.exception.ForbiddenOperationException;
import com.microapproval.api.exception.InvalidOperationException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.DecisionCardVoteRepository;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TeamVotingService {

    private static final EnumSet<WorkspaceRole> ELIGIBLE_ROLES =
            EnumSet.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.REVIEWER);
    private static final ObjectMapper AUDIT_JSON = new ObjectMapper();

    private final ReviewSessionRepository sessionRepository;
    private final MicroDecisionRepository decisionRepository;
    private final ReviewSessionReviewerRepository reviewerRepository;
    private final DecisionCardVoteRepository voteRepository;
    private final TeamReviewAuditEventRepository auditRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final TeamReviewAggregationService aggregationService;

    @Transactional(readOnly = true)
    public SessionVotingResponse getSessionVotes(
            String workspaceId,
            String sessionId,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireActiveMembership(workspaceId, caller.getId());
        ReviewSession session = requireSharedSession(workspaceId, sessionId);
        return aggregationService.currentResponse(session);
    }

    @Transactional
    public SessionVotingResponse upsertOwnVote(
            String workspaceId,
            String sessionId,
            String cardId,
            UpsertTeamVoteRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        WorkspaceMember callerMembership = workspaceAccessService
                .requireActiveMembership(workspaceId, caller.getId());
        ReviewSession session = requireSharedSessionForUpdate(workspaceId, sessionId);
        requireOpen(session);
        MicroDecision card = decisionRepository
                .findByIdAndSessionIdForUpdate(cardId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Shared Decision Card"
                ));
        ReviewSessionReviewer assignment = reviewerRepository
                .findBySessionAndUserForUpdate(sessionId, caller.getId())
                .orElseThrow(() -> new ForbiddenOperationException(
                        "Bạn chưa được phân công reviewer"
                ));
        requireEligibleAssignment(assignment, callerMembership);
        validateVote(request);

        DecisionCardVote vote = voteRepository
                .findByCardAndAssignmentForUpdate(cardId, assignment.getId())
                .map(existing -> updateVote(existing, assignment, request, caller, session, card))
                .orElseGet(() -> createVote(assignment, request, caller, session, card));

        if (vote.getId() == null) {
            throw new ConflictException("Không thể lưu Team vote");
        }
        return aggregationService.recalculate(session);
    }

    private DecisionCardVote createVote(
            ReviewSessionReviewer assignment,
            UpsertTeamVoteRequest request,
            User caller,
            ReviewSession session,
            MicroDecision card
    ) {
        if (request.version() != null) {
            throw new ConflictException("Vote chưa tồn tại; không được gửi version cập nhật");
        }
        DecisionCardVote vote = DecisionCardVote.builder()
                .decisionCard(card)
                .reviewerAssignment(assignment)
                .decision(request.decision())
                .note(request.note())
                .assignmentVersion(assignment.getVersion())
                .build();
        try {
            voteRepository.saveAndFlush(vote);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Vote đã được tạo bởi request khác");
        }
        appendAudit(
                session,
                card,
                assignment,
                caller,
                TeamReviewAuditEventType.VOTE_CREATED,
                null,
                voteSnapshot(vote)
        );
        return vote;
    }

    private DecisionCardVote updateVote(
            DecisionCardVote vote,
            ReviewSessionReviewer assignment,
            UpsertTeamVoteRequest request,
            User caller,
            ReviewSession session,
            MicroDecision card
    ) {
        if (request.version() == null || !request.version().equals(vote.getVersion())) {
            throw new ConflictException("Vote đã thay đổi; hãy tải lại version mới nhất");
        }
        boolean assignmentIsCurrent = Objects.equals(
                vote.getAssignmentVersion(),
                assignment.getVersion()
        );
        if (assignmentIsCurrent
                && vote.getDecision() == request.decision()
                && Objects.equals(vote.getNote(), request.note())) {
            return vote;
        }

        String oldValue = voteSnapshot(vote);
        vote.setDecision(request.decision());
        vote.setNote(request.note());
        vote.setAssignmentVersion(assignment.getVersion());
        try {
            voteRepository.saveAndFlush(vote);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException("Vote đã thay đổi; hãy tải lại version mới nhất");
        }
        appendAudit(
                session,
                card,
                assignment,
                caller,
                TeamReviewAuditEventType.VOTE_UPDATED,
                oldValue,
                voteSnapshot(vote)
        );
        return vote;
    }

    private void requireEligibleAssignment(
            ReviewSessionReviewer assignment,
            WorkspaceMember callerMembership
    ) {
        WorkspaceMember assignedMembership = assignment.getWorkspaceMember();
        if (assignment.getStatus() != ReviewSessionReviewerStatus.ASSIGNED
                || callerMembership.getStatus() != MembershipStatus.ACTIVE
                || assignedMembership.getStatus() != MembershipStatus.ACTIVE
                || !ELIGIBLE_ROLES.contains(callerMembership.getRole())
                || !ELIGIBLE_ROLES.contains(assignedMembership.getRole())) {
            throw new ForbiddenOperationException("Reviewer assignment không còn hợp lệ");
        }
    }

    private void validateVote(UpsertTeamVoteRequest request) {
        if (request.decision() == TeamVoteDecision.REJECTED && request.note() == null) {
            throw new InvalidOperationException("REJECTED bắt buộc có ghi chú");
        }
    }

    private void appendAudit(
            ReviewSession session,
            MicroDecision card,
            ReviewSessionReviewer assignment,
            User caller,
            TeamReviewAuditEventType eventType,
            String oldValue,
            String newValue
    ) {
        auditRepository.save(TeamReviewAuditEvent.builder()
                .session(session)
                .actor(caller)
                .eventType(eventType)
                .targetUser(caller)
                .targetAssignment(assignment)
                .decisionCard(card)
                .oldValueJson(oldValue)
                .newValueJson(newValue)
                .build());
    }

    private String voteSnapshot(DecisionCardVote vote) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("decision", vote.getDecision());
        snapshot.put("note", vote.getNote());
        snapshot.put("assignmentVersion", vote.getAssignmentVersion());
        snapshot.put("voteVersion", vote.getVersion());
        try {
            return AUDIT_JSON.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo audit snapshot", exception);
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

    private void requireOpen(ReviewSession session) {
        if (session.getClosedAt() != null) {
            throw new ConflictException("Shared Review Session đã đóng; không thể thay đổi vote");
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }
}
