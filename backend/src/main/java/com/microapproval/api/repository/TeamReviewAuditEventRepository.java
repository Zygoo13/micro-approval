package com.microapproval.api.repository;

import com.microapproval.api.entity.TeamReviewAuditEvent;
import com.microapproval.api.entity.TeamReviewAuditEventType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamReviewAuditEventRepository extends JpaRepository<TeamReviewAuditEvent, String> {

    long countBySessionId(String sessionId);

    long countBySessionIdAndEventType(String sessionId, TeamReviewAuditEventType eventType);

    void deleteBySessionId(String sessionId);
}
