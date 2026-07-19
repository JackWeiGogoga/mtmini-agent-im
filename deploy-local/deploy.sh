#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

REMOTE_HOST_FROM_ENV="${REMOTE_HOST-}"
BACKEND_HOSTS_FROM_ENV="${BACKEND_HOSTS-}"
REMOTE_HOST="${REMOTE_HOST:-mt6}"
BACKEND_HOSTS_EXPLICIT=0
if [[ -n "${BACKEND_HOSTS_FROM_ENV}" ]]; then
  BACKEND_HOSTS="${BACKEND_HOSTS_FROM_ENV}"
  BACKEND_HOSTS_EXPLICIT=1
elif [[ -n "${REMOTE_HOST_FROM_ENV}" ]]; then
  # Preserve the previous single-host behavior for callers that already set
  # REMOTE_HOST in their environment.
  BACKEND_HOSTS="${REMOTE_HOST}"
else
  BACKEND_HOSTS="mt5,mt6"
fi
REMOTE_BACKEND_DIR="${REMOTE_BACKEND_DIR:-/opt/waimaidashi/backend}"
REMOTE_H5_DIR="${REMOTE_H5_DIR:-/var/www/mt-h5}"
REMOTE_FRONTEND_DIR="${REMOTE_FRONTEND_DIR:-/var/www/mt-admin}"
REMOTE_ENV_PATH="${REMOTE_ENV_PATH:-/etc/waimaidashi/backend.env}"
REMOTE_CERT_DIR="${REMOTE_CERT_DIR:-/opt/waimaidashi/certs/wechat}"
APP_USER="${APP_USER:-waimaidashi}"
APP_GROUP="${APP_GROUP:-waimaidashi}"
BACKEND_SERVICE_NAME="${BACKEND_SERVICE_NAME:-waimaidashi-backend}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8080/health/check}"
MVN_BIN="${MVN_BIN:-/Users/jackwei/data/apache-maven-3.9.14/bin/mvn}"
H5_PACKAGE_MANAGER="${H5_PACKAGE_MANAGER:-npm}"
FRONTEND_PACKAGE_MANAGER="${FRONTEND_PACKAGE_MANAGER:-auto}"
H5_BUILD_DIR="${H5_BUILD_DIR:-${REPO_ROOT}/h5/dist/build/h5}"
FRONTEND_BUILD_DIR="${FRONTEND_BUILD_DIR:-${REPO_ROOT}/frontend/dist}"
LOCAL_ENV_FILE="${LOCAL_ENV_FILE:-${REPO_ROOT}/deploy-local/backend.env}"
LOCAL_WECHAT_KEY_FILE="${LOCAL_WECHAT_KEY_FILE:-}"
LOCAL_WECHAT_PUB_KEY_FILE="${LOCAL_WECHAT_PUB_KEY_FILE:-}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-3}"
SSH_CONNECT_TIMEOUT_SECONDS="${SSH_CONNECT_TIMEOUT_SECONDS:-10}"
SSH_CONNECTION_ATTEMPTS="${SSH_CONNECTION_ATTEMPTS:-2}"
SSH_SERVER_ALIVE_INTERVAL_SECONDS="${SSH_SERVER_ALIVE_INTERVAL_SECONDS:-15}"
SSH_SERVER_ALIVE_COUNT_MAX="${SSH_SERVER_ALIVE_COUNT_MAX:-4}"
SSH_RETRY_ATTEMPTS="${SSH_RETRY_ATTEMPTS:-5}"
SSH_RETRY_DELAY_SECONDS="${SSH_RETRY_DELAY_SECONDS:-3}"
TRANSFER_MODE="${TRANSFER_MODE:-auto}"
RSYNC_IO_TIMEOUT_SECONDS="${RSYNC_IO_TIMEOUT_SECONDS:-60}"

SSH_OPTIONS=()
RSYNC_SSH_COMMAND=""
USE_RSYNC=0
BACKEND_HOST_LIST=()
BACKEND_JAR_PATH=""
BACKEND_JAR_SHA256=""

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
  --host <ssh-host>           Static host; also backend host unless BACKEND_HOSTS/--backend-hosts is set
  --backend-hosts <hosts>     Backend SSH hosts separated by commas, default: ${BACKEND_HOSTS}
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
  REMOTE_HOST                 Static-file SSH host; also selects one backend host for backward compatibility
  BACKEND_HOSTS               Backend SSH hosts separated by commas, default: mt5,mt6
  REMOTE_BACKEND_DIR          Remote backend directory
  REMOTE_H5_DIR               Remote H5 static directory
  REMOTE_FRONTEND_DIR         Remote frontend static directory
  REMOTE_ENV_PATH             Remote backend env file path
  REMOTE_CERT_DIR             Remote WeChat cert directory
  APP_USER                    App runtime user, default: ${APP_USER}
  APP_GROUP                   App runtime group, default: ${APP_GROUP}
  BACKEND_SERVICE_NAME        systemd backend service name
  BACKEND_HEALTH_URL          Local backend health URL checked after restart; empty to disable
  MVN_BIN                     Maven binary path
  H5_PACKAGE_MANAGER          H5 package manager command, default: npm
  FRONTEND_PACKAGE_MANAGER    Frontend package manager command, default: auto
  FRONTEND_BUILD_DIR          Local frontend build directory
  LOCAL_ENV_FILE              Local backend env file path
  LOCAL_WECHAT_KEY_FILE       Local apiclient_key.pem path
  LOCAL_WECHAT_PUB_KEY_FILE   Local pub_key.pem path
  BACKUP_RETENTION_DAYS       Backup retention days, default: 3
  SSH_CONNECT_TIMEOUT_SECONDS SSH connection timeout, default: 10
  SSH_CONNECTION_ATTEMPTS     Attempts made by each SSH connection, default: 2
  SSH_SERVER_ALIVE_INTERVAL_SECONDS
                              SSH keepalive interval, default: 15
  SSH_SERVER_ALIVE_COUNT_MAX  Missed keepalives before disconnect, default: 4
  SSH_RETRY_ATTEMPTS          Retries after SSH transport failures, default: 5
  SSH_RETRY_DELAY_SECONDS     Initial retry delay, default: 3
  TRANSFER_MODE               auto/rsync/scp, default: auto
  RSYNC_IO_TIMEOUT_SECONDS    Rsync I/O timeout, default: 60
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

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

build_ssh_options() {
  SSH_OPTIONS=(
    -o "ConnectTimeout=${SSH_CONNECT_TIMEOUT_SECONDS}"
    -o "ConnectionAttempts=${SSH_CONNECTION_ATTEMPTS}"
    -o "ServerAliveInterval=${SSH_SERVER_ALIVE_INTERVAL_SECONDS}"
    -o "ServerAliveCountMax=${SSH_SERVER_ALIVE_COUNT_MAX}"
    -o "TCPKeepAlive=yes"
    -o "ControlMaster=no"
  )
  RSYNC_SSH_COMMAND="ssh -o ConnectTimeout=${SSH_CONNECT_TIMEOUT_SECONDS} -o ConnectionAttempts=${SSH_CONNECTION_ATTEMPTS} -o ServerAliveInterval=${SSH_SERVER_ALIVE_INTERVAL_SECONDS} -o ServerAliveCountMax=${SSH_SERVER_ALIVE_COUNT_MAX} -o TCPKeepAlive=yes -o ControlMaster=no"
}

retry_delay_for_attempt() {
  local attempt="$1"
  local delay=$((SSH_RETRY_DELAY_SECONDS * attempt))
  (( delay <= 30 )) || delay=30
  printf '%s\n' "${delay}"
}

ssh_remote() {
  local remote_command="$1"
  local attempt=1
  local exit_code=0
  local delay=0

  while true; do
    if ssh "${SSH_OPTIONS[@]}" "${REMOTE_HOST}" "${remote_command}"; then
      return 0
    else
      exit_code=$?
    fi

    # OpenSSH uses 255 for connection/transport failures. Do not retry a
    # deterministic non-zero status returned by the remote command itself.
    if (( exit_code != 255 || attempt >= SSH_RETRY_ATTEMPTS )); then
      return "${exit_code}"
    fi

    delay="$(retry_delay_for_attempt "${attempt}")"
    log "SSH connection to ${REMOTE_HOST} dropped (attempt ${attempt}/${SSH_RETRY_ATTEMPTS}); retrying in ${delay}s"
    sleep "${delay}"
    attempt=$((attempt + 1))
  done
}

run_transfer_with_retry() {
  local description="$1"
  shift
  local attempt=1
  local exit_code=0
  local delay=0

  while true; do
    if "$@"; then
      return 0
    else
      exit_code=$?
    fi

    if (( attempt >= SSH_RETRY_ATTEMPTS )); then
      return "${exit_code}"
    fi

    delay="$(retry_delay_for_attempt "${attempt}")"
    log "${description} interrupted (attempt ${attempt}/${SSH_RETRY_ATTEMPTS}); retrying in ${delay}s"
    sleep "${delay}"
    attempt=$((attempt + 1))
  done
}

scp_transfer() {
  run_transfer_with_retry "SCP transfer" scp "${SSH_OPTIONS[@]}" "$@"
}

rsync_transfer() {
  run_transfer_with_retry "Rsync transfer" \
    rsync -a --partial --timeout="${RSYNC_IO_TIMEOUT_SECONDS}" -e "${RSYNC_SSH_COMMAND}" "$@"
}

transfer_file() {
  if (( USE_RSYNC == 1 )); then
    rsync_transfer "$@"
  else
    scp_transfer "$@"
  fi
}

transfer_directory_contents() {
  local source_dir="$1"
  local remote_dir="$2"
  if (( USE_RSYNC == 1 )); then
    rsync_transfer "${source_dir}/" "${REMOTE_HOST}:${remote_dir}/"
  else
    scp_transfer -r "${source_dir}/." "${REMOTE_HOST}:${remote_dir}/"
  fi
}

configure_transfer_mode() {
  case "${TRANSFER_MODE}" in
    auto)
      if command -v rsync >/dev/null 2>&1 && ssh_remote "command -v rsync >/dev/null 2>&1"; then
        USE_RSYNC=1
      else
        require_cmd scp
        USE_RSYNC=0
      fi
      ;;
    rsync)
      require_cmd rsync
      ssh_remote "command -v rsync >/dev/null 2>&1" || fail "rsync is not available on ${REMOTE_HOST}"
      USE_RSYNC=1
      ;;
    scp)
      require_cmd scp
      USE_RSYNC=0
      ;;
    *)
      fail "TRANSFER_MODE must be auto, rsync, or scp"
      ;;
  esac
  if (( USE_RSYNC == 1 )); then
    log "Transfer mode: rsync (partial transfers will resume after reconnect)"
  else
    log "Transfer mode: scp (transfers retry from the beginning)"
  fi
}

local_sha256() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}" | awk '{print $1}'
    return 0
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}" | awk '{print $1}'
    return 0
  fi
  fail "sha256sum/shasum is required for backend artifact verification"
}

parse_backend_hosts() {
  local normalized="${BACKEND_HOSTS//,/ }"
  local candidates=()
  local candidate
  local existing
  local duplicate

  read -r -a candidates <<< "${normalized}"
  (( ${#candidates[@]} > 0 )) || fail "BACKEND_HOSTS must contain at least one SSH host"

  BACKEND_HOST_LIST=()
  for candidate in "${candidates[@]}"; do
    [[ "${candidate}" =~ ^[A-Za-z0-9._@:-]+$ ]] || fail "Invalid backend SSH host: ${candidate}"
    duplicate=0
    # macOS still ships Bash 3.2, where expanding an empty array under
    # `set -u` raises an error. The `-` fallback keeps the first iteration safe.
    for existing in "${BACKEND_HOST_LIST[@]-}"; do
      [[ -n "${existing}" ]] || continue
      if [[ "${existing}" == "${candidate}" ]]; then
        duplicate=1
        break
      fi
    done
    if (( duplicate == 0 )); then
      BACKEND_HOST_LIST+=("${candidate}")
    fi
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      [[ $# -ge 2 ]] || fail "--host requires a value"
      REMOTE_HOST="$2"
      if (( BACKEND_HOSTS_EXPLICIT == 0 )); then
        BACKEND_HOSTS="$2"
      fi
      shift 2
      ;;
    --backend-hosts)
      [[ $# -ge 2 ]] || fail "--backend-hosts requires a value"
      BACKEND_HOSTS="$2"
      BACKEND_HOSTS_EXPLICIT=1
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
is_positive_integer "${SSH_CONNECT_TIMEOUT_SECONDS}" || fail "SSH_CONNECT_TIMEOUT_SECONDS must be a positive integer"
is_positive_integer "${SSH_CONNECTION_ATTEMPTS}" || fail "SSH_CONNECTION_ATTEMPTS must be a positive integer"
is_positive_integer "${SSH_SERVER_ALIVE_INTERVAL_SECONDS}" || fail "SSH_SERVER_ALIVE_INTERVAL_SECONDS must be a positive integer"
is_positive_integer "${SSH_SERVER_ALIVE_COUNT_MAX}" || fail "SSH_SERVER_ALIVE_COUNT_MAX must be a positive integer"
is_positive_integer "${SSH_RETRY_ATTEMPTS}" || fail "SSH_RETRY_ATTEMPTS must be a positive integer"
is_positive_integer "${SSH_RETRY_DELAY_SECONDS}" || fail "SSH_RETRY_DELAY_SECONDS must be a positive integer"
is_positive_integer "${RSYNC_IO_TIMEOUT_SECONDS}" || fail "RSYNC_IO_TIMEOUT_SECONDS must be a positive integer"
if (( DEPLOY_BACKEND == 1 )); then
  parse_backend_hosts
fi

require_cmd ssh
build_ssh_options

if [[ ! -x "${MVN_BIN}" ]]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN_BIN="$(command -v mvn)"
  else
    fail "Maven not found. Set MVN_BIN or install mvn."
  fi
fi

resolve_package_manager() {
  local configured="$1"
  local dir="$2"
  if [[ "${configured}" != "auto" ]]; then
    command -v "${configured}" >/dev/null 2>&1 || fail "Missing package manager: ${configured}"
    printf '%s\n' "${configured}"
    return 0
  fi
  if [[ -f "${dir}/pnpm-lock.yaml" ]] && command -v pnpm >/dev/null 2>&1; then
    printf '%s\n' pnpm
    return 0
  fi
  if command -v npm >/dev/null 2>&1; then
    printf '%s\n' npm
    return 0
  fi
  fail "No package manager found for ${dir}; install pnpm/npm or set package manager env"
}

run_package_script() {
  local package_manager="$1"
  local script_name="$2"
  if [[ "${package_manager}" == "npm" ]]; then
    npm run "${script_name}"
  else
    "${package_manager}" run "${script_name}"
  fi
}

H5_PACKAGE_MANAGER="$(resolve_package_manager "${H5_PACKAGE_MANAGER}" "${REPO_ROOT}/h5")"
FRONTEND_PACKAGE_MANAGER="$(resolve_package_manager "${FRONTEND_PACKAGE_MANAGER}" "${REPO_ROOT}/frontend")"

upload_env_file() {
  [[ -f "${LOCAL_ENV_FILE}" ]] || fail "Env file not found: ${LOCAL_ENV_FILE}"
  assert_safe_remote_path "${REMOTE_ENV_PATH}" "Remote env path"
  log "Uploading backend env to ${REMOTE_HOST}:${REMOTE_ENV_PATH}"
  transfer_file "${LOCAL_ENV_FILE}" "${REMOTE_HOST}:/tmp/waimaidashi-backend.env"
  ssh_remote "
    set -eu
    mkdir -p \"$(dirname "${REMOTE_ENV_PATH}")\"
    mv -f /tmp/waimaidashi-backend.env '${REMOTE_ENV_PATH}'
    chown root:root '${REMOTE_ENV_PATH}'
    chmod 600 '${REMOTE_ENV_PATH}'
  "
}

upload_cert_files() {
  [[ -n "${LOCAL_WECHAT_KEY_FILE}" ]] || fail "LOCAL_WECHAT_KEY_FILE is required for --upload-certs"
  [[ -n "${LOCAL_WECHAT_PUB_KEY_FILE}" ]] || fail "LOCAL_WECHAT_PUB_KEY_FILE is required for --upload-certs"
  [[ -f "${LOCAL_WECHAT_KEY_FILE}" ]] || fail "Cert file not found: ${LOCAL_WECHAT_KEY_FILE}"
  [[ -f "${LOCAL_WECHAT_PUB_KEY_FILE}" ]] || fail "Cert file not found: ${LOCAL_WECHAT_PUB_KEY_FILE}"
  assert_safe_remote_path "${REMOTE_CERT_DIR}" "Remote cert directory"
  ssh_remote "mkdir -p '${REMOTE_CERT_DIR}'"
  log "Uploading WeChat cert files to ${REMOTE_HOST}:${REMOTE_CERT_DIR}"
  transfer_file "${LOCAL_WECHAT_KEY_FILE}" "${REMOTE_HOST}:/tmp/apiclient_key.pem"
  transfer_file "${LOCAL_WECHAT_PUB_KEY_FILE}" "${REMOTE_HOST}:/tmp/pub_key.pem"
  ssh_remote "
    set -eu
    mkdir -p '${REMOTE_CERT_DIR}'
    mv -f /tmp/apiclient_key.pem '${REMOTE_CERT_DIR}/apiclient_key.pem'
    mv -f /tmp/pub_key.pem '${REMOTE_CERT_DIR}/pub_key.pem'
    chown -R '${APP_USER}:${APP_GROUP}' '${REMOTE_CERT_DIR}'
    chmod 700 '${REMOTE_CERT_DIR}'
    chmod 600 '${REMOTE_CERT_DIR}/apiclient_key.pem'
    chmod 644 '${REMOTE_CERT_DIR}/pub_key.pem'
  "
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
  ssh_remote "
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
  ssh_remote "
    set -eu
    find '${remote_parent}' -maxdepth 1 -type d -name '${remote_base}-backup-*' -mmin +${retention_minutes} -exec rm -rf {} +
  "
}

build_backend_artifact() {
  log "Building backend jar once for all backend hosts"
  "${MVN_BIN}" -f "${REPO_ROOT}/backend/pom.xml" clean package -DskipTests

  BACKEND_JAR_PATH="${REPO_ROOT}/backend/target/backend.jar"
  [[ -f "${BACKEND_JAR_PATH}" ]] || fail "Backend jar not found: ${BACKEND_JAR_PATH}"
  BACKEND_JAR_SHA256="$(local_sha256 "${BACKEND_JAR_PATH}")"
  log "Backend artifact SHA-256: ${BACKEND_JAR_SHA256}"
}

deploy_backend_artifact() {
  assert_safe_remote_path "${REMOTE_BACKEND_DIR}" "Remote backend directory"
  [[ -f "${BACKEND_JAR_PATH}" ]] || fail "Backend jar not found: ${BACKEND_JAR_PATH}"
  [[ -n "${BACKEND_JAR_SHA256}" ]] || fail "Backend artifact SHA-256 is empty"

  ssh_remote "mkdir -p '${REMOTE_BACKEND_DIR}'"

  local remote_jar="${REMOTE_BACKEND_DIR}/backend.jar"
  local remote_new_jar="${REMOTE_BACKEND_DIR}/backend.jar.new"
  local remote_backup_jar=""
  if (( BACKUP_BACKEND == 1 )); then
    remote_backup_jar="${REMOTE_BACKEND_DIR}/backend.jar.backup-$(date +%Y%m%d-%H%M%S)"
  fi

  log "Uploading backend artifact ${BACKEND_JAR_SHA256} to ${REMOTE_HOST}:${remote_new_jar}"
  transfer_file "${BACKEND_JAR_PATH}" "${REMOTE_HOST}:${remote_new_jar}"

  log "Installing backend jar atomically"
  if (( BACKUP_BACKEND == 1 )); then
    ssh_remote "
      set -eu
      command -v sha256sum >/dev/null 2>&1
      if [ -f '${remote_new_jar}' ]; then
        if [ -f '${remote_jar}' ] && printf '%s  %s\n' '${BACKEND_JAR_SHA256}' '${remote_jar}' | sha256sum -c - >/dev/null 2>&1; then
          rm -f '${remote_new_jar}'
        else
          if [ -f '${remote_jar}' ]; then
            cp -a '${remote_jar}' '${remote_backup_jar}'
          fi
          mv -f '${remote_new_jar}' '${remote_jar}'
        fi
      fi
      printf '%s  %s\n' '${BACKEND_JAR_SHA256}' '${remote_jar}' | sha256sum -c - >/dev/null
      chown '${APP_USER}:${APP_GROUP}' '${remote_jar}' || true
      chmod 644 '${remote_jar}'
    "
  else
    ssh_remote "
      set -eu
      command -v sha256sum >/dev/null 2>&1
      if [ -f '${remote_new_jar}' ]; then
        if [ -f '${remote_jar}' ] && printf '%s  %s\n' '${BACKEND_JAR_SHA256}' '${remote_jar}' | sha256sum -c - >/dev/null 2>&1; then
          rm -f '${remote_new_jar}'
        else
          mv -f '${remote_new_jar}' '${remote_jar}'
        fi
      fi
      printf '%s  %s\n' '${BACKEND_JAR_SHA256}' '${remote_jar}' | sha256sum -c - >/dev/null
      chown '${APP_USER}:${APP_GROUP}' '${remote_jar}' || true
      chmod 644 '${remote_jar}'
    "
  fi

  if (( RESTART_BACKEND == 1 )); then
    log "Restarting backend service ${BACKEND_SERVICE_NAME}"
    if ! ssh_remote "
      sudo systemctl restart '${BACKEND_SERVICE_NAME}'
      sudo systemctl is-active --quiet '${BACKEND_SERVICE_NAME}'
      if [ -n '${BACKEND_HEALTH_URL}' ]; then
        for i in \$(seq 1 20); do
          if curl -fsS --max-time 2 '${BACKEND_HEALTH_URL}' >/dev/null; then
            exit 0
          fi
          sleep 1
        done
        exit 1
      fi
    "; then
      if (( BACKUP_BACKEND == 1 )); then
        log "Backend restart failed; restoring backup ${remote_backup_jar}"
        ssh_remote "
          set -eu
          if [ -f '${remote_backup_jar}' ]; then
            mv -f '${remote_backup_jar}' '${remote_jar}'
          fi
          sudo systemctl restart '${BACKEND_SERVICE_NAME}' || true
          sudo systemctl status '${BACKEND_SERVICE_NAME}' --no-pager || true
        "
      else
        ssh_remote "sudo systemctl status '${BACKEND_SERVICE_NAME}' --no-pager || true"
      fi
      fail "Backend service restart failed"
    fi
    ssh_remote "sudo systemctl status '${BACKEND_SERVICE_NAME}' --no-pager"
  else
    log "Skipping backend restart"
  fi

  if (( BACKUP_BACKEND == 1 )); then
    cleanup_remote_file_backups "${REMOTE_BACKEND_DIR}" "backend.jar.backup-*"
  fi

  log "Backend artifact ${BACKEND_JAR_SHA256} deployed successfully to ${REMOTE_HOST}"
}

deploy_h5() {
  assert_safe_remote_path "${REMOTE_H5_DIR}" "Remote H5 directory"
  log "Building H5"
  (
    cd "${REPO_ROOT}/h5"
    run_package_script "${H5_PACKAGE_MANAGER}" build:h5
  )

  [[ -d "${H5_BUILD_DIR}" ]] || fail "H5 build directory not found: ${H5_BUILD_DIR}"
  local remote_parent
  local remote_base
  local remote_tmp
  remote_parent="$(dirname "${REMOTE_H5_DIR}")"
  remote_base="$(basename "${REMOTE_H5_DIR}")"
  remote_tmp="${remote_parent}/.${remote_base}.new-$(date +%Y%m%d-%H%M%S)"
  assert_safe_remote_path "${remote_parent}" "Remote H5 parent directory"
  ssh_remote "mkdir -p '${remote_tmp}'"

  log "Uploading H5 dist to ${REMOTE_HOST}:${remote_tmp}"
  transfer_directory_contents "${H5_BUILD_DIR}" "${remote_tmp}"

  if (( BACKUP_H5 == 1 )); then
    log "Backing up remote H5 files at ${REMOTE_H5_DIR}"
    ssh_remote "
      set -eu
      if [ -d '${remote_tmp}' ]; then
        backup_dir=${REMOTE_H5_DIR}-backup-\$(date +%Y%m%d-%H%M%S)
        if [ -d '${REMOTE_H5_DIR}' ]; then
          mv '${REMOTE_H5_DIR}' \"\$backup_dir\"
        fi
        mv '${remote_tmp}' '${REMOTE_H5_DIR}'
      elif [ ! -d '${REMOTE_H5_DIR}' ]; then
        echo 'H5 staging and target directories are both missing' >&2
        exit 1
      fi
      chown -R root:root '${REMOTE_H5_DIR}'
      find '${REMOTE_H5_DIR}' -type d -exec chmod 755 {} +
      find '${REMOTE_H5_DIR}' -type f -exec chmod 644 {} +
    "
  else
    log "Replacing remote H5 files without backup"
    ssh_remote "
      set -eu
      if [ -d '${remote_tmp}' ]; then
        rm -rf '${REMOTE_H5_DIR}'
        mv '${remote_tmp}' '${REMOTE_H5_DIR}'
      elif [ ! -d '${REMOTE_H5_DIR}' ]; then
        echo 'H5 staging and target directories are both missing' >&2
        exit 1
      fi
      chown -R root:root '${REMOTE_H5_DIR}'
      find '${REMOTE_H5_DIR}' -type d -exec chmod 755 {} +
      find '${REMOTE_H5_DIR}' -type f -exec chmod 644 {} +
    "
  fi

  if (( BACKUP_H5 == 1 )); then
    cleanup_remote_dir_backups "${REMOTE_H5_DIR}"
  fi
}

deploy_frontend() {
  assert_safe_remote_path "${REMOTE_FRONTEND_DIR}" "Remote frontend directory"
  log "Building frontend"
  (
    cd "${REPO_ROOT}/frontend"
    run_package_script "${FRONTEND_PACKAGE_MANAGER}" build
  )

  [[ -d "${FRONTEND_BUILD_DIR}" ]] || fail "Frontend build directory not found: ${FRONTEND_BUILD_DIR}"
  local remote_parent
  local remote_base
  local remote_tmp
  remote_parent="$(dirname "${REMOTE_FRONTEND_DIR}")"
  remote_base="$(basename "${REMOTE_FRONTEND_DIR}")"
  remote_tmp="${remote_parent}/.${remote_base}.new-$(date +%Y%m%d-%H%M%S)"
  assert_safe_remote_path "${remote_parent}" "Remote frontend parent directory"
  ssh_remote "mkdir -p '${remote_tmp}'"

  log "Uploading frontend dist to ${REMOTE_HOST}:${remote_tmp}"
  transfer_directory_contents "${FRONTEND_BUILD_DIR}" "${remote_tmp}"

  if (( BACKUP_FRONTEND == 1 )); then
    log "Backing up remote frontend files at ${REMOTE_FRONTEND_DIR}"
    ssh_remote "
      set -eu
      if [ -d '${remote_tmp}' ]; then
        backup_dir=${REMOTE_FRONTEND_DIR}-backup-\$(date +%Y%m%d-%H%M%S)
        if [ -d '${REMOTE_FRONTEND_DIR}' ]; then
          mv '${REMOTE_FRONTEND_DIR}' \"\$backup_dir\"
        fi
        mv '${remote_tmp}' '${REMOTE_FRONTEND_DIR}'
      elif [ ! -d '${REMOTE_FRONTEND_DIR}' ]; then
        echo 'Frontend staging and target directories are both missing' >&2
        exit 1
      fi
      chown -R root:root '${REMOTE_FRONTEND_DIR}'
      find '${REMOTE_FRONTEND_DIR}' -type d -exec chmod 755 {} +
      find '${REMOTE_FRONTEND_DIR}' -type f -exec chmod 644 {} +
    "
  else
    log "Replacing remote frontend files without backup"
    ssh_remote "
      set -eu
      if [ -d '${remote_tmp}' ]; then
        rm -rf '${REMOTE_FRONTEND_DIR}'
        mv '${remote_tmp}' '${REMOTE_FRONTEND_DIR}'
      elif [ ! -d '${REMOTE_FRONTEND_DIR}' ]; then
        echo 'Frontend staging and target directories are both missing' >&2
        exit 1
      fi
      chown -R root:root '${REMOTE_FRONTEND_DIR}'
      find '${REMOTE_FRONTEND_DIR}' -type d -exec chmod 755 {} +
      find '${REMOTE_FRONTEND_DIR}' -type f -exec chmod 644 {} +
    "
  fi

  if (( BACKUP_FRONTEND == 1 )); then
    cleanup_remote_dir_backups "${REMOTE_FRONTEND_DIR}"
  fi
}

PRIMARY_REMOTE_HOST="${REMOTE_HOST}"
if (( DEPLOY_BACKEND == 1 )); then
  build_backend_artifact
  log "Backend deploy hosts: ${BACKEND_HOST_LIST[*]}"
  for backend_host in "${BACKEND_HOST_LIST[@]}"; do
    REMOTE_HOST="${backend_host}"
    log "Backend deploy target: ${REMOTE_HOST}"
    configure_transfer_mode
    if (( UPLOAD_ENV == 1 )); then
      upload_env_file
    fi
    if (( UPLOAD_CERTS == 1 )); then
      upload_cert_files
    fi
    deploy_backend_artifact
  done
elif (( UPLOAD_ENV == 1 || UPLOAD_CERTS == 1 )); then
  REMOTE_HOST="${PRIMARY_REMOTE_HOST}"
  log "Configuration upload target: ${REMOTE_HOST}"
  configure_transfer_mode
  if (( UPLOAD_ENV == 1 )); then
    upload_env_file
  fi
  if (( UPLOAD_CERTS == 1 )); then
    upload_cert_files
  fi
fi

if (( DEPLOY_H5 == 1 || DEPLOY_FRONTEND == 1 )); then
  REMOTE_HOST="${PRIMARY_REMOTE_HOST}"
  log "Static deploy target: ${REMOTE_HOST}"
  configure_transfer_mode
  if (( DEPLOY_H5 == 1 )); then
    deploy_h5
  fi
  if (( DEPLOY_FRONTEND == 1 )); then
    deploy_frontend
  fi
fi

log "Deployment finished"
