-- ============================================================
-- V10: 多舱位支持 (issue #50 / B-CABIN-1)
-- 新建 flight_cabin:一个航班多个舱位行(ECONOMY/BUSINESS/FIRST),
-- 各舱独立 price + total_seats。
-- 余座不在此表维护计数器,实时从 flight_seat AVAILABLE 算(沿用既有
-- countAvailableSeatsByFlightAndCabin),避免双写不一致与下单/退票扣减改造。
-- backfill 已有座位的航班为单 ECONOMY 舱,兼容 demo 数据与存量订单。
-- ============================================================

CREATE TABLE flight_cabin (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flight_id BIGINT NOT NULL,
    cabin_class VARCHAR(30) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    total_seats INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flight_cabin (flight_id, cabin_class),
    INDEX idx_flight_cabin_flight(flight_id),
    CONSTRAINT fk_flight_cabin_flight FOREIGN KEY (flight_id) REFERENCES flight(id),
    CONSTRAINT chk_flight_cabin_class CHECK (cabin_class IN ('ECONOMY','BUSINESS','FIRST')),
    CONSTRAINT chk_flight_cabin_price CHECK (price > 0),
    CONSTRAINT chk_flight_cabin_total CHECK (total_seats > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- backfill:为每个已有座位的航班补一行 ECONOMY 配置
-- total_seats 取实际生成的座位数(而非 flight.total_seats),确保与 flight_seat 严格对齐
INSERT INTO flight_cabin(flight_id, cabin_class, price, total_seats)
SELECT f.id, 'ECONOMY', f.base_price,
       (SELECT COUNT(*) FROM flight_seat fs WHERE fs.flight_id = f.id)
FROM flight f
WHERE EXISTS (SELECT 1 FROM flight_seat fs WHERE fs.flight_id = f.id);
