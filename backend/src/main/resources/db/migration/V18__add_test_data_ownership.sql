-- Test-data batches are identified by a stable profile key rather than by an
-- auto-increment ID range.  This keeps normal application-created rows safe
-- when a seed is refreshed after the database has been used manually.
CREATE TABLE test_data_batch (
    batch_key VARCHAR(64) PRIMARY KEY,
    profile VARCHAR(20) NOT NULL,
    seed BIGINT NOT NULL,
    source_ref VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_test_data_batch_profile(profile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_data_ownership (
    batch_key VARCHAR(64) NOT NULL,
    table_name VARCHAR(64) NOT NULL,
    row_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (batch_key, table_name, row_id),
    INDEX idx_test_data_ownership_row(table_name, row_id),
    CONSTRAINT fk_test_data_ownership_batch
        FOREIGN KEY (batch_key) REFERENCES test_data_batch(batch_key) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
