## Why

生产环境在评价定时批处理时会由双后端节点并发调用同一个 OpenAI 兼容模型，已经触发 TokenHub `deepseek-v4-flash` 的 60 RPM 上限并产生集中 429。随着门店和评价数量增长，系统需要把数据库任务积压作为正常状态，以供应商额度稳定消化任务，而不是依赖瞬时并发和失败兜底。

## What Changes

- 为 Chat Completion、图片识别和 Embedding 增加基于 Redis 的集群级模型限流，按“供应商 + API Key 安全指纹 + 模型”隔离额度。
- 使用可平滑请求间隔的分布式 GCRA 限流算法，并对供应商 429 建立全节点共享冷却。
- 将 LLM 相关任务从普通异步任务中拆分为有界执行 lane，每节点默认并发 2，不再把大量待处理任务预先堆入 JVM 线程池。
- 对 LLM 任务增加任务类型权重与按门店公平选择，使自动回复优先且单个大门店不能长期占满处理能力。
- 将评论日志 AI 分析从自动回复主任务拆为独立的低优先级数据库异步任务。
- 将内部限流等待和供应商容量冷却定义为无损等待：不增加业务重试次数、不记录模型失败、不触发官方回复或群聊固定文案兜底。
- 增加安全限流指标、任务积压年龄和供应商 429 可观测性；日志和 Redis 均不得保存原始 API Key。

## Capabilities

### New Capabilities

- `cluster-llm-rate-limiting`: 覆盖所有 OpenAI 兼容模型出口的集群级额度隔离、平滑限速、429 冷却、密钥安全与指标。
- `llm-task-backpressure`: 覆盖 LLM 任务的有界领取、数据库积压、任务类型权重、门店公平性、无损等待和评论日志分析独立任务。

### Modified Capabilities

- `comment-reply-knowledge`: 评价回复遇到内部限流或供应商容量冷却时等待后继续处理，不得立即使用官方回复降级。
- `comment-image-vision-summary`: 图片识别遇到内部限流或供应商容量冷却时等待后继续处理，不得把容量等待记录为识别失败。

## Impact

- 后端：`OpenAiCompatibleChatService`、图片识别与 Embedding 出口、数据库异步任务 worker、任务 Mapper、评价自动回复后置链路、配置和指标服务。
- Redis：新增短生命周期的限流状态与供应商冷却 key，不新增外部依赖。
- MySQL：复用 `mt_async_task` 作为持久化积压队列，新增评论日志分析任务类型；预计不新增业务表。
- 运维：新增每个供应商/模型的有效 RPM、burst、等待和 LLM worker 并发配置；TokenHub `deepseek-v4-flash` 默认生产有效额度为 50 RPM、burst 为 1。
- 外部接口：不新增或修改美团接口；不改变门店授权、权益、评价幂等和算力隔离约束。
