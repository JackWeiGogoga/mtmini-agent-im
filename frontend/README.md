# 外卖大师管理后台

React 19 + TypeScript + Ant Design 6 + Vite 管理后台，包含门店、权限、算力、知识库和美团 IM 自动回复管理。

## IM 自动回复记录

- 总控台门店列表可打开该门店的回复日志，一行同时展示顾客消息与 AI 回复。
- “自动回复记录”独立页面受 `system:im-auto-reply:list` 保护，包含“消息明细”和“会话列表”两个 Tab。
- 消息明细支持门店名称/ID、顾客、订单、状态和 RAG 筛选。
- 会话列表展示顾客、消息数、Session 数和最新消息，默认按最新活动倒序。
- 完整对话抽屉先定位最新消息，向上滚动使用 `(event_time,id)` 游标加载更早记录，并跨 Session 展示分隔线。

## 开发

```bash
pnpm install
pnpm dev
```

## 验证

```bash
pnpm lint
pnpm typecheck
pnpm build
```

请求统一经 `src/utils/request.ts` 处理；页面消息提示使用 `src/components/AppMessageContext`。菜单、路由和后端权限字符串必须同步。
