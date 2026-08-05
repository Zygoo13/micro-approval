ALTER TABLE workspaces
    MODIFY COLUMN description TEXT NULL,
    ADD COLUMN github_repo_id VARCHAR(255) NULL AFTER owner_id,
    ADD COLUMN webhook_secret VARCHAR(255) NULL AFTER github_repo_id,
    ADD COLUMN ai_engine_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER webhook_secret,
    ADD COLUMN max_cards_per_session INT NOT NULL DEFAULT 10 AFTER ai_engine_enabled;

ALTER TABLE workspace_members
    MODIFY COLUMN role ENUM('OWNER', 'ADMIN', 'REVIEWER', 'MEMBER', 'AUDITOR') NOT NULL;

-- Preserve legacy collaboration IDs and settings. A duplicate ID intentionally
-- fails the migration instead of overwriting an existing Workspace silently.
INSERT INTO workspaces (
    id,
    name,
    description,
    owner_id,
    github_repo_id,
    webhook_secret,
    ai_engine_enabled,
    max_cards_per_session,
    created_at,
    updated_at
)
SELECT
    team.id,
    team.name,
    team.description,
    team.owner_id,
    team.github_repo_id,
    team.webhook_secret,
    COALESCE(team.ai_engine_enabled, TRUE),
    COALESCE(team.max_cards_per_session, 10),
    COALESCE(team.created_at, CURRENT_TIMESTAMP),
    COALESCE(team.created_at, CURRENT_TIMESTAMP)
FROM teams team;

-- Reuse a legacy membership ID when the owner was also listed in team_members.
INSERT INTO workspace_members (id, workspace_id, user_id, role, status, joined_at)
SELECT
    COALESCE(owner_member.id, UUID()),
    team.id,
    team.owner_id,
    'OWNER',
    'ACTIVE',
    COALESCE(owner_member.joined_at, team.created_at, CURRENT_TIMESTAMP)
FROM teams team
LEFT JOIN team_members owner_member
    ON owner_member.team_id = team.id
   AND owner_member.user_id = team.owner_id;

INSERT INTO workspace_members (id, workspace_id, user_id, role, status, joined_at)
SELECT
    member.id,
    member.team_id,
    member.user_id,
    CASE member.role
        WHEN 'MANAGER' THEN 'ADMIN'
        WHEN 'REVIEWER' THEN 'REVIEWER'
        WHEN 'DEVELOPER' THEN 'MEMBER'
        WHEN 'AUDITOR' THEN 'AUDITOR'
    END,
    'ACTIVE',
    COALESCE(member.joined_at, CURRENT_TIMESTAMP)
FROM team_members member
JOIN teams team ON team.id = member.team_id
WHERE member.user_id <> team.owner_id;

DELIMITER //
CREATE PROCEDURE validate_legacy_workspace_migration()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM teams team
        LEFT JOIN workspaces workspace ON workspace.id = team.id
        WHERE workspace.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Legacy Team migration failed: workspace is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM team_members member
        LEFT JOIN workspace_members workspace_member
            ON workspace_member.workspace_id = member.team_id
           AND workspace_member.user_id = member.user_id
        WHERE workspace_member.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Legacy Team migration failed: membership is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM teams team
        LEFT JOIN workspace_members workspace_member
            ON workspace_member.workspace_id = team.id
           AND workspace_member.role = 'OWNER'
           AND workspace_member.status = 'ACTIVE'
        GROUP BY team.id
        HAVING COUNT(workspace_member.id) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Legacy Team migration failed: owner invariant is violated';
    END IF;
END//
DELIMITER ;

CALL validate_legacy_workspace_migration();
DROP PROCEDURE validate_legacy_workspace_migration;

ALTER TABLE projects
    DROP FOREIGN KEY fk_projects_team,
    RENAME COLUMN team_id TO workspace_id,
    ADD CONSTRAINT fk_projects_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;

ALTER TABLE rule_patterns
    DROP FOREIGN KEY fk_rules_team,
    RENAME COLUMN team_id TO workspace_id,
    RENAME INDEX idx_rule_patterns_active_scope_priority
        TO idx_rule_patterns_active_workspace_priority,
    ADD CONSTRAINT fk_rules_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;

ALTER TABLE audit_links
    DROP FOREIGN KEY fk_audit_team,
    RENAME COLUMN team_id TO workspace_id,
    ADD CONSTRAINT fk_audit_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;

DROP TABLE team_members;
DROP TABLE teams;
