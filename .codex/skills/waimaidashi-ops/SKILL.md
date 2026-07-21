---
name: waimaidashi-ops
description: 用于 Waimaidashi/外卖大师/美团 IM 消息回复项目的生产运维，包括 Tencent Cloud SSH server ops, log analysis, MySQL/Redis read-only query, systemd service governance, Caddy checks, scheduled tasks, async workers, deployment verification, rollback triage。
---

# Waimaidashi Ops

用于外卖大师美团 IM 消息回复项目的生产运维。默认目标是稳定定位问题、控制输出 token、避免泄露密钥、避免误操作生产。

## 先做什么

1. 优先使用本 skill 附带脚本，少手写长 SSH 命令：

   ```bash
   .codex/skills/waimaidashi-ops/scripts/ops.sh health
   .codex/skills/waimaidashi-ops/scripts/ops.sh errors --host web --since "2 hours ago"
   .codex/skills/waimaidashi-ops/scripts/ops.sh logs --host web --since "30 min ago" --lines 200
   .codex/skills/waimaidashi-ops/scripts/ops.sh caddy-access --since "2026/05/17 14:15"
   .codex/skills/waimaidashi-ops/scripts/ops.sh redis-persistence
   .codex/skills/waimaidashi-ops/scripts/ops.sh mysql-info
   .codex/skills/waimaidashi-ops/scripts/ops.sh mysql-query --sql "SELECT NOW();"
   .codex/skills/waimaidashi-ops/scripts/ops.sh mysql-slow --lines 200
   .codex/skills/waimaidashi-ops/scripts/ops.sh mysql-backup  # 只打印手动备份脚本用法
   ```

2. 根据问题按需读取 reference，不要一次加载全部：

   - 服务器角色、路径、SSH alias：读 `references/topology.md`
   - 线上事故、日志、任务、双节点排查：读 `references/incident-playbooks.md`
   - 需要查库时：读 `references/database-readonly-queries.md`
   - 发版后验证、Caddy、站点检查：读 `references/deployment-verification.md`

3. 默认 SSH alias 是 `mt6` Web/后端节点和 `mt5` 数据/后端节点；两台机器都运行 `waimaidashi-backend`。如本地 alias 不匹配，用环境变量覆盖：

   ```bash
   WMD_WEB_HOST=<web-ssh-alias> WMD_DB_HOST=<db-ssh-alias> \
     .codex/skills/waimaidashi-ops/scripts/ops.sh health
   ```

## 安全边界

默认只做只读诊断：

- `systemctl status` / `is-active`
- `journalctl` 查询
- `curl -I` / 健康探测
- `ss` / `df` / `free` / `uptime`
- MySQL `SELECT` / `SHOW`
- MySQL 首次访问必须先用 `ops.sh mysql-check` 验证连接；业务只读 SQL 用 `ops.sh mysql-query --sql "SELECT ... LIMIT ..."`；基础信息、空间、大表统计用 `ops.sh mysql-info`
- MySQL 命令统一在 `mt5` 使用远端 `/root/.my.cnf` 连接数据库，`/root/.my.cnf` 的 `[client]` 必须包含 `database=mtmini_ai_agent`；查库不要读取 `backend.env`，也不要临时拼数据库用户名/密码
- Caddy access log 慢请求检查用 `ops.sh caddy-access`。Caddy access log 每行是文本前缀 + JSON，必须先截取第一个 `{` 之后的 JSON 再解析；只输出 `time/host/method/uri/status/duration/size` 这类字段，不输出 headers、Cookie、Authorization
- Redis `PING` / `SCAN` / `TTL` / 针对明确 key 的 `GET`
- Redis 持久化只读检查用 `ops.sh redis-persistence`，不要临时拼认证和 `CONFIG GET`

以下动作必须得到用户明确授权后才能执行：

- `systemctl restart|stop|start`
- `systemctl reload caddy`
- `tools/mysql-backup-download.sh` 或其他会导出、下载生产数据库备份的动作
- 数据库 `INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE`
- Redis `DEL|FLUSH*`
- 回滚 jar 或静态资源
- 修改 `/etc/waimaidashi/backend.env`
- 修改 `/etc/caddy/Caddyfile`
- 修改 systemd service
- 重新部署生产

永远不要完整打印 `backend.env`。输出环境变量时必须脱敏密码、secret、签名 key、API key、私钥路径、token history key、access/refresh token、商户号、证书序列号等敏感字段；cron、host、port、database、普通 app id 可保留用于排障。

## 输出习惯

事故或排查类回复按这个结构组织：

```text
范围：
结论：
证据：
风险：
建议动作：
已执行命令：
```

要写清楚服务器角色和时间窗口。日志只摘关键行，不要把大段日志原样贴进最终回复。
