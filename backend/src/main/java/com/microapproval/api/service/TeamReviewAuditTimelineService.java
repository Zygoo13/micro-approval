package com.microapproval.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microapproval.api.dto.SessionAuditChangeResponse;
import com.microapproval.api.dto.SessionAuditEventResponse;
import com.microapproval.api.dto.SessionAuditTimelineResponse;
import com.microapproval.api.dto.SessionAuditValueResponse;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.TeamReviewAuditEvent;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.InvalidOperationException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamReviewAuditTimelineService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final ObjectMapper AUDIT_JSON = new ObjectMapper();

    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final TeamReviewAuditEventRepository auditRepository;
    private final ReviewSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;

    @Transactional(readOnly = true)
    public SessionAuditTimelineResponse getTimeline(
            String workspaceId,
            String sessionId,
            int page,
            int size,
            String callerEmail
    ) {
        validatePage(page, size);
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        workspaceAccessService.requireActiveMembership(workspaceId, caller.getId());

        ReviewSession session = sessionRepository
                .findWithSubmitterAndWorkspaceByIdAndWorkspaceIdAndType(
                        sessionId,
                        workspaceId,
                        WorkspaceType.SHARED
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Shared Review Session"
                ));

        Page<TeamReviewAuditEvent> result = auditRepository.findBySessionId(
                session.getId(),
                PageRequest.of(page, size, NEWEST_FIRST)
        );
        List<SessionAuditEventResponse> events = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new SessionAuditTimelineResponse(
                session.getId(),
                events,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidOperationException("Page không được nhỏ hơn 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidOperationException("Size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
    }

    private SessionAuditEventResponse toResponse(TeamReviewAuditEvent event) {
        User actor = event.getActor();
        User target = event.getTargetUser();
        MicroDecision card = event.getDecisionCard();
        SessionAuditValueResponse oldValue = parseAllowlistedValue(event.getOldValueJson());
        SessionAuditValueResponse newValue = parseAllowlistedValue(event.getNewValueJson());

        return new SessionAuditEventResponse(
                event.getId(),
                event.getEventType(),
                actor == null ? null : actor.getId(),
                actor == null ? "Unknown user" : actor.getFullName(),
                actor == null ? null : actor.getEmail(),
                target == null ? null : target.getId(),
                target == null ? null : target.getFullName(),
                event.getTargetAssignment() == null
                        ? null : event.getTargetAssignment().getId(),
                card == null ? null : card.getId(),
                cardSummary(card),
                event.getReason(),
                oldValue == null && newValue == null
                        ? null : new SessionAuditChangeResponse(oldValue, newValue),
                event.getCreatedAt()
        );
    }

    private String cardSummary(MicroDecision card) {
        if (card == null) {
            return null;
        }
        Integer displayOrder = card.getDisplayOrder();
        return displayOrder == null
                ? "Decision Card"
                : "Decision Card #" + (displayOrder + 1);
    }

    private SessionAuditValueResponse parseAllowlistedValue(String valueJson) {
        if (valueJson == null || valueJson.isBlank()) {
            return null;
        }
        try {
            JsonNode value = AUDIT_JSON.readTree(valueJson);
            if (value == null || !value.isObject() || !hasAllowlistedField(value)) {
                return null;
            }
            return new SessionAuditValueResponse(
                    text(value, "status"),
                    text(value, "decision"),
                    text(value, "note"),
                    number(value, "assignmentVersion"),
                    number(value, "voteVersion"),
                    bool(value, "closed"),
                    dateTime(value, "closedAt"),
                    text(value, "closedByUserId"),
                    text(value, "closeReason"),
                    number(value, "lifecycleVersion"),
                    dateTime(value, "reopenedAt")
            );
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private boolean hasAllowlistedField(JsonNode value) {
        return value.has("status")
                || value.has("decision")
                || value.has("note")
                || value.has("assignmentVersion")
                || value.has("voteVersion")
                || value.has("closed")
                || value.has("closedAt")
                || value.has("closedByUserId")
                || value.has("closeReason")
                || value.has("lifecycleVersion")
                || value.has("reopenedAt");
    }

    private String text(JsonNode value, String field) {
        JsonNode node = value.get(field);
        return node == null || node.isNull() || !node.isValueNode()
                ? null : node.asText();
    }

    private Long number(JsonNode value, String field) {
        JsonNode node = value.get(field);
        return node == null || node.isNull() || !node.isIntegralNumber()
                ? null : node.longValue();
    }

    private Boolean bool(JsonNode value, String field) {
        JsonNode node = value.get(field);
        return node == null || node.isNull() || !node.isBoolean()
                ? null : node.booleanValue();
    }

    private LocalDateTime dateTime(JsonNode value, String field) {
        String text = text(value, field);
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
