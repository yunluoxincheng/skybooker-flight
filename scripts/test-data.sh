#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="skybooker-test-data"
DEFAULT_REPO="yunluoxincheng/skybooker-flight"
DEFAULT_REF="main"
DEFAULT_DIR="${SKYBOOKER_DEPLOY_DIR:-/opt/skybooker}"
MIN_FLYWAY_VERSION=18

COMMAND="status"
DEPLOY_DIR="$DEFAULT_DIR"
REPO="${SKYBOOKER_REPO:-$DEFAULT_REPO}"
REF="${SKYBOOKER_REF:-$DEFAULT_REF}"
SOURCE_DIR="${SKYBOOKER_TEST_DATA_SOURCE_DIR:-}"
PROFILE="dev"
SEED="20260707"
BASE_DATE="${SKYBOOKER_BASE_DATE:-}"
COMPONENTS="all"
SCENARIOS="all"
SCENARIOS_EXPLICIT="false"
OUTPUT=""
FILE=""
SUMMARY_FILE=""
YES="false"
ALLOW_PRODUCTION="false"
CONFIRM_PRODUCTION="false"
NO_AUTO_DEPENDENCIES="false"
DATABASE="false"
HOST="${MYSQL_HOST:-127.0.0.1}"
PORT="${MYSQL_PORT:-3306}"
DB_USER="${MYSQL_USER:-root}"
DB_NAME="${MYSQL_DB:-flight_booking}"
DB_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_BIN="mysql"
RESOLVED_SHA=""
HOST_EXPLICIT="false"
PORT_EXPLICIT="false"
USER_EXPLICIT="false"
DB_EXPLICIT="false"

usage() {
  cat <<'EOF'
Usage:
  test-data.sh <doctor|generate|validate|import|seed|clean|status> [options]

Commands:
  doctor       Check Python, optional Docker/Compose, MySQL connectivity and Flyway.
  generate     Generate SQL only; never connects to the database.
  validate     Run static validation, or database consistency checks with --database.
  import       Import an existing SQL file after confirmation.
  seed         Generate, validate and import one profile in a single operation.
  clean        Remove only ownership-registered rows after confirmation.
  status       Show ownership-registered records and connecting-itinerary coverage.

Common options:
  --dir DIR                    Deployment/config directory (default: /opt/skybooker)
  --repo OWNER/REPO            GitHub repository for downloaded helpers
  --ref REF                    Exact Git ref for downloaded helpers (default: main)
  --source-dir DIR             Local repository root; avoids downloads
  --profile dev|test|perf      Dataset scale (default: dev)
  --seed NUMBER                Deterministic seed (default: 20260707)
  --base-date YYYY-MM-DD       Flight date base (default: today for generate/seed)
  --components LIST            reference,users,flights,orders,refunds,changes,waitlists,ai,all
  --scenarios LIST             direct,connecting,payment,cancel,refund,change,waitlist,sold-out,delayed,near-departure,all
                               (omitted: all applicable scenarios within selected components)
  --no-auto-dependencies       Fail when components or scenarios omit dependencies
  --output FILE                Generated SQL destination
  --file FILE                  Existing SQL file for validate/import
  --yes                        Skip destructive-operation confirmation
  --allow-production           Permit writes in a production-marked environment
  --confirm-production         Required second confirmation for production writes
  --database                   Validate against MySQL as well as a seed file
  -h, --help                  Show this help

Database options:
  --host HOST --port PORT --user USER --db NAME --mysql PATH

Examples:
  ./scripts/test-data.sh seed --profile dev --scenarios all --yes
  ./scripts/test-data.sh generate --profile test --components flights,orders --output /tmp/test.sql
  ./scripts/test-data.sh validate --file /tmp/test.sql
  ./scripts/test-data.sh clean --profile dev --yes
EOF
}

log() {
  printf '[%s] %s\n' "$APP_NAME" "$*"
}

fail() {
  printf '[%s] ERROR: %s\n' "$APP_NAME" "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' was not found."
}

env_file_value() {
  local key="$1"
  [[ -f "$DEPLOY_DIR/.env" ]] || return 1
  awk -v key="$key" '$0 ~ "^[[:space:]]*" key "=" { sub("^[[:space:]]*" key "=", ""); print; exit }' "$DEPLOY_DIR/.env"
}

normalized_env_value() {
  local value="$1"
  value="${value%%#*}"
  value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  value="${value#\"}"; value="${value%\"}"
  value="${value#\'}"; value="${value%\'}"
  printf '%s' "${value,,}"
}

is_production_environment() {
  local key value
  for key in APP_ENV ENVIRONMENT SPRING_PROFILES_ACTIVE DEPLOY_ENV NODE_ENV; do
    value="${!key:-}"
    if [[ -z "$value" ]]; then
      value="$(env_file_value "$key" || true)"
    fi
    value="$(normalized_env_value "$value")"
    case ",$value," in
      *,prod,*|*,production,*|*,live,*) return 0 ;;
    esac
  done
  return 1
}

guard_write_environment() {
  if is_production_environment; then
    [[ "$ALLOW_PRODUCTION" == "true" && "$CONFIRM_PRODUCTION" == "true" ]] ||
      fail "Refusing seed/import/clean in a production environment; use --allow-production --confirm-production only for an intentional operation."
    [[ "$YES" == "true" ]] ||
      fail "Production writes also require --yes after the explicit production override."
    log "WARNING: production override accepted; only ownership-registered rows will be changed."
  fi
}

load_database_config() {
  local value
  value="$(env_file_value MYSQL_HOST || true)"; [[ -n "$value" && "$HOST_EXPLICIT" != "true" && -z "${MYSQL_HOST:-}" ]] && HOST="$value"
  value="$(env_file_value MYSQL_PORT || true)"; [[ -n "$value" && "$PORT_EXPLICIT" != "true" && -z "${MYSQL_PORT:-}" ]] && PORT="$value"
  value="$(env_file_value MYSQL_USER || true)"; [[ -n "$value" && "$USER_EXPLICIT" != "true" && -z "${MYSQL_USER:-}" ]] && DB_USER="$value"
  value="$(env_file_value MYSQL_DB || true)"; [[ -n "$value" && "$DB_EXPLICIT" != "true" && -z "${MYSQL_DB:-}" ]] && DB_NAME="$value"
  value="$(env_file_value MYSQL_PASSWORD || true)"
  if [[ -z "$DB_PASSWORD" ]]; then
    DB_PASSWORD="$value"
  fi
}

local_source_dir() {
  if [[ -n "$SOURCE_DIR" ]]; then
    printf '%s\n' "$SOURCE_DIR"
    return
  fi
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ -f "$script_dir/generate_test_data.py" ]]; then
    printf '%s\n' "$(dirname "$script_dir")"
  fi
}

SOURCE_CACHE=""
resolve_remote_ref() {
  [[ -n "$RESOLVED_SHA" ]] && return
  [[ "$REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || fail "Invalid GitHub repository '$REPO'."
  [[ "$REF" =~ ^[A-Za-z0-9._/-]+$ ]] || fail "Invalid Git ref '$REF'."
  require_command curl
  require_command python3
  local payload
  payload="$(curl -fsSL -H 'Accept: application/vnd.github+json' "https://api.github.com/repos/$REPO/commits/$REF")" ||
    fail "Could not resolve Git ref '$REF' in '$REPO'."
  RESOLVED_SHA="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("sha", ""))')"
  [[ "$RESOLVED_SHA" =~ ^[0-9a-fA-F]{40}$ ]] || fail "Git ref '$REF' did not resolve to a commit SHA."
  RESOLVED_SHA="${RESOLVED_SHA,,}"
  SOURCE_CACHE="$DEPLOY_DIR/.skybooker-test-data/${REPO//\//_}/${REF//\//_}/$RESOLVED_SHA"
  log "Resolved helper ref '$REF' to $RESOLVED_SHA" >&2
}

source_file() {
  local relative="$1"
  local local_root
  local_root="$(local_source_dir || true)"
  if [[ -n "$local_root" && -f "$local_root/$relative" ]]; then
    printf '%s\n' "$local_root/$relative"
    return
  fi
  resolve_remote_ref
  local destination="$SOURCE_CACHE/$relative"
  if [[ ! -f "$destination" ]]; then
    require_command curl
    mkdir -p "$(dirname "$destination")"
    curl -fsSL "https://raw.githubusercontent.com/$REPO/$RESOLVED_SHA/$relative" -o "$destination"
  fi
  printf '%s\n' "$destination"
}

python_script() {
  local script="$1"
  if [[ "$script" == "generate_test_data.py" ]]; then
    # The generator loads versioned catalogs relative to its own path. Fetch
    # them into the same ref/SHA cache before running a remotely downloaded
    # generator.
    source_file "scripts/data/airports-cn.json" >/dev/null
    source_file "scripts/data/airports-international.json" >/dev/null
    source_file "scripts/data/airlines.json" >/dev/null
  fi
  source_file "scripts/$script"
}

data_dir() {
  printf '%s\n' "$DEPLOY_DIR/test-data"
}

default_sql_file() {
  printf '%s/seed-%s.sql\n' "$(data_dir)" "$PROFILE"
}

prepare_database_access() {
  load_database_config
  if [[ -f "$DEPLOY_DIR/docker-compose.yml" ]] && command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
    COMPOSE_ENV="$DEPLOY_DIR/.env"
  else
    COMPOSE_FILE=""
    COMPOSE_ENV=""
  fi
}

mysql_container() {
  [[ -n "$COMPOSE_FILE" ]] || return 1
  local args=(docker compose -f "$COMPOSE_FILE" --project-directory "$DEPLOY_DIR")
  [[ -f "$COMPOSE_ENV" ]] && args+=(--env-file "$COMPOSE_ENV")
  "${args[@]}" ps -q mysql 2>/dev/null | head -n 1
}

run_mysql() {
  prepare_database_access
  local container
  container="$(mysql_container || true)"
  if [[ -n "$container" ]]; then
    docker exec -i "$container" sh -c \
      'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql --default-character-set=utf8mb4 -uroot -D "$1" --batch --skip-column-names --raw' \
      sh "$DB_NAME"
    return
  fi
  require_command "$MYSQL_BIN"
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" --protocol=tcp -h "$HOST" -P "$PORT" -u "$DB_USER" -D "$DB_NAME" --default-character-set=utf8mb4 --batch --skip-column-names --raw
}

require_flyway_schema() {
  local schema
  schema="$(run_mysql <<'SQL'
SELECT COALESCE(MAX(CAST(version AS UNSIGNED)), 0) FROM flyway_schema_history WHERE success=1;
SQL
)" || fail "Could not read Flyway schema version. Run the backend migrations first."
  schema="$(printf '%s' "$schema" | tr -d '[:space:]')"
  [[ "$schema" =~ ^[0-9]+$ && "$schema" -ge "$MIN_FLYWAY_VERSION" ]] ||
    fail "Flyway schema V$schema is incompatible; test-data ownership requires V$MIN_FLYWAY_VERSION or newer."
  log "Flyway schema: $schema (minimum: $MIN_FLYWAY_VERSION)"
}

confirm_destructive() {
  [[ "$YES" == "true" ]] && return
  [[ -t 0 ]] || fail "This operation rewrites or deletes seed data; rerun with --yes in a non-interactive environment."
  local answer
  read -r -p "This affects profile '$PROFILE' in database '$DB_NAME'. Continue? [y/N] " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || fail "Cancelled."
}

generate_sql() {
  require_command python3
  local output="$OUTPUT"
  [[ -n "$output" ]] || output="$(default_sql_file)"
  mkdir -p "$(dirname "$output")"
  local summary="$SUMMARY_FILE"
  [[ -n "$summary" ]] || summary="$(data_dir)/seed-$PROFILE.json"
  mkdir -p "$(dirname "$summary")"
  local generator effective_base_date
  generator="$(python_script generate_test_data.py)"
  effective_base_date="${BASE_DATE:-$(date +%F)}"
  local args=("$generator" --profile "$PROFILE" --seed "$SEED" --components "$COMPONENTS" --output "$output" --summary-file "$summary")
  [[ "$SCENARIOS_EXPLICIT" == "true" ]] && args+=(--scenarios "$SCENARIOS")
  [[ -n "$RESOLVED_SHA" ]] && args+=(--source-ref "$RESOLVED_SHA")
  args+=(--base-date "$effective_base_date")
  [[ "$NO_AUTO_DEPENDENCIES" == "true" ]] && args+=(--no-auto-dependencies)
  python3 "${args[@]}"
  OUTPUT="$output"
  SUMMARY_FILE="$summary"
}

static_validate() {
  local file="$1"
  require_command python3
  python3 "$(python_script validate_test_data.py)" --file "$file"
}

database_validate() {
  local file="$1"
  local output
  local coverage_output coverage_sql
  local batch_key profile seed source_ref source_condition coverage_required
  local flight_codes mainland_codes non_mainland_codes required_routes reference_codes legacy_codes catalog_fingerprint
  IFS=$'\x1f' read -r batch_key profile seed source_ref coverage_required flight_codes mainland_codes non_mainland_codes required_routes reference_codes legacy_codes catalog_fingerprint <<< "$(python3 - "$file" <<'PY'
import json
import re
import sys
text = open(sys.argv[1], encoding="utf-8").read()
match = re.search(
    r"-- SKYBOOKER_SEED_SUMMARY_BEGIN\s*\n-- (?P<json>\{.*?\})\s*\n-- SKYBOOKER_SEED_SUMMARY_END",
    text,
    re.DOTALL,
)
if not match:
    raise SystemExit(1)
summary = json.loads(match.group("json"))
batch = summary.get("batchKey")
profile = summary.get("profile")
seed = summary.get("seed")
source_ref = summary.get("sourceRef")
if not re.fullmatch(r"skybooker:(?:dev|test|perf)", str(batch)):
    raise SystemExit(1)
if profile not in {"dev", "test", "perf"} or not isinstance(seed, int) or isinstance(seed, bool):
    raise SystemExit(1)
if source_ref is not None and not re.fullmatch(r"[0-9a-fA-F]{40}", str(source_ref)):
    raise SystemExit(1)
def codes(name):
    values = summary.get(name, [])
    if not isinstance(values, list) or any(not isinstance(value, str) or not re.fullmatch(r"[A-Z]{3}", value) for value in values):
        raise SystemExit(1)
    return ",".join(sorted(set(values)))
def quoted_codes(name):
    values = codes(name).split(",")
    return ",".join(f"'{value}'" for value in values if value)
fingerprint = summary.get("airportCatalogFingerprintSha256")
if not isinstance(fingerprint, str) or not re.fullmatch(r"[0-9a-f]{64}", fingerprint):
    raise SystemExit(1)
print("\x1f".join([
    batch, profile, str(seed), source_ref or "NULL",
    str(bool(summary.get("flightCoverageRequired"))).lower(),
    codes("flightAirportCodes"), codes("mainlandFlightAirportCodes"),
    codes("nonMainlandFlightAirportCodes"), json.dumps(summary.get("requiredBidirectionalRoutes", []), separators=(",", ":")),
    quoted_codes("airportCodes"), quoted_codes("legacyManagedAirportCodes"), fingerprint,
]))
PY
  )" || fail "Could not read ownership batch metadata from $file."
  [[ "$batch_key" == "skybooker:$profile" ]] || fail "Seed summary batch/profile metadata is inconsistent."
  if [[ "$source_ref" == "NULL" ]]; then
    source_condition="b.source_ref <=> NULL"
  else
    source_condition="b.source_ref = '$source_ref'"
  fi
  output="$(run_mysql <<SQL
SELECT 'batch_metadata_mismatch', CASE WHEN EXISTS (
  SELECT 1 FROM test_data_batch b
  WHERE b.batch_key='${batch_key}' AND b.profile='${profile}' AND b.seed=${seed}
    AND ${source_condition}
) THEN 0 ELSE 1 END;
SELECT 'flight_remaining_matches_available_seats', COUNT(*) FROM flight f WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='${batch_key}' AND o.table_name='flight' AND o.row_id=f.id) AND f.remaining_seats <> (SELECT COUNT(*) FROM flight_seat s WHERE s.flight_id=f.id AND s.status='AVAILABLE');
SELECT 'flight_cabin_totals_match', COUNT(*) FROM flight f WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='${batch_key}' AND o.table_name='flight' AND o.row_id=f.id) AND f.total_seats <> (SELECT COALESCE(SUM(c.total_seats),0) FROM flight_cabin c WHERE c.flight_id=f.id);
SELECT 'seat_prices_match_cabin', COUNT(*) FROM flight_seat s JOIN flight_cabin c ON c.flight_id=s.flight_id AND c.cabin_class=s.cabin_class WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='${batch_key}' AND o.table_name='flight_seat' AND o.row_id=s.id) AND s.price <> c.price;
SELECT 'sold_seats_have_snapshot', COUNT(*) FROM flight_seat s LEFT JOIN order_passenger op ON op.seat_id=s.id LEFT JOIN order_segment_passenger osp ON osp.seat_id=s.id WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='${batch_key}' AND o.table_name='flight_seat' AND o.row_id=s.id) AND s.status='SOLD' AND op.id IS NULL AND osp.id IS NULL;
SELECT 'locked_seats_have_order_and_expiry', COUNT(*) FROM flight_seat s WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='${batch_key}' AND o.table_name='flight_seat' AND o.row_id=s.id) AND s.status='LOCKED' AND (s.locked_by_order_id IS NULL OR s.lock_expire_time IS NULL);
SELECT 'connecting_orders_have_two_segments', COUNT(*) FROM ticket_order o WHERE EXISTS (SELECT 1 FROM test_data_ownership own WHERE own.batch_key='${batch_key}' AND own.table_name='ticket_order' AND own.row_id=o.id) AND o.journey_type='CONNECTING' AND (SELECT COUNT(*) FROM ticket_order_segment s WHERE s.order_id=o.id) <> 2;
SELECT 'connecting_passengers_match', COUNT(*) FROM ticket_order o WHERE EXISTS (SELECT 1 FROM test_data_ownership own WHERE own.batch_key='${batch_key}' AND own.table_name='ticket_order' AND own.row_id=o.id) AND o.journey_type='CONNECTING' AND (SELECT GROUP_CONCAT(DISTINCT p.passenger_id ORDER BY p.passenger_id) FROM order_segment_passenger p JOIN ticket_order_segment s ON s.id=p.order_segment_id WHERE s.order_id=o.id AND s.segment_no=1) <> (SELECT GROUP_CONCAT(DISTINCT p.passenger_id ORDER BY p.passenger_id) FROM order_segment_passenger p JOIN ticket_order_segment s ON s.id=p.order_segment_id WHERE s.order_id=o.id AND s.segment_no=2);
SELECT 'connecting_change_snapshots_complete', COUNT(*) FROM connecting_change_record c WHERE EXISTS (SELECT 1 FROM test_data_ownership own WHERE own.batch_key='${batch_key}' AND own.table_name='connecting_change_record' AND own.row_id=c.id) AND (SELECT COUNT(*) FROM connecting_change_segment s WHERE s.change_record_id=c.id) <> 4;
SELECT 'airport_catalog_rows_missing_or_disabled', 311 - COUNT(*) FROM airport WHERE code IN (${reference_codes}) AND status='ENABLED';
SET SESSION group_concat_max_len=1048576;
SELECT 'airport_catalog_fields_mismatch', CASE WHEN LOWER(SHA2(GROUP_CONCAT(CONCAT_WS(CHAR(9), code, name, city, province, status) ORDER BY code SEPARATOR '\n'), 256))='${catalog_fingerprint}' THEN 0 ELSE 1 END FROM airport WHERE code IN (${reference_codes});
SELECT 'legacy_managed_airports_still_enabled', COUNT(*) FROM airport WHERE code IN (${legacy_codes}) AND status <> 'DISABLED';
SQL
)"
  local failed="false"
  while IFS=$'\t' read -r name count; do
    [[ -z "$name" ]] && continue
    if [[ "$count" != "0" ]]; then
      printf '[%s] FAIL %s: %s\n' "$APP_NAME" "$name" "$count" >&2
      failed="true"
    else
      printf '[%s] OK %s\n' "$APP_NAME" "$name"
    fi
  done <<< "$output"
  if [[ "$coverage_required" == "true" ]]; then
    coverage_sql="$(python3 - "$file" "$batch_key" <<'PY'
import json
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
batch = sys.argv[2]
match = re.search(
    r"-- SKYBOOKER_SEED_SUMMARY_BEGIN\s*\n-- (?P<json>\{.*?\})\s*\n-- SKYBOOKER_SEED_SUMMARY_END",
    text,
    re.DOTALL,
)
if not match:
    raise SystemExit(1)
summary = json.loads(match.group("json"))

def codes(name):
    values = summary.get(name, [])
    if not isinstance(values, list) or any(not isinstance(value, str) or not re.fullmatch(r"[A-Z]{3}", value) for value in values):
        raise SystemExit(1)
    return ", ".join("'" + value + "'" for value in sorted(set(values)))

flight_codes = codes("flightAirportCodes")
mainland_codes = codes("mainlandFlightAirportCodes")
external_codes = codes("nonMainlandFlightAirportCodes")
print(f"SELECT 'airport_outbound_coverage', COUNT(*) FROM airport a WHERE a.code IN ({flight_codes}) AND NOT EXISTS (SELECT 1 FROM flight f JOIN test_data_ownership o ON o.batch_key='{batch}' AND o.table_name='flight' AND o.row_id=f.id WHERE f.departure_airport_id=a.id);")
print(f"SELECT 'airport_inbound_coverage', COUNT(*) FROM airport a WHERE a.code IN ({flight_codes}) AND NOT EXISTS (SELECT 1 FROM flight f JOIN test_data_ownership o ON o.batch_key='{batch}' AND o.table_name='flight' AND o.row_id=f.id WHERE f.arrival_airport_id=a.id);")
if mainland_codes and external_codes:
    print(f"SELECT 'international_gateway_coverage', COUNT(*) FROM airport external_airport WHERE external_airport.code IN ({external_codes}) AND NOT EXISTS (SELECT 1 FROM flight f JOIN test_data_ownership o ON o.batch_key='{batch}' AND o.table_name='flight' AND o.row_id=f.id JOIN airport departure ON departure.id=f.departure_airport_id JOIN airport arrival ON arrival.id=f.arrival_airport_id WHERE (departure.id=external_airport.id AND arrival.code IN ({mainland_codes})) OR (arrival.id=external_airport.id AND departure.code IN ({mainland_codes})));")
for index, pair in enumerate(summary.get("requiredBidirectionalRoutes", [])):
    if not isinstance(pair, list) or len(pair) != 2 or any(not isinstance(code, str) or not re.fullmatch(r"[A-Z]{3}", code) for code in pair):
        raise SystemExit(1)
    departure, arrival = pair
    print(f"SELECT 'bidirectional_route_{index}_{departure}_{arrival}', CASE WHEN EXISTS (SELECT 1 FROM flight f JOIN test_data_ownership o ON o.batch_key='{batch}' AND o.table_name='flight' AND o.row_id=f.id JOIN airport d ON d.id=f.departure_airport_id JOIN airport a ON a.id=f.arrival_airport_id WHERE d.code='{departure}' AND a.code='{arrival}') AND EXISTS (SELECT 1 FROM flight f JOIN test_data_ownership o ON o.batch_key='{batch}' AND o.table_name='flight' AND o.row_id=f.id JOIN airport d ON d.id=f.departure_airport_id JOIN airport a ON a.id=f.arrival_airport_id WHERE d.code='{arrival}' AND a.code='{departure}') THEN 0 ELSE 1 END;")
PY
    )" || fail "Could not build airport coverage checks from $file."
    coverage_output="$(run_mysql <<< "$coverage_sql")"
    while IFS=$'\t' read -r name count; do
      [[ -z "$name" ]] && continue
      if [[ "$count" != "0" ]]; then
        printf '[%s] FAIL %s: %s\n' "$APP_NAME" "$name" "$count" >&2
        failed="true"
      else
        printf '[%s] OK %s\n' "$APP_NAME" "$name"
      fi
    done <<< "$coverage_output"
  fi
  [[ "$failed" == "false" ]] || return 1
}

doctor() {
  local failed="false"
  if command -v python3 >/dev/null 2>&1; then log "Python: $(python3 --version 2>&1)"; else log "Python: missing"; failed="true"; fi
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "Docker Compose: available"
  else
    log "Docker Compose: unavailable (host MySQL fallback will be used)"
  fi
  prepare_database_access
  if container="$(mysql_container || true)"; then
    if [[ -n "$container" ]]; then
      log "MySQL container: $container"
    else
      log "MySQL container: not running (host MySQL fallback will be used)"
    fi
  fi
  if schema="$(run_mysql <<'SQL'
SELECT COALESCE(MAX(CAST(version AS UNSIGNED)), 0) FROM flyway_schema_history WHERE success=1;
SQL
)"; then
    log "MySQL: connected"
    schema="$(printf '%s' "$schema" | tr -d '[:space:]')"
    if [[ "$schema" =~ ^[0-9]+$ && "$schema" -ge "$MIN_FLYWAY_VERSION" ]]; then
      log "Flyway schema: $schema (minimum: $MIN_FLYWAY_VERSION)"
    else
      log "Flyway schema: $schema (requires V$MIN_FLYWAY_VERSION or newer)"
      failed="true"
    fi
  else
    log "MySQL: connection failed"
    failed="true"
  fi
  [[ "$failed" == "false" ]]
}

import_sql() {
  local require_confirmation="${1:-true}"
  local file="$FILE"
  [[ -n "$file" ]] || file="$OUTPUT"
  [[ -n "$file" && -f "$file" ]] || fail "SQL file not found; use --file FILE."
  static_validate "$file"
  prepare_database_access
  guard_write_environment
  require_flyway_schema
  if [[ "$require_confirmation" == "true" ]]; then
    confirm_destructive
  fi
  log "Importing $file into $DB_NAME"
  run_mysql < "$file"
}

seed() {
  generate_sql
  static_validate "$OUTPUT"
  prepare_database_access
  guard_write_environment
  require_flyway_schema
  confirm_destructive
  import_sql false
}

clean() {
  prepare_database_access
  guard_write_environment
  require_flyway_schema
  confirm_destructive
  local cleaner
  cleaner="$(python_script clean_test_data.py)"
  local sql_file="$(mktemp)"
  python3 "$cleaner" --profile "$PROFILE" --components "$COMPONENTS" --output "$sql_file"
  if ! run_mysql < "$sql_file"; then
    rm -f "$sql_file"
    return 1
  fi
  rm -f "$sql_file"
  log "Cleaned generated rows for profile '$PROFILE'; reference data and other profiles were retained."
}

status() {
  local batch_key="skybooker:$PROFILE"
  run_mysql <<SQL
SELECT 'users', COUNT(*) FROM test_data_ownership WHERE batch_key='${batch_key}' AND table_name='users';
SELECT 'passengers', COUNT(*) FROM test_data_ownership WHERE batch_key='${batch_key}' AND table_name='passenger';
SELECT 'flights', COUNT(*) FROM test_data_ownership WHERE batch_key='${batch_key}' AND table_name='flight';
SELECT 'seats', COUNT(*) FROM test_data_ownership WHERE batch_key='${batch_key}' AND table_name='flight_seat';
SELECT 'orders', COUNT(*) FROM test_data_ownership WHERE batch_key='${batch_key}' AND table_name='ticket_order';
SELECT 'connecting_itineraries', COUNT(*) FROM test_data_ownership WHERE batch_key='${batch_key}' AND table_name='connecting_itinerary';
SELECT 'connecting_orders', COUNT(*) FROM test_data_ownership o JOIN ticket_order t ON t.id=o.row_id WHERE o.batch_key='${batch_key}' AND o.table_name='ticket_order' AND t.journey_type='CONNECTING';
SELECT 'connecting_changes', COUNT(*) FROM test_data_ownership WHERE batch_key='${batch_key}' AND table_name='connecting_change_record';
SQL
}

parse_args() {
  if [[ $# -gt 0 && "$1" != --* && "$1" != "-h" ]]; then
    COMMAND="$1"
    shift
  fi
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dir) DEPLOY_DIR="$2"; shift 2 ;;
      --repo) REPO="$2"; shift 2 ;;
      --ref) REF="$2"; shift 2 ;;
      --source-dir) SOURCE_DIR="$2"; shift 2 ;;
      --profile) PROFILE="$2"; shift 2 ;;
      --seed) SEED="$2"; shift 2 ;;
      --base-date) BASE_DATE="$2"; shift 2 ;;
      --components) COMPONENTS="$2"; shift 2 ;;
      --scenarios) SCENARIOS="$2"; SCENARIOS_EXPLICIT="true"; shift 2 ;;
      --no-auto-dependencies) NO_AUTO_DEPENDENCIES="true"; shift ;;
      --output) OUTPUT="$2"; shift 2 ;;
      --file) FILE="$2"; shift 2 ;;
      --summary-file) SUMMARY_FILE="$2"; shift 2 ;;
      --yes) YES="true"; shift ;;
      --allow-production) ALLOW_PRODUCTION="true"; shift ;;
      --confirm-production) CONFIRM_PRODUCTION="true"; shift ;;
      --database) DATABASE="true"; shift ;;
      --host) HOST="$2"; HOST_EXPLICIT="true"; shift 2 ;;
      --port) PORT="$2"; PORT_EXPLICIT="true"; shift 2 ;;
      --user) DB_USER="$2"; USER_EXPLICIT="true"; shift 2 ;;
      --db) DB_NAME="$2"; DB_EXPLICIT="true"; shift 2 ;;
      --mysql) MYSQL_BIN="$2"; shift 2 ;;
      -h|--help) usage; exit 0 ;;
      *) fail "Unknown option: $1" ;;
    esac
  done
}

main() {
  parse_args "$@"
  case "$PROFILE" in dev|test|perf) ;; *) fail "Unknown profile '$PROFILE'." ;; esac
  case "$COMMAND" in
    generate|validate|seed|clean)
      local helper_root
      helper_root="$(local_source_dir || true)"
      if [[ -z "$helper_root" || ! -f "$helper_root/scripts/generate_test_data.py" ]]; then
        resolve_remote_ref
      fi
      ;;
  esac
  case "$COMMAND" in
    doctor) doctor ;;
    generate) generate_sql ;;
    validate)
      local file="$FILE"
      [[ -n "$file" ]] || file="$OUTPUT"
      [[ -n "$file" ]] || file="$(default_sql_file)"
      [[ -f "$file" ]] || fail "SQL file not found: $file"
      static_validate "$file"
      if [[ "$DATABASE" == "true" ]]; then
        database_validate "$file"
      fi
      ;;
    import) import_sql ;;
    seed) seed ;;
    clean) clean ;;
    status) status ;;
    *) usage; fail "Unknown command '$COMMAND'." ;;
  esac
}

main "$@"
