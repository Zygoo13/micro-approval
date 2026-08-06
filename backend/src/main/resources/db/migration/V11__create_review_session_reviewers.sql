CREATE TABLE review_session_reviewers (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    workspace_member_id VARCHAR(36) NOT NULL,
    assigned_by_user_id VARCHAR(36) NOT NULL,
    status ENUM('ASSIGNED', 'REMOVED') NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    removed_at TIMESTAMP(6) NULL,
    removed_by_user_id VARCHAR(36) NULL,
    removal_reason VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_review_session_reviewers_session_member
        UNIQUE (session_id, workspace_member_id),
    CONSTRAINT fk_review_session_reviewers_session
        FOREIGN KEY (session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_session_reviewers_member
        FOREIGN KEY (workspace_member_id) REFERENCES workspace_members(id),
    CONSTRAINT fk_review_session_reviewers_assigned_by
        FOREIGN KEY (assigned_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_review_session_reviewers_removed_by
        FOREIGN KEY (removed_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_review_session_reviewers_removal_state
        CHECK (
            (status = 'ASSIGNED'
                AND removed_at IS NULL
                AND removed_by_user_id IS NULL
                AND removal_reason IS NULL)
            OR
            (status = 'REMOVED'
                AND removed_at IS NOT NULL
                AND removed_by_user_id IS NOT NULL
                AND removal_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(removal_reason)) > 0)
        ),
    INDEX idx_review_session_reviewers_session_id (session_id),
    INDEX idx_review_session_reviewers_workspace_member_id (workspace_member_id),
    INDEX idx_review_session_reviewers_status (status),
    INDEX idx_review_session_reviewers_session_status (session_id, status)
);

CREATE TABLE team_review_audit_events (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    event_type ENUM(
        'REVIEWER_ASSIGNED',
        'REVIEWER_REMOVED',
        'REVIEWER_REACTIVATED'
    ) NOT NULL,
    target_user_id VARCHAR(36) NULL,
    target_assignment_id VARCHAR(36) NULL,
    old_value_json TEXT NULL,
    new_value_json TEXT NULL,
    reason VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_team_review_audit_session
        FOREIGN KEY (session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_review_audit_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT fk_team_review_audit_target_user
        FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_team_review_audit_target_assignment
        FOREIGN KEY (target_assignment_id) REFERENCES review_session_reviewers(id),
    INDEX idx_team_review_audit_session_created_at (session_id, created_at),
    INDEX idx_team_review_audit_actor_created_at (actor_user_id, created_at),
    INDEX idx_team_review_audit_event_type_created_at (event_type, created_at)
);
