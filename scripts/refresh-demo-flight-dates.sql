-- Refresh demo flight and order dates to tomorrow before classroom demos.
-- Run against the local SkyBooker database after Flyway initialization.

SET @demo_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY);
SET @order_pay_time = TIMESTAMP(CURDATE(), '12:00:00');

UPDATE flight
SET departure_time = TIMESTAMP(@demo_date, TIME(departure_time)),
    arrival_time = TIMESTAMP(@demo_date, TIME(arrival_time));

UPDATE ticket_order
SET order_no = CONCAT('DEMO', DATE_FORMAT(@demo_date, '%Y%m%d'), LPAD(CAST(id AS CHAR), 6, '0')),
    pay_time = @order_pay_time,
    expire_time = DATE_ADD(@order_pay_time, INTERVAL 15 MINUTE)
WHERE order_no LIKE 'DEMO%';
