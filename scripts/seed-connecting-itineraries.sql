-- Deterministic template for local issue #140 verification. Run only against a disposable demo database.
SET @first_departure = TIMESTAMP(DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY), '08:00:00');
INSERT INTO flight(flight_no, airline_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time, duration_minutes, base_price, remaining_seats, total_seats, status, publish_status, direct_flag)
VALUES ('CNX140A',1,3,1,@first_departure,DATE_ADD(@first_departure,INTERVAL 2 HOUR),120,520,6,6,'ON_TIME','PUBLISHED',1);
SET @first_id=LAST_INSERT_ID();
INSERT INTO flight(flight_no, airline_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time, duration_minutes, base_price, remaining_seats, total_seats, status, publish_status, direct_flag)
VALUES ('CNX140B',2,1,5,DATE_ADD(@first_departure,INTERVAL 4 HOUR),DATE_ADD(@first_departure,INTERVAL 6 HOUR),120,680,6,6,'ON_TIME','PUBLISHED',1);
SET @second_id=LAST_INSERT_ID();
INSERT INTO connecting_itinerary(first_flight_id, second_flight_id, publish_status)
VALUES (@first_id, @second_id, 'PUBLISHED');
INSERT INTO flight_seat(flight_id,seat_no,cabin_class,seat_type,price,status) VALUES
(@first_id,'1A','ECONOMY','WINDOW',520,'AVAILABLE'),(@first_id,'1B','ECONOMY','MIDDLE',520,'AVAILABLE'),
(@second_id,'1A','ECONOMY','WINDOW',680,'AVAILABLE'),(@second_id,'1B','ECONOMY','MIDDLE',680,'AVAILABLE');
