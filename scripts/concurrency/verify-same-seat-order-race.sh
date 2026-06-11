#!/usr/bin/env sh
set -eu

: "${SEAT_ID:?Set SEAT_ID to the target flight_seat.id used by the concurrency run}"

case "$SEAT_ID" in
  *[!0-9]* | "")
    echo "SEAT_ID must be a numeric flight_seat.id." >&2
    exit 1
    ;;
esac

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-flight_booking}"
MYSQL_USER="${MYSQL_USER:-root}"
: "${MYSQL_PASSWORD:?Set MYSQL_PASSWORD for database verification}"

run_mysql() {
  sql=$1

  if command -v mysql >/dev/null 2>&1; then
    MYSQL_PWD="$MYSQL_PASSWORD" mysql \
      -h "$MYSQL_HOST" \
      -P "$MYSQL_PORT" \
      -u "$MYSQL_USER" \
      -D "$MYSQL_DB" \
      --batch \
      --raw \
      --skip-column-names \
      -e "$sql"
    return
  fi

  if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx 'skybooker-mysql'; then
    docker exec -i -e MYSQL_PWD="$MYSQL_PASSWORD" skybooker-mysql mysql \
      -u "$MYSQL_USER" \
      -D "$MYSQL_DB" \
      --batch \
      --raw \
      --skip-column-names \
      -e "$sql"
    return
  fi

  echo "mysql client is not installed and skybooker-mysql container is not running." >&2
  exit 1
}

row_count=$(run_mysql "SELECT COUNT(*) FROM order_passenger op WHERE op.seat_id = $SEAT_ID;")
distinct_order_count=$(run_mysql "SELECT COUNT(DISTINCT op.order_id) FROM order_passenger op WHERE op.seat_id = $SEAT_ID;")

echo "Target seat: $SEAT_ID"
echo "Order-passenger rows for target seat: $row_count"
echo "Distinct order bindings for target seat: $distinct_order_count"

echo "Seat state:"
run_mysql "SELECT id, flight_id, seat_no, status, locked_by_order_id FROM flight_seat WHERE id = $SEAT_ID;"

echo "Order-passenger bindings:"
run_mysql "SELECT op.order_id, op.passenger_id, op.seat_id, o.status, o.created_at FROM order_passenger op JOIN ticket_order o ON o.id = op.order_id WHERE op.seat_id = $SEAT_ID ORDER BY op.order_id;"

if [ "$row_count" -ne 1 ] || [ "$distinct_order_count" -ne 1 ]; then
  echo "Expected exactly one order-passenger row and one distinct order binding for seat $SEAT_ID after the race." >&2
  exit 1
fi

echo "Concurrency verification passed."
