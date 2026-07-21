# 本地部署入口

IM 环境的服务器拓扑、初始化、数据库/Redis 配置、安全组、首次发布和验证步骤统一维护在：

```text
docs/IM服务器初始化与部署.md
```

核心入口：

```bash
# 基础环境
ansible-playbook -i ansible/inventory.ini ansible/site.yml

# 上传 data 节点 env/systemd；不处理 Caddy
./deploy-local/bootstrap-server.sh --host mt5 --role data

# 上传 web 节点 env/systemd/Caddy
./deploy-local/bootstrap-server.sh --host mt6 --role web

# 同一 JAR 发布到 mt5/mt6，静态资源只发布到 mt6
./deploy-local/deploy.sh --upload-env

# 只读验证
.codex/skills/waimaidashi-ops/scripts/ops.sh health
```

`deploy-local/backend.env`、证书和真实凭据不纳入 Git。不要在本文档中保存可用的
密码、token、签名密钥或 API key。

本地 H5/IM 自动回复数据初始化、固定测试 URL 和全局美团 API dry-run 说明见
[`本地测试清单.md`](./本地测试清单.md)。所有环境的美团 SDK 出站调用默认 dry-run，
真实联调必须显式设置 `MT_API_DRY_RUN=false`。
