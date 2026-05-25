-- 演示订单数据，可按实际开发需要调整或删除
INSERT INTO ticket_order(order_no, user_id, flight_id, status, ticket_amount, airport_fee, fuel_fee, service_fee, total_amount, pay_time, expire_time)
VALUES
('DEMO202605260001', 2, 1, 'ISSUED', 680.00, 50.00, 30.00, 0.00, 760.00, '2026-05-25 12:00:00', '2026-05-25 12:15:00');

UPDATE flight_seat
SET status = 'SOLD', locked_by_order_id = 1
WHERE flight_id = 1 AND seat_no = '1A';

UPDATE flight
SET remaining_seats = remaining_seats - 1
WHERE id = 1;

INSERT INTO order_passenger(order_id, passenger_id, passenger_name, passenger_type, seat_id, seat_no, ticket_price)
SELECT 1, 1, '张三', 'ADULT', id, seat_no, price
FROM flight_seat
WHERE flight_id = 1 AND seat_no = '1A';
