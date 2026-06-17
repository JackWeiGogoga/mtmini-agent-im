# 生产拓扑

## 服务器

| 角色 | 默认 SSH alias | 公网 IP | 内网 IP | 服务 |
| --- | --- | --- | --- | --- |
| edge | `mt_ai_agent` | `49.232.251.19` | `172.21.16.3` | Caddy、backend、MySQL、Redis、定时任务、异步任务消费 |
| worker | `root@49.233.251.114` | `49.233.251.114` | `172.21.16.12` | backend、定时任务、异步任务消费 |

本地 `~/.ssh/config` 当前已知存在 `mt_ai_agent` 指向第一台。第二台暂用 `root@49.233.251.114` 直连；如果后续配置了 alias，使用时通过 `WMD_WORKER_HOST=<alias>` 覆盖。

## 路径和服务

```text
Backend service: waimaidashi-backend
Backend jar: /opt/waimaidashi/backend/backend.jar
Backend env: /etc/waimaidashi/backend.env
H5 root: /var/www/mt-ai-promotion
Admin root: /var/www/mt-admin
Caddy config: /etc/caddy/Caddyfile
Caddy logs: /var/log/caddy/
MySQL: 172.21.16.3:31306
Redis: 172.21.16.3:6379
```

## 访问关系

```text
公网用户 / 美团 / 微信
        |
        v
edge: 49.232.251.19 / 172.21.16.3
  Caddy -> backend:8080
  MySQL:31306
  Redis:6379
        ^
        |
worker: 49.233.251.114 / 172.21.16.12
  backend -> edge MySQL/Redis
```

公网只应暴露第一台的 `80/443` 和受限 SSH。`8080`、`31306`、`6379` 不应公网开放。
