package com.microapproval.api.repository;

import com.microapproval.api.entity.TeamReviewAuditEvent;
import com.microapproval.api.entity.TeamReviewAuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamReviewAuditEventRepository extends JpaRepository<TeamReviewAuditEvent, String> {

    long countBySessionId(String sessionId);

    long countBySessionIdAndEventType(String sessionId, TeamReviewAuditEventType eventType);

    @EntityGraph(attributePaths = {
            "actor",
            "targetUser",
            "targetAssignment",
            "decisionCard"
    })
    Page<TeamReviewAuditEvent> findBySessionId(String sessionId, Pageable pageable);

    void deleteBySessionId(String sessionId);
}
