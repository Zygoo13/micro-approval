package com.microapproval.api.dto;

import java.util.List;

public record SessionAuditTimelineResponse(
        String sessionId,
        List<SessionAuditEventResponse> events,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
