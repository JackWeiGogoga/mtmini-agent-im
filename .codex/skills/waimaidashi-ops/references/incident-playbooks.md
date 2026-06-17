# 事故排查 Playbook

## 通用入口

先跑：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh health
.codex/skills/waimaidashi-ops/scripts/ops.sh errors --host both --since "2 hours ago"
```

判断顺序：

1. 两台 backend 是否 active
2. edge 的 Caddy 是否 active 且配置有效
3. edge 的 MySQL/Redis 是否监听内网端口
4. worker 是否能通过内网访问 MySQL/Redis
5. 同一时间窗口两台日志是否出现相同任务重复执行或锁争用
6. 是否集中出现美团、微信、Redis、MySQL、LLM、COS、短信相关错误

## 后端不可用

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host both
.codex/skills/waimaidashi-ops/scripts/ops.sh logs --host both --since "30 min ago" --lines 300
.codex/skills/waimaidashi-ops/scripts/ops.sh ports --host both
```

关注：

- Java 启动异常
- `EnvironmentFile` 缺失或权限错误
- MySQL/Redis 连接拒绝、超时、鉴权失败
- 端口 `8080` 被占用
- OOM 或频繁重启

## Caddy 或站点异常

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh caddy
.codex/skills/waimaidashi-ops/scripts/ops.sh sites
```

`curl -I` 回调接口返回 `405` 不一定是错误，可能只是接口不支持 `HEAD`/`GET`。重点看是否到达 Caddy/后端，以及是否出现 `502`、证书错误、DNS 错误。

## 定时任务与异步消费

两台都运行定时任务和异步任务消费，不能只看一台。

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh tasks --host both --since "6 hours ago"
.codex/skills/waimaidashi-ops/scripts/ops.sh redis-locks --lines 100
```

重点任务名：

- `MtPromotionAutoBidTask`
- `MtPromotionAsyncTaskWorkerTask`
- `MtAppOrderPullTask`
- `MtAccessTokenRefreshTask`
- `MtPromotionAdDataTask`
- `MtPromotionRightExpiryShutdownTask`
- `MtPoiTrafficDailyTask`
- `MtShopPeakWindowProfileRefreshTask`
- `YouzanAccessTokenRefreshTask`

如果两台都出现同一任务日志，先确认是否有 Redis 分布式锁的 acquire/skip 记录，再判断是否重复执行。

## Redis 排查

Redis 在 edge 上，需要认证。不要直接 `redis-cli CONFIG GET ...`，也不要 `source /etc/waimaidashi/backend.env`；`backend.env` 是 systemd EnvironmentFile，JDBC URL 中的 `&` 会让 shell 解析失败。

持久化配置和状态统一用：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh redis-persistence
```

这个命令会：

- 从 `/etc/waimaidashi/backend.env` 只提取 `SPRING_DATA_REDIS_PASSWORD`
- 使用 `REDISCLI_AUTH` 认证，不打印密码
- 兼容 Redis 5：对 `CONFIG GET` 逐项查询，而不是一次传多个参数
- 输出 `INFO persistence`、RDB/AOF 配置、RDB 文件路径和配置文件相关行

锁排查用：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh redis-locks --lines 100
```

## 服务治理

重启前必须先采集：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host <edge|worker>
.codex/skills/waimaidashi-ops/scripts/ops.sh logs --host <edge|worker> --since "20 min ago" --lines 200
```

用户明确授权后才可重启：

```bash
ssh <host> 'sudo systemctl restart waimaidashi-backend && sudo systemctl status waimaidashi-backend --no-pager -l'
```

重启后验证：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host <edge|worker>
.codex/skills/waimaidashi-ops/scripts/ops.sh errors --host <edge|worker> --since "10 min ago"
.codex/skills/waimaidashi-ops/scripts/ops.sh sites
```
