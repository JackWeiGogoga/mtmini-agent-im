# 生产拓扑

## 服务器

| 角色 | 默认 SSH alias | 公网 IP | 内网 IP | 服务 |
| --- | --- | --- | --- | --- |
| db + backend | `mt3` | `58.87.93.163` | `172.21.16.9` | MySQL、Redis、backend（定时任务/异步 worker 竞争节点） |
| web + backend | `mt4` | `82.157.21.216` | `172.21.16.13` | Caddy、backend、H5、管理后台 |

本地 `~/.ssh/config` 已配置 `mt3` 和 `mt4`。`mt3` 是数据库/缓存节点，同时运行一个后端实例；`mt4` 是公网入口，同时运行另一个后端实例。公网 API 流量由 `mt4` 的 Caddy 转发到本机后端；两台后端共同竞争定时任务和数据库异步任务。

## 路径和服务

```text
Backend service (mt3 + mt4): waimaidashi-backend
Backend jar (mt3 + mt4): /opt/waimaidashi/backend/backend.jar
Backend env (mt3 + mt4): /etc/waimaidashi/backend.env
H5 root (mt4): /var/www/mt-h5
Admin root (mt4): /var/www/mt-admin
Caddy config (mt4): /etc/caddy/Caddyfile
Caddy logs (mt4):
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
    -> backend@mt4 127.0.0.1:8080
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
  backend@mt3 :8080
```

公网只应暴露 `mt4` 的 `80/443` 和两台机器受限 SSH。两台机器的 `8080` 以及 `mt3` 的 `31306`、`6379` 不应公网开放。

## 双后端协作

- `mt3`、`mt4` 都运行同名 systemd 服务 `waimaidashi-backend`，并连接同一个 MySQL 和 Redis。
- 普通定时任务会在两台机器同时到达触发点；带 `RedisDistributedLockService` 的任务只由抢到共享 Redis 锁的节点执行业务，另一节点记录“分布式锁未获取，跳过执行”。
- 数据库异步 worker 也会在两台机器运行，通过数据库原子领取避免同一任务被重复消费。
- 排查定时任务时必须同时读取两台机器的应用文件日志；只看 `mt4` 可能只看到抢锁失败，而真正执行日志位于 `mt3`。
- `deploy-local/deploy.sh` 默认只发布 `mt4`。后端变更需要分别向 `mt3`、`mt4` 执行后端发布，保证两台 JAR 版本一致；H5、管理后台和 Caddy 仍只属于 `mt4`。

## 任务开关

当前两台后端使用相同的包内默认配置；若 `/etc/waimaidashi/backend.env` 未覆盖，则定时任务和数据库异步 worker 都开启：

```text
scheduled.enabled = ${SCHEDULED_ENABLED:true}
mt.async-task.enabled = ${MT_ASYNC_TASK_ENABLED:true}
mt.async-task.worker-enabled = ${MT_ASYNC_TASK_WORKER_ENABLED:true}
```

线上排查时仍需分别核对两台 JAR 的 `application.yml` 和 `/etc/waimaidashi/backend.env`，防止双节点版本或开关漂移。授权回调会保存授权状态并写入数据库异步任务；只有 async worker 实际开启时，这些任务才会被消费执行。
