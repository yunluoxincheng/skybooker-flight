-- Test-only aviation baseline.
-- Production/dev/demo data belongs in backend/src/main/resources/db/seed and scripts/generate_test_data.py.

SET FOREIGN_KEY_CHECKS = 0;

DELETE wp FROM waitlist_passenger wp
JOIN waitlist_order wo ON wo.id = wp.waitlist_id
WHERE wo.flight_id BETWEEN 1 AND 5 OR wo.ticket_order_id = 1;

DELETE FROM waitlist_order
WHERE flight_id BETWEEN 1 AND 5 OR ticket_order_id = 1;

DELETE rr FROM refund_record rr
JOIN ticket_order o ON o.id = rr.order_id
WHERE o.id = 1 OR o.flight_id BETWEEN 1 AND 5 OR o.order_no = 'DEMO202605260001';

DELETE cr FROM change_record cr
LEFT JOIN ticket_order o ON o.id = cr.order_id
WHERE cr.order_id = 1
   OR cr.old_flight_id BETWEEN 1 AND 5
   OR cr.new_flight_id BETWEEN 1 AND 5
   OR o.flight_id BETWEEN 1 AND 5
   OR o.order_no = 'DEMO202605260001';

DELETE op FROM order_passenger op
JOIN ticket_order o ON o.id = op.order_id
WHERE o.id = 1 OR o.flight_id BETWEEN 1 AND 5 OR o.order_no = 'DEMO202605260001';

DELETE FROM ticket_order
WHERE id = 1 OR flight_id BETWEEN 1 AND 5 OR order_no = 'DEMO202605260001';

DELETE FROM flight_seat WHERE flight_id BETWEEN 1 AND 5 OR id BETWEEN 1 AND 150;
DELETE FROM flight_cabin WHERE flight_id BETWEEN 1 AND 5;
DELETE FROM flight WHERE id BETWEEN 1 AND 5;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO airline(id, code, name, status) VALUES
(1, 'MU', '东方航空', 'ENABLED'),
(2, 'CZ', '南方航空', 'ENABLED'),
(3, 'CA', '中国国航', 'ENABLED'),
(4, 'HU', '海南航空', 'ENABLED')
ON DUPLICATE KEY UPDATE
code = VALUES(code),
name = VALUES(name),
status = VALUES(status);

INSERT INTO airport(id, code, name, city, province, status) VALUES
(1, 'SHA', '上海虹桥国际机场', '上海', '上海', 'ENABLED'),
(2, 'PVG', '上海浦东国际机场', '上海', '上海', 'ENABLED'),
(3, 'PEK', '北京首都国际机场', '北京', '北京', 'ENABLED'),
(4, 'PKX', '北京大兴国际机场', '北京', '北京', 'ENABLED'),
(5, 'CAN', '广州白云国际机场', '广州', '广东', 'ENABLED'),
(6, 'SZX', '深圳宝安国际机场', '深圳', '广东', 'ENABLED'),
(7, 'CTU', '成都双流国际机场', '成都', '四川', 'ENABLED')
ON DUPLICATE KEY UPDATE
code = VALUES(code),
name = VALUES(name),
city = VALUES(city),
province = VALUES(province),
status = VALUES(status);

INSERT INTO flight(id, flight_no, airline_id, departure_airport_id, arrival_airport_id,
                   departure_time, arrival_time, duration_minutes, base_price,
                   remaining_seats, total_seats, status, publish_status, direct_flag,
                   baggage_allowance, punctuality_rate) VALUES
(1, 'MU5101', 1, 1, 3, '2026-05-26 08:30:00', '2026-05-26 10:45:00', 135, 680.00, 28, 30, 'ON_TIME', 'PUBLISHED', 1, '20kg 托运行李', 96.50),
(2, 'CZ3101', 2, 1, 4, '2026-05-26 10:20:00', '2026-05-26 12:45:00', 145, 720.00, 29, 30, 'ON_TIME', 'PUBLISHED', 1, '20kg 托运行李', 95.20),
(3, 'CA1502', 3, 2, 3, '2026-05-26 14:00:00', '2026-05-26 16:20:00', 140, 760.00, 29, 30, 'DELAYED', 'PUBLISHED', 1, '20kg 托运行李', 92.80),
(4, 'HU7608', 4, 5, 1, '2026-05-26 09:10:00', '2026-05-26 11:35:00', 145, 650.00, 29, 30, 'ON_TIME', 'PUBLISHED', 1, '20kg 托运行李', 94.60),
(5, 'CZ8888', 2, 6, 7, '2026-05-26 19:30:00', '2026-05-26 21:50:00', 140, 580.00, 29, 30, 'ON_TIME', 'PUBLISHED', 1, '20kg 托运行李', 97.10);

INSERT INTO flight_cabin(flight_id, cabin_class, price, total_seats)
SELECT id, 'ECONOMY', base_price, total_seats
FROM flight
WHERE id BETWEEN 1 AND 5
ON DUPLICATE KEY UPDATE
price = VALUES(price),
total_seats = VALUES(total_seats);

INSERT INTO flight_seat(id, flight_id, seat_no, cabin_class, seat_type, price, status)
SELECT ((f.id - 1) * 30) + ((r.row_no - 1) * 6) + s.pos AS id,
       f.id,
       CONCAT(r.row_no, s.seat_code),
       'ECONOMY',
       CASE WHEN s.seat_code IN ('A', 'F') THEN 'WINDOW'
            WHEN s.seat_code IN ('C', 'D') THEN 'AISLE'
            ELSE 'NORMAL' END,
       f.base_price,
       CASE WHEN r.row_no = 3 AND s.seat_code = 'A' THEN 'DISABLED' ELSE 'AVAILABLE' END
FROM flight f
JOIN (
    SELECT 1 AS row_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) r
JOIN (
    SELECT 1 AS pos, 'A' AS seat_code UNION ALL SELECT 2, 'B' UNION ALL SELECT 3, 'C'
    UNION ALL SELECT 4, 'D' UNION ALL SELECT 5, 'E' UNION ALL SELECT 6, 'F'
) s
WHERE f.id BETWEEN 1 AND 5;

INSERT INTO ticket_order(id, order_no, user_id, flight_id, status, ticket_amount, airport_fee,
                         fuel_fee, service_fee, total_amount, pay_time, expire_time)
VALUES(1, 'DEMO202605260001', 2, 1, 'ISSUED', 680.00, 50.00,
       30.00, 0.00, 760.00, '2026-05-25 12:00:00', '2026-05-25 12:15:00');

UPDATE flight_seat
SET status = 'SOLD', locked_by_order_id = 1
WHERE flight_id = 1 AND seat_no = '1A';

INSERT INTO order_passenger(order_id, passenger_id, passenger_name, passenger_type, seat_id, seat_no, ticket_price)
SELECT 1, 1, '张三', 'ADULT', id, seat_no, price
FROM flight_seat
WHERE flight_id = 1 AND seat_no = '1A';
