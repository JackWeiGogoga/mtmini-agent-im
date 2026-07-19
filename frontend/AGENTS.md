# Frontend Guide

## 角色定位

- `frontend/` 是 PC 管理后台，不是商家 H5。
- 这里承载内部管理能力：登录、用户/角色/菜单、门店、算力、本地 IM 开关、自动回复记录与会话历史、通用门店知识库。
- 如果需求是商家在美团里看到的页面或交互，通常应该去 `h5/`，不是这里。

## 关键结构

- `src/router/`：路由与受保护页面入口。
- `src/pages/`：当前主要是登录、注册、欢迎页、用户管理、角色管理、菜单管理、错误页。
- `src/pages/im-auto-replies/`：自动回复消息明细、会话聚合和跨 Session 完整对话抽屉。
- `src/services/`：按业务拆分的接口封装。
- `src/utils/request.ts`：Axios 实例、统一错误处理、登录态跳转。
- `src/components/AppMessageContext/`：Ant Design `message`、`modal`、`notification` 的统一上下文出口。

## 修改约束

- 使用消息提示时，从 `@/components/AppMessageContext` 引入实例，不要直接调用 Ant Design 静态方法。
- 菜单、路由、权限点要和后端权限字符串保持一致；改动页面权限时，同时检查后端接口与菜单数据。
- 知识库权限统一使用 `knowledge:list/add/edit/delete`，不得恢复旧业务权限前缀。
- 变更接口结构时，要同步更新：
  - `src/services/*`
  - 页面表单/表格
  - 相关类型定义
- 保持当前管理后台的信息架构，不要把 H5 的移动端交互模式直接搬到后台页面。
- 优先复用现有请求封装、布局和权限包装组件，不要在页面里重复写一套鉴权或错误处理。

## 开发与验证

- 本目录使用 React 19、TypeScript、Ant Design 6、Vite。
- 当前没有自有前端测试文件；改动后至少执行 `pnpm lint` 和 `pnpm typecheck`。
- 如果改了登录态、路由或权限跳转，至少手工验证 401/403、登录后跳转和菜单可见性。
