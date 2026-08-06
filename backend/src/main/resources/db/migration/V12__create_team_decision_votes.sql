ALTER TABLE micro_decisions
    ADD COLUMN team_decision ENUM('PENDING', 'APPROVED', 'REJECTED') NULL
        AFTER human_decision;

UPDATE micro_decisions decision_card
JOIN review_sessions session ON session.id = decision_card.session_id
SET decision_card.team_decision = 'PENDING'
WHERE session.workspace_type = 'SHARED';

CREATE TABLE decision_card_votes (
    id VARCHAR(36) PRIMARY KEY,
    decision_card_id VARCHAR(36) NOT NULL,
    reviewer_assignment_id VARCHAR(36) NOT NULL,
    decision ENUM('APPROVED', 'REJECTED') NOT NULL,
    note VARCHAR(2000) NULL,
    assignment_version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_decision_card_votes_card_assignment
        UNIQUE (decision_card_id, reviewer_assignment_id),
    CONSTRAINT fk_decision_card_votes_card
        FOREIGN KEY (decision_card_id) REFERENCES micro_decisions(id) ON DELETE CASCADE,
    CONSTRAINT fk_decision_card_votes_assignment
        FOREIGN KEY (reviewer_assignment_id) REFERENCES review_session_reviewers(id),
    CONSTRAINT chk_decision_card_votes_rejected_note
        CHECK (
            decision = 'APPROVED'
            OR (note IS NOT NULL AND CHAR_LENGTH(TRIM(note)) > 0)
        ),
    INDEX idx_decision_card_votes_card_id (decision_card_id),
    INDEX idx_decision_card_votes_assignment_id (reviewer_assignment_id),
    INDEX idx_decision_card_votes_decision (decision),
    INDEX idx_decision_card_votes_card_decision (decision_card_id, decision)
);

ALTER TABLE team_review_audit_events
    MODIFY COLUMN event_type ENUM(
        'REVIEWER_ASSIGNED',
        'REVIEWER_REMOVED',
        'REVIEWER_REACTIVATED',
        'VOTE_CREATED',
        'VOTE_UPDATED'
    ) NOT NULL,
    ADD COLUMN decision_card_id VARCHAR(36) NULL AFTER target_assignment_id,
    ADD CONSTRAINT fk_team_review_audit_decision_card
        FOREIGN KEY (decision_card_id) REFERENCES micro_decisions(id) ON DELETE CASCADE,
    ADD INDEX idx_team_review_audit_card_created_at (decision_card_id, created_at);
