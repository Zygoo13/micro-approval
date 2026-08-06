package com.microapproval.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microapproval.api.dto.CloseSharedReviewSessionRequest;
import com.microapproval.api.dto.SharedReviewSessionLifecycleResponse;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.TeamReviewAuditEvent;
import com.microapproval.api.entity.TeamReviewAuditEventType;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SharedReviewSessionLifecycleService {

    private static final ObjectMapper AUDIT_JSON = new ObjectMapper();

    private final ReviewSessionRepository sessionRepository;
    private final TeamReviewAuditEventRepository auditRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final TeamReviewAggregationService aggregationService;

    @Transactional
    public SharedReviewSessionLifecycleResponse closeSession(
            String workspaceId,
            String sessionId,
            CloseSharedReviewSessionRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        ReviewSession session = requireSharedSessionForUpdate(workspaceId, sessionId);
        if (session.getClosedAt() != null) {
            throw new ConflictException("Shared Review Session đã được đóng");
        }

        String oldValue = lifecycleSnapshot(session, null);
        aggregationService.recalculate(session);
        if (session.getStatus() != SessionStatus.APPROVED
                && session.getStatus() != SessionStatus.REJECTED) {
            throw new ConflictException("Session chưa có kết quả APPROVED hoặc REJECTED để đóng");
        }

        LocalDateTime closedAt = LocalDateTime.now();
        session.setClosedAt(closedAt);
        session.setClosedBy(caller);
        session.setCloseReason(request == null ? null : request.reason());
        sessionRepository.saveAndFlush(session);
        appendAudit(
                session,
                caller,
                TeamReviewAuditEventType.SESSION_CLOSED,
                oldValue,
                lifecycleSnapshot(session, null),
                session.getCloseReason()
        );
        return SharedReviewSessionLifecycleResponse.from(session);
    }

    @Transactional
    public SharedReviewSessionLifecycleResponse reopenSession(
            String workspaceId,
            String sessionId,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireOwnerOrAdminForUpdate(workspaceId, caller.getId());
        ReviewSession session = requireSharedSessionForUpdate(workspaceId, sessionId);
        if (session.getClosedAt() == null) {
            throw new ConflictException("Shared Review Session chưa được đóng");
        }

        String oldValue = lifecycleSnapshot(session, null);
        session.setClosedAt(null);
        session.setClosedBy(null);
        session.setCloseReason(null);
        aggregationService.recalculate(session);
        LocalDateTime reopenedAt = LocalDateTime.now();
        sessionRepository.saveAndFlush(session);
        appendAudit(
                session,
                caller,
                TeamReviewAuditEventType.SESSION_REOPENED,
                oldValue,
                lifecycleSnapshot(session, reopenedAt),
                null
        );
        return SharedReviewSessionLifecycleResponse.from(session);
    }

    private void appendAudit(
            ReviewSession session,
            User caller,
            TeamReviewAuditEventType eventType,
            String oldValue,
            String newValue,
            String reason
    ) {
        auditRepository.save(TeamReviewAuditEvent.builder()
                .session(session)
                .actor(caller)
                .eventType(eventType)
                .oldValueJson(oldValue)
                .newValueJson(newValue)
                .reason(reason)
                .build());
    }

    private String lifecycleSnapshot(ReviewSession session, LocalDateTime reopenedAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", session.getStatus());
        snapshot.put("closed", session.getClosedAt() != null);
        snapshot.put("closedAt", toText(session.getClosedAt()));
        snapshot.put("closedByUserId", session.getClosedBy() == null
                ? null : session.getClosedBy().getId());
        snapshot.put("closeReason", session.getCloseReason());
        snapshot.put("lifecycleVersion", session.getLifecycleVersion());
        if (reopenedAt != null) {
            snapshot.put("reopenedAt", reopenedAt.toString());
        }
        try {
            return AUDIT_JSON.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo lifecycle audit snapshot", exception);
        }
    }

    private String toText(LocalDateTime value) {
        return value == null ? null : value.toString();
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
