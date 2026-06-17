# H5 Guide

## 角色定位

- `h5/` 是商家端 uni-app 小应用，运行在美团门店使用场景中。
- 核心目标是让商家配置和查看 AI 推广，而不是做后台管理。
- 这里的关键业务是：门店直登、推广开关、策略配置、推广日志、算力余额与充值。

## 关键页面与模块

- `src/App.vue`：应用启动与美团拉起参数处理。
- `src/pages/auth/callback/`：美团门店直登回调页。
- `src/pages/index/index.vue`：首页，承载 AI 推广开关、余额提醒、策略配置入口。
- `src/pages/log/index.vue`：推广日志查看。
- `src/pages/power/index.vue`：算力余额、充值、支付结果轮询。
- `src/utils/mt-auth.js`：统一请求、token 存储、门店信息存储和 H5 API 封装。

## 修改约束

- 只要变更首页策略字段或 JSON 结构，必须同时检查后端：
  - `MtH5PromotionSettingsUpdateParam`
  - `MtPromotionSettingService`
  - `PromotionDecisionEngine`
- 只要改动美团拉起参数、门店直登链路或新增 H5 侧美团接入点，必须同步检查并按需更新 `../docs/美团接口调用索引.md`。
- 保持移动端优先，不要引入只适合桌面的交互方式。
- 登录态、门店信息、token 读写继续收敛在 `src/utils/mt-auth.js`，不要在页面里散落复制存储逻辑。
- 门店进入链路依赖美团传入参数，如 `wmPoiId`、`bizToken`、`platform`、`appKey`；涉及登录或回调改动时，要同时核对后端 `MtH5AuthService`。
- 权益不足、未授权、网络失败等场景要显式给商家反馈，不能静默失败。
- 算力充值和 mock 支付能力只适合开发/测试场景，改动支付逻辑时要同步检查回调和查单链路。

## 开发与验证

- 本目录使用 uni-app + Vue 3。
- 当前没有自有 H5 自动化测试；改动后至少验证首页、日志页、算力页和登录回调页的关键路径。
- 如果修改接口字段，必须核对 `mt-auth.js` 的请求负载和页面读取逻辑是否同步。
