ALTER TABLE review_sessions
    ADD COLUMN workspace_id VARCHAR(36) NULL AFTER workspace_type;

UPDATE review_sessions session
JOIN projects project ON project.id = session.project_id
SET session.workspace_id = project.workspace_id
WHERE session.workspace_type = 'SHARED';

DELIMITER //
CREATE PROCEDURE validate_shared_review_session_backfill()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM review_sessions
        WHERE workspace_type = 'SHARED'
          AND workspace_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Shared ReviewSession migration failed: workspace cannot be inferred';
    END IF;
END//
DELIMITER ;

CALL validate_shared_review_session_backfill();
DROP PROCEDURE validate_shared_review_session_backfill;

ALTER TABLE review_sessions
    ADD CONSTRAINT fk_review_sessions_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    ADD CONSTRAINT chk_review_sessions_workspace_scope
        CHECK (
            (workspace_type = 'PERSONAL' AND workspace_id IS NULL)
            OR (workspace_type = 'SHARED' AND workspace_id IS NOT NULL)
        ),
    ADD INDEX idx_review_sessions_workspace_id (workspace_id),
    ADD INDEX idx_review_sessions_workspace_created_at (workspace_id, created_at);
