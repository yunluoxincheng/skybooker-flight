CREATE TABLE connecting_itinerary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    first_flight_id BIGINT NOT NULL,
    second_flight_id BIGINT NOT NULL,
    publish_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_connecting_itinerary_flights UNIQUE (first_flight_id, second_flight_id),
    CONSTRAINT fk_connecting_itinerary_first_flight FOREIGN KEY (first_flight_id) REFERENCES flight(id),
    CONSTRAINT fk_connecting_itinerary_second_flight FOREIGN KEY (second_flight_id) REFERENCES flight(id),
    CONSTRAINT chk_connecting_itinerary_distinct CHECK (first_flight_id <> second_flight_id),
    CONSTRAINT chk_connecting_itinerary_publish_status CHECK (publish_status IN ('DRAFT', 'PUBLISHED')),
    INDEX idx_connecting_itinerary_publish (publish_status, first_flight_id, second_flight_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve the combinations that were sellable before this management layer was
-- introduced. Administrators can subsequently unpublish or replace them explicitly.
INSERT INTO connecting_itinerary(first_flight_id, second_flight_id, publish_status, created_by)
SELECT f1.id, f2.id,
       CASE WHEN f1.publish_status='PUBLISHED' AND f2.publish_status='PUBLISHED'
            THEN 'PUBLISHED' ELSE 'DRAFT' END,
       NULL
FROM flight f1
JOIN flight f2 ON f2.departure_airport_id=f1.arrival_airport_id
WHERE f1.id <> f2.id
  AND f1.direct_flag=1 AND f2.direct_flag=1
  AND f1.departure_airport_id <> f2.arrival_airport_id
  AND f2.departure_time >= f1.arrival_time + INTERVAL 90 MINUTE
  AND f2.departure_time <= f1.arrival_time + INTERVAL 6 HOUR;
