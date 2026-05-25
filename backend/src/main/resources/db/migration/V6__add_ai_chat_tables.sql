CREATE TABLE ai_chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NULL,
    session_title VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_session_public(public_session_id),
    INDEX idx_ai_session_user(user_id)
);

CREATE TABLE ai_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
    content TEXT NOT NULL,
    message_type VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    extra_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_message_session(session_id, created_at)
);

CREATE TABLE ai_recommendation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    query_text TEXT NOT NULL,
    parsed_condition_json JSON NULL,
    recommended_flight_ids VARCHAR(255),
    search_url VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_recommend_session(session_id),
    INDEX idx_ai_recommend_user(user_id)
);

ALTER TABLE ai_chat_session
    ADD CONSTRAINT fk_ai_session_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ai_chat_message
    ADD CONSTRAINT fk_ai_message_session FOREIGN KEY (session_id) REFERENCES ai_chat_session(id);

ALTER TABLE ai_recommendation_record
    ADD CONSTRAINT fk_ai_recommend_session FOREIGN KEY (session_id) REFERENCES ai_chat_session(id),
    ADD CONSTRAINT fk_ai_recommend_user FOREIGN KEY (user_id) REFERENCES users(id);
