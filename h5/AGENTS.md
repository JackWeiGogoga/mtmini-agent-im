# H5 Guide

## 角色定位

- `h5/` 是美团门店场景中的商家端 uni-app 小应用。
- 首页展示本地 IM 自动回复总开关、授权状态、权益与算力，并提供知识库和客服入口。

## 关键模块

- `src/App.vue` 与授权回调页：美团拉起和门店直登。
- `src/pages/index/index.vue`：IM 服务状态与唯一总开关。
- `src/pages/knowledge/index.vue`：通用 QA、知识文档与切片管理。
- `src/pages/power/`、`src/pages/support/`：算力和客服。
- `src/utils/mt-auth.js`：登录态、门店信息和 H5 API 封装。

## 修改约束

- 美团拉起参数或接入点变化时同步更新 `../docs/美团接口调用索引.md`。
- 登录态、门店信息和 token 继续收敛在 `mt-auth.js`。
- 本地 IM 开关更新只调用本系统接口，不调用任何美团机器人状态查询或设置接口。
- 权益不足、未授权和网络失败必须显式反馈；保持移动端优先。

## 开发与验证

- 使用 uni-app + Vue 3；修改后至少构建 H5，并核对首页、授权、算力、知识库和客服路径。
