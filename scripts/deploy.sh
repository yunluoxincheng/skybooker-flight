#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="skybooker"
DEFAULT_REPO="yunluoxincheng/skybooker-flight"
DEFAULT_REF="main"
DEFAULT_DEPLOY_DIR="/opt/skybooker"

ACTION="install"
DEPLOY_DIR="${SKYBOOKER_DEPLOY_DIR:-$DEFAULT_DEPLOY_DIR}"
REPO="${SKYBOOKER_REPO:-$DEFAULT_REPO}"
REF="${SKYBOOKER_REF:-$DEFAULT_REF}"
TEMPLATE_BASE_URL="${SKYBOOKER_TEMPLATE_BASE_URL:-}"
TEMPLATE_SOURCE_DIR="${SKYBOOKER_TEMPLATE_SOURCE_DIR:-}"
IMAGE_NAMESPACE="${SKYBOOKER_IMAGE_NAMESPACE:-}"
IMAGE_TAG="${SKYBOOKER_IMAGE_TAG:-}"
REGENERATE_SECRETS="false"
PREPARE_ONLY="false"
SKIP_PULL="false"

usage() {
  cat <<'EOF'
Usage:
  deploy.sh [install|update|status|logs|rollback|down] [options]

Operations:
  install              Prepare files, pull images, and start the stack (default)
  update               Refresh templates, pull images, and recreate services
  status               Show Docker Compose service status
  logs [args...]       Show Docker Compose logs; extra args are passed through
  rollback --tag TAG   Set IMAGE_TAG to TAG, pull images, and recreate services
  down                 Stop services without deleting persistent volumes

Options:
  --dir DIR                    Deployment directory (default: /opt/skybooker)
  --repo OWNER/REPO            GitHub repository for templates and image defaults
  --ref REF                    Git ref for templates (default: main)
  --template-base-url URL      Override raw template base URL
  --template-source-dir DIR    Copy templates from a local checkout instead of curl
  --image-namespace PATH       Registry namespace, e.g. ghcr.io/acme/skybooker-flight
  --tag TAG                    Image tag for install/update/rollback
  --regenerate-secrets         Replace generated secret values in .env
  --prepare-only               Prepare files and .env, then exit before Docker actions
  --skip-pull                  Start/recreate without docker compose pull
  -h, --help                   Show this help

Environment overrides use the SKYBOOKER_* names matching these options.
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

require_docker() {
  require_command docker
  docker compose version >/dev/null 2>&1 || fail "Docker Compose V2 is required. Install the docker-compose-plugin package and retry."
}

random_b64() {
  local bytes="$1"
  require_command openssl
  openssl rand -base64 "$bytes"
}

template_base_url() {
  if [[ -n "$TEMPLATE_BASE_URL" ]]; then
    printf '%s\n' "${TEMPLATE_BASE_URL%/}"
  else
    printf 'https://raw.githubusercontent.com/%s/%s\n' "$REPO" "$REF"
  fi
}

default_image_namespace() {
  if [[ -n "$IMAGE_NAMESPACE" ]]; then
    printf '%s\n' "${IMAGE_NAMESPACE%/}"
  else
    printf 'ghcr.io/%s\n' "$REPO" | tr '[:upper:]' '[:lower:]'
  fi
}

download_template() {
  local rel="$1"
  local dest="$2"

  mkdir -p "$(dirname "$dest")"
  if [[ -n "$TEMPLATE_SOURCE_DIR" ]]; then
    [[ -f "$TEMPLATE_SOURCE_DIR/$rel" ]] || fail "Template not found: $TEMPLATE_SOURCE_DIR/$rel"
    cp "$TEMPLATE_SOURCE_DIR/$rel" "$dest"
    return
  fi

  require_command curl
  curl -fsSL "$(template_base_url)/$rel" -o "$dest"
}

env_has_key() {
  local key="$1"
  [[ -f "$ENV_FILE" ]] && grep -Eq "^[[:space:]]*${key}=" "$ENV_FILE"
}

append_env_if_missing() {
  local key="$1"
  local value="$2"
  if ! env_has_key "$key"; then
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

set_env_value() {
  local key="$1"
  local value="$2"
  local tmp
  tmp="$(mktemp)"
  if [[ -f "$ENV_FILE" ]] && env_has_key "$key"; then
    awk -v key="$key" -v value="$value" '
      BEGIN { replaced = 0 }
      $0 ~ "^[[:space:]]*" key "=" {
        print key "=" value
        replaced = 1
        next
      }
      { print }
      END {
        if (!replaced) {
          print key "=" value
        }
      }
    ' "$ENV_FILE" > "$tmp"
    cat "$tmp" > "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
  rm -f "$tmp"
}

prepare_env() {
  local namespace
  namespace="$(default_image_namespace)"

  umask 077
  touch "$ENV_FILE"

  append_env_if_missing COMPOSE_PROJECT_NAME "$APP_NAME"
  append_env_if_missing BACKEND_IMAGE "$namespace/skybooker-backend"
  append_env_if_missing FRONTEND_IMAGE "$namespace/skybooker-frontend"
  append_env_if_missing IMAGE_TAG "${IMAGE_TAG:-latest}"
  append_env_if_missing PUBLIC_HTTP_PORT "8088"
  append_env_if_missing MYSQL_DB "flight_booking"
  append_env_if_missing MYSQL_USER "root"
  append_env_if_missing JWT_ACCESS_MS "3600000"
  append_env_if_missing JWT_REFRESH_MS "1209600000"
  append_env_if_missing OPENAPI_ENABLED "false"
  append_env_if_missing CORS_ALLOWED_ORIGINS "http://localhost:8088"
  append_env_if_missing MAIL_PROVIDER "log"
  append_env_if_missing MAIL_FROM ""
  append_env_if_missing RESEND_API_KEY ""
  append_env_if_missing RESEND_BASE_URL "https://api.resend.com"
  append_env_if_missing AI_LLM_ENABLED "false"
  append_env_if_missing AI_LLM_BASE_URL ""
  append_env_if_missing AI_LLM_API_KEY ""
  append_env_if_missing AI_LLM_MODEL ""
  append_env_if_missing AI_LLM_TIMEOUT_MS "8000"
  append_env_if_missing AI_LLM_MAX_RETRIES "1"
  append_env_if_missing DB_USE_SSL "false"
  append_env_if_missing DB_ALLOW_PUBLIC_KEY_RETRIEVAL "true"

  if [[ "$REGENERATE_SECRETS" == "true" ]] || ! env_has_key MYSQL_PASSWORD; then
    set_env_value MYSQL_PASSWORD "$(random_b64 32)"
  fi
  if [[ "$REGENERATE_SECRETS" == "true" ]] || ! env_has_key JWT_SECRET; then
    set_env_value JWT_SECRET "$(random_b64 48)"
  fi
  if [[ "$REGENERATE_SECRETS" == "true" ]] || ! env_has_key AI_CONFIG_ENC_KEY; then
    set_env_value AI_CONFIG_ENC_KEY "$(random_b64 32)"
  fi

  if [[ -n "$IMAGE_TAG" ]]; then
    set_env_value IMAGE_TAG "$IMAGE_TAG"
  fi

  chmod 600 "$ENV_FILE"
}

prepare_files() {
  mkdir -p "$DEPLOY_DIR/nginx"
  COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
  ENV_FILE="$DEPLOY_DIR/.env"

  download_template "deploy/docker-compose.prod.yml" "$COMPOSE_FILE"
  download_template "deploy/nginx/prod.conf" "$DEPLOY_DIR/nginx/default.conf"
  if download_template "scripts/deploy.sh" "$DEPLOY_DIR/deploy.sh"; then
    chmod +x "$DEPLOY_DIR/deploy.sh"
  fi

  prepare_env
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --project-directory "$DEPLOY_DIR" "$@"
}

run_up() {
  if [[ "$SKIP_PULL" != "true" ]]; then
    compose pull
  fi
  compose up -d
}

install_or_update() {
  prepare_files
  log "Deployment files are ready in $DEPLOY_DIR"
  if [[ "$PREPARE_ONLY" == "true" ]]; then
    log "prepare-only requested; skipping Docker actions."
    return
  fi
  require_docker
  run_up
}

rollback() {
  [[ -n "$IMAGE_TAG" ]] || fail "rollback requires --tag TAG"
  prepare_files
  set_env_value IMAGE_TAG "$IMAGE_TAG"
  log "Rolling back application images to tag $IMAGE_TAG"
  if [[ "$PREPARE_ONLY" == "true" ]]; then
    log "prepare-only requested; skipping Docker actions."
    return
  fi
  require_docker
  run_up
}

require_existing_deployment() {
  COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
  ENV_FILE="$DEPLOY_DIR/.env"
  [[ -f "$COMPOSE_FILE" ]] || fail "Compose file not found in $DEPLOY_DIR. Run install first."
  [[ -f "$ENV_FILE" ]] || fail ".env not found in $DEPLOY_DIR. Run install first."
}

parse_args() {
  if [[ $# -gt 0 && "$1" != --* && "$1" != "-h" ]]; then
    ACTION="$1"
    shift
  fi

  PASSTHROUGH_ARGS=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dir)
        DEPLOY_DIR="$2"
        shift 2
        ;;
      --repo)
        REPO="$2"
        shift 2
        ;;
      --ref)
        REF="$2"
        shift 2
        ;;
      --template-base-url)
        TEMPLATE_BASE_URL="$2"
        shift 2
        ;;
      --template-source-dir)
        TEMPLATE_SOURCE_DIR="$2"
        shift 2
        ;;
      --image-namespace)
        IMAGE_NAMESPACE="$2"
        shift 2
        ;;
      --tag)
        IMAGE_TAG="$2"
        shift 2
        ;;
      --regenerate-secrets)
        REGENERATE_SECRETS="true"
        shift
        ;;
      --prepare-only)
        PREPARE_ONLY="true"
        shift
        ;;
      --skip-pull)
        SKIP_PULL="true"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      --)
        shift
        PASSTHROUGH_ARGS+=("$@")
        break
        ;;
      *)
        if [[ "$ACTION" == "logs" ]]; then
          PASSTHROUGH_ARGS+=("$1")
          shift
        else
          fail "Unknown option: $1"
        fi
        ;;
    esac
  done
}

main() {
  parse_args "$@"

  case "$ACTION" in
    install|update)
      install_or_update
      ;;
    rollback)
      rollback
      ;;
    status)
      require_existing_deployment
      require_docker
      compose ps
      ;;
    logs)
      require_existing_deployment
      require_docker
      compose logs "${PASSTHROUGH_ARGS[@]}"
      ;;
    down)
      require_existing_deployment
      require_docker
      compose down
      ;;
    *)
      usage
      fail "Unknown operation: $ACTION"
      ;;
  esac
}

main "$@"
