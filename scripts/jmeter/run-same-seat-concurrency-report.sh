#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JMX_FILE="$ROOT_DIR/scripts/jmeter/same-seat-order-race.jmx"
VERIFY_SCRIPT="$ROOT_DIR/scripts/concurrency/verify-same-seat-order-race.sh"
TEMPLATE_FILE="$ROOT_DIR/scripts/jmeter/same-seat-concurrency-report-template.md"

REPORT_ROOT="${REPORT_ROOT:-$ROOT_DIR/reports/jmeter}"
RUN_TIMESTAMP="${RUN_TIMESTAMP:-$(date +%Y%m%d-%H%M%S)}"
EVIDENCE_DIR="${EVIDENCE_DIR:-$REPORT_ROOT/$RUN_TIMESTAMP}"
JTL_FILE="$EVIDENCE_DIR/same-seat-order-race.jtl"
HTML_DIR="$EVIDENCE_DIR/html"
RUNNER_LOG="$EVIDENCE_DIR/runner.log"
COMMAND_SUMMARY="$EVIDENCE_DIR/command-summary.md"
JMETER_OUTPUT="$EVIDENCE_DIR/jmeter-output.log"
VERIFY_OUTPUT="$EVIDENCE_DIR/database-verification.txt"
SUMMARY_FILE="$EVIDENCE_DIR/summary.md"
ORDER_SAMPLER_LABEL="同座位创建订单"

THREADS="${THREADS:-20}"
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-flight_booking}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-skybooker-mysql}"
JMETER_BIN="${JMETER_BIN:-jmeter}"

SECRET_VALUES=()

die() {
  log "错误：$*"
  exit 1
}

sanitize_text() {
  local text="$1"
  local secret
  for secret in "${SECRET_VALUES[@]}"; do
    if [ -n "$secret" ]; then
      text="${text//"$secret"/******}"
    fi
  done
  printf '%s\n' "$text" | sed -E \
    -e 's/(Bearer )[A-Za-z0-9._~+\/=-]+/\1******/g' \
    -e 's/((password|passwd|pwd|secret|token|jwt|api[_-]?key)[[:space:]]*[=:][[:space:]]*)[^[:space:]]+/\1******/Ig'
}

sanitize_file() {
  local file="$1"
  while IFS= read -r line || [ -n "$line" ]; do
    sanitize_text "$line"
  done < "$file"
}

log() {
  local line
  line="$(sanitize_text "$*")"
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$line" | tee -a "$RUNNER_LOG"
}

require_var() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    die "缺少必填变量 $name"
  fi
}

require_number() {
  local name="$1"
  local value="${!name:-}"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || [ "$value" -le 0 ]; then
    die "$name 必须是正整数"
  fi
}

add_secret_value() {
  local value="${1:-}"
  if [ -n "$value" ]; then
    SECRET_VALUES+=("$value")
  fi
}

collect_secret_values() {
  local name value
  add_secret_value "${USER_PASSWORD:-}"
  add_secret_value "${MYSQL_PASSWORD:-}"
  for name in $(env | sed -n -E 's/^([^=]*(PASSWORD|PASSWD|SECRET|TOKEN|JWT|API_KEY|CREDENTIAL)[^=]*)=.*/\1/Ip'); do
    value="${!name:-}"
    add_secret_value "$value"
  done
}

masked_value() {
  local name="$1"
  local value="${!name:-}"
  if [[ "$name" =~ (PASSWORD|PASSWD|SECRET|TOKEN|JWT|API_KEY|CREDENTIAL) ]]; then
    printf '******'
  else
    printf '%s' "$value"
  fi
}

write_command_summary() {
  {
    printf '# JMeter 同座位并发报告命令摘要\n\n'
    printf '%s `%s`\n' "- 生成时间：" "$(date '+%Y-%m-%d %H:%M:%S')"
    printf '%s `%s`\n' "- 证据目录：" "$EVIDENCE_DIR"
    printf '%s `%s`\n' "- JMeter 计划：" "$JMX_FILE"
    printf '%s `%s`\n' "- JMeter 结果：" "$JTL_FILE"
    printf '%s `%s`\n\n' "- HTML 报告：" "$HTML_DIR"
    printf '## 运行变量\n\n'
    for name in BASE_URL USER_EMAIL USER_PASSWORD FLIGHT_ID PASSENGER_ID SEAT_ID THREADS MYSQL_HOST MYSQL_PORT MYSQL_DB MYSQL_USER MYSQL_PASSWORD MYSQL_CONTAINER JMETER_BIN; do
      printf '%s `%s=%s`\n' "-" "$name" "$(masked_value "$name")"
    done
    printf '\n## 等效 JMeter 命令\n\n'
    printf '```bash\n'
    printf '%q -n -t %q -l %q -e -o %q \\\n' "$JMETER_BIN" "$JMX_FILE" "$JTL_FILE" "$HTML_DIR"
    printf '  -JBASE_URL=%q \\\n' "$BASE_URL"
    printf '  -JUSER_EMAIL=%q \\\n' "$USER_EMAIL"
    printf '  -JUSER_PASSWORD=****** \\\n'
    printf '  -JFLIGHT_ID=%q \\\n' "$FLIGHT_ID"
    printf '  -JPASSENGER_ID=%q \\\n' "$PASSENGER_ID"
    printf '  -JSEAT_ID=%q \\\n' "$SEAT_ID"
    printf '  -JTHREADS=%q\n' "$THREADS"
    printf '```\n'
  } > "$COMMAND_SUMMARY"
}

run_and_capture() {
  local output_file="$1"
  shift
  local tmp_file status
  tmp_file="$(mktemp)"
  set +e
  "$@" > "$tmp_file" 2>&1
  status=$?
  set -e
  sanitize_file "$tmp_file" | tee "$output_file" | tee -a "$RUNNER_LOG" >/dev/null
  rm -f "$tmp_file"
  return "$status"
}

read_jmeter_stats() {
  local stats
  stats="$(awk -F',' -v target_label="$ORDER_SAMPLER_LABEL" '
    function clean(value) {
      gsub(/^"/, "", value)
      gsub(/"$/, "", value)
      return value
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        header = clean($i)
        if (header == "label") label_col = i
        if (header == "success") success_col = i
      }
      next
    }
    label_col && success_col {
      label = clean($label_col)
      success = tolower(clean($success_col))
      if (label == target_label) {
        total++
        if (success == "true") passed++
      }
    }
    END {
      if (!label_col || !success_col) {
        print "HEADER_ERROR HEADER_ERROR"
        exit 2
      }
      print total + 0, passed + 0
    }
  ' "$JTL_FILE")" || die "无法解析 JMeter 结果文件：$JTL_FILE"

  read -r ORDER_TOTAL ORDER_SUCCESS <<< "$stats"
  if [ "$ORDER_TOTAL" = "HEADER_ERROR" ]; then
    die "JMeter 结果文件缺少 label 或 success 列：$JTL_FILE"
  fi
  ORDER_FAILURE=$((ORDER_TOTAL - ORDER_SUCCESS))
}

write_summary() {
  local verification_status="$1"
  local conclusion
  if [ "$ORDER_TOTAL" -eq "$THREADS" ] \
    && [ "$ORDER_SUCCESS" -eq 1 ] \
    && [ "$ORDER_FAILURE" -eq $((THREADS - 1)) ] \
    && [ "$verification_status" -eq 0 ]; then
    conclusion="通过：同一座位并发创建订单请求数等于线程数，只有 1 个请求成功，其余请求失败，数据库最终也只保留 1 个订单乘机人座位绑定。"
  else
    conclusion="未通过：请检查 JMeter 请求总数、成功数、失败数或数据库校验输出，不应作为成功并发报告提交。"
  fi

  {
    printf '# SkyBooker 同座位并发下单 JMeter 报告摘要\n\n'
    printf '## 1. 测试目的\n\n'
    printf '验证多个并发请求同时购买同一 `seatId` 时，系统不会出现超卖，最终只能有一个订单绑定目标座位。\n\n'
    printf '## 2. 测试环境\n\n'
    printf '%s `%s`\n' "- 后端地址：" "$BASE_URL"
    printf '%s `%s`\n' "- JMeter 线程数：" "$THREADS"
    printf '%s `%s:%s/%s`\n' "- 数据库：" "$MYSQL_HOST" "$MYSQL_PORT" "$MYSQL_DB"
    printf '%s `%s`\n\n' "- 证据目录：" "$EVIDENCE_DIR"
    printf '## 3. 测试目标\n\n'
    printf '%s `%s`\n' "- 航班 ID：" "$FLIGHT_ID"
    printf '%s `%s`\n' "- 乘机人 ID：" "$PASSENGER_ID"
    printf '%s `%s`\n\n' "- 座位 ID：" "$SEAT_ID"
    printf '## 4. JMeter 结果\n\n'
    printf '%s `%s`\n' "- 同座位创建订单请求数：" "$ORDER_TOTAL"
    printf '%s `%s`\n' "- 成功数：" "$ORDER_SUCCESS"
    printf '%s `%s`\n' "- 失败数：" "$ORDER_FAILURE"
    printf '%s `%s`\n' "- .jtl 文件：" "$JTL_FILE"
    printf '%s `%s`\n\n' "- HTML 报告：" "$HTML_DIR"
    printf '## 5. 数据库校验\n\n'
    printf '校验输出保存在：`%s`\n\n' "$VERIFY_OUTPUT"
    printf '```text\n'
    if [ -s "$VERIFY_OUTPUT" ]; then
      cat "$VERIFY_OUTPUT"
    else
      printf '数据库校验未产生输出。\n'
    fi
    printf '```\n\n'
    printf '## 6. 最终结论\n\n'
    printf '%s\n\n' "$conclusion"
    printf '> 说明：JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；课程报告和 PPT 以本中文摘要及数据库校验输出为准。\n'
  } > "$SUMMARY_FILE"
}

main() {
  mkdir -p "$EVIDENCE_DIR"
  : > "$RUNNER_LOG"

  require_var BASE_URL
  require_var USER_EMAIL
  require_var USER_PASSWORD
  require_var FLIGHT_ID
  require_var PASSENGER_ID
  require_var SEAT_ID
  require_var MYSQL_PASSWORD
  require_number FLIGHT_ID
  require_number PASSENGER_ID
  require_number SEAT_ID
  require_number THREADS
  collect_secret_values

  command -v "$JMETER_BIN" >/dev/null 2>&1 || die "找不到 JMeter 命令：$JMETER_BIN，请先安装 JMeter 或设置 JMETER_BIN"
  [ -f "$JMX_FILE" ] || die "找不到 JMeter 计划：$JMX_FILE"
  [ -x "$VERIFY_SCRIPT" ] || die "数据库校验脚本不可执行：$VERIFY_SCRIPT"

  write_command_summary
  if [ -f "$TEMPLATE_FILE" ]; then
    cp "$TEMPLATE_FILE" "$EVIDENCE_DIR/summary-template.md"
  fi

  log "证据目录：$EVIDENCE_DIR"
  log "开始运行 JMeter 同座位并发测试"
  run_and_capture "$JMETER_OUTPUT" "$JMETER_BIN" \
    -n \
    -t "$JMX_FILE" \
    -l "$JTL_FILE" \
    -e \
    -o "$HTML_DIR" \
    -JBASE_URL="$BASE_URL" \
    -JUSER_EMAIL="$USER_EMAIL" \
    -JUSER_PASSWORD="$USER_PASSWORD" \
    -JFLIGHT_ID="$FLIGHT_ID" \
    -JPASSENGER_ID="$PASSENGER_ID" \
    -JSEAT_ID="$SEAT_ID" \
    -JTHREADS="$THREADS" || die "JMeter 执行失败，详情见 $JMETER_OUTPUT"

  [ -s "$JTL_FILE" ] || die "JMeter 未生成结果文件：$JTL_FILE"
  [ -d "$HTML_DIR" ] || die "JMeter 未生成 HTML 报告目录：$HTML_DIR"

  read_jmeter_stats
  log "同座位创建订单请求数：$ORDER_TOTAL，成功数：$ORDER_SUCCESS，失败数：$ORDER_FAILURE"

  log "开始执行数据库校验"
  verification_status=0
  run_and_capture "$VERIFY_OUTPUT" env \
    SEAT_ID="$SEAT_ID" \
    MYSQL_HOST="$MYSQL_HOST" \
    MYSQL_PORT="$MYSQL_PORT" \
    MYSQL_DB="$MYSQL_DB" \
    MYSQL_USER="$MYSQL_USER" \
    MYSQL_PASSWORD="$MYSQL_PASSWORD" \
    MYSQL_CONTAINER="$MYSQL_CONTAINER" \
    "$VERIFY_SCRIPT" || verification_status=$?

  write_summary "$verification_status"

  if [ "$ORDER_TOTAL" -eq 0 ]; then
    die "JMeter 结果中没有找到“$ORDER_SAMPLER_LABEL”请求"
  fi
  if [ "$ORDER_TOTAL" -ne "$THREADS" ]; then
    die "期望同座位创建订单请求数等于线程数 $THREADS，实际为 $ORDER_TOTAL"
  fi
  if [ "$ORDER_SUCCESS" -ne 1 ]; then
    die "期望同座位创建订单成功数为 1，实际为 $ORDER_SUCCESS"
  fi
  if [ "$ORDER_FAILURE" -ne $((THREADS - 1)) ]; then
    die "期望同座位创建订单失败数为 $((THREADS - 1))，实际为 $ORDER_FAILURE"
  fi
  if [ "$verification_status" -ne 0 ]; then
    die "数据库校验失败，详情见 $VERIFY_OUTPUT"
  fi

  log "报告生成完成：$SUMMARY_FILE"
}

main "$@"
