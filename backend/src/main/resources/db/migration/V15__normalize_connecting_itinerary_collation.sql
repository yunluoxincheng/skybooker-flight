-- V14 tables inherited the database/server default collation. On a fresh MySQL 8
-- installation that can be utf8mb4_0900_ai_ci, while legacy tables were normalized
-- to utf8mb4_unicode_ci by V8. Keep cross-table route queries deterministic.
ALTER TABLE ticket_order_segment
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE order_segment_passenger
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE connecting_change_record
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE connecting_change_segment
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
