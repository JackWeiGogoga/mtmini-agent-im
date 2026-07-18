## Context

两台生产后端同时运行 `MtAsyncTaskWorkerTask`，连接同一个 MySQL 和 Redis。当前 worker 每秒按固定顺序为每种任务查询最多 20 个待执行 ID，再统一提交到核心 4、最大 8、队列 200 的 JVM 线程池；任务直到在线程中开始运行才执行数据库原子领取。因此定时评价批量入队时会形成较大的内存等待队列和双节点并发。

一条评价可能调用多个模型出口：图片识别使用 TokenHub `qwen3.5-flash`，知识检索使用 DashScope `text-embedding-v4`，回复、评论日志分析、差评申诉和 IM 消息生成使用 TokenHub `deepseek-v4-flash`。这些调用既有统一 `OpenAiCompatibleChatService`，也有图片和 Embedding 的直接 `RestClient` 出口。生产已确认 `deepseek-v4-flash` 在 60 RPM 配额下返回集中 429。

数据库异步任务已经提供幂等入队、原子领取、退避重试、无损重排和超时回收，可以继续作为可靠积压队列。Redis 已是双节点共享基础设施，适合保存短生命周期的限流状态，但不得存储 API Key 明文。

## Goals / Non-Goals

**Goals:**

- 所有 OpenAI 兼容模型调用在外呼前按“供应商 + API Key 指纹 + 模型”获取集群共享额度。
- TokenHub `deepseek-v4-flash` 默认以 50 RPM、burst 1 平滑调用，为供应商 60 RPM 保留安全余量。
- 待处理任务主要积压在 MySQL，不因门店增长而等比例增加 JVM 内存队列、线程数或瞬时外呼。
- 自动回复优先处理，同时保证申诉、IM、日志分析、知识解析以及不同门店最终都能获得处理机会。
- 内部限流和供应商容量冷却属于等待状态，不触发模型失败、固定文案或官方文案降级，也不消耗业务重试次数。
- 双节点共享供应商 429 冷却并提供可排障但不泄密的指标。

**Non-Goals:**

- 不在本变更申请或自动调整供应商配额。
- 不把 MySQL 异步任务整体替换为消息中间件。
- 不修改美团评价回复、申诉、建群或发送消息接口及其业务幂等规则。
- 不合并回复、申诉或分析提示词以减少模型调用次数。
- 不提供运营后台动态编辑限流规则；第一版使用开发者配置和环境变量。

## Decisions

### 1. 在模型出口统一执行集群限流

新增 `LlmClusterRateLimiter` 和统一的请求描述 `LlmRateLimitDescriptor`。描述包含显式供应商、解析后的 API Key、模型和调用类别，限流器在计算安全指纹后立即丢弃对明文 Key 的引用。Chat Completion 在 `OpenAiCompatibleChatService` 接入；图片识别和 Embedding 的直接 HTTP 出口也必须接入，避免绕过。

Redis key 使用：

```text
llm:rate:v1:{provider}:{sha256(apiKey)前32位}:{sha256(model)前16位}
llm:cooldown:v1:{provider}:{sha256(apiKey)前32位}:{sha256(model)前16位}
```

日志只记录供应商、模型、调用类别、等待时间和不可逆的短桶标识，不记录 API Key、Authorization、提示词或正文。

选择出口限流而不是仅在任务领取处限流，是因为一个任务可能调用不同供应商和多个模型，任务类型无法准确代表实际配额消耗。

### 2. 使用 Redis Lua GCRA 平滑请求，不使用固定分钟窗口

Lua 脚本原子维护 theoretical arrival time。有效 RPM 决定相邻许可间隔，burst 决定可提前消费的间隔数；状态 TTL 至少覆盖两个额度周期。生产默认规则：

```text
TOKENHUB / deepseek-v4-flash: effectiveRpm=50, burst=1
```

其他生产模型必须配置各自额度；没有精确规则时使用开发者配置的保守默认值。固定窗口计数会在分钟边界允许两倍突发，因此不采用；单纯分布式锁虽然能串行，但无法表达不同模型额度和稳定吞吐，也不采用。

### 3. 小并发线程内短等待，长冷却无损重排

获取许可时先检查共享 cooldown。正常 GCRA 等待由当前 LLM worker 线程短暂等待后重试，避免申诉的两段生成或敏感词改写在中间被拆断并重复前一段模型调用。LLM lane 每节点只有 2 个线程，因此等待线程数量有上限。

若等待超过配置的单次最大等待，抛出 `LlmCapacityDeferredException`，由异步 worker 使用现有 `rescheduleWithoutRetry` 将任务恢复为 `PENDING`，`available_at` 设置为建议时间加随机抖动，`retry_count` 保持不变。该异常必须穿透各业务服务的 catch-all，不得转换成 `LLM_ERROR`、`FALLBACK_STATIC`、图片识别失败或申诉失败。

人工预览等同步 HTTP 场景使用较短最大等待，超时后返回安全的“模型繁忙”错误，不长期占用 Web 请求线程。

### 4. 供应商 429 建立共享冷却并有限重试当前调用

模型出口识别 SDK `RateLimitException` 或 HTTP 429，优先使用响应 `Retry-After`，缺失时使用默认 60 秒，在相同桶写入 `blockedUntil`。当前调用在总等待上限内等待冷却、重新获取许可并重试；超过上限则抛出 `LlmCapacityDeferredException`。其他节点在下一次外呼前读取同一 cooldown，避免继续冲击供应商。

限流器 Redis 异常对异步任务 fail-closed：停止模型外呼并无损重排。对人工预览快速失败。这样符合任务可延迟的业务前提，并防止 Redis 故障退化为供应商请求洪峰。

### 5. 将普通任务和 LLM 任务拆成两个有界 lane

授权、权益、关闭订单推送等普通任务继续使用现有执行器。以下任务进入独立 `mtLlmTaskExecutor`：

- `COMMENT_AUTO_REPLY`
- `COMMENT_AUTO_COMPLAINT`
- `COMMENT_AUTO_IM_FOLLOW_UP`
- `COMMENT_LOG_ANALYSIS`
- `KNOWLEDGE_DOCUMENT_PARSE`

LLM lane 默认核心/最大线程均为 2，内存排队容量为 0。dispatcher 只在本地执行槽空闲时选取和领取任务；没有槽位时任务保留在 MySQL `PENDING`。若领取后提交失败，必须立即无损重排，不能等待超时回收。

### 6. 使用加权轮询和门店公平选择

LLM 任务类型默认权重为自动回复 5、IM 2、申诉 2、日志分析 1、知识解析 1。dispatcher 在每次有空闲槽时按加权轮询游标选择任务类型，而不是固定顺序为每类批量提交。

同一任务类型查询使用 `scope_key` 门店维度公平排序：先选择不同门店最早的一条，再选择各门店第二条。MySQL 8 可用 `ROW_NUMBER() OVER (PARTITION BY scope_key ORDER BY available_at, id)` 实现；空 `scope_key` 以任务 ID 作为独立分组。最终仍由 `claimPendingById` 原子更新保证双节点不重复执行。

### 7. 评论日志分析拆为独立低优先级任务

自动回复成功落库后不再同步调用 `generateAfterAutoReply`，而是幂等入队 `COMMENT_LOG_ANALYSIS`，payload 只包含 `shopId/commentId`，bizKey 为 `comment-log-analysis:{commentId}`。任务执行时重新校验门店和评价归属、自动回复成功状态及已有分析状态，并复用原有算力 requestNo。

分析失败不影响自动回复；容量等待不写 `analysis_status=FAILED`。普通模型错误继续记录分析失败并遵循任务执行策略。

### 8. 配置与可观测性

新增 `llm.rate-limit` 配置，包括启用状态、默认 RPM/burst、短等待、最大总等待、默认 429 冷却和规则列表；新增 `mt.async-task.llm-executor-concurrency=2` 与任务权重配置。供应商名称在 chat、vision 和 embedding 配置中显式提供，API Key 仍沿用现有秘密配置。

内存指标至少包含 `acquired`、`waited`、`deferred`、`provider_429`、`redis_error` 和累计等待毫秒；数据库积压查询补充每类最老 PENDING 时间。日志按限流桶聚合，避免每次短等待都输出 WARN。

## Risks / Trade-offs

- [Risk] 严格平滑会增加单条任务完成时间 → LLM lane 小并发允许不同调用重叠，MySQL 保留可靠积压，并通过优先级保障公开回复。
- [Risk] 多阶段任务在长冷却后重排可能重复已完成但未持久化的模型阶段 → 正常配额等待在线程内完成；只有超长冷却才重排，现有业务外部调用均发生在生成完成之后，重复影响限于模型调用成本。
- [Risk] 双节点公平查询可能同时选中同一 ID → 数据库原子领取保持最终唯一；dispatcher 内存不预排大量 ID，重复竞争成本受限。
- [Risk] Redis 故障暂停模型任务 → 这是保护供应商额度的明确 fail-closed 取舍；普通美团和授权任务使用独立 lane，不受影响。
- [Risk] 50 RPM 可能低于业务增长后的实时需要 → 额度与 worker 并发均可配置，积压年龄指标用于决定申请配额或增加 API Key。
- [Risk] 配置错误导致模型长期不处理 → 启动时校验规则的 provider/model/RPM/burst，并对未知生产桶记录明确错误和告警。

## Migration Plan

1. 发布代码但限流先以 dry-run 指标模式运行，确认所有 Chat、Vision、Embedding 出口均能构造正确安全桶。
2. 启用 LLM 独立 lane，每节点并发 2；验证普通任务不受 LLM 积压影响，任务仍保持幂等领取。
3. 启用 TokenHub `deepseek-v4-flash` 50 RPM、burst 1，观察实际 RPM、等待时间、429 和最老积压年龄。
4. 配置并启用视觉和 Embedding 模型各自额度，再关闭 dry-run 指标模式。
5. 将评论日志分析切换为独立任务，核对新旧评价分析幂等和算力扣减。
6. 回滚代码时删除或忽略短生命周期 Redis key；`mt_async_task` 中新增任务可暂留 PENDING，旧代码不会领取未知类型。若需要完全回滚，先停止入队新任务再回退双节点代码。

## Open Questions

无。已确认生产初始值为 TokenHub `deepseek-v4-flash` 50 RPM、burst 1、LLM 每节点并发 2，并启用日志分析独立任务、任务类型权重和门店公平调度。
