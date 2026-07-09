CREATE TABLE admin_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_operation_log_admin(admin_user_id),
    INDEX idx_admin_operation_log_target(target_type, target_id),
    INDEX idx_admin_operation_log_created(created_at),
    CONSTRAINT chk_admin_operation_log_target_type CHECK (target_type IN ('ORDER','USER')),
    CONSTRAINT fk_admin_operation_log_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE ticket_order
    ADD COLUMN admin_note VARCHAR(500) NULL;

ALTER TABLE ticket_order DROP CHECK chk_ticket_order_status;
ALTER TABLE ticket_order
    ADD CONSTRAINT chk_ticket_order_status CHECK (
        status IN ('PENDING_PAYMENT','ISSUED','CANCELLED','REFUNDED','CHANGED','CHANGE_PENDING','VOIDED')
    );

ALTER TABLE users DROP CHECK chk_users_status;
ALTER TABLE users
    ADD CONSTRAINT chk_users_status CHECK (status IN ('NORMAL','DISABLED','DELETED'));
