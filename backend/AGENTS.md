# Backend Guide

## 角色定位

- `backend/` 是业务真源，负责门店授权、用户登录、订单同步、门店权益、推广配置、自动调价、算力充值和支付回调。
- 如果业务语义与前端/H5 展示不一致，以后端接口、实体、Mapper SQL 和任务链路为准。

## 关键模块

- `controller/`
  - 系统管理接口：用户、角色、菜单。
  - 美团/H5 接口：`MtAuthController`、`MtH5ShopController`、`MtH5PromotionSettingController`、`MtH5PromotionLogController`、`MtH5PowerController`。
  - 回调接口：美团授权、订单回调、微信支付回调。
- `service/`
  - `MtH5AuthService`：美团门店 H5 直登。
  - `MtPromotionSettingService`：推广配置读取、默认值填充、策略校验。
  - `MtPromotionAutoBidService`：自动调价主编排。
  - `MtShopRightService`：门店权益同步与校验。
  - `MtPowerRechargeService`：算力充值、支付查询、支付通知处理。
  - `MtOrderService` / `MtAppOrderService`：订单与订阅单相关逻辑。
- `service/promotion/`
  - `PromotionDecisionEngine`：调价决策。
  - `PromotionTimeWindowEvaluator`：执行时段判断。
  - `MtPromotionMetricsProvider`：拉取广告花费和订单实收指标。
  - `PromotionBidGateway`：真正执行出价更新。
- `task/`
  - `MtPromotionAutoBidTask`：按 cron 执行自动调价。
  - `MtAccessTokenRefreshTask`、`MtAppOrderPullTask`：授权与订阅相关任务。
- `mapper/` + `src/main/resources/mapper/`
  - Mapper 接口和 XML 必须同步维护。
- `src/main/resources/db/`
  - 涉及表结构或初始化数据变更时，同时更新对应 SQL。

## 核心业务链路

- 门店从美团进入 H5，小程序参数通过 `/api/mt/auth/login` 完成直登。
- 商家在 `/api/h5/promotion/settings` 维护 AI 推广策略。
- 定时任务按 10 分钟槽位执行自动调价：候选门店筛选 -> 时段判断 -> 指标查询 -> 决策 -> 更新出价 -> 记录日志。
- 商家算力充值由 H5 发起，后端落单、调支付、查单、处理微信回调，并给门店账户入账。
- 门店权益既可能来自美团买量权益接口，也可能来自订阅订单补偿，接口鉴权依赖 `@RequiresShopRight`。

## 修改约束

- H5 接口继续使用 `ResultVO` 包装，业务失败优先抛 `BusinessException`，不要把上游异常原样暴露给前端。
- 只要新增或修改美团外部调用，必须同步更新 `../docs/美团接口调用索引.md`，不要遗漏：
  - `MeituanClient` / SDK request
  - `OkHttp` 等手动 HTTP 请求
  - 美团回调入口
  - 会真实打到美团的联调测试
- 修改推广策略时，不能只改校验或 VO。至少同时检查：
  - `MtPromotionSettingService`
  - `MtPromotionAutoBidService`
  - `service/promotion/*`
  - 对应测试
- 新增或修改大模型相关解析时，不要在各业务服务里各自手写 markdown fenced JSON 清洗逻辑。优先复用 `JsonUtil` 中统一的 LLM JSON 提取/解析方法，保证：
  - 纯 JSON
  - ```` ```json ... ``` ```` 包裹
  - 前后带少量说明文本
  这几类输出都能用一致方式处理并测试覆盖。
- 修改订单回调、支付回调、定时任务时，优先保证幂等，避免重复入账、重复调价、重复授权。
- 任何涉及 `shopId`、`epoiId`、`poiId`、access token 的逻辑都要明确作用域，不能串店。
- 只改 Mapper 接口不改 XML，或只改 XML 不改实体/查询字段，都属于不完整修改。

## 开发与验证

- 本目录使用 Java 17 和 Maven。
- 当前环境运行 Maven 时，默认使用 `/Users/jackwei/data/apache-maven-3.9.14/bin/mvn`，不要假设 `mvn` 已在 `PATH` 中。
- 在仓库根目录执行后端命令时，优先使用 `-f backend/pom.xml`；例如：`/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtH5HomeDashboardServiceTest test`。
- 变更后优先运行与改动最接近的测试；调价、时段、指标相关逻辑至少检查 `src/test/java/com/gogoga/backend/service/promotion/`。
- 如果修改了接口字段或 SQL，至少自检对应 Controller、Service、Mapper、VO/Param 是否一致。
