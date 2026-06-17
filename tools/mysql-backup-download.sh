#!/usr/bin/env bash

set -euo pipefail

REMOTE_HOST="${WMD_MYSQL_BACKUP_HOST:-mt_ai_agent}"
DB_NAME="${WMD_MYSQL_BACKUP_DB:-mtmini_ai_agent}"
REMOTE_DIR="${WMD_MYSQL_BACKUP_REMOTE_DIR:-/data/backup/mysql}"
LOCAL_DIR="${WMD_MYSQL_BACKUP_LOCAL_DIR:-${HOME}/data/outsourcing/takeout-master-ai/db-backups}"
INCLUDE_BINLOG_POSITION="1"
PROGRESS_INTERVAL="${WMD_MYSQL_BACKUP_PROGRESS_INTERVAL:-30}"

usage() {
  cat <<'EOF'
Usage:
  tools/mysql-backup-download.sh [options]

Options:
  --host HOST                 SSH host or alias. Default: mt_ai_agent
  --db DB                     MySQL database name. Default: mtmini_ai_agent
  --remote-dir DIR            Remote backup dir. Default: /data/backup/mysql
  --local-dir DIR             Local download dir. Default: ~/data/outsourcing/takeout-master-ai/db-backups
  --progress-interval SECONDS Print remote dump file size every N seconds. Default: 30
  --no-binlog-position        Do not pass --source-data=2 to mysqldump
  -h, --help                  Show this help

Environment overrides:
  WMD_MYSQL_BACKUP_HOST
  WMD_MYSQL_BACKUP_DB
  WMD_MYSQL_BACKUP_REMOTE_DIR
  WMD_MYSQL_BACKUP_LOCAL_DIR
  WMD_MYSQL_BACKUP_PROGRESS_INTERVAL

Prerequisites:
  - Run this script from your local machine.
  - The remote server must have /root/.my.cnf configured for mysqldump.
  - Your local SSH config should allow scp/ssh to the remote host.
EOF
}

fail() {
  printf '[mysql-backup] ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '\n[mysql-backup] %s\n' "$*"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      [[ $# -ge 2 ]] || fail "--host requires a value"
      REMOTE_HOST="$2"
      shift 2
      ;;
    --db)
      [[ $# -ge 2 ]] || fail "--db requires a value"
      DB_NAME="$2"
      shift 2
      ;;
    --remote-dir)
      [[ $# -ge 2 ]] || fail "--remote-dir requires a value"
      REMOTE_DIR="$2"
      shift 2
      ;;
    --local-dir)
      [[ $# -ge 2 ]] || fail "--local-dir requires a value"
      LOCAL_DIR="$2"
      shift 2
      ;;
    --progress-interval)
      [[ $# -ge 2 ]] || fail "--progress-interval requires a value"
      PROGRESS_INTERVAL="$2"
      shift 2
      ;;
    --no-binlog-position)
      INCLUDE_BINLOG_POSITION="0"
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

[[ "${DB_NAME}" =~ ^[A-Za-z0-9_]+$ ]] || fail "--db must contain only letters, numbers, and underscores"
[[ "${REMOTE_DIR}" = /* ]] || fail "--remote-dir must be an absolute path"
[[ "${REMOTE_DIR}" =~ ^/[A-Za-z0-9._/-]+$ ]] || fail "--remote-dir contains unsupported characters"
[[ "${PROGRESS_INTERVAL}" =~ ^[0-9]+$ ]] || fail "--progress-interval must be a positive integer"
[[ "${PROGRESS_INTERVAL}" -gt 0 ]] || fail "--progress-interval must be greater than 0"

LOCAL_DIR="${LOCAL_DIR/#\~/${HOME}}"
mkdir -p "${LOCAL_DIR}"

log "creating MySQL backup on ${REMOTE_HOST}"
remote_log="$(mktemp)"
trap 'rm -f "${remote_log}"' EXIT

ssh -o BatchMode=yes -o ConnectTimeout=8 "${REMOTE_HOST}" \
  "DB_NAME='${DB_NAME}' REMOTE_DIR='${REMOTE_DIR}' INCLUDE_BINLOG_POSITION='${INCLUDE_BINLOG_POSITION}' PROGRESS_INTERVAL='${PROGRESS_INTERVAL}' bash -s" <<'REMOTE_SCRIPT' | tee "${remote_log}"
set -euo pipefail

remote_user="$(id -un)"
date_tag="$(date +%F_%H-%M-%S)"
file="${REMOTE_DIR}/${DB_NAME}_${date_tag}.sql.gz"
tmp_file="${file}.tmp"
dump_pid=""

cleanup() {
  if [ -n "${dump_pid}" ] && kill -0 "${dump_pid}" 2>/dev/null; then
    kill "${dump_pid}" 2>/dev/null || true
  fi
  rm -f "${tmp_file}"
}

trap cleanup INT TERM EXIT

sudo mkdir -p "${REMOTE_DIR}"
sudo chown "${remote_user}:${remote_user}" "${REMOTE_DIR}"
sudo chmod 700 "${REMOTE_DIR}"

dump_args=(
  --defaults-extra-file=/root/.my.cnf
  --single-transaction
  --no-tablespaces
  --routines
  --triggers
  --events
  --hex-blob
)

if [ "${INCLUDE_BINLOG_POSITION}" = "1" ]; then
  dump_args+=(--source-data=2)
fi

echo "hostname=$(hostname)"
echo "started_at=$(date '+%F %T %Z')"
echo "database=${DB_NAME}"
echo "remote_file=${file}"

echo "dump_started_at=$(date '+%F %T %Z')"
(sudo mysqldump "${dump_args[@]}" "${DB_NAME}" | gzip -1 > "${tmp_file}") &
dump_pid="$!"

while kill -0 "${dump_pid}" 2>/dev/null; do
  if [ -f "${tmp_file}" ]; then
    size="$(ls -lh "${tmp_file}" | awk '{print $5}')"
    echo "dump_progress time=$(date '+%F %T %Z') temp_size=${size}"
  else
    echo "dump_progress time=$(date '+%F %T %Z') temp_size=0"
  fi
  sleep "${PROGRESS_INTERVAL}"
done

set +e
wait "${dump_pid}"
dump_status="$?"
set -e
if [ "${dump_status}" -ne 0 ]; then
  echo "dump_failed_at=$(date '+%F %T %Z')"
  exit "${dump_status}"
fi
dump_pid=""
echo "dump_finished_at=$(date '+%F %T %Z')"

mv "${tmp_file}" "${file}"
chmod 600 "${file}"

echo "gzip_check_started_at=$(date '+%F %T %Z')"
gzip -t "${file}"
echo "gzip_check_finished_at=$(date '+%F %T %Z')"
ls -lh "${file}"
echo "finished_at=$(date '+%F %T %Z')"
echo "WMD_BACKUP_FILE=${file}"
REMOTE_SCRIPT

remote_file="$(awk -F= '$1 == "WMD_BACKUP_FILE" { print substr($0, index($0, "=") + 1) }' "${remote_log}" | tail -n 1)"
[[ -n "${remote_file}" ]] || fail "remote backup did not return a file path"

log "downloading backup to ${LOCAL_DIR}"
scp "${REMOTE_HOST}:${remote_file}" "${LOCAL_DIR}/"

local_file="${LOCAL_DIR}/$(basename "${remote_file}")"
gzip -t "${local_file}"
ls -lh "${local_file}"
log "backup downloaded and gzip verified: ${local_file}"
