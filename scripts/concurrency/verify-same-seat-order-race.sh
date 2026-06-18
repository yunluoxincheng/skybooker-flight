#!/usr/bin/env sh
set -eu

: "${SEAT_ID:?请设置 SEAT_ID 为本次并发测试使用的 flight_seat.id}"

case "$SEAT_ID" in
  *[!0-9]* | "")
    echo "SEAT_ID 必须是数字类型的 flight_seat.id。" >&2
    exit 1
    ;;
esac

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-flight_booking}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-skybooker-mysql}"
: "${MYSQL_PASSWORD:?请设置 MYSQL_PASSWORD 用于数据库校验}"

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

  if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
    docker exec -i -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" mysql \
      -u "$MYSQL_USER" \
      -D "$MYSQL_DB" \
      --batch \
      --raw \
      --skip-column-names \
      -e "$sql"
    return
  fi

  echo "未安装 mysql 客户端，且未发现正在运行的 $MYSQL_CONTAINER 容器。" >&2
  exit 1
}

row_count=$(run_mysql "SELECT COUNT(*) FROM order_passenger op WHERE op.seat_id = $SEAT_ID;")
distinct_order_count=$(run_mysql "SELECT COUNT(DISTINCT op.order_id) FROM order_passenger op WHERE op.seat_id = $SEAT_ID;")

echo "目标座位 ID：$SEAT_ID"
echo "目标座位的订单乘机人绑定行数：$row_count"
echo "目标座位绑定的不同订单数：$distinct_order_count"

echo "座位状态："
run_mysql "SELECT id, flight_id, seat_no, status, locked_by_order_id FROM flight_seat WHERE id = $SEAT_ID;"

echo "订单乘机人座位绑定："
run_mysql "SELECT op.order_id, op.passenger_id, op.seat_id, o.status, o.created_at FROM order_passenger op JOIN ticket_order o ON o.id = op.order_id WHERE op.seat_id = $SEAT_ID ORDER BY op.order_id;"

if [ "$row_count" -ne 1 ] || [ "$distinct_order_count" -ne 1 ]; then
  echo "并发测试后，座位 $SEAT_ID 应只有 1 行订单乘机人绑定，并且只绑定 1 个不同订单。" >&2
  exit 1
fi

echo "并发数据库校验通过。"
