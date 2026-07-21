# IM 服务器初始化与部署

## 1. 目标拓扑

| 实例名 | 角色 | SSH alias | 公网 IP | 内网 IP | 服务 |
| --- | --- | --- | --- | --- | --- |
| AI-Agent-IM-Private | private data + backend | `mt5` | `81.70.204.198` | `172.21.16.5` | MySQL、Redis、backend |
| AI-Agent-IM-Web | web + backend | `mt6` | `49.232.40.179` | `172.21.16.2` | Caddy、H5、管理后台、backend |

两台 backend 使用相同 JAR，连接 `mt5` 上的同一套 MySQL 和 Redis。Caddy 只在
`mt6` 运行，并把 `/api/*` 转发到 `mt6` 本机的 `127.0.0.1:8080`。

当前继续沿用以下兼容名称，避免在基础设施切换时同时引入大范围重命名：

- Linux 用户和目录：`waimaidashi`、`/opt/waimaidashi`
- systemd 服务：`waimaidashi-backend`
- 数据库：`mtmini_ai_agent`

## 2. 域名和安全组前置准备

在 DNS 中创建或确认以下 A 记录都指向 `49.232.40.179`：

```text
aic.waimaidashi.com
aicadmin.waimaidashi.com
aicapi.waimaidashi.com
```

腾讯云安全组建议：

### mt5

| 端口 | 来源 | 用途 |
| --- | --- | --- |
| TCP 22 | 固定办公公网 IP `/32`、VPN 或堡垒机 | SSH |
| TCP 31306 | `172.21.16.2/32` | mt6 访问 MySQL |
| TCP 6379 | `172.21.16.2/32` | mt6 访问 Redis |

不要向公网开放 `80/443/8080/31306/6379`。

### mt6

| 端口 | 来源 | 用途 |
| --- | --- | --- |
| TCP 22 | 固定办公公网 IP `/32`、VPN 或堡垒机 | SSH |
| TCP 80 | `0.0.0.0/0` | HTTP 和证书签发 |
| TCP 443 | `0.0.0.0/0` | HTTPS |
| UDP 443 | `0.0.0.0/0`，可选 | HTTP/3 |

不要向公网开放 `8080/31306/6379`。初始化阶段出站可暂时全放行，稳定后再按
美团、微信、腾讯云、软件源和 DNS/NTP 的实际依赖收紧。

## 3. 本地预检

在仓库根目录执行：

```bash
ssh mt5 'hostname; hostname -I'
ssh mt6 'hostname; hostname -I'

ansible-playbook -i ansible/inventory.ini ansible/site.yml --syntax-check
bash -n deploy-local/bootstrap-server.sh
bash -n deploy-local/deploy.sh
bash -n .codex/skills/waimaidashi-ops/scripts/ops.sh
```

`ansible/inventory.ini` 不纳入 Git，内容应为：

```ini
[app]
mt6

[data]
mt5

[all:vars]
ansible_user=root
ansible_ssh_private_key_file=~/.ssh/mt_ai_agent_im
# site.yml 会先通过 raw 模块安装该解释器
ansible_python_interpreter=/usr/bin/python3.11
```

## 4. Ansible 基础环境初始化

本地准备离线包：

```bash
ansible/scripts/prepare-offline-packages.sh
```

初始化前使用不依赖远端 Python 的 `raw` 模块验证 SSH，然后运行初始化：

```bash
ansible -i ansible/inventory.ini all -m raw \
  -a 'printf "connected host=%s\n" "$(hostname)"'

ansible-playbook -i ansible/inventory.ini ansible/site.yml
```

`site.yml` 第一段会通过 `raw` 安装 `/usr/bin/python3.11`。初始化完成后再验证
普通 Ansible 模块：

```bash
ansible -i ansible/inventory.ini all -m ping
```

在首次初始化前直接运行 `-m ping`，出现 `/usr/bin/python3.11: No such file or
directory` 属于预期现象，不代表 SSH 配置失败。

Ansible 会在两台机器安装 Java 和通用工具、创建应用用户和目录；只在 `mt6`
安装 Caddy，只在 `mt5` 安装 MySQL 和 Redis。MySQL、Redis 的监听地址、账号和
密码需要按后续步骤单独配置。

## 5. 配置 mt5 MySQL

先在 `mt5` 写入 `/etc/my.cnf.d/waimaidashi.cnf`：

```ini
[mysqld]
port=31306
bind-address=127.0.0.1,172.21.16.5
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-time-zone=+08:00
mysqlx=0
max_connections=300

[client]
port=31306
default-character-set=utf8mb4
```

首次启动前验证配置，然后启动：

```bash
ssh mt5 'mysqld --validate-config && systemctl enable --now mysqld && systemctl status mysqld --no-pager'
```

从 `mt5` 的 `/var/log/mysqld.log` 获取临时 root 密码，在交互式 MySQL 会话中修改
root 密码。随后创建空库和两个限定来源的应用账号；`<APP_DB_PASSWORD>` 必须换成
新 IM 环境的独立强密码：

```sql
CREATE DATABASE `mtmini_ai_agent`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER 'mtmini_ai_agent'@'172.21.16.5' IDENTIFIED BY '<APP_DB_PASSWORD>';
CREATE USER 'mtmini_ai_agent'@'172.21.16.2' IDENTIFIED BY '<APP_DB_PASSWORD>';

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE
ON `mtmini_ai_agent`.* TO 'mtmini_ai_agent'@'172.21.16.5';

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE
ON `mtmini_ai_agent`.* TO 'mtmini_ai_agent'@'172.21.16.2';

FLUSH PRIVILEGES;
```

为本仓库的只读运维和备份脚本配置 `mt5:/root/.my.cnf`，权限必须是 `600`。首次
建库和导入阶段使用本机 `root` 账号；该文件不要提交到 Git：

```ini
[client]
host=127.0.0.1
port=31306
user=root
password=<MYSQL_ROOT_PASSWORD>
database=mtmini_ai_agent
```

空库导入当前完整结构和初始化权限数据：

```bash
ssh mt5 'sudo mysql --defaults-extra-file=/root/.my.cnf' \
  < backend/src/main/resources/db/full_schema.sql

ssh mt5 'sudo mysql --defaults-extra-file=/root/.my.cnf' \
  < backend/src/main/resources/db/auth_init_data.sql
```

导入后应立即核对并修改初始化管理账号密码，不要沿用复制项目中的账号凭据。

## 6. 配置 mt5 Redis

备份并编辑 `mt5:/etc/redis.conf`：

```conf
bind 127.0.0.1 172.21.16.5
protected-mode yes
port 6379
requirepass <NEW_IM_REDIS_PASSWORD>

appendonly yes
appendfsync everysec
save 900 1
save 300 10
save 60 10000
```

启动并检查监听地址：

```bash
ssh mt5 'systemctl enable --now redis && systemctl status redis --no-pager && ss -lntp | grep 6379'
```

预期只监听 `127.0.0.1:6379` 和 `172.21.16.5:6379`，不能监听公网地址。

Ansible 只安装 firewalld，不自动启动，避免初始化时意外切断 SSH。首次启用前保留
当前 SSH 会话，确认默认 `public` zone 已包含 `ssh`，然后启动并用第二个终端确认
仍可登录：

```bash
ssh mt5 'firewall-offline-cmd --get-default-zone && firewall-offline-cmd --zone=public --list-services'
ssh mt5 'systemctl enable --now firewalld'
ssh mt5 'firewall-cmd --get-active-zones && firewall-cmd --zone=public --list-all'
```

确认 SSH 正常后，在 `mt5` 只允许本机内网 IP 和 `mt6` 内网 IP 访问数据库、缓存：

```bash
ssh mt5 "firewall-cmd --permanent --zone=public --add-rich-rule='rule family=\"ipv4\" source address=\"172.21.16.5/32\" port protocol=\"tcp\" port=\"31306\" accept'"
ssh mt5 "firewall-cmd --permanent --zone=public --add-rich-rule='rule family=\"ipv4\" source address=\"172.21.16.2/32\" port protocol=\"tcp\" port=\"31306\" accept'"
ssh mt5 "firewall-cmd --permanent --zone=public --add-rich-rule='rule family=\"ipv4\" source address=\"172.21.16.5/32\" port protocol=\"tcp\" port=\"6379\" accept'"
ssh mt5 "firewall-cmd --permanent --zone=public --add-rich-rule='rule family=\"ipv4\" source address=\"172.21.16.2/32\" port protocol=\"tcp\" port=\"6379\" accept'"
ssh mt5 'firewall-cmd --reload && firewall-cmd --zone=public --list-rich-rules'
```

## 7. 准备 backend.env

`deploy-local/backend.env` 被 Git 忽略。至少确认这些非敏感值：

```env
SPRING_PROFILES_ACTIVE=online
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:mysql://172.21.16.5:31306/mtmini_ai_agent?useUnicode=true&characterEncoding=UTF8&autoReconnect=true&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull
SPRING_DATA_REDIS_HOST=172.21.16.5
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_DATABASE=0
MT_CALLBACK_SUCCESS_URL=https://aic.waimaidashi.com/#/
POWER_WECHAT_PAY_NOTIFY_URL=https://aicapi.waimaidashi.com/api/callbacks/wechat/pay
POWER_WECHAT_PAY_WAP_URL=https://aic.waimaidashi.com/#/pages/power/index
```

同时把数据库密码、Redis 密码、美团/微信/LLM/COS 等凭据替换为新 IM 环境应使用
的真实值。不要直接照搬旧评论回复服务器的数据库、Redis 密码；不要把该文件加入 Git。

## 8. 上传服务配置

先给 data 节点上传 env 和 systemd service；`--role data` 会跳过 Caddy：

```bash
./deploy-local/bootstrap-server.sh --host mt5 --role data
```

再初始化 web 节点；这一步会上传并启用 Caddy 配置：

```bash
./deploy-local/bootstrap-server.sh --host mt6 --role web
```

此时 backend JAR 尚未发布，脚本默认只 enable、不启动 backend，这是预期行为。

## 9. 首次服务部署

如需部署微信支付证书，把路径通过环境变量传入；发布脚本会把同一套证书上传到
两个 backend 节点：

```bash
LOCAL_WECHAT_KEY_FILE=/absolute/path/to/apiclient_key.pem \
LOCAL_WECHAT_PUB_KEY_FILE=/absolute/path/to/pub_key.pem \
./deploy-local/deploy.sh --upload-env --upload-certs
```

如果本次不需要证书：

```bash
./deploy-local/deploy.sh --upload-env
```

默认行为：

- 后端只构建一次，同一个 JAR 依次发布到 `mt5`、`mt6`，校验 SHA-256 后重启并做健康检查。
- H5 和管理后台只构建并发布到 `mt6`。
- Caddyfile 已由 bootstrap 上传到 `mt6`，不会上传到 `mt5`。

## 10. 部署后验证

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh health
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host both
.codex/skills/waimaidashi-ops/scripts/ops.sh mysql-check
.codex/skills/waimaidashi-ops/scripts/ops.sh redis-persistence
.codex/skills/waimaidashi-ops/scripts/ops.sh caddy
.codex/skills/waimaidashi-ops/scripts/ops.sh sites
.codex/skills/waimaidashi-ops/scripts/ops.sh errors --host both --since "20 min ago"
```

确认两台 backend 的 JAR 一致：

```bash
ssh mt5 'sha256sum /opt/waimaidashi/backend/backend.jar'
ssh mt6 'sha256sum /opt/waimaidashi/backend/backend.jar'
```

三个站点的 HTTPS 证书签发依赖 DNS 已经指向 `mt6`，且安全组允许公网访问
`80/443`。

## 11. 回滚注意事项

双 backend 回滚必须分别处理两台机器，不能只回滚 `mt6`：

```bash
./deploy-local/rollback.sh --backend-only --host mt5
./deploy-local/rollback.sh --backend-only --host mt6
```

H5 和管理后台静态资源只在 `mt6`：

```bash
./deploy-local/rollback.sh --skip-backend --host mt6
```
