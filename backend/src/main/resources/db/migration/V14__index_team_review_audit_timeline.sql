ALTER TABLE team_review_audit_events
    DROP INDEX idx_team_review_audit_session_created_at,
    ADD INDEX idx_team_review_audit_session_created_at_id (session_id, created_at, id);
