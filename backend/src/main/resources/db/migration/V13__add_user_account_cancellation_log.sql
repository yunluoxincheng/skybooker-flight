CREATE TABLE user_account_cancellation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL DEFAULT 'SELF_CANCEL',
    client_ip VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_account_cancellation_user(user_id),
    INDEX idx_user_account_cancellation_created(created_at),
    CONSTRAINT fk_user_account_cancellation_user FOREIGN KEY (user_id) REFERENCES users(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
