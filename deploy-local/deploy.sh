#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

REMOTE_HOST="${REMOTE_HOST:-mt_ai_agent}"
REMOTE_BACKEND_DIR="${REMOTE_BACKEND_DIR:-/opt/waimaidashi/backend}"
REMOTE_H5_DIR="${REMOTE_H5_DIR:-/var/www/mt-h5}"
REMOTE_FRONTEND_DIR="${REMOTE_FRONTEND_DIR:-/var/www/mt-admin}"
REMOTE_ENV_PATH="${REMOTE_ENV_PATH:-/etc/waimaidashi/backend.env}"
REMOTE_CERT_DIR="${REMOTE_CERT_DIR:-/opt/waimaidashi/certs/wechat}"
BACKEND_SERVICE_NAME="${BACKEND_SERVICE_NAME:-waimaidashi-backend}"
MVN_BIN="${MVN_BIN:-/Users/jackwei/data/apache-maven-3.9.14/bin/mvn}"
H5_BUILD_DIR="${H5_BUILD_DIR:-${REPO_ROOT}/h5/dist/build/h5}"
FRONTEND_BUILD_DIR="${FRONTEND_BUILD_DIR:-${REPO_ROOT}/frontend/dist}"
LOCAL_ENV_FILE="${LOCAL_ENV_FILE:-${REPO_ROOT}/deploy-local/backend.env}"
LOCAL_WECHAT_KEY_FILE="${LOCAL_WECHAT_KEY_FILE:-}"
LOCAL_WECHAT_PUB_KEY_FILE="${LOCAL_WECHAT_PUB_KEY_FILE:-}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-3}"

DEPLOY_BACKEND=1
DEPLOY_H5=1
DEPLOY_FRONTEND=1
UPLOAD_ENV=0
UPLOAD_CERTS=0
RESTART_BACKEND=1
BACKUP_BACKEND=1
BACKUP_H5=1
BACKUP_FRONTEND=1

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  --host <ssh-host>           SSH host alias, default: ${REMOTE_HOST}
  --backend-only              Deploy backend only
  --h5-only                   Deploy H5 only
  --frontend-only             Deploy frontend only
  --skip-backend              Skip backend deploy
  --skip-h5                   Skip H5 deploy
  --skip-frontend             Skip frontend deploy
  --upload-env                Upload deploy-local/backend.env to remote env path
  --upload-certs              Upload WeChat cert files defined by env vars
  --no-restart-backend        Upload backend.jar without restarting service
  --no-backup-backend         Replace backend.jar without remote backup
  --no-backup-h5              Replace H5 files without remote backup
  --no-backup-frontend        Replace frontend files without remote backup
  --backup-retention-days <n> Remove backup files/dirs older than n days, default: ${BACKUP_RETENTION_DAYS}; set 0 to disable cleanup
  -h, --help                  Show help

Environment variables:
  REMOTE_HOST                 SSH host alias
  REMOTE_BACKEND_DIR          Remote backend directory
  REMOTE_H5_DIR               Remote H5 static directory
  REMOTE_FRONTEND_DIR         Remote frontend static directory
  REMOTE_ENV_PATH             Remote backend env file path
  REMOTE_CERT_DIR             Remote WeChat cert directory
  BACKEND_SERVICE_NAME        systemd backend service name
  MVN_BIN                     Maven binary path
  FRONTEND_BUILD_DIR          Local frontend build directory
  LOCAL_ENV_FILE              Local backend env file path
  LOCAL_WECHAT_KEY_FILE       Local apiclient_key.pem path
  LOCAL_WECHAT_PUB_KEY_FILE   Local pub_key.pem path
  BACKUP_RETENTION_DAYS       Backup retention days, default: 3
EOF
}

log() {
  printf '[deploy] %s\n' "$*"
}

fail() {
  printf '[deploy] ERROR: %s\n' "$*" >&2
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
    --backend-only)
      DEPLOY_BACKEND=1
      DEPLOY_H5=0
      DEPLOY_FRONTEND=0
      shift
      ;;
    --h5-only)
      DEPLOY_BACKEND=0
      DEPLOY_H5=1
      DEPLOY_FRONTEND=0
      shift
      ;;
    --frontend-only)
      DEPLOY_BACKEND=0
      DEPLOY_H5=0
      DEPLOY_FRONTEND=1
      shift
      ;;
    --skip-backend)
      DEPLOY_BACKEND=0
      shift
      ;;
    --skip-h5)
      DEPLOY_H5=0
      shift
      ;;
    --skip-frontend)
      DEPLOY_FRONTEND=0
      shift
      ;;
    --upload-env)
      UPLOAD_ENV=1
      shift
      ;;
    --upload-certs)
      UPLOAD_CERTS=1
      shift
      ;;
    --no-restart-backend)
      RESTART_BACKEND=0
      shift
      ;;
    --no-backup-backend)
      BACKUP_BACKEND=0
      shift
      ;;
    --no-backup-h5)
      BACKUP_H5=0
      shift
      ;;
    --no-backup-frontend)
      BACKUP_FRONTEND=0
      shift
      ;;
    --backup-retention-days)
      [[ $# -ge 2 ]] || fail "--backup-retention-days requires a value"
      BACKUP_RETENTION_DAYS="$2"
      shift 2
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

(( DEPLOY_BACKEND == 1 || DEPLOY_H5 == 1 || DEPLOY_FRONTEND == 1 )) || fail "Nothing to deploy"
[[ "${BACKUP_RETENTION_DAYS}" =~ ^[0-9]+$ ]] || fail "BACKUP_RETENTION_DAYS must be a non-negative integer"

require_cmd ssh
require_cmd scp
require_cmd npm

if [[ ! -x "${MVN_BIN}" ]]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN_BIN="$(command -v mvn)"
  else
    fail "Maven not found. Set MVN_BIN or install mvn."
  fi
fi

upload_env_file() {
  [[ -f "${LOCAL_ENV_FILE}" ]] || fail "Env file not found: ${LOCAL_ENV_FILE}"
  ssh "${REMOTE_HOST}" "mkdir -p \"$(dirname "${REMOTE_ENV_PATH}")\""
  log "Uploading backend env to ${REMOTE_HOST}:${REMOTE_ENV_PATH}"
  scp "${LOCAL_ENV_FILE}" "${REMOTE_HOST}:${REMOTE_ENV_PATH}"
}

upload_cert_files() {
  [[ -n "${LOCAL_WECHAT_KEY_FILE}" ]] || fail "LOCAL_WECHAT_KEY_FILE is required for --upload-certs"
  [[ -n "${LOCAL_WECHAT_PUB_KEY_FILE}" ]] || fail "LOCAL_WECHAT_PUB_KEY_FILE is required for --upload-certs"
  [[ -f "${LOCAL_WECHAT_KEY_FILE}" ]] || fail "Cert file not found: ${LOCAL_WECHAT_KEY_FILE}"
  [[ -f "${LOCAL_WECHAT_PUB_KEY_FILE}" ]] || fail "Cert file not found: ${LOCAL_WECHAT_PUB_KEY_FILE}"
  ssh "${REMOTE_HOST}" "mkdir -p '${REMOTE_CERT_DIR}'"
  log "Uploading WeChat cert files to ${REMOTE_HOST}:${REMOTE_CERT_DIR}"
  scp "${LOCAL_WECHAT_KEY_FILE}" "${REMOTE_HOST}:${REMOTE_CERT_DIR}/apiclient_key.pem"
  scp "${LOCAL_WECHAT_PUB_KEY_FILE}" "${REMOTE_HOST}:${REMOTE_CERT_DIR}/pub_key.pem"
}

assert_safe_remote_path() {
  local path="$1"
  local label="$2"
  [[ -n "${path}" ]] || fail "${label} must not be empty"
  [[ "${path}" != "/" ]] || fail "${label} must not be /"
  [[ "${path}" != *"'"* ]] || fail "${label} must not contain single quotes"
  [[ "${path}" != *$'\n'* ]] || fail "${label} must not contain newlines"
}

cleanup_remote_file_backups() {
  local remote_dir="$1"
  local name_pattern="$2"
  local retention_minutes=$((BACKUP_RETENTION_DAYS * 24 * 60))
  (( BACKUP_RETENTION_DAYS > 0 )) || return 0
  assert_safe_remote_path "${remote_dir}" "Remote backup cleanup directory"
  log "Cleaning ${remote_dir}/${name_pattern} backups older than ${BACKUP_RETENTION_DAYS} days"
  ssh "${REMOTE_HOST}" "
    set -eu
    find '${remote_dir}' -maxdepth 1 -type f -name '${name_pattern}' -mmin +${retention_minutes} -delete
  "
}

cleanup_remote_dir_backups() {
  local remote_path="$1"
  local remote_parent
  local remote_base
  local retention_minutes=$((BACKUP_RETENTION_DAYS * 24 * 60))
  (( BACKUP_RETENTION_DAYS > 0 )) || return 0
  assert_safe_remote_path "${remote_path}" "Remote backup cleanup path"
  remote_parent="$(dirname "${remote_path}")"
  remote_base="$(basename "${remote_path}")"
  assert_safe_remote_path "${remote_parent}" "Remote backup cleanup parent"
  [[ "${remote_parent}" != "/" ]] || fail "Remote backup cleanup parent must not be /"
  [[ -n "${remote_base}" && "${remote_base}" != "." && "${remote_base}" != "/" ]] || fail "Remote backup cleanup basename is invalid"
  [[ "${remote_base}" != *"*"* && "${remote_base}" != *"?"* && "${remote_base}" != *"["* ]] || fail "Remote backup cleanup basename must not contain glob characters"
  log "Cleaning ${remote_parent}/${remote_base}-backup-* backups older than ${BACKUP_RETENTION_DAYS} days"
  ssh "${REMOTE_HOST}" "
    set -eu
    find '${remote_parent}' -maxdepth 1 -type d -name '${remote_base}-backup-*' -mmin +${retention_minutes} -exec rm -rf {} +
  "
}

deploy_backend() {
  log "Building backend jar"
  "${MVN_BIN}" -f "${REPO_ROOT}/backend/pom.xml" clean package -DskipTests

  local jar_path="${REPO_ROOT}/backend/target/backend.jar"
  [[ -f "${jar_path}" ]] || fail "Backend jar not found: ${jar_path}"

  ssh "${REMOTE_HOST}" "mkdir -p '${REMOTE_BACKEND_DIR}'"

  local remote_jar="${REMOTE_BACKEND_DIR}/backend.jar"
  local remote_new_jar="${REMOTE_BACKEND_DIR}/backend.jar.new"
  local remote_backup_jar=""
  if (( BACKUP_BACKEND == 1 )); then
    remote_backup_jar="${REMOTE_BACKEND_DIR}/backend.jar.backup-$(date +%Y%m%d-%H%M%S)"
  fi

  log "Uploading backend jar to ${REMOTE_HOST}:${remote_new_jar}"
  scp "${jar_path}" "${REMOTE_HOST}:${remote_new_jar}"

  log "Installing backend jar atomically"
  if (( BACKUP_BACKEND == 1 )); then
    ssh "${REMOTE_HOST}" "
      set -eu
      if [ -f '${remote_jar}' ]; then
        cp -a '${remote_jar}' '${remote_backup_jar}'
      fi
      mv -f '${remote_new_jar}' '${remote_jar}'
    "
  else
    ssh "${REMOTE_HOST}" "
      set -eu
      mv -f '${remote_new_jar}' '${remote_jar}'
    "
  fi

  if (( RESTART_BACKEND == 1 )); then
    log "Restarting backend service ${BACKEND_SERVICE_NAME}"
    if ! ssh "${REMOTE_HOST}" "sudo systemctl restart '${BACKEND_SERVICE_NAME}' && sudo systemctl is-active --quiet '${BACKEND_SERVICE_NAME}'"; then
      if (( BACKUP_BACKEND == 1 )); then
        log "Backend restart failed; restoring backup ${remote_backup_jar}"
        ssh "${REMOTE_HOST}" "
          set -eu
          if [ -f '${remote_backup_jar}' ]; then
            mv -f '${remote_backup_jar}' '${remote_jar}'
            sudo systemctl restart '${BACKEND_SERVICE_NAME}' || true
          fi
          sudo systemctl status '${BACKEND_SERVICE_NAME}' --no-pager || true
        "
      else
        ssh "${REMOTE_HOST}" "sudo systemctl status '${BACKEND_SERVICE_NAME}' --no-pager || true"
      fi
      fail "Backend service restart failed"
    fi
    ssh "${REMOTE_HOST}" "sudo systemctl status '${BACKEND_SERVICE_NAME}' --no-pager"
  else
    log "Skipping backend restart"
  fi

  if (( BACKUP_BACKEND == 1 )); then
    cleanup_remote_file_backups "${REMOTE_BACKEND_DIR}" "backend.jar.backup-*"
  fi
}

deploy_h5() {
  log "Building H5"
  (
    cd "${REPO_ROOT}/h5"
    npm run build:h5
  )

  [[ -d "${H5_BUILD_DIR}" ]] || fail "H5 build directory not found: ${H5_BUILD_DIR}"
  ssh "${REMOTE_HOST}" "mkdir -p '${REMOTE_H5_DIR}'"

  if (( BACKUP_H5 == 1 )); then
    log "Backing up remote H5 files at ${REMOTE_H5_DIR}"
    ssh "${REMOTE_HOST}" "backup_dir=${REMOTE_H5_DIR}-backup-\$(date +%Y%m%d-%H%M%S) && mkdir -p \"\$backup_dir\" && if [ -d '${REMOTE_H5_DIR}' ]; then cp -a '${REMOTE_H5_DIR}/.' \"\$backup_dir\"/ 2>/dev/null || true; fi && find '${REMOTE_H5_DIR}' -mindepth 1 -maxdepth 1 -exec rm -rf {} +"
  else
    log "Clearing remote H5 files without backup"
    ssh "${REMOTE_HOST}" "find '${REMOTE_H5_DIR}' -mindepth 1 -maxdepth 1 -exec rm -rf {} +"
  fi

  log "Uploading H5 dist to ${REMOTE_HOST}:${REMOTE_H5_DIR}"
  scp -r "${H5_BUILD_DIR}/." "${REMOTE_HOST}:${REMOTE_H5_DIR}/"

  if (( BACKUP_H5 == 1 )); then
    cleanup_remote_dir_backups "${REMOTE_H5_DIR}"
  fi
}

deploy_frontend() {
  log "Building frontend"
  (
    cd "${REPO_ROOT}/frontend"
    npm run build
  )

  [[ -d "${FRONTEND_BUILD_DIR}" ]] || fail "Frontend build directory not found: ${FRONTEND_BUILD_DIR}"
  ssh "${REMOTE_HOST}" "mkdir -p '${REMOTE_FRONTEND_DIR}'"

  if (( BACKUP_FRONTEND == 1 )); then
    log "Backing up remote frontend files at ${REMOTE_FRONTEND_DIR}"
    ssh "${REMOTE_HOST}" "backup_dir=${REMOTE_FRONTEND_DIR}-backup-\$(date +%Y%m%d-%H%M%S) && mkdir -p \"\$backup_dir\" && if [ -d '${REMOTE_FRONTEND_DIR}' ]; then cp -a '${REMOTE_FRONTEND_DIR}/.' \"\$backup_dir\"/ 2>/dev/null || true; fi && find '${REMOTE_FRONTEND_DIR}' -mindepth 1 -maxdepth 1 -exec rm -rf {} +"
  else
    log "Clearing remote frontend files without backup"
    ssh "${REMOTE_HOST}" "find '${REMOTE_FRONTEND_DIR}' -mindepth 1 -maxdepth 1 -exec rm -rf {} +"
  fi

  log "Uploading frontend dist to ${REMOTE_HOST}:${REMOTE_FRONTEND_DIR}"
  scp -r "${FRONTEND_BUILD_DIR}/." "${REMOTE_HOST}:${REMOTE_FRONTEND_DIR}/"

  if (( BACKUP_FRONTEND == 1 )); then
    cleanup_remote_dir_backups "${REMOTE_FRONTEND_DIR}"
  fi
}

log "Deploy target: ${REMOTE_HOST}"

if (( UPLOAD_ENV == 1 )); then
  upload_env_file
fi

if (( UPLOAD_CERTS == 1 )); then
  upload_cert_files
fi

if (( DEPLOY_BACKEND == 1 )); then
  deploy_backend
fi

if (( DEPLOY_H5 == 1 )); then
  deploy_h5
fi

if (( DEPLOY_FRONTEND == 1 )); then
  deploy_frontend
fi

log "Deployment finished"
