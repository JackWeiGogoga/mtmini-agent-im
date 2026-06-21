# 生产拓扑

## 服务器

| 角色 | 默认 SSH alias | 公网 IP | 内网 IP | 服务 |
| --- | --- | --- | --- | --- |
| db | `mt3` | `58.87.93.163` | `172.21.16.9` | MySQL、Redis |
| web | `mt4` | `82.157.21.216` | `172.21.16.13` | Caddy、backend、H5、管理后台 |

本地 `~/.ssh/config` 已配置 `mt3` 和 `mt4`。`mt3` 是数据库/缓存节点，`mt4` 是公网入口和应用节点。

## 路径和服务

```text
Backend service: waimaidashi-backend
Backend jar: /opt/waimaidashi/backend/backend.jar
Backend env: /etc/waimaidashi/backend.env
H5 root: /var/www/mt-h5
Admin root: /var/www/mt-admin
Caddy config: /etc/caddy/Caddyfile
Caddy logs:
  /var/log/caddy/aib-access.log
  /var/log/caddy/aibadmin-access.log
  /var/log/caddy/aibapi-access.log
MySQL: 172.21.16.9:31306
Redis: 172.21.16.9:6379
Database: mtmini_ai_agent
```

## 访问关系

```text
公网用户 / 美团 / 微信
        |
        v
web: mt4 / 82.157.21.216 / 172.21.16.13
  Caddy :80/:443
    -> backend 127.0.0.1:8080
  static:
    aib.waimaidashi.com -> /var/www/mt-h5
    aibadmin.waimaidashi.com -> /var/www/mt-admin
    aibapi.waimaidashi.com -> backend
        |
        | 内网访问
        v
db: mt3 / 58.87.93.163 / 172.21.16.9
  MySQL :31306
  Redis :6379
```

公网只应暴露 `mt4` 的 `80/443` 和两台机器受限 SSH。`8080`、`31306`、`6379` 不应公网开放。

## 任务开关

当前线上策略是后端先跑 Web/API，定时任务和数据库异步 worker 默认关闭：

```text
scheduled.enabled = ${SCHEDULED_ENABLED:false}
mt.async-task.enabled = ${MT_ASYNC_TASK_ENABLED:false}
mt.async-task.worker-enabled = ${MT_ASYNC_TASK_WORKER_ENABLED:false}
```

授权回调仍会保存授权状态并写入数据库异步任务；只有开启 async worker 后，这些任务才会被消费执行。
