# 发版与变更后验证

本 skill 不负责主动发版；发版脚本在仓库 `deploy-local/deploy.sh`。运维侧重点是发版前后验证、失败排查和回滚建议。

## 发版后基础验证

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh status --host both
.codex/skills/waimaidashi-ops/scripts/ops.sh errors --host both --since "20 min ago"
.codex/skills/waimaidashi-ops/scripts/ops.sh caddy
.codex/skills/waimaidashi-ops/scripts/ops.sh sites
```

## 静态站点验证

H5：

```bash
curl -I https://aia.waimaidashi.com/
```

管理后台：

```bash
curl -I https://aiadmin.waimaidashi.com/
```

React Router 管理后台必须保留 `try_files {path} /index.html`。

## 回调入口验证

```bash
curl -I https://aiapi.waimaidashi.com/api/callbacks/meituan/auth
curl -I https://aiapi.waimaidashi.com/api/callbacks/meituan/order
curl -I https://aiapi.waimaidashi.com/api/callbacks/wechat/pay
```

这些接口通常限制真实请求方法，`405` 可能只是说明路由已到达服务，不必直接判定为异常。

## Caddy reload

用户明确授权后才 reload。必须先 validate：

```bash
ssh mt_ai_agent 'sudo caddy validate --config /etc/caddy/Caddyfile && sudo systemctl reload caddy'
```

## 后端重启

用户明确授权后才重启：

```bash
ssh <host> 'sudo systemctl restart waimaidashi-backend && sudo systemctl status waimaidashi-backend --no-pager -l'
```

重启后至少观察 10 分钟内错误：

```bash
.codex/skills/waimaidashi-ops/scripts/ops.sh errors --host <edge|worker> --since "10 min ago"
```

