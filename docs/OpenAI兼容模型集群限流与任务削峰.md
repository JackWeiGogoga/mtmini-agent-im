# OpenAI 兼容模型集群限流与任务削峰

## 当前范围

模型基础设施保留给通用知识库 Embedding 和后续 IM 回复生成复用。当前 `/imMsg` 接收链路禁止调用模型、扣减算力或创建回复任务。

限流维度为“供应商 + API Key 指纹 + 模型”。指纹使用 SHA-256；日志不得输出 API Key、Authorization、提示词、知识正文或 IM 消息正文。

## 执行语义

- 通用知识文档解析使用幂等任务 `KNOWLEDGE_DOCUMENT_PARSE`，按门店 `scope_key` 隔离。
- LLM worker 当前加权队列只包含知识文档解析；授权、权益与 IM 初始化走普通任务执行器，不受模型积压阻塞。
- 模型调用前获取 Redis GCRA 许可；429 按 `Retry-After` 进入共享冷却。
- 容量不足抛出 `LlmCapacityDeferredException`，数据库任务无损重排且不增加业务重试次数。
- Redis 异常 fail-closed，避免绕过限流冲击供应商。

主要环境变量继续使用 `LLM_RATE_LIMIT_*`、`MT_ASYNC_TASK_LLM_EXECUTOR_CONCURRENCY` 和通用 `MT_KNOWLEDGE_*` 配置。知识模型供应商、Chat 模型与 Embedding 模型分别由 `MT_KNOWLEDGE_MODEL_*` 和 `MT_KNOWLEDGE_EMBEDDING_*` 指定。

## 观测与回滚

继续观测 `acquired`、`waited`、`deferred`、`provider_429`、`redis_error`，以及数据库任务的 `enqueue`、`claim`、`success`、`retry`、`reschedule`、`dead` 和最老积压时间。

回滚时优先启用 observe-only；如需回退 worker，先停止新任务入队或暂停 worker，再同时回退节点。Redis 限流 key 均有短 TTL，不应宽泛删除。
