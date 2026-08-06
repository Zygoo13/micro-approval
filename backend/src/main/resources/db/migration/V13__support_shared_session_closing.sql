ALTER TABLE review_sessions
    ADD COLUMN closed_at TIMESTAMP(6) NULL AFTER completed_at,
    ADD COLUMN closed_by_user_id VARCHAR(36) NULL AFTER closed_at,
    ADD COLUMN close_reason VARCHAR(1000) NULL AFTER closed_by_user_id,
    ADD COLUMN lifecycle_version BIGINT NOT NULL DEFAULT 0 AFTER close_reason,
    ADD CONSTRAINT fk_review_sessions_closed_by
        FOREIGN KEY (closed_by_user_id) REFERENCES users(id),
    ADD CONSTRAINT chk_review_sessions_close_state
        CHECK (
            (closed_at IS NULL
                AND closed_by_user_id IS NULL
                AND close_reason IS NULL)
            OR
            (closed_at IS NOT NULL
                AND closed_by_user_id IS NOT NULL)
        ),
    ADD INDEX idx_review_sessions_workspace_closed_at (workspace_id, closed_at);

ALTER TABLE team_review_audit_events
    MODIFY COLUMN event_type ENUM(
        'REVIEWER_ASSIGNED',
        'REVIEWER_REMOVED',
        'REVIEWER_REACTIVATED',
        'VOTE_CREATED',
        'VOTE_UPDATED',
        'SESSION_CLOSED',
        'SESSION_REOPENED'
    ) NOT NULL;
