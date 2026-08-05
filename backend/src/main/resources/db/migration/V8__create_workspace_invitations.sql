CREATE TABLE workspace_invitations (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'REVIEWER', 'MEMBER', 'AUDITOR') NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'REVOKED', 'EXPIRED') NOT NULL,
    invited_by_user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL,
    pending_email VARCHAR(255)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN LOWER(TRIM(email)) ELSE NULL END
        ) STORED,
    CONSTRAINT fk_workspace_invitations_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_invitations_inviter
        FOREIGN KEY (invited_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_workspace_invitations_normalized_email
        CHECK (BINARY email = BINARY LOWER(TRIM(email))),
    CONSTRAINT uq_workspace_invitations_pending_email
        UNIQUE (workspace_id, pending_email),
    INDEX idx_workspace_invitations_workspace_id (workspace_id),
    INDEX idx_workspace_invitations_email (email),
    INDEX idx_workspace_invitations_status (status),
    INDEX idx_workspace_invitations_email_status (email, status)
);
