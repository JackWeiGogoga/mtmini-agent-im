## ADDED Requirements

### Requirement: 模型调用按供应商密钥和模型集群限流
系统 SHALL 在所有 OpenAI 兼容 Chat Completion、图片识别和 Embedding 外呼前，按供应商、API Key 安全指纹和模型组成的维度获取 Redis 集群共享许可；双节点对相同维度 MUST 共用同一额度，不同维度 MUST 独立计算。

#### Scenario: 双节点共享同一额度
- **WHEN** 两个后端节点使用相同供应商、API Key 和模型并发发起模型调用
- **THEN** 系统 SHALL 使用同一个 Redis 限流桶，并 MUST 将集群实际调用速率限制在配置额度内

#### Scenario: 不同模型额度隔离
- **WHEN** 相同供应商和 API Key 分别调用 `deepseek-v4-flash` 与 `qwen3.5-flash`
- **THEN** 系统 SHALL 使用两个独立限流桶

#### Scenario: 所有模型出口受控
- **WHEN** 业务调用文本 Chat Completion、评价图片识别或 Embedding
- **THEN** 系统 MUST 在发出 HTTP 请求前通过统一集群限流器

### Requirement: 模型请求平滑削峰
系统 SHALL 使用原子 Redis Lua GCRA 或等价的平滑限速算法控制模型请求启动间隔，生产 TokenHub `deepseek-v4-flash` 默认有效额度 SHALL 为 50 RPM 且 burst SHALL 为 1。

#### Scenario: 批量任务集中到达
- **WHEN** 大量评价任务在同一分钟进入待执行状态
- **THEN** 系统 SHALL 按配置间隔平滑启动模型请求，不得在分钟边界形成固定窗口双倍突发

#### Scenario: 运行时修改额度
- **WHEN** 开发者通过环境配置调整某供应商和模型的有效 RPM
- **THEN** 后续许可 SHALL 按新配置计算且不需要修改业务代码

### Requirement: 供应商 429 集群冷却
系统 SHALL 将供应商 HTTP 429 识别为容量限制，为相同限流桶写入全节点共享冷却；SHALL 优先使用 `Retry-After`，缺失时使用配置的默认冷却时间。

#### Scenario: 一个节点收到 429
- **WHEN** 任一后端节点收到某限流桶的供应商 429
- **THEN** 系统 SHALL 写入该桶冷却时间，其他节点在冷却结束前 MUST NOT 继续调用该桶

#### Scenario: 长时间冷却
- **WHEN** 供应商冷却超过当前异步调用允许的最大等待时间
- **THEN** 系统 SHALL 抛出可无损重排的容量等待信号，不得包装为普通模型失败

### Requirement: 限流等待不触发业务降级
系统 SHALL 将内部限流等待和供应商容量冷却视为可恢复等待；异步任务 MUST NOT 因此增加 `retry_count`、记录模型失败、扣减模型算力或触发官方/固定文案兜底。

#### Scenario: 内部额度暂不可用
- **WHEN** 异步任务在模型外呼前暂时无法获取许可
- **THEN** 系统 SHALL 等待许可或无损重排任务，并 MUST 保持业务生成状态不变

#### Scenario: 非容量模型错误
- **WHEN** 模型返回鉴权失败、非法参数、空响应或不合规内容
- **THEN** 系统 SHALL 继续执行对应业务既有的失败或兜底策略

### Requirement: 限流状态与日志不泄露密钥
系统 MUST 使用 API Key 的不可逆安全指纹构造 Redis key，且 Redis、数据库、指标和日志 MUST NOT 保存或输出原始 API Key、Authorization、提示词或消息正文。

#### Scenario: 构造限流桶
- **WHEN** 系统为模型调用构造 Redis key 和可观测标签
- **THEN** key 和标签 SHALL 只包含供应商、模型安全标识和 API Key 不可逆指纹

### Requirement: 限流器故障保护供应商
系统 SHALL 在异步任务无法访问 Redis 限流状态时停止模型外呼并无损重排；同步人工预览 SHALL 在短等待后返回安全繁忙错误。

#### Scenario: 异步任务遇到 Redis 故障
- **WHEN** 集群限流器无法读取或写入 Redis
- **THEN** 系统 MUST NOT 绕过限流调用模型，并 SHALL 无损重排异步任务

#### Scenario: 人工预览遇到 Redis 故障
- **WHEN** 管理后台人工预览无法使用集群限流器
- **THEN** 系统 SHALL 快速返回不包含内部异常和秘密的模型繁忙错误

### Requirement: 模型吞吐可观测
系统 SHALL 记录安全维度的许可、等待、无损重排、供应商 429、Redis 错误和累计等待时间指标，并 SHALL 支持统计每类异步任务的积压数量与最老待处理时间。

#### Scenario: 查询限流运行状态
- **WHEN** 运维检查模型吞吐和任务积压
- **THEN** 系统 SHALL 提供不含秘密的模型、供应商、事件计数、等待耗时和最老积压年龄
