# 事故排查 Playbook

## 通用入口

先跑：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh health
.codex/skills/waimaidashi-ops/scripts/ops.sh errors --host web --since "2 hours ago"
```

判断顺序：

1. `mt4` backend 是否 active，`/health/check` 是否正常
2. `mt4` Caddy 是否 active 且配置有效
3. `mt3` MySQL/Redis 是否监听内网端口，`mt3` 后端是否 active
4. `mt4` 后端是否 active，且能否通过内网访问 `mt3` MySQL/Redis
5. 是否集中出现美团、微信、Redis、MySQL、COS、短信相关错误
6. 定时任务和异步 worker 是否按当前发布策略开启或关闭

## 后端不可用

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host web
.codex/skills/waimaidashi-ops/scripts/ops.sh logs --host web --since "30 min ago" --lines 300
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

当前 `mt3`、`mt4` 都运行 backend，并连接同一个 MySQL 和 Redis。定时任务会在双节点同时触发，由共享 Redis 锁决定实际执行节点；数据库异步 worker 通过数据库原子领取任务。排查时必须同时确认两台 JAR 内配置、`/etc/waimaidashi/backend.env` 的最终开关和两台应用文件日志，不能只依据 `mt4` 的抢锁失败日志判断任务未执行。

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh tasks --host web --since "6 hours ago"
.codex/skills/waimaidashi-ops/scripts/ops.sh redis-locks --lines 100
```

重点任务名：

- `MtCommentAutoReplyTask`
- `MtAsyncTaskWorkerTask`
- `MtAppOrderPullTask`
- `MtAccessTokenRefreshTask`
- `YouzanAccessTokenRefreshTask`

如果授权回调正常但后续没有关闭订单推送、同步权益、回补评价或自动回复，优先检查 `MT_ASYNC_TASK_ENABLED` 和 `MT_ASYNC_TASK_WORKER_ENABLED` 是否开启，再查 `mt_async_task` 的状态分布。

## Redis 排查

Redis 在 `mt3` 上，需要认证。不要直接 `source /etc/waimaidashi/backend.env`；`backend.env` 是 systemd EnvironmentFile，JDBC URL 中的 `&` 会让 shell 解析失败。

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
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host web
.codex/skills/waimaidashi-ops/scripts/ops.sh logs --host web --since "20 min ago" --lines 200
```

用户明确授权后才可重启：

```bash
ssh mt4 'sudo systemctl restart waimaidashi-backend && sudo systemctl status waimaidashi-backend --no-pager -l'
```

重启后验证：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host web
.codex/skills/waimaidashi-ops/scripts/ops.sh errors --host web --since "10 min ago"
.codex/skills/waimaidashi-ops/scripts/ops.sh sites
```
