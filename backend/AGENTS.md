# Backend Guide

## 角色定位

- `backend/` 是业务真源，负责门店授权、用户登录、订单同步、门店权益、评价自动回复、算力充值和支付回调。
- 如果业务语义与前端/H5 展示不一致，以后端接口、实体、Mapper SQL 和任务链路为准。

## 关键模块

- `controller/`
  - 系统管理接口：用户、角色、菜单。
  - 美团/H5 接口：`MtAuthController`、`MtH5ShopController`、`MtH5PowerController`。
  - 回调接口：美团授权、订单回调、微信支付回调、有赞回调。
- `service/`
  - `MtH5AuthService`：美团门店 H5 直登。
  - `MtCommentAutoReplyService`：查询未回复评价、保存评价、调用美团回复接口。
  - `MtShopRightService`：门店权益同步与校验。
  - `MtPowerRechargeService`：算力充值、支付查询、支付通知处理。
  - `MtOrderService` / `MtAppOrderService`：订单与订阅单相关逻辑。
- `task/`
  - `MtCommentAutoReplyTask`：每天早中晚查询近 7 天未回复评价并自动回复。
  - `MtAccessTokenRefreshTask`、`MtAppOrderPullTask`：授权与订阅相关任务。
  - `MtAsyncTaskWorkerTask`：数据库异步任务 worker。
- `mapper/` + `src/main/resources/mapper/`
  - Mapper 接口和 XML 必须同步维护。
- `src/main/resources/db/`
  - 涉及表结构或初始化数据变更时，同时更新对应 SQL。

## 核心业务链路

- 门店从美团进入 H5，小程序参数通过 `/api/mt/auth/login` 完成直登。
- 定时任务通过美团接口查询近 7 天未回复评价，按 `comment_id` 幂等保存到 `mt_comment`。
- 对本地未回复且未成功自动回复的评价，调用美团“根据评价id添加商家回复”接口，当前固定回复“很好”。
- 商家算力充值由 H5 发起，后端落单、调支付、查单、处理微信回调，并给门店账户入账。
- 门店权益既可能来自美团买量权益接口，也可能来自订阅订单补偿，接口鉴权依赖 `@RequiresShopRight`。

## 修改约束

- H5 接口继续使用 `ResultVO` 包装，业务失败优先抛 `BusinessException`，不要把上游异常原样暴露给前端。
- 只要新增或修改美团外部调用，必须同步更新 `../docs/美团接口调用索引.md`。
- 修改评价结构或自动回复语义时，至少同时检查：
  - `MtCommentAutoReplyService`
  - `MtComment` / `MtCommentMapper`
  - `MtCommentAutoReplyTask`
  - 对应 DB SQL
- 新增或修改大模型相关解析时，不要在各业务服务里各自手写 markdown fenced JSON 清洗逻辑。优先复用 `JsonUtil`。
- 修改订单回调、支付回调、定时任务时，优先保证幂等，避免重复入账、重复回复、重复授权。
- 任何涉及 `shopId`、`epoiId`、`poiId`、access token 的逻辑都要明确作用域，不能串店。

## 开发与验证

- 本目录使用 Java 17 和 Maven。
- 当前环境运行 Maven 时，默认使用 `/Users/jackwei/data/apache-maven-3.9.14/bin/mvn`。
- 在仓库根目录执行后端命令时，优先使用 `-f backend/pom.xml`。
- 变更后优先运行与改动最接近的测试；评价自动回复至少运行后端编译并补充服务单测。
