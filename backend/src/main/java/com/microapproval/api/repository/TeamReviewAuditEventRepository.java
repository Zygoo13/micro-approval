package com.microapproval.api.repository;

import com.microapproval.api.entity.TeamReviewAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamReviewAuditEventRepository extends JpaRepository<TeamReviewAuditEvent, String> {

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
