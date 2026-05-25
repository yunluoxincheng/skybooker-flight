INSERT INTO flight(flight_no, airline_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time, duration_minutes, base_price, remaining_seats, total_seats, status, direct_flag, baggage_allowance, punctuality_rate)
VALUES
('MU5101', 1, 1, 3, '2026-05-26 08:30:00', '2026-05-26 10:45:00', 135, 680.00, 29, 30, 'ON_TIME', 1, '20kg 托运行李', 96.50),
('CZ3101', 2, 1, 4, '2026-05-26 10:20:00', '2026-05-26 12:45:00', 145, 720.00, 29, 30, 'ON_TIME', 1, '20kg 托运行李', 95.20),
('CA1502', 3, 2, 3, '2026-05-26 14:00:00', '2026-05-26 16:20:00', 140, 760.00, 29, 30, 'DELAYED', 1, '20kg 托运行李', 92.80),
('HU7608', 4, 5, 1, '2026-05-26 09:10:00', '2026-05-26 11:35:00', 145, 650.00, 29, 30, 'ON_TIME', 1, '20kg 托运行李', 94.60),
('CZ8888', 2, 6, 7, '2026-05-26 19:30:00', '2026-05-26 21:50:00', 140, 580.00, 29, 30, 'ON_TIME', 1, '20kg 托运行李', 97.10);
