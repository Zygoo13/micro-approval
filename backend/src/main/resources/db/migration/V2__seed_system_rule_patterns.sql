ALTER TABLE rule_patterns
    ADD COLUMN priority INT NOT NULL DEFAULT 100 AFTER name;

CREATE INDEX idx_rule_patterns_active_scope_priority
    ON rule_patterns (is_active, team_id, priority);

INSERT INTO rule_patterns (id, team_id, name, priority, pattern, risk_category, risk_level, question_template, is_active, created_by)
VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'SQL string interpolation', 10,
     '(?is)\\bselect\\b[\\s\\S]{0,500}?(?:\\+|\\$\\{)', 'SECURITY', 'HIGH',
     'Truy vấn này có dùng prepared statement hoặc tham số hóa để tránh SQL Injection không?', TRUE, NULL),
    ('00000000-0000-0000-0000-000000000002', NULL, 'Destructive SQL command', 20,
     '(?i)\\b(?:drop\\s+table|delete\\s+from)\\b', 'DATABASE', 'HIGH',
     'Thao tác dữ liệu này đã được giới hạn phạm vi, kiểm tra điều kiện và có phương án khôi phục chưa?', TRUE, NULL),
    ('00000000-0000-0000-0000-000000000003', NULL, 'Hardcoded credential', 30,
     '(?i)\\b(?:password|api[_-]?key|secret)\\s*[:=]\\s*["''][^"'']+', 'SECURITY', 'HIGH',
     'Giá trị nhạy cảm này đã được lấy từ secret manager hoặc biến môi trường thay vì ghi trực tiếp trong mã chưa?', TRUE, NULL),
    ('00000000-0000-0000-0000-000000000004', NULL, 'Dependency change', 40,
     '(?im)^\\s*(?:import\\s+|(?:"dependencies"|''dependencies'')\\s*:|require\\s*\\()', 'DEPENDENCY', 'MEDIUM',
     'Dependency mới hoặc được cập nhật này đã được kiểm tra nguồn gốc, license và lỗ hổng bảo mật chưa?', TRUE, NULL);
