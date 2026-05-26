CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20) NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    real_name VARCHAR(50) NULL,
    avatar_url VARCHAR(500) NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    email_verified TINYINT NOT NULL DEFAULT 0,
    phone_verified TINYINT NOT NULL DEFAULT 0,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE admin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    job_no VARCHAR(50) NULL UNIQUE,
    real_name VARCHAR(50),
    remark VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_admin_user_user(user_id),
    CONSTRAINT fk_admin_user_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE auth_verification_code_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    target VARCHAR(100) NOT NULL,
    target_type VARCHAR(20) NOT NULL COMMENT 'EMAIL/PHONE',
    scene VARCHAR(30) NOT NULL COMMENT 'REGISTER/LOGIN/RESET_PASSWORD',
    send_status VARCHAR(20) NOT NULL,
    ip_address VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oauth_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL COMMENT 'WECHAT/ALIPAY/GITHUB',
    open_id VARCHAR(100) NOT NULL,
    union_id VARCHAR(100) NULL,
    nickname VARCHAR(100) NULL,
    avatar_url VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_oauth_user(user_id),
    UNIQUE KEY uk_provider_openid (provider, open_id)
);

CREATE TABLE passenger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    id_card_no VARCHAR(50) NOT NULL,
    passenger_type VARCHAR(20) NOT NULL DEFAULT 'ADULT',
    phone VARCHAR(30),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_passenger_user(user_id),
    UNIQUE KEY uk_passenger_user_card(user_id, id_card_no)
);

CREATE TABLE airline (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    logo_url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE airport (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(50) NOT NULL,
    province VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_airport_city(city)
);

CREATE TABLE flight (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flight_no VARCHAR(30) NOT NULL,
    airline_id BIGINT NOT NULL,
    departure_airport_id BIGINT NOT NULL,
    arrival_airport_id BIGINT NOT NULL,
    departure_time DATETIME NOT NULL,
    arrival_time DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    base_price DECIMAL(10,2) NOT NULL,
    remaining_seats INT NOT NULL DEFAULT 0,
    total_seats INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ON_TIME',
    publish_status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    direct_flag TINYINT NOT NULL DEFAULT 1,
    baggage_allowance VARCHAR(100),
    punctuality_rate DECIMAL(5,2) DEFAULT 95.00,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_flight_airline(airline_id),
    INDEX idx_flight_no_time(flight_no, departure_time),
    INDEX idx_flight_route_time(departure_airport_id, arrival_airport_id, departure_time),
    INDEX idx_flight_status(status, publish_status)
);

CREATE TABLE flight_seat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flight_id BIGINT NOT NULL,
    seat_no VARCHAR(10) NOT NULL,
    cabin_class VARCHAR(30) NOT NULL DEFAULT 'ECONOMY',
    seat_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    version INT NOT NULL DEFAULT 0,
    locked_by_order_id BIGINT NULL,
    lock_expire_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flight_seat(flight_id, seat_no),
    INDEX idx_seat_flight_status(flight_id, status),
    INDEX idx_seat_locked_order(locked_by_order_id)
);

CREATE TABLE ticket_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    flight_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    ticket_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    airport_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    fuel_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    service_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    pay_time DATETIME NULL,
    expire_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_user_created(user_id, created_at),
    INDEX idx_order_flight(flight_id),
    INDEX idx_order_status(status)
);

CREATE TABLE order_passenger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    passenger_name VARCHAR(50) NOT NULL,
    passenger_type VARCHAR(20) NOT NULL,
    seat_id BIGINT NOT NULL,
    seat_no VARCHAR(10) NOT NULL,
    ticket_price DECIMAL(10,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_passenger_order(order_id),
    INDEX idx_order_passenger_passenger(passenger_id),
    INDEX idx_order_passenger_seat(seat_id)
);

CREATE TABLE refund_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(255),
    refund_amount DECIMAL(10,2) NOT NULL,
    fee_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refund_order(order_id),
    INDEX idx_refund_user(user_id)
);

CREATE TABLE change_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    old_flight_id BIGINT NOT NULL,
    new_flight_id BIGINT NOT NULL,
    old_seat_id BIGINT,
    new_seat_id BIGINT,
    price_diff DECIMAL(10,2) NOT NULL DEFAULT 0,
    change_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_change_order(order_id),
    INDEX idx_change_old_flight(old_flight_id),
    INDEX idx_change_new_flight(new_flight_id),
    INDEX idx_change_old_seat(old_seat_id),
    INDEX idx_change_new_seat(new_seat_id)
);

CREATE TABLE waitlist_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    waitlist_no VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    flight_id BIGINT NOT NULL,
    ticket_order_id BIGINT NULL,
    passenger_count INT NOT NULL DEFAULT 1,
    cabin_class VARCHAR(30) NOT NULL DEFAULT 'ECONOMY',
    pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    expire_time DATETIME NULL,
    paid_at DATETIME NULL,
    refund_amount DECIMAL(10,2) NULL,
    refund_time DATETIME NULL,
    last_skip_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_waitlist_flight_status_paid(flight_id, status, paid_at, id),
    INDEX idx_waitlist_status_expire(status, expire_time),
    INDEX idx_waitlist_user(user_id),
    INDEX idx_waitlist_ticket_order(ticket_order_id)
);

CREATE TABLE waitlist_passenger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    waitlist_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    passenger_name VARCHAR(50) NOT NULL,
    passenger_type VARCHAR(20) NOT NULL,
    locked_seat_id BIGINT NULL,
    locked_seat_no VARCHAR(10) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_waitlist_passenger_waitlist(waitlist_id),
    INDEX idx_waitlist_passenger_passenger(passenger_id),
    INDEX idx_waitlist_passenger_seat(locked_seat_id)
);

ALTER TABLE oauth_account
    ADD CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE passenger
    ADD CONSTRAINT fk_passenger_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE flight
    ADD CONSTRAINT fk_flight_airline FOREIGN KEY (airline_id) REFERENCES airline(id),
    ADD CONSTRAINT fk_flight_departure_airport FOREIGN KEY (departure_airport_id) REFERENCES airport(id),
    ADD CONSTRAINT fk_flight_arrival_airport FOREIGN KEY (arrival_airport_id) REFERENCES airport(id);

ALTER TABLE flight_seat
    ADD CONSTRAINT fk_seat_flight FOREIGN KEY (flight_id) REFERENCES flight(id),
    ADD CONSTRAINT fk_seat_locked_order FOREIGN KEY (locked_by_order_id) REFERENCES ticket_order(id) ON DELETE SET NULL;

ALTER TABLE ticket_order
    ADD CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_order_flight FOREIGN KEY (flight_id) REFERENCES flight(id);

ALTER TABLE order_passenger
    ADD CONSTRAINT fk_order_passenger_order FOREIGN KEY (order_id) REFERENCES ticket_order(id),
    ADD CONSTRAINT fk_order_passenger_passenger FOREIGN KEY (passenger_id) REFERENCES passenger(id),
    ADD CONSTRAINT fk_order_passenger_seat FOREIGN KEY (seat_id) REFERENCES flight_seat(id);

ALTER TABLE refund_record
    ADD CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES ticket_order(id),
    ADD CONSTRAINT fk_refund_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE change_record
    ADD CONSTRAINT fk_change_order FOREIGN KEY (order_id) REFERENCES ticket_order(id),
    ADD CONSTRAINT fk_change_old_flight FOREIGN KEY (old_flight_id) REFERENCES flight(id),
    ADD CONSTRAINT fk_change_new_flight FOREIGN KEY (new_flight_id) REFERENCES flight(id),
    ADD CONSTRAINT fk_change_old_seat FOREIGN KEY (old_seat_id) REFERENCES flight_seat(id),
    ADD CONSTRAINT fk_change_new_seat FOREIGN KEY (new_seat_id) REFERENCES flight_seat(id);

ALTER TABLE waitlist_order
    ADD CONSTRAINT fk_waitlist_user FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_waitlist_flight FOREIGN KEY (flight_id) REFERENCES flight(id),
    ADD CONSTRAINT fk_waitlist_ticket_order FOREIGN KEY (ticket_order_id) REFERENCES ticket_order(id);

ALTER TABLE waitlist_passenger
    ADD CONSTRAINT fk_waitlist_passenger_waitlist FOREIGN KEY (waitlist_id) REFERENCES waitlist_order(id),
    ADD CONSTRAINT fk_waitlist_passenger_passenger FOREIGN KEY (passenger_id) REFERENCES passenger(id),
    ADD CONSTRAINT fk_waitlist_passenger_seat FOREIGN KEY (locked_seat_id) REFERENCES flight_seat(id) ON DELETE SET NULL;
