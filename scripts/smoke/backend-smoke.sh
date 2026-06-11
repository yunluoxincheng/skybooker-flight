#!/usr/bin/env sh
set -eu

BASE_URL="${SKYBOOKER_BASE_URL:-http://localhost:8080}"
OUTPUT_DIR="${SKYBOOKER_SMOKE_OUTPUT_DIR:-reports/smoke}"
USER_EMAIL="${SKYBOOKER_USER_EMAIL:-user1@example.com}"
USER_PASSWORD="${SKYBOOKER_USER_PASSWORD:-User@123456}"
ADMIN_USERNAME="${SKYBOOKER_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${SKYBOOKER_ADMIN_PASSWORD:-Admin@123456}"

mkdir -p "$OUTPUT_DIR"

fail() {
  echo "SMOKE FAIL: $1" >&2
  exit 1
}

request() {
  name="$1"
  method="$2"
  path="$3"
  body="${4:-}"
  auth="${5:-}"
  output="$OUTPUT_DIR/$name.json"

  if [ -n "$body" ] && [ -n "$auth" ]; then
    code=$(curl -sS -o "$output" -w "%{http_code}" -X "$method" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $auth" \
      --data "$body" "$BASE_URL$path")
  elif [ -n "$body" ]; then
    code=$(curl -sS -o "$output" -w "%{http_code}" -X "$method" \
      -H "Content-Type: application/json" \
      --data "$body" "$BASE_URL$path")
  elif [ -n "$auth" ]; then
    code=$(curl -sS -o "$output" -w "%{http_code}" -X "$method" \
      -H "Authorization: Bearer $auth" \
      "$BASE_URL$path")
  else
    code=$(curl -sS -o "$output" -w "%{http_code}" -X "$method" "$BASE_URL$path")
  fi
  printf '%s' "$code" > "$OUTPUT_DIR/$name.status"
  echo "$code"
}

assert_http_200() {
  name="$1"
  code="$2"
  [ "$code" = "200" ] || fail "$name returned HTTP $code"
  grep -q '"code"[[:space:]]*:[[:space:]]*200' "$OUTPUT_DIR/$name.json" || fail "$name did not return ApiResponse code 200"
}

assert_not_200() {
  name="$1"
  code="$2"
  [ "$code" != "200" ] || fail "$name unexpectedly returned HTTP 200"
}

extract_access_token() {
  sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$1" | head -n 1
}

echo "Smoke base URL: $BASE_URL"
echo "Smoke output: $OUTPUT_DIR"

code=$(request public_flights GET "/api/flights?page=1&size=1")
assert_http_200 public_flights "$code"

code=$(request user_login POST "/api/auth/login" "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
assert_http_200 user_login "$code"
USER_TOKEN=$(extract_access_token "$OUTPUT_DIR/user_login.json")
[ -n "$USER_TOKEN" ] || fail "user login did not return accessToken"

code=$(request admin_login POST "/api/admin/auth/login" "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")
assert_http_200 admin_login "$code"
ADMIN_TOKEN=$(extract_access_token "$OUTPUT_DIR/admin_login.json")
[ -n "$ADMIN_TOKEN" ] || fail "admin login did not return accessToken"

code=$(request user_me GET "/api/auth/me" "" "$USER_TOKEN")
assert_http_200 user_me "$code"

code=$(request admin_me GET "/api/admin/me" "" "$ADMIN_TOKEN")
assert_http_200 admin_me "$code"

code=$(request user_rejected_from_admin GET "/api/admin/me" "" "$USER_TOKEN")
assert_not_200 user_rejected_from_admin "$code"

code=$(request admin_rejected_from_user GET "/api/auth/me" "" "$ADMIN_TOKEN")
assert_not_200 admin_rejected_from_user "$code"

code=$(request user_orders GET "/api/orders?page=1&size=5" "" "$USER_TOKEN")
assert_http_200 user_orders "$code"

code=$(request ai_chat POST "/api/ai/chat" '{"message":"我想去北京"}')
assert_http_200 ai_chat "$code"

code=$(request admin_dashboard GET "/api/admin/dashboard/summary" "" "$ADMIN_TOKEN")
assert_http_200 admin_dashboard "$code"

echo "Smoke checks passed."
