ALTER TABLE ticket_order
    ADD COLUMN journey_type VARCHAR(20) NOT NULL DEFAULT 'DIRECT' AFTER flight_id,
    ADD COLUMN client_request_id VARCHAR(64) NULL AFTER journey_type,
    ADD CONSTRAINT chk_ticket_order_journey_type CHECK (journey_type IN ('DIRECT', 'CONNECTING')),
    ADD UNIQUE KEY uk_order_user_client_request (user_id, client_request_id);

CREATE TABLE ticket_order_segment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    segment_no TINYINT NOT NULL,
    flight_id BIGINT NOT NULL,
    flight_no VARCHAR(30) NOT NULL,
    airline_code VARCHAR(20) NOT NULL,
    airline_name VARCHAR(100) NOT NULL,
    departure_airport_code VARCHAR(20) NOT NULL,
    departure_airport_name VARCHAR(100) NOT NULL,
    departure_city VARCHAR(50) NOT NULL,
    arrival_airport_code VARCHAR(20) NOT NULL,
    arrival_airport_name VARCHAR(100) NOT NULL,
    arrival_city VARCHAR(50) NOT NULL,
    departure_time DATETIME NOT NULL,
    arrival_time DATETIME NOT NULL,
    ticket_amount DECIMAL(10,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_segment_no CHECK (segment_no IN (1, 2)),
    UNIQUE KEY uk_order_segment_no (order_id, segment_no),
    INDEX idx_order_segment_flight (flight_id),
    CONSTRAINT fk_order_segment_order FOREIGN KEY (order_id) REFERENCES ticket_order(id),
    CONSTRAINT fk_order_segment_flight FOREIGN KEY (flight_id) REFERENCES flight(id)
);

CREATE TABLE order_segment_passenger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_segment_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    passenger_name VARCHAR(50) NOT NULL,
    passenger_type VARCHAR(20) NOT NULL,
    seat_id BIGINT NOT NULL,
    seat_no VARCHAR(10) NOT NULL,
    ticket_price DECIMAL(10,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_segment_passenger (order_segment_id, passenger_id),
    UNIQUE KEY uk_segment_seat (order_segment_id, seat_id),
    INDEX idx_osp_passenger (passenger_id),
    INDEX idx_osp_seat (seat_id),
    CONSTRAINT fk_osp_segment FOREIGN KEY (order_segment_id) REFERENCES ticket_order_segment(id),
    CONSTRAINT fk_osp_passenger FOREIGN KEY (passenger_id) REFERENCES passenger(id),
    CONSTRAINT fk_osp_seat FOREIGN KEY (seat_id) REFERENCES flight_seat(id)
);

CREATE TABLE connecting_change_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    old_total_amount DECIMAL(10,2) NOT NULL,
    new_total_amount DECIMAL(10,2) NOT NULL,
    price_difference DECIMAL(10,2) NOT NULL,
    change_fee DECIMAL(10,2) NOT NULL,
    reason VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_connecting_change_user_request (user_id, client_request_id),
    INDEX idx_connecting_change_order (order_id),
    CONSTRAINT fk_connecting_change_order FOREIGN KEY (order_id) REFERENCES ticket_order(id),
    CONSTRAINT fk_connecting_change_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE connecting_change_segment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    change_record_id BIGINT NOT NULL,
    snapshot_type VARCHAR(10) NOT NULL,
    segment_no TINYINT NOT NULL,
    flight_id BIGINT NOT NULL,
    flight_no VARCHAR(30) NOT NULL,
    departure_airport_code VARCHAR(20) NOT NULL,
    arrival_airport_code VARCHAR(20) NOT NULL,
    departure_time DATETIME NOT NULL,
    arrival_time DATETIME NOT NULL,
    passenger_seats JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_change_snapshot_type CHECK (snapshot_type IN ('OLD', 'NEW')),
    UNIQUE KEY uk_change_snapshot_segment (change_record_id, snapshot_type, segment_no),
    CONSTRAINT fk_change_segment_record FOREIGN KEY (change_record_id) REFERENCES connecting_change_record(id)
);
