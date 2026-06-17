# 数据库只读查询

查库前先确认连接和表：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-check
```

数据库版本、空间和大表统计：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-info
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-info --lines 30
```

慢查询固定入口：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-slow --lines 200
```

业务只读 SQL 固定入口：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-query --sql "SELECT NOW();"
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-query --sql "SHOW TABLES LIKE 'mt_%';"
```

只能默认执行 `SELECT` / `SHOW`。不要执行写入、DDL、删除或大表无条件全表扫描。所有业务查询必须带 `LIMIT` 或明确时间范围。

## 基础信息排查

`mysql-info` 会输出：

- MySQL 版本、端口、datadir、uptime
- `mysqld` systemd 状态和进程内存
- 连接数、最大连接数、慢查询计数、InnoDB buffer pool 读状态
- 慢日志、binlog、performance_schema 等关键变量
- `datadir` 所在文件系统空间和目录大小
- binlog 文件列表和总大小；应用账号无 `SHOW BINARY LOGS` 权限时走文件系统只读统计
- schema 级空间统计
- 当前业务库最大表 top N

表行数来自 `information_schema.tables.table_rows`，InnoDB 下是估算值，不适合作为精确计数。

## 远端执行 SQL 模板

不要手写 SSH + MySQL 密码命令。统一使用 `ops.sh mysql-query`，脚本会在 edge 上通过 `/root/.my.cnf` 连接 MySQL；`/root/.my.cnf` 的 `[client]` 必须包含 `database=mtmini_ai_agent`。

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-query --sql "
SELECT id, poi_id, epoi_id, name, update_time
FROM mt_shop
ORDER BY update_time DESC
LIMIT 20;
"
```

不要 `source /etc/waimaidashi/backend.env`；这是 systemd `EnvironmentFile`，JDBC URL 中的 `&` 会被 shell 解释。也不要从 `backend.env` 提取 `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` 拼命令，避免首次查库因 shell 转义、权限或密钥输出问题失败。

## 慢查询排查

固定命令会输出：

- `slow_query_log`
- `slow_query_log_file`
- `long_query_time`
- `log_queries_not_using_indexes`
- `min_examined_row_limit`
- `log_output`
- 慢日志文件大小和最近 N 行
- 如存在 `mysqldumpslow`，输出按耗时和次数聚合的 top SQL
- 如 `performance_schema=ON` 且账号有权限，输出 statement digest top；没有权限时降级提示，不视为命令失败

默认只读，不修改慢查询配置。如果 `slow_query_log=OFF`，只能看到配置状态和 `performance_schema` 统计；开启慢查询日志属于配置变更，必须用户明确授权。

## 常用表方向

表名通常按实体驼峰转下划线。执行前可用 `SHOW TABLES LIKE 'mt_%';` 确认。

- 门店：`mt_shop`
- 门店权益：`mt_shop_right`
- 美团 token：`mt_access_token`、`mt_access_token_history`
- 美团订单：`mt_order`、`mt_order_detail`、`mt_app_order`
- 推广配置：`mt_promotion_setting`、`mt_promotion_setting_history`
- 推广日志/出价：`mt_promotion_bid_record`
- 推广指标：`mt_promotion_metric_snapshot`、`mt_promotion_period_metric_daily`
- 广告实时/小时/日数据：`mt_promotion_ad_realtime_snapshot`、`mt_promotion_ad_hourly_detail`、`mt_promotion_ad_daily`
- 异步任务：`mt_async_task`
- 算力：`mt_power_account`、`mt_power_consume`、`mt_power_consume_detail`、`mt_power_recharge`、`mt_power_grant`

## 查询模板

最近异步任务状态：

```sql
SELECT task_type, status, COUNT(*) AS cnt
FROM mt_async_task
WHERE create_time >= NOW() - INTERVAL 6 HOUR
GROUP BY task_type, status
ORDER BY task_type, status;
```

最近失败任务：

```sql
SELECT id, task_type, status, retry_count, next_retry_time, LEFT(error_msg, 300) AS error_msg, create_time, update_time
FROM mt_async_task
WHERE status IN ('FAILED', 'RETRY')
ORDER BY update_time DESC
LIMIT 30;
```

按门店查基础状态：

```sql
SELECT id, poi_id, epoi_id, name, poi_name, promotion_ad_backfill_status, historical_order_backfill_status, update_time
FROM mt_shop
WHERE poi_id = '<wmPoiId>' OR epoi_id = '<wmPoiId>' OR mini_poi_id = '<wmPoiId>'
LIMIT 20;
```

按门店查权益：

```sql
SELECT shop_id, right_code, status, start_at, expire_at, update_time
FROM mt_shop_right
WHERE shop_id = <shopId>
ORDER BY update_time DESC
LIMIT 20;
```

最近推广出价记录：

```sql
SELECT id, shop_id, poi_id, period_start_time, period_end_time, bid_amount, status, error_msg, create_time
FROM mt_promotion_bid_record
WHERE shop_id = <shopId>
ORDER BY create_time DESC
LIMIT 30;
```

最近充值记录：

```sql
SELECT id, recharge_no, shop_id, power_amount, total_amount_fen, pay_channel, pay_status, transaction_id, paid_at, create_time
FROM mt_power_recharge
WHERE shop_id = <shopId>
ORDER BY create_time DESC
LIMIT 20;
```

最近订单同步：

```sql
SELECT shop_id, COUNT(*) AS order_count, MAX(update_time) AS latest_update_time
FROM mt_order
WHERE create_time >= NOW() - INTERVAL 1 DAY
GROUP BY shop_id
ORDER BY latest_update_time DESC
LIMIT 30;
```
