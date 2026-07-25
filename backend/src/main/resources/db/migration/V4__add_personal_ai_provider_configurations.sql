CREATE TABLE ai_provider_configurations (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(100) NOT NULL,
    api_key_ciphertext TEXT NOT NULL,
    api_key_suffix VARCHAR(4) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_provider_configuration_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
