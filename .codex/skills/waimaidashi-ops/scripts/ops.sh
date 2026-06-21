#!/usr/bin/env bash

set -euo pipefail

WEB_HOST="${WMD_WEB_HOST:-${WMD_EDGE_HOST:-mt4}}"
DB_HOST="${WMD_DB_HOST:-mt3}"
EDGE_HOST="${WEB_HOST}"
BACKEND_SERVICE="${WMD_BACKEND_SERVICE:-waimaidashi-backend}"
CADDY_SERVICE="${WMD_CADDY_SERVICE:-caddy}"
REMOTE_ENV_PATH="${WMD_REMOTE_ENV_PATH:-/etc/waimaidashi/backend.env}"

HOST_SELECTOR="both"
SINCE="30 min ago"
LINES="200"
PATTERN="error|exception|failed|timeout|refused|deadlock|duplicate|lock|meituan|wechat|redis|mysql|caddy|callback|async|comment|reply|auth"
SLOW_THRESHOLD="0.5"

usage() {
  cat <<'EOF'
Usage:
  ops.sh health [--host web|db|both]
  ops.sh status [--host web|db|both]
  ops.sh logs [--host web] [--since "30 min ago"] [--lines 200]
  ops.sh errors [--host web] [--since "2 hours ago"] [--lines 200] [--pattern REGEX]
  ops.sh tasks [--host web] [--since "6 hours ago"]
  ops.sh ports [--host web|db|both]
  ops.sh caddy
  ops.sh caddy-access [--since "2026/05/17 14:15"] [--lines 20000] [--slow-threshold 0.5]
  ops.sh env-summary [--host web]
  ops.sh redis-locks [--lines 100]
  ops.sh redis-persistence
  ops.sh mysql-check
  ops.sh mysql-query --sql "SELECT NOW();"
  ops.sh mysql-info [--lines 20]
  ops.sh mysql-slow [--lines 200]
  ops.sh mysql-backup
  ops.sh sites

Env overrides:
  WMD_WEB_HOST=mt4
  WMD_DB_HOST=mt3
  WMD_BACKEND_SERVICE=waimaidashi-backend
  WMD_REMOTE_ENV_PATH=/etc/waimaidashi/backend.env
EOF
}

fail() {
  printf '[ops] ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '\n[ops] %s\n' "$*"
}

parse_common_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --host)
        [[ $# -ge 2 ]] || fail "--host requires a value"
        HOST_SELECTOR="$2"
        shift 2
        ;;
      --since)
        [[ $# -ge 2 ]] || fail "--since requires a value"
        SINCE="$2"
        shift 2
        ;;
      --lines)
        [[ $# -ge 2 ]] || fail "--lines requires a value"
        LINES="$2"
        shift 2
        ;;
      --pattern)
        [[ $# -ge 2 ]] || fail "--pattern requires a value"
        PATTERN="$2"
        shift 2
        ;;
      --slow-threshold)
        [[ $# -ge 2 ]] || fail "--slow-threshold requires a value"
        SLOW_THRESHOLD="$2"
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
}

selected_hosts() {
  case "${HOST_SELECTOR}" in
    web|edge)
      printf '%s\n' "${WEB_HOST}"
      ;;
    db|worker)
      printf '%s\n' "${DB_HOST}"
      ;;
    both)
      printf '%s\n%s\n' "${WEB_HOST}" "${DB_HOST}"
      ;;
    *)
      printf '%s\n' "${HOST_SELECTOR}"
      ;;
  esac
}

host_role() {
  local host="$1"
  if [[ "${host}" == "${WEB_HOST}" ]]; then
    printf 'web\n'
  elif [[ "${host}" == "${DB_HOST}" ]]; then
    printf 'db\n'
  else
    printf 'custom\n'
  fi
}

run_ssh() {
  local host="$1"
  shift
  ssh -n -o BatchMode=yes -o ConnectTimeout=8 "${host}" "$@"
}

run_ssh_with_stdin() {
  local host="$1"
  shift
  ssh -o BatchMode=yes -o ConnectTimeout=8 "${host}" "$@"
}

cmd_status() {
  parse_common_args "$@"
  while IFS= read -r host; do
    if [[ "$(host_role "${host}")" == "db" ]]; then
      log "database/cache status on ${host}"
      run_ssh "${host}" "hostname; date; (systemctl is-active mysqld || systemctl is-active mysql || true); sudo systemctl status mysqld --no-pager -l 2>/dev/null | sed -n '1,35p' || sudo systemctl status mysql --no-pager -l 2>/dev/null | sed -n '1,35p' || true; echo; (systemctl is-active redis || systemctl is-active redis-server || true); sudo systemctl status redis --no-pager -l 2>/dev/null | sed -n '1,35p' || sudo systemctl status redis-server --no-pager -l 2>/dev/null | sed -n '1,35p' || true"
    else
      log "backend status on ${host}"
      run_ssh "${host}" "hostname; date; systemctl is-active '${BACKEND_SERVICE}' || true; sudo systemctl status '${BACKEND_SERVICE}' --no-pager -l | sed -n '1,45p'"
    fi
  done < <(selected_hosts)
}

cmd_health() {
  parse_common_args "$@"
  while IFS= read -r host; do
    if [[ "$(host_role "${host}")" == "db" ]]; then
      log "database/cache health on ${host}"
      run_ssh "${host}" "hostname; date; uptime; echo; (systemctl is-active mysqld || systemctl is-active mysql || true); (systemctl is-active redis || systemctl is-active redis-server || true); echo; free -m; echo; df -h / /opt /var 2>/dev/null || df -h; echo; ss -lntp | egrep '(:31306|:6379)' || true"
    else
      log "web/backend health on ${host}"
      run_ssh "${host}" "hostname; date; uptime; echo; systemctl is-active '${BACKEND_SERVICE}' || true; sudo systemctl status '${BACKEND_SERVICE}' --no-pager -l | sed -n '1,25p'; echo; systemctl is-active '${CADDY_SERVICE}' || true; echo; curl -fsS http://127.0.0.1:8080/health/check || true; echo; free -m; echo; df -h / /opt /var 2>/dev/null || df -h; echo; ss -lntp | egrep '(:8080|:80|:443)' || true"
    fi
  done < <(selected_hosts)
}

cmd_logs() {
  parse_common_args "$@"
  while IFS= read -r host; do
    if [[ "$(host_role "${host}")" == "db" ]]; then
      log "skip backend logs on database host ${host}"
      continue
    fi
    log "backend logs on ${host} since ${SINCE}"
    run_ssh "${host}" "sudo journalctl -u '${BACKEND_SERVICE}' --since '${SINCE}' --no-pager -n '${LINES}'"
  done < <(selected_hosts)
}

cmd_errors() {
  parse_common_args "$@"
  while IFS= read -r host; do
    if [[ "$(host_role "${host}")" == "db" ]]; then
      log "skip backend filtered logs on database host ${host}"
      continue
    fi
    log "backend filtered logs on ${host} since ${SINCE}"
    run_ssh "${host}" "sudo journalctl -u '${BACKEND_SERVICE}' --since '${SINCE}' --no-pager | egrep -i '${PATTERN}' | tail -n '${LINES}' || true"
  done < <(selected_hosts)
}

cmd_tasks() {
  SINCE="6 hours ago"
  PATTERN="MtCommentAutoReplyTask|MtAsyncTaskWorkerTask|MtAppOrderPullTask|MtAccessTokenRefreshTask|YouzanAccessTokenRefreshTask|RedisDistributedLockService|async-task|数据库异步任务|评价自动回复|授权后异步|lock"
  parse_common_args "$@"
  while IFS= read -r host; do
    if [[ "$(host_role "${host}")" == "db" ]]; then
      log "skip backend task logs on database host ${host}"
      continue
    fi
    log "task logs on ${host} since ${SINCE}"
    run_ssh "${host}" "sudo journalctl -u '${BACKEND_SERVICE}' --since '${SINCE}' --no-pager | egrep -i '${PATTERN}' | tail -n '${LINES}' || true"
  done < <(selected_hosts)
}

cmd_ports() {
  parse_common_args "$@"
  while IFS= read -r host; do
    log "listening ports on ${host}"
    run_ssh "${host}" "hostname; ss -lntp | egrep '(:8080|:31306|:6379|:80|:443)' || true"
  done < <(selected_hosts)
}

cmd_caddy() {
  HOST_SELECTOR="edge"
  parse_common_args "$@"
  local host="${EDGE_HOST}"
  log "caddy status on ${host}"
  run_ssh "${host}" "hostname; systemctl is-active '${CADDY_SERVICE}' || true; sudo caddy validate --config /etc/caddy/Caddyfile; sudo systemctl status '${CADDY_SERVICE}' --no-pager -l | sed -n '1,45p'; echo; sudo journalctl -u '${CADDY_SERVICE}' --since '${SINCE}' --no-pager -n '${LINES}'"
}

cmd_caddy_access() {
  HOST_SELECTOR="edge"
  LINES="20000"
  SINCE=""
  parse_common_args "$@"
  [[ "${SLOW_THRESHOLD}" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "--slow-threshold must be numeric seconds"
  log "caddy access summary on ${EDGE_HOST}"
  run_ssh_with_stdin "${EDGE_HOST}" "LINES='${LINES}' SINCE='${SINCE}' SLOW_THRESHOLD='${SLOW_THRESHOLD}' python3 -s" <<'PY'
import collections
import json
import os
import subprocess

lines = int(float(os.environ["LINES"]))
since_raw = os.environ["SINCE"]
since = since_raw[:16] if since_raw[:1].isdigit() else ""
slow_threshold = float(os.environ["SLOW_THRESHOLD"])
paths = [
    "/var/log/caddy/aib-access.log",
    "/var/log/caddy/aibadmin-access.log",
    "/var/log/caddy/aibapi-access.log",
]

rows = []
errs = []
counts = collections.Counter()
total = 0

for path in paths:
    try:
        content = subprocess.check_output(
            ["sudo", "tail", "-n", str(lines), path],
            universal_newlines=True,
            stderr=subprocess.DEVNULL,
        )
    except Exception:
        continue
    for line in content.splitlines():
        if "{" not in line:
            continue
        prefix, raw_tail = line.split("{", 1)
        ts = prefix.strip()[:16]
        if since and ts < since:
            continue
        try:
            obj = json.loads("{" + raw_tail)
        except Exception:
            continue
        req = obj.get("request") or {}
        dur = float(obj.get("duration") or 0)
        status = int(obj.get("status") or 0)
        host = req.get("host") or ""
        method = req.get("method") or ""
        uri = req.get("uri") or ""
        size = obj.get("size") or 0
        total += 1
        counts[(host, status)] += 1
        record = (dur, prefix.strip()[:23], host, method, uri, status, size)
        if dur >= slow_threshold:
            rows.append(record)
        if status >= 500 or status == 0:
            errs.append(record)

print(f"since={since} lines_per_file={lines} slow_threshold={slow_threshold}s total_parsed={total} slow_count={len(rows)} status_0_or_5xx={len(errs)}")
print("--- top slow requests ---")
for dur, ts, host, method, uri, status, size in sorted(rows, reverse=True)[:30]:
    print(f"{dur:.3f}s status={status} size={size} host={host} method={method} uri={uri} time={ts}")
print("--- status 0/5xx requests ---")
for dur, ts, host, method, uri, status, size in errs[-30:]:
    print(f"{dur:.3f}s status={status} size={size} host={host} method={method} uri={uri} time={ts}")
print("--- status counts ---")
for (host, status), count in counts.most_common(20):
    print(f"{host} status={status} count={count}")
PY
}

cmd_env_summary() {
  HOST_SELECTOR="edge"
  parse_common_args "$@"
  local host
  host="$(selected_hosts | head -n 1)"
  log "masked env summary on ${host}"
  run_ssh "${host}" "sudo awk -F= '
    /^[[:space:]]*#/ || NF < 2 { next }
    {
      key=\$1
      value=substr(\$0, length(key)+2)
      if (key ~ /(PASSWORD|SECRET|PRIVATE|SIGN_KEY|API.*KEY|_KEY$|TOKEN_HISTORY|ACCESS_TOKEN|REFRESH_TOKEN|MCH_ID|SERIAL_NUMBER|CLIENT_SECRET)/) {
        value=\"***\"
      }
      print key \"=\" value
    }
  ' '${REMOTE_ENV_PATH}'"
}

cmd_redis_locks() {
  HOST_SELECTOR="db"
  parse_common_args "$@"
  log "redis lock-like keys on ${DB_HOST}"
  run_ssh "${DB_HOST}" "pass=\$(sudo sed -n 's/^requirepass[[:space:]]\\+//p' /etc/redis/redis.conf /etc/redis.conf 2>/dev/null | tail -n1); REDISCLI_AUTH=\"\$pass\" redis-cli -h 127.0.0.1 -p 6379 ping; echo; REDISCLI_AUTH=\"\$pass\" redis-cli -h 127.0.0.1 -p 6379 --scan --pattern '*lock*' | head -n '${LINES}'"
}

cmd_redis_persistence() {
  HOST_SELECTOR="db"
  parse_common_args "$@"
  log "redis persistence on ${DB_HOST}"
  run_ssh "${DB_HOST}" "
    pass=\$(sudo sed -n 's/^requirepass[[:space:]]\\+//p' /etc/redis/redis.conf /etc/redis.conf 2>/dev/null | tail -n1)
    rc() { REDISCLI_AUTH=\"\$pass\" redis-cli -h 127.0.0.1 -p 6379 \"\$@\"; }

    hostname
    date
    echo
    systemctl is-active redis || systemctl is-active redis-server || true
    ss -lntp | egrep ':6379' || true

    echo
    rc INFO server | egrep '^(redis_version|config_file|executable|process_id|tcp_port)'

    echo
    for key in save appendonly appendfsync dir dbfilename appendfilename rdbcompression rdbchecksum stop-writes-on-bgsave-error auto-aof-rewrite-percentage auto-aof-rewrite-min-size aof-use-rdb-preamble; do
      rc CONFIG GET \"\$key\"
    done

    echo
    rc INFO persistence | egrep '^(loading|rdb_|aof_)'

    echo
    dir=\$(rc CONFIG GET dir | tail -n1 | tr -d '\r')
    dbfilename=\$(rc CONFIG GET dbfilename | tail -n1 | tr -d '\r')
    last_save=\$(rc INFO persistence | sed -n 's/^rdb_last_save_time://p' | tr -d '\r')
    printf 'rdb_file=%s/%s\n' \"\$dir\" \"\$dbfilename\"
    [ -n \"\$last_save\" ] && date -d @\"\$last_save\" '+rdb_last_save_time_human=%F %T %Z' 2>/dev/null || true
    sudo ls -lh \"\$dir/\$dbfilename\" 2>/dev/null || true

    echo
    config_file=\$(rc INFO server | sed -n 's/^config_file://p' | tr -d '\r')
    if [ -n \"\$config_file\" ]; then
      sudo egrep -n '^[[:space:]]*(save|appendonly|appendfsync|dir|dbfilename|appendfilename|rdbcompression|rdbchecksum|stop-writes-on-bgsave-error|auto-aof-rewrite-percentage|auto-aof-rewrite-min-size|aof-use-rdb-preamble)[[:space:]]' \"\$config_file\" | sed -n '1,120p'
    fi
  "
}

cmd_mysql_check() {
  HOST_SELECTOR="db"
  parse_common_args "$@"
  log "mysql connectivity and table overview on ${DB_HOST}"
  run_ssh_with_stdin "${DB_HOST}" "bash -s" <<'REMOTE'
    set -euo pipefail
    mysql_exec() {
      sudo test -r /root/.my.cnf || {
        echo "ERROR: /root/.my.cnf is missing or unreadable; configure remote MySQL defaults before querying" >&2
        return 1
      }
      sudo mysql --defaults-extra-file=/root/.my.cnf --batch --raw -e "$1"
    }
    hostname
    date
    echo
    mysql_exec "SELECT NOW() AS now_time, DATABASE() AS db_name, CURRENT_USER() AS mysql_user, @@hostname AS mysql_hostname, @@port AS mysql_port;"
    db_name=$(mysql_exec "SELECT DATABASE();" | awk 'NR==2 {print $1}')
    if [ -z "${db_name}" ] || [ "${db_name}" = "NULL" ]; then
      echo "ERROR: /root/.my.cnf must define default database=mtmini_ai_agent under [client]" >&2
      exit 1
    fi
    echo
    mysql_exec "SHOW TABLES LIKE 'mt_%';"
REMOTE
}

cmd_mysql_query() {
  HOST_SELECTOR="db"
  local sql=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --host)
        [[ $# -ge 2 ]] || fail "--host requires a value"
        HOST_SELECTOR="$2"
        shift 2
        ;;
      --sql)
        [[ $# -ge 2 ]] || fail "--sql requires a value"
        sql="$2"
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
  if [[ -z "${sql}" ]]; then
    sql="$(cat)"
  fi
  [[ -n "${sql//[[:space:]]/}" ]] || fail "mysql-query requires --sql or SQL on stdin"
  if ! printf '%s' "${sql}" | tr '\n' ' ' | grep -Eiq '^[[:space:]]*(SELECT|SHOW|EXPLAIN|DESCRIBE|DESC|WITH)[[:space:]]'; then
    fail "mysql-query only allows read-only SELECT/SHOW/EXPLAIN/DESCRIBE/WITH statements"
  fi
  if printf '%s' "${sql}" | grep -Eiq '\b(INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE|CREATE|REPLACE|GRANT|REVOKE|LOAD|CALL|SET)[[:space:]]'; then
    fail "mysql-query rejected potentially mutating SQL"
  fi

  local host sql_b64
  host="$(selected_hosts | head -n 1)"
  sql_b64="$(printf '%s' "${sql}" | base64 | tr -d '\n')"
  log "mysql read-only query on ${host}"
  run_ssh_with_stdin "${host}" "SQL_B64='${sql_b64}' bash -s" <<'REMOTE'
    set -euo pipefail
    sql=$(printf '%s' "${SQL_B64}" | base64 -d)
    sudo test -r /root/.my.cnf || {
      echo "ERROR: /root/.my.cnf is missing or unreadable; configure remote MySQL defaults before querying" >&2
      exit 1
    }
    sudo mysql --defaults-extra-file=/root/.my.cnf --batch --raw -e "${sql}"
REMOTE
}

cmd_mysql_info() {
  HOST_SELECTOR="db"
  LINES="20"
  parse_common_args "$@"
  log "mysql basic info and table sizes on ${DB_HOST}"
  run_ssh "${DB_HOST}" "
    mysql_exec() {
      sudo test -r /root/.my.cnf || {
        echo 'ERROR: /root/.my.cnf is missing or unreadable; configure remote MySQL defaults before querying' >&2
        return 1
      }
      sudo mysql --defaults-extra-file=/root/.my.cnf --batch --raw -e \"\$1\"
    }

    hostname
    date
    echo
    systemctl is-active mysqld || systemctl is-active mysql || true
    sudo systemctl status mysqld --no-pager -l 2>/dev/null | sed -n '1,20p' || sudo systemctl status mysql --no-pager -l 2>/dev/null | sed -n '1,20p' || true

    echo
    ps -p \$(pidof mysqld 2>/dev/null | awk '{print \$1}') -o pid,comm,%cpu,%mem,rss,etime,args 2>/dev/null || true

    echo
    mysql_exec \"SELECT NOW() AS now_time, VERSION() AS mysql_version, @@hostname AS mysql_hostname, @@port AS mysql_port, DATABASE() AS current_db, @@datadir AS datadir, @@socket AS socket_path;\"

    echo
    mysql_exec \"SHOW GLOBAL STATUS WHERE Variable_name IN ('Uptime','Threads_connected','Threads_running','Connections','Max_used_connections','Questions','Slow_queries','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests');\"

    echo
    mysql_exec \"SHOW VARIABLES WHERE Variable_name IN ('max_connections','innodb_buffer_pool_size','slow_query_log','long_query_time','log_bin','binlog_expire_logs_seconds','performance_schema','datadir','tmpdir');\"

    echo
    echo '--- filesystem usage ---'
    datadir=\$(mysql_exec \"SHOW VARIABLES LIKE 'datadir';\" | awk 'NR==2 {print \$2}')
    sudo df -h \"\$datadir\" 2>/dev/null || df -h
    sudo du -sh \"\$datadir\" 2>/dev/null || true

    echo
    echo '--- binlog files ---'
    mysql_exec \"SHOW VARIABLES WHERE Variable_name IN ('log_bin','log_bin_basename','log_bin_index','binlog_expire_logs_seconds','max_binlog_size');\"
    sudo find \"\$datadir\" -maxdepth 1 \( -name 'binlog.*' -o -name 'mysql-bin.*' \) -type f -printf '%f %s %TY-%Tm-%Td %TH:%TM\n' 2>/dev/null | sort | tail -n '${LINES}' || true
    sudo find \"\$datadir\" -maxdepth 1 \( -name 'binlog.*' -o -name 'mysql-bin.*' \) -type f -printf '%s\n' 2>/dev/null | awk '{sum+=\$1; count++} END {printf \"binlog_count=%d\nbinlog_total_bytes=%d\nbinlog_total_mb=%.2f\n\", count, sum, sum/1024/1024}'

    echo
    echo '--- schema sizes ---'
    mysql_exec \"SELECT table_schema, ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS total_mb, ROUND(SUM(data_length) / 1024 / 1024, 2) AS data_mb, ROUND(SUM(index_length) / 1024 / 1024, 2) AS index_mb, COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema NOT IN ('mysql','information_schema','performance_schema','sys') GROUP BY table_schema ORDER BY total_mb DESC;\"

    echo
    echo '--- largest tables ---'
    mysql_exec \"SELECT table_schema, table_name, table_rows, ROUND((data_length + index_length) / 1024 / 1024, 2) AS total_mb, ROUND(data_length / 1024 / 1024, 2) AS data_mb, ROUND(index_length / 1024 / 1024, 2) AS index_mb, engine, update_time FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY (data_length + index_length) DESC LIMIT ${LINES};\"
  "
}

cmd_mysql_slow() {
  HOST_SELECTOR="db"
  parse_common_args "$@"
  log "mysql slow query diagnostics on ${DB_HOST}"
  run_ssh "${DB_HOST}" "
    mysql_exec() { sudo mysql --defaults-extra-file=/root/.my.cnf --batch --raw -e \"\$1\"; }

    hostname
    date
    echo
    mysql_exec \"SELECT NOW() AS now_time, VERSION() AS mysql_version, @@hostname AS mysql_hostname, @@port AS mysql_port, @@datadir AS datadir;\"

    echo
    mysql_exec \"SHOW VARIABLES WHERE Variable_name IN ('slow_query_log','slow_query_log_file','long_query_time','log_queries_not_using_indexes','min_examined_row_limit','log_output','performance_schema');\"

    echo
    slow_file=\$(mysql_exec \"SHOW VARIABLES LIKE 'slow_query_log_file';\" | awk 'NR==2 {print \$2}')
    if [ -n \"\$slow_file\" ]; then
      printf 'slow_query_log_file=%s\n' \"\$slow_file\"
      sudo ls -lh \"\$slow_file\" 2>/dev/null || true
      echo
      echo '--- slow log tail ---'
      sudo tail -n '${LINES}' \"\$slow_file\" 2>/dev/null || true
      echo
      if command -v mysqldumpslow >/dev/null 2>&1; then
        echo '--- mysqldumpslow top by time ---'
        sudo mysqldumpslow -s t -t 10 \"\$slow_file\" 2>/dev/null | sed -n '1,160p' || true
        echo
        echo '--- mysqldumpslow top by count ---'
        sudo mysqldumpslow -s c -t 10 \"\$slow_file\" 2>/dev/null | sed -n '1,160p' || true
      else
        echo 'mysqldumpslow not found'
      fi
    fi

    echo
    perf_enabled=\$(mysql_exec \"SHOW VARIABLES LIKE 'performance_schema';\" | awk 'NR==2 {print \$2}')
    if [ \"\$perf_enabled\" = \"ON\" ]; then
      echo '--- performance_schema statement digest top by total latency ---'
      mysql_exec \"SELECT IFNULL(SCHEMA_NAME,'') AS schema_name, LEFT(REPLACE(REPLACE(DIGEST_TEXT, CHAR(10), ' '), CHAR(9), ' '), 180) AS digest_text, COUNT_STAR, ROUND(SUM_TIMER_WAIT/1000000000000, 3) AS total_sec, ROUND(AVG_TIMER_WAIT/1000000000000, 6) AS avg_sec, SUM_ROWS_EXAMINED, SUM_ROWS_SENT, FIRST_SEEN, LAST_SEEN FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME = 'mtmini_ai_agent' ORDER BY SUM_TIMER_WAIT DESC LIMIT 15;\" 2>/dev/null || echo 'performance_schema digest query unavailable'
    else
      echo 'performance_schema is not ON'
    fi
  "
}

cmd_mysql_backup() {
  [[ $# -eq 0 ]] || fail "mysql-backup only prints project script usage; run tools/mysql-backup-download.sh directly for options"
  cat <<'EOF'
Manual MySQL backup script:

  tools/mysql-backup-download.sh

Defaults:
  remote host: mt3
  database: mtmini_ai_agent
  remote dir: /data/backup/mysql
  local dir: ~/data/outsourcing/takeout-master-ai/db-backups

Examples:
  tools/mysql-backup-download.sh
  tools/mysql-backup-download.sh --no-binlog-position
  tools/mysql-backup-download.sh --local-dir ~/data/outsourcing/takeout-master-ai/db-backups

Notes:
  - Run this manually in a local terminal.
  - The remote server must already have /root/.my.cnf configured for mysqldump.
  - The script creates a gzip dump on the server, downloads it with scp, then verifies gzip locally.
EOF
}

cmd_sites() {
  log "public site headers"
  for url in \
    https://aib.waimaidashi.com/ \
    https://aibadmin.waimaidashi.com/ \
    https://aibapi.waimaidashi.com/api/callbacks/meituan/auth \
    https://aibapi.waimaidashi.com/api/callbacks/wechat/pay
  do
    printf '\n[ops] curl -I %s\n' "${url}"
    curl -I --connect-timeout 8 --max-time 15 "${url}" || true
  done
}

main() {
  [[ $# -ge 1 ]] || {
    usage
    exit 1
  }

  local command="$1"
  shift

  case "${command}" in
    health) cmd_health "$@" ;;
    status) cmd_status "$@" ;;
    logs) cmd_logs "$@" ;;
    errors) cmd_errors "$@" ;;
    tasks) cmd_tasks "$@" ;;
    ports) cmd_ports "$@" ;;
    caddy) cmd_caddy "$@" ;;
    caddy-access) cmd_caddy_access "$@" ;;
    env-summary) cmd_env_summary "$@" ;;
    redis-locks) cmd_redis_locks "$@" ;;
    redis-persistence) cmd_redis_persistence "$@" ;;
    mysql-check) cmd_mysql_check "$@" ;;
    mysql-query) cmd_mysql_query "$@" ;;
    mysql-info) cmd_mysql_info "$@" ;;
    mysql-slow) cmd_mysql_slow "$@" ;;
    mysql-backup) cmd_mysql_backup "$@" ;;
    sites) cmd_sites "$@" ;;
    -h|--help) usage ;;
    *) fail "Unknown command: ${command}" ;;
  esac
}

main "$@"
