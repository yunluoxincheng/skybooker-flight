-- 为演示航班生成简化座位数据：1-5 排，每排 A-F
INSERT INTO flight_seat(flight_id, seat_no, cabin_class, seat_type, price, status)
SELECT f.id, CONCAT(r.row_no, s.seat_code), 'ECONOMY',
       CASE WHEN s.seat_code IN ('A','F') THEN 'WINDOW'
            WHEN s.seat_code IN ('C','D') THEN 'AISLE'
            ELSE 'NORMAL' END,
       f.base_price,
       CASE WHEN r.row_no = 3 AND s.seat_code = 'A' THEN 'DISABLED' ELSE 'AVAILABLE' END
FROM flight f
JOIN (
    SELECT 1 AS row_no UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) r
JOIN (
    SELECT 'A' AS seat_code UNION SELECT 'B' UNION SELECT 'C' UNION SELECT 'D' UNION SELECT 'E' UNION SELECT 'F'
) s;
