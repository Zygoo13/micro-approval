ALTER TABLE review_sessions
    MODIFY COLUMN workspace_type ENUM('PERSONAL', 'TEAM', 'SHARED') NOT NULL;

UPDATE review_sessions
SET workspace_type = 'SHARED'
WHERE workspace_type = 'TEAM';

ALTER TABLE review_sessions
    MODIFY COLUMN workspace_type ENUM('PERSONAL', 'SHARED') NOT NULL;
