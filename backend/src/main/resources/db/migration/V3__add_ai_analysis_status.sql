ALTER TABLE review_sessions
    ADD COLUMN ai_analysis_status ENUM('NOT_REQUESTED', 'SUCCEEDED', 'FALLBACK', 'DISABLED') NOT NULL DEFAULT 'NOT_REQUESTED' AFTER ai_token_used,
    ADD COLUMN ai_analysis_error VARCHAR(500) NULL AFTER ai_analysis_status;
