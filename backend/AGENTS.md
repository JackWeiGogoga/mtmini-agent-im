# Backend Guide

## 角色定位

- `backend/` 是业务真源，负责门店授权、IM 初始化、消息可靠入库、用户登录、权益、算力充值、支付回调和通用知识库。
- `/imMsg` 当前只做验签、解析、解密、落库与响应，不得触发模型、算力扣减、回复任务或消息发送。

## 关键模块

- `MtAuthAsyncTaskService` / `MtAsyncTaskWorkerTask`：授权后普通任务与通用知识文档任务。
- `MtImConfigurationClient`：仅封装开启门店 IM、关闭美团全部智能回复机器人两个写接口。
- `MtImMessageClient`：封装 `biz_type=1` 两方会话商家消息的单次发送；不生成消息 ID、不查询状态、不重试。
- `SensitiveWordService`：通用敏感词归一化检测、类别/严重级别结果和知识内容拦截；IM 文本发送在加密前复用。
- `MtImMessageIngestionService`：IM 回调验签、解析、门店关联、解密和双重幂等入库。
- `ShopOperationSettingsService`：本地 IM 自动回复总开关与历史审计。
- `KnowledgeService` / `KnowledgeDocumentService`：按门店隔离的通用 QA、文档、切片和向量能力。
- `MtShopRightService`、算力与支付服务继续作为可复用基础能力。

## 修改约束

- H5 接口使用 `ResultVO`；业务失败优先抛 `BusinessException`，不直接暴露上游异常。
- 美团调用变更必须同步更新 `../docs/美团接口调用索引.md`。
- 禁止新增机器人状态查询、写后查询验证或客户端内部重试。
- 两方会话接收支持 `msg_type=1/2/3/17`，发送只放行 `1/2/3`；官方没有独立视频类型，不得自行扩展类型码。
- `msg_type=1` 发送必须先执行敏感词检测；命中时不得加密或调用美团。图片和其他消息正文按不透明协议载荷处理，不把 URL、JSON 或 Base64 当自然语言检测。
- Mapper 接口与 XML、实体与 `src/main/resources/db/full_schema.sql` 必须同步。
- 涉及 `shopId`、`epoiId`、`poiId` 和 token 的逻辑必须明确门店作用域。
- 修改回调、支付或异步任务时优先保证幂等；日志只记录安全标识和错误码。

## 开发与验证

- Java 17，Maven 路径 `/Users/jackwei/data/apache-maven-3.9.14/bin/mvn`。
- 根目录运行时使用 `-f backend/pom.xml`；至少执行相关单测、`test-compile` 和完整测试。
