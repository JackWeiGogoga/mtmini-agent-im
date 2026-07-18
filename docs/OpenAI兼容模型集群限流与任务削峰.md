# OpenAI 兼容模型集群限流与任务削峰

## 目标与边界

本方案用于约束多后端节点共享的 OpenAI 兼容模型额度，并让评价回复等任务可靠积压在 MySQL，而不是进入无界 JVM 队列。限流维度为“供应商 + API Key 指纹 + 模型”，API Key 和模型均以 SHA-256 指纹进入 Redis key，日志不输出 API Key、Authorization、提示词或评价正文。

本变更不调整美团接口、供应商配额和生产环境变量，也不要求短时间内处理完全部积压。

## 初始参数

| 项目 | 默认值 | 说明 |
| --- | ---: | --- |
| TokenHub `deepseek-v4-flash` | 50 RPM | 对供应商 60 RPM 配额保留安全余量 |
| burst | 1 | 使用 GCRA 平滑请求，避免分钟边界突发 |
| LLM 执行器 | 每节点 2 并发、队列 0 | 无空闲槽时任务留在 MySQL `PENDING` |
| 普通容量最大等待 | 120 秒 | 超过后无损重排，不增加 `retry_count` |
| 人工预览最大等待 | 2 秒 | 快速返回“模型繁忙”，不长期占用 Web 线程 |
| 429 默认冷却 | 60 秒 | 优先采用供应商 `Retry-After` |
| 重排抖动 | 0～3 秒 | 避免多节点同时再次抢占 |

未配置精确规则的模型使用保守默认值 30 RPM、burst 1，并按供应商、API Key 和模型各自隔离。确认供应商配额后应增加精确规则。

LLM 任务类型的默认权重为：自动回复 5、IM 跟进 2、差评申诉 2、评论日志分析 1、知识文档解析 1。同类任务按 `scope_key` 门店游标轮转，数据库原子领取负责双节点防重。

## 配置项

主要环境变量如下：

```text
LLM_RATE_LIMIT_ENABLED=true
LLM_RATE_LIMIT_OBSERVE_ONLY=false
LLM_RATE_LIMIT_DEFAULT_EFFECTIVE_RPM=30
LLM_RATE_LIMIT_DEFAULT_BURST=1
LLM_RATE_LIMIT_SHORT_WAIT_MILLIS=5000
LLM_RATE_LIMIT_MAX_WAIT_MILLIS=120000
LLM_RATE_LIMIT_INTERACTIVE_MAX_WAIT_MILLIS=2000
LLM_RATE_LIMIT_DEFAULT_COOLDOWN_SECONDS=60
LLM_RATE_LIMIT_STATE_TTL_SECONDS=180
LLM_RATE_LIMIT_RESCHEDULE_JITTER_MILLIS=3000
LLM_RATE_LIMIT_TOKENHUB_DEEPSEEK_V4_FLASH_RPM=50
LLM_RATE_LIMIT_TOKENHUB_DEEPSEEK_V4_FLASH_BURST=1
MT_ASYNC_TASK_LLM_EXECUTOR_CONCURRENCY=2
```

供应商标识使用 `LLM_OPENAI_COMPATIBLE_DEFAULT_PROVIDER`、`MT_COMMENT_KNOWLEDGE_MODEL_PROVIDER`、`MT_COMMENT_KNOWLEDGE_VISION_PROVIDER` 和 `MT_COMMENT_KNOWLEDGE_EMBEDDING_PROVIDER` 显式配置。不同 API Key 即使供应商和模型相同也会使用独立桶。

## 运行语义

- Chat、图片识别和 Embedding 在外呼前统一获取 Redis GCRA 许可。
- 供应商返回 429 时，相同桶写入集群共享冷却；当前调用仅在总等待上限内重试一次。
- Redis 异常执行 fail-closed：不继续冲击供应商，异步任务无损重排。
- `LlmCapacityDeferredException` 不转换为 `FALLBACK_STATIC`、官方文案、图片识别失败或申诉失败。
- 评论日志分析在自动回复成功后独立幂等入队，失败不回滚已经成功的商家回复。
- LLM 任务领取量不超过本节点实时空闲槽；执行器拒绝已领取任务时立即重排。

## 观测与告警

`LlmRateLimitMetricsService` 按安全短桶记录以下内存计数：

- `acquired`：获得许可；
- `waited`、`wait_millis`：发生等待及累计等待毫秒；
- `deferred`：超过最大等待后交回数据库；
- `observed_deferred`：观察模式下本应延迟的请求；
- `provider_429`：供应商真实返回 429；
- `redis_error`：限流 Redis 读写异常。

`MtAsyncTaskMetricsService` 继续记录任务 `enqueue`、`claim`、`success`、`retry`、`reschedule` 和 `dead`。任务积压摘要新增每类任务的 `oldestPendingAt`。

建议告警条件：

- 任意生产桶 `provider_429` 持续增长；
- `redis_error` 大于 0；
- `COMMENT_AUTO_REPLY` 最老 PENDING 年龄超过业务允许时长；
- `dead` 增长，或 `reschedule` 持续增长同时 `success` 不增长；
- LLM 积压增长时普通授权任务也出现老化，需检查线程池隔离是否生效。

## 灰度步骤

1. 先在一个节点设置 `LLM_RATE_LIMIT_OBSERVE_ONLY=true`，确认安全桶和 `observed_deferred` 符合预期。
2. 两个节点均启用 LLM 独立执行器，每节点并发设为 2；确认普通任务不受 LLM 积压影响。
3. 两个节点设置 `LLM_RATE_LIMIT_OBSERVE_ONLY=false`，启用 TokenHub `deepseek-v4-flash` 50 RPM、burst 1。
4. 观察真实 429、累计等待、无损重排和最老积压时间，再为视觉及 Embedding 模型补充经供应商确认的精确规则。
5. 确认自动回复与评论日志分析分别成功，且日志分析重复任务不会重复扣减算力。

每一步至少覆盖一个完整的日常评价查询和回复周期，再进入下一步。

## 回滚

1. 优先将 `LLM_RATE_LIMIT_OBSERVE_ONLY=true`，保留安全桶观测但停止实际拦截。
2. 如需回退 LLM lane，先停止新任务入队或暂停 worker，再回退两个节点，避免新旧节点同时使用不同任务集合。
3. Redis `llm:rate:v1:*` 和 `llm:cooldown:v1:*` 均为短 TTL 状态，无需手工删除；确需清理时必须先精确列出目标 key，禁止使用宽泛删除。
4. `COMMENT_LOG_ANALYSIS` 的 PENDING 任务可保留，旧版本不会领取未知任务；恢复新版本后可继续处理。

