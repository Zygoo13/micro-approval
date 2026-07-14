-- 1. Bảng User (Đăng nhập & Thông tin cơ bản)
CREATE TABLE users (
                       id VARCHAR(36) PRIMARY KEY,
                       full_name VARCHAR(100) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng Team
CREATE TABLE teams (
                       id VARCHAR(36) PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       description TEXT,
                       owner_id VARCHAR(36) NOT NULL,
                       github_repo_id VARCHAR(255),
                       webhook_secret VARCHAR(255),
                       ai_engine_enabled BOOLEAN DEFAULT TRUE,
                       max_cards_per_session INT DEFAULT 10,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT fk_teams_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- 3. Bảng trung gian TeamMember (Xác định Role của User trong từng Team)
CREATE TABLE team_members (
                              id VARCHAR(36) PRIMARY KEY,
                              team_id VARCHAR(36) NOT NULL,
                              user_id VARCHAR(36) NOT NULL,
                              role ENUM('MANAGER', 'REVIEWER', 'DEVELOPER', 'AUDITOR') NOT NULL,
                              joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT uq_team_user UNIQUE (team_id, user_id),
                              CONSTRAINT fk_teammembers_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                              CONSTRAINT fk_teammembers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 4. Bảng Project (Thuộc về Team)
CREATE TABLE projects (
                          id VARCHAR(36) PRIMARY KEY,
                          team_id VARCHAR(36) NOT NULL,
                          name VARCHAR(100) NOT NULL,
                          description TEXT,
                          created_by VARCHAR(36) NOT NULL,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_projects_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                          CONSTRAINT fk_projects_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

-- 5. Bảng ReviewSession (Phiên kiểm duyệt)
CREATE TABLE review_sessions (
                                 id VARCHAR(36) PRIMARY KEY,
                                 title VARCHAR(255) NOT NULL,
                                 workspace_type ENUM('PERSONAL', 'TEAM') NOT NULL,
                                 mode ENUM('RAW_SNIPPET', 'INTENT_MATCHING', 'GIT_DIFF') NOT NULL,
                                 raw_content LONGTEXT NOT NULL,
                                 prompt_content TEXT,
                                 project_id VARCHAR(36),
                                 submitted_by VARCHAR(36) NOT NULL,
                                 assigned_to VARCHAR(36),
                                 status ENUM('PENDING', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'COMPLETED') DEFAULT 'PENDING',
                                 is_automated BOOLEAN DEFAULT FALSE,
                                 external_link VARCHAR(500),
                                 ai_token_used INT DEFAULT 0,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP NULL,
                                 CONSTRAINT fk_sessions_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL,
                                 CONSTRAINT fk_sessions_submitter FOREIGN KEY (submitted_by) REFERENCES users(id),
                                 CONSTRAINT fk_sessions_assignee FOREIGN KEY (assigned_to) REFERENCES users(id)
);

-- 6. Bảng MicroDecision (Các Thẻ quyết định chi tiết)
CREATE TABLE micro_decisions (
                                 id VARCHAR(36) PRIMARY KEY,
                                 session_id VARCHAR(36) NOT NULL,
                                 engine_type ENUM('RULE_BASED', 'AI_BASED') NOT NULL,
                                 risk_category ENUM('SECURITY', 'DATABASE', 'DEPENDENCY', 'BUSINESS_LOGIC', 'INTENT_GAP') NOT NULL,
                                 risk_level ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL,
                                 code_snippet TEXT,
                                 question_text TEXT NOT NULL,
                                 is_ai_bypassed BOOLEAN DEFAULT FALSE,
                                 human_decision ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
                                 reviewer_note TEXT,
                                 decided_by VARCHAR(36),
                                 decided_at TIMESTAMP NULL,
                                 display_order INT NOT NULL,
                                 CONSTRAINT fk_decisions_session FOREIGN KEY (session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_decisions_reviewer FOREIGN KEY (decided_by) REFERENCES users(id)
);

-- 7. Bảng RulePattern (Cấu hình mẫu kiểm tra tĩnh)
CREATE TABLE rule_patterns (
                               id VARCHAR(36) PRIMARY KEY,
                               team_id VARCHAR(36) NULL, -- NULL nghĩa là Rule hệ thống chung cho toàn project
                               name VARCHAR(100) NOT NULL,
                               pattern TEXT NOT NULL,
                               risk_category ENUM('SECURITY', 'DATABASE', 'DEPENDENCY', 'BUSINESS_LOGIC', 'INTENT_GAP') NOT NULL,
                               risk_level ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL,
                               question_template TEXT NOT NULL,
                               is_active BOOLEAN DEFAULT TRUE,
                               created_by VARCHAR(36),
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_rules_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                               CONSTRAINT fk_rules_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

-- 8. Bảng AuditLink (Cho Auditor ngoài xem không cần login)
CREATE TABLE audit_links (
                             id VARCHAR(36) PRIMARY KEY,
                             team_id VARCHAR(36) NOT NULL,
                             token VARCHAR(64) NOT NULL UNIQUE,
                             label VARCHAR(255),
                             created_by VARCHAR(36) NOT NULL,
                             expires_at TIMESTAMP NOT NULL,
                             is_active BOOLEAN DEFAULT TRUE,
                             last_accessed_at TIMESTAMP NULL,
                             CONSTRAINT fk_audit_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                             CONSTRAINT fk_audit_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

-- 9. Bảng Notification (Thông báo In-app)
CREATE TABLE notifications (
                               id VARCHAR(36) PRIMARY KEY,
                               user_id VARCHAR(36) NOT NULL,
                               type ENUM('SESSION_ASSIGNED', 'SESSION_APPROVED', 'SESSION_REJECTED', 'SESSION_DECLINED') NOT NULL,
                               session_id VARCHAR(36) NOT NULL,
                               is_read BOOLEAN DEFAULT FALSE,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                               CONSTRAINT fk_notifications_session FOREIGN KEY (session_id) REFERENCES review_sessions(id) ON DELETE CASCADE
);