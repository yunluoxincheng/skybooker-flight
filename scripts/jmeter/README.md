# JMeter same-seat concurrency test

This directory contains the committed test plan for the SkyBooker same-seat booking race. Generated `.jtl`, HTML report, logs, and screenshots belong under `reports/` and are ignored by Git.

## Prerequisites

- Start MySQL, Redis, and the backend.
- Refresh demo dates if seeded flights are stale:

```bash
mysql -h localhost -P 3306 -u root -p flight_booking < scripts/refresh-demo-flight-dates.sql
```

- Choose one target `flight_seat.id` with `status = 'AVAILABLE'`, one owned `passenger.id` for `user1@example.com`, and the seat's `flight_id`.

Example lookup:

```sql
SELECT p.id AS passenger_id
FROM passenger p
JOIN users u ON u.id = p.user_id
WHERE u.email = 'user1@example.com'
ORDER BY p.id
LIMIT 1;

SELECT s.flight_id, s.id AS seat_id, s.seat_no
FROM flight_seat s
JOIN flight f ON f.id = s.flight_id
WHERE s.status = 'AVAILABLE'
  AND f.publish_status = 'PUBLISHED'
  AND f.departure_time > NOW()
ORDER BY s.id
LIMIT 1;
```

## Run

```bash
mkdir -p reports/jmeter
jmeter -n \
  -t scripts/jmeter/same-seat-order-race.jmx \
  -l reports/jmeter/same-seat-order-race.jtl \
  -e -o reports/jmeter/same-seat-order-race-html \
  -JBASE_URL=http://localhost:8080 \
  -JUSER_EMAIL=user1@example.com \
  -JUSER_PASSWORD='User@123456' \
  -JFLIGHT_ID=<flight_id> \
  -JPASSENGER_ID=<passenger_id> \
  -JSEAT_ID=<seat_id> \
  -JTHREADS=20
```

Expected result: one `POST /api/orders` request succeeds and the remaining synchronized requests fail with the standard occupied-seat or business-rule response. Then verify database state:

```bash
SEAT_ID=<seat_id> MYSQL_PASSWORD=<password> scripts/concurrency/verify-same-seat-order-race.sh
```

The verification script uses a local `mysql` client when available. If the Compose MySQL service is running and the host does not have `mysql` installed, it falls back to `docker exec skybooker-mysql`.
