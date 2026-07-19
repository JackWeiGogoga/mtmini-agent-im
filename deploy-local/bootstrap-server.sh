#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

REMOTE_HOST="${REMOTE_HOST:-mt6}"
SERVER_ROLE="${SERVER_ROLE:-web}"
APP_USER="${APP_USER:-waimaidashi}"
APP_GROUP="${APP_GROUP:-waimaidashi}"
APP_HOME="${APP_HOME:-/opt/waimaidashi}"
REMOTE_BACKEND_DIR="${REMOTE_BACKEND_DIR:-${APP_HOME}/backend}"
REMOTE_CERT_DIR="${REMOTE_CERT_DIR:-${APP_HOME}/certs/wechat}"
REMOTE_ENV_PATH="${REMOTE_ENV_PATH:-/etc/waimaidashi/backend.env}"
REMOTE_SERVICE_PATH="${REMOTE_SERVICE_PATH:-/etc/systemd/system/waimaidashi-backend.service}"
REMOTE_CADDYFILE_PATH="${REMOTE_CADDYFILE_PATH:-/etc/caddy/Caddyfile}"
BACKEND_SERVICE_NAME="${BACKEND_SERVICE_NAME:-waimaidashi-backend}"
LOCAL_ENV_FILE="${LOCAL_ENV_FILE:-${REPO_ROOT}/deploy-local/backend.env}"
LOCAL_SERVICE_FILE="${LOCAL_SERVICE_FILE:-${REPO_ROOT}/deploy-local/waimaidashi-backend.service}"
LOCAL_CADDYFILE="${LOCAL_CADDYFILE:-${REPO_ROOT}/deploy-local/Caddyfile}"
LOCAL_WECHAT_KEY_FILE="${LOCAL_WECHAT_KEY_FILE:-}"
LOCAL_WECHAT_PUB_KEY_FILE="${LOCAL_WECHAT_PUB_KEY_FILE:-}"

UPLOAD_ENV=1
UPLOAD_CADDY=1
UPLOAD_SERVICE=1
UPLOAD_CERTS=0
ENABLE_BACKEND=1
START_BACKEND=0
RELOAD_CADDY=1

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  --host <ssh-host>           SSH host alias, default: ${REMOTE_HOST}
  --role <web|data>           Server role, default: ${SERVER_ROLE}; data skips Caddy
  --skip-env                  Do not upload backend.env
  --skip-caddy                Do not upload Caddyfile
  --skip-service              Do not upload systemd service file
  --upload-certs              Upload WeChat cert files defined by env vars
  --skip-enable-backend       Do not daemon-reload/enable backend service
  --start-backend             Start backend service after enabling it
  --skip-reload-caddy         Do not validate/reload caddy
  -h, --help                  Show help

Environment variables:
  REMOTE_HOST                 SSH host alias
  SERVER_ROLE                 Server role: web or data
  APP_USER                    App user name
  APP_GROUP                   App group name
  APP_HOME                    App home directory
  REMOTE_BACKEND_DIR          Remote backend directory
  REMOTE_CERT_DIR             Remote WeChat cert directory
  REMOTE_ENV_PATH             Remote backend env file path
  REMOTE_SERVICE_PATH         Remote systemd service file path
  REMOTE_CADDYFILE_PATH       Remote Caddyfile path
  BACKEND_SERVICE_NAME        systemd backend service name
  LOCAL_ENV_FILE              Local backend env file path
  LOCAL_SERVICE_FILE          Local service file path
  LOCAL_CADDYFILE             Local Caddyfile path
  LOCAL_WECHAT_KEY_FILE       Local apiclient_key.pem path
  LOCAL_WECHAT_PUB_KEY_FILE   Local pub_key.pem path
EOF
}

log() {
  printf '[bootstrap] %s\n' "$*"
}

fail() {
  printf '[bootstrap] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      [[ $# -ge 2 ]] || fail "--host requires a value"
      REMOTE_HOST="$2"
      shift 2
      ;;
    --role)
      [[ $# -ge 2 ]] || fail "--role requires a value"
      SERVER_ROLE="$2"
      shift 2
      ;;
    --skip-env)
      UPLOAD_ENV=0
      shift
      ;;
    --skip-caddy)
      UPLOAD_CADDY=0
      shift
      ;;
    --skip-service)
      UPLOAD_SERVICE=0
      shift
      ;;
    --upload-certs)
      UPLOAD_CERTS=1
      shift
      ;;
    --skip-enable-backend)
      ENABLE_BACKEND=0
      shift
      ;;
    --start-backend)
      START_BACKEND=1
      shift
      ;;
    --skip-reload-caddy)
      RELOAD_CADDY=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

case "${SERVER_ROLE}" in
  web)
    ;;
  data)
    UPLOAD_CADDY=0
    RELOAD_CADDY=0
    ;;
  *)
    fail "--role/SERVER_ROLE must be web or data"
    ;;
esac

require_cmd ssh
require_cmd scp

upload_env() {
  [[ -f "${LOCAL_ENV_FILE}" ]] || fail "Env file not found: ${LOCAL_ENV_FILE}"
  log "Uploading backend env"
  scp "${LOCAL_ENV_FILE}" "${REMOTE_HOST}:/tmp/waimaidashi-backend.env"
  ssh "${REMOTE_HOST}" "
    sudo mkdir -p \"$(dirname "${REMOTE_ENV_PATH}")\"
    sudo mv /tmp/waimaidashi-backend.env '${REMOTE_ENV_PATH}'
    sudo chown root:root '${REMOTE_ENV_PATH}'
    sudo chmod 600 '${REMOTE_ENV_PATH}'
  "
}

upload_service() {
  [[ -f "${LOCAL_SERVICE_FILE}" ]] || fail "Service file not found: ${LOCAL_SERVICE_FILE}"
  log "Uploading systemd service"
  scp "${LOCAL_SERVICE_FILE}" "${REMOTE_HOST}:/tmp/${BACKEND_SERVICE_NAME}.service"
  ssh "${REMOTE_HOST}" "
    sudo mv /tmp/${BACKEND_SERVICE_NAME}.service '${REMOTE_SERVICE_PATH}'
    sudo chown root:root '${REMOTE_SERVICE_PATH}'
    sudo chmod 644 '${REMOTE_SERVICE_PATH}'
  "
}

upload_caddy() {
  [[ -f "${LOCAL_CADDYFILE}" ]] || fail "Caddyfile not found: ${LOCAL_CADDYFILE}"
  log "Uploading and validating Caddyfile"
  scp "${LOCAL_CADDYFILE}" "${REMOTE_HOST}:/tmp/Caddyfile"
  ssh "${REMOTE_HOST}" "
    set -e
    sudo caddy validate --config /tmp/Caddyfile
    if [ -f '${REMOTE_CADDYFILE_PATH}' ]; then
      sudo cp -a '${REMOTE_CADDYFILE_PATH}' '${REMOTE_CADDYFILE_PATH}.backup-\$(date +%Y%m%d-%H%M%S)'
    fi
    sudo mkdir -p \"\$(dirname '${REMOTE_CADDYFILE_PATH}')\"
    sudo mkdir -p /var/log/caddy
    sudo chown -R caddy:caddy /var/log/caddy || true
    sudo chmod 750 /var/log/caddy || true
    sudo mv /tmp/Caddyfile '${REMOTE_CADDYFILE_PATH}'
    sudo chown root:root '${REMOTE_CADDYFILE_PATH}'
    sudo chmod 644 '${REMOTE_CADDYFILE_PATH}'
  "
}

upload_certs() {
  [[ -n "${LOCAL_WECHAT_KEY_FILE}" ]] || fail "LOCAL_WECHAT_KEY_FILE is required for --upload-certs"
  [[ -n "${LOCAL_WECHAT_PUB_KEY_FILE}" ]] || fail "LOCAL_WECHAT_PUB_KEY_FILE is required for --upload-certs"
  [[ -f "${LOCAL_WECHAT_KEY_FILE}" ]] || fail "Cert file not found: ${LOCAL_WECHAT_KEY_FILE}"
  [[ -f "${LOCAL_WECHAT_PUB_KEY_FILE}" ]] || fail "Cert file not found: ${LOCAL_WECHAT_PUB_KEY_FILE}"
  log "Uploading WeChat cert files"
  scp "${LOCAL_WECHAT_KEY_FILE}" "${REMOTE_HOST}:/tmp/apiclient_key.pem"
  scp "${LOCAL_WECHAT_PUB_KEY_FILE}" "${REMOTE_HOST}:/tmp/pub_key.pem"
  ssh "${REMOTE_HOST}" "
    set -e
    sudo mkdir -p '${REMOTE_CERT_DIR}'
    sudo mv /tmp/apiclient_key.pem '${REMOTE_CERT_DIR}/apiclient_key.pem'
    sudo mv /tmp/pub_key.pem '${REMOTE_CERT_DIR}/pub_key.pem'
    sudo chown '${APP_USER}:${APP_GROUP}' '${REMOTE_CERT_DIR}/apiclient_key.pem' '${REMOTE_CERT_DIR}/pub_key.pem'
    sudo chmod 600 '${REMOTE_CERT_DIR}/apiclient_key.pem'
    sudo chmod 644 '${REMOTE_CERT_DIR}/pub_key.pem'
  "
}

enable_backend() {
  log "Reloading systemd and enabling backend service"
  ssh "${REMOTE_HOST}" "
    sudo systemctl daemon-reload
    sudo systemctl enable '${BACKEND_SERVICE_NAME}'
    if [ '${START_BACKEND}' = '1' ]; then
      sudo systemctl start '${BACKEND_SERVICE_NAME}'
    fi
    sudo systemctl status '${BACKEND_SERVICE_NAME}' --no-pager || true
  "
}

reload_caddy() {
  log "Validating and reloading or starting caddy"
  ssh "${REMOTE_HOST}" "
    set -e
    sudo mkdir -p /var/log/caddy
    sudo chown -R caddy:caddy /var/log/caddy || true
    sudo chmod 750 /var/log/caddy || true
    sudo caddy validate --config '${REMOTE_CADDYFILE_PATH}'
    if sudo systemctl is-active --quiet caddy; then
      sudo systemctl reload caddy
    else
      sudo systemctl start caddy
    fi
    sudo systemctl status caddy --no-pager || true
  "
}

if (( UPLOAD_ENV == 1 )); then
  upload_env
fi

if (( UPLOAD_SERVICE == 1 )); then
  upload_service
fi

if (( UPLOAD_CADDY == 1 )); then
  upload_caddy
fi

if (( UPLOAD_CERTS == 1 )); then
  upload_certs
fi

if (( ENABLE_BACKEND == 1 )); then
  enable_backend
fi

if (( RELOAD_CADDY == 1 )); then
  reload_caddy
fi

log "Bootstrap finished"
