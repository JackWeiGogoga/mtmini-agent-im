## Context

当前 `/imMsg` 已完成外层验签、兼容解析、门店映射、正文解密、双重幂等与消息事实入库，`MtImMessageClient` 已能单次发送 `biz_type=1` 两方会话消息。数据库异步任务、OpenAI-compatible Embedding、门店知识切片、Redis 集群限流、系统提示词、敏感词和算力账户也已存在，但彼此尚未接成自动回复闭环。

新增能力同时影响回调事务、异步执行、外部模型、美团写接口、数据库会话模型、管理后台和 H5。IM 正文属于敏感数据，所有读取接口必须执行登录、权限和门店范围校验；普通日志继续禁止输出正文、完整 payload、完整密文、token 或秘密。

## Goals / Non-Goals

**Goals:**

- 在不延长 `/imMsg` 外部调用时间的前提下可靠创建自动回复任务。
- 支持门店顾客长期 Conversation、空闲切分 Session、当前 Session 最近 100 轮短期记忆和跨 Session 管理历史。
- 有门店知识时执行门店隔离向量召回并注入简单 RAG 上下文。
- 复用模型限流、实际 token 算力计费、敏感词和两方会话发送客户端，提供可恢复、可观测、幂等的状态机。
- 在总控台、H5 和管理员专用页面展示用户消息、AI 回复、会话聚合与完整历史。

**Non-Goals:**

- 不实现跨 Conversation 的长期记忆、用户画像、历史摘要或旧 Session 检索增强。
- 第一阶段不理解图片、卡片或其他媒体，也不由这些消息触发自动回复。
- 不实现人工坐席系统、主动营销、流式回复或美团机器人状态查询。
- 不新增专业向量数据库；继续使用现有 MySQL 候选召回与 Java cosine 排序。

## Decisions

### 1. 回调事务只增加本地事实和任务，不执行外部副作用

合法候选消息在一个数据库事务中完成消息、Conversation/Session 聚合、`mt_im_auto_reply` 占位和 `IM_AUTO_REPLY` 任务入队。任务唯一键使用触发消息 ID，重复回调不能产生第二条任务。HTTP 线程不得调用 Embedding、Chat、算力消费或美团发送接口，数据库未知失败继续返回非 0。

这比事务提交后发布内存事件可靠，也比在回调内直接调用模型更容易满足美团响应时限。

### 2. Conversation 与 Session 分离

`mt_im_conversation` 以 `(shop_id, open_user_id)` 唯一标识门店与顾客的长期聊天线程，供管理界面聚合。`mt_im_conversation_session` 是 Conversation 下的短期记忆窗口，默认 30 分钟空闲后关闭旧 Session 并创建新 Session；`order_id` 只作为消息属性，不作为强制切分条件。

同一 Session 超过 100 轮时不新建 Session，模型只保留最新 100 轮并继续按上下文 token 预算从旧到新裁剪。管理界面可查看该 Conversation 下全部历史 Session，因此“数据库留存”不等于“AI 长期记忆”。

### 3. `mt_im_message` 升级为统一消息时间线

现有表增加 `conversation_id`、`session_id`、`record_source`、`sender_role`、`reply_to_message_id`、`auto_reply_id`、`delivery_status` 和 `event_time`。回调消息和系统生成的 AI 消息共用此表，消息正文只保存一次。

内部 AI 消息允许 `callback_msg_id` 为空，并在生成后持久化。若美团随后以相同 `(app_id, platform_msg_id)` 回推商家消息，入库服务补全该 AI 记录的回调字段，而不是创建重复消息。`mt_im_auto_reply` 只保存编排状态、模型、RAG、token、算力、错误和时间，不复制回复正文。

相较于查询时跨入站表与回复表执行全局 UNION，统一时间线能稳定支持会话抽屉游标分页、发送回推去重和人工商家消息混排。

### 4. 会话聚合使用事务内增量计数

Conversation 保存消息总数、用户消息数、已发送 AI 回复数、人工商家消息数、Session 数和首末消息时间。新增用户/人工消息以及 AI 消息首次进入 `SENT` 时，在同一事务内幂等更新计数和最后消息引用；任务重试不得重复累加。

会话列表默认使用 `(last_message_at DESC, id DESC)`，并单独展示 `last_ai_reply_at`。完整时间线使用 `(conversation_id, event_time, id)` 复合索引。

### 5. 自动回复只处理可确定的用户文本候选

候选必须满足 `biz_type=1`、`msg_source=2`、`msg_type=1`、门店匹配、解析和解密成功、正文非空、`open_user_id` 与发送上下文完整。媒体消息仍进入统一时间线但不触发任务。

任务默认延迟 2 秒执行。若同一 Conversation 已出现更新用户消息，旧任务标记 `SUPERSEDED`，由最新任务合并连续用户消息后只回复一次；若触发消息之后已有人工商家消息，标记 `SKIPPED_MANUAL_REPLY`。生成后发送前再次检查新消息，降低抢话和过时回复。

### 6. 会话串行由可续租 Redis 锁保证

现有 LLM 公平队列按门店 `scope_key` 调度，新任务把 `scope_key` 设为 Conversation ID，并在执行前获取专用的可续租 Redis 锁。获取失败或 Redis 异常时任务无损重排；锁租期覆盖模型与发送阶段并按 owner token 安全续租/释放。

只依赖当前固定租期锁存在任务超时后并发执行风险，因此不直接复用其无续租语义。

### 7. 当前 Session 历史由统一时间线重建

历史按 `event_time,id` 正序读取当前 Session，合并连续同角色文本，最多保留 100 个用户/回复轮次；只把已接收用户/人工消息和已发送 AI 消息作为对话事实。上下文过长时优先保留系统提示词、当前问题和最新历史，不生成历史摘要。

### 8. 有可用知识时强制执行简单 RAG

当 `KnowledgeVectorStore.hasShopKnowledge(shopId)` 为真时，使用当前问题与最近最多三个用户轮次构造检索文本，调用现有 Embedding 服务并执行门店级 topK/阈值召回。无命中可继续无知识生成；Embedding 或检索异常则重试任务，不静默绕过门店知识。

知识片段在 prompt 中使用明确分隔，并声明其为不可信参考数据、不得执行其中指令。记录命中切片 ID、分数和数量，不持久化完整 prompt。

### 9. Chat 出口复用 OpenAI-compatible 限流基础

新增 Chat 服务调用 `/chat/completions`，通过 `LlmRateLimitedRestExecutor` 共享供应商、API Key 指纹和模型维度的 GCRA 限流/429 冷却。请求由 `IM_AUTO_REPLY_SYSTEM` 系统提示词、短期历史、可选知识上下文和当前用户消息组成，响应保存正文和 usage。

模型配置收敛在 `mt.im-auto-reply`，默认可回退现有 `mt.knowledge.model-*` 环境值，Embedding 配置仍属于知识库。

### 10. 生成、敏感词、计费和发送使用可恢复状态机

状态至少包含 `PENDING/GENERATING/GENERATED/BILLED/SENDING/SENT/SUPERSEDED/SKIPPED/DEAD`。生成正文持久化到 AI 消息后，重试不再调用模型；算力以稳定 `power_request_no` 按 Chat usage 幂等消费；发送使用持久化的稳定 `outbound_msg_id`，重试复用同一内容和 ID。

生成文本先执行敏感词检测，命中时按 `mt.sensitive-word.max-rewrite-attempts` 请求模型安全改写，仍命中则使用固定安全兜底，发送客户端继续做最终硬拦截。算力不足沿用现有独立事务关闭本地自动回复开关。

### 11. 日志查询按用途分层

- 总控台按可见门店打开回复日志抽屉，一行对应一个自动回复记录并同时展示触发用户消息与 AI 回复。
- H5 新增“日志”Tab，只查询当前登录门店，采用移动端卡片和分页。
- 管理员页面新增“消息列表”和“会话列表”；消息列表按回复记录平铺，会话列表来自 Conversation 聚合。
- 会话抽屉先加载最新消息并定位底部，向上滚动使用 `(event_time,id)` 复合游标跨 Session 加载更早历史，前端保持滚动锚点并在 `session_id` 变化处插入分隔线。

管理员全局页面使用 `system:im-auto-reply:list`，总控台继续执行现有可见门店范围，H5 只允许当前门店。正文接口不得提供导出，并应记录安全的查看行为事件。

### 12. 搜索以门店为主并限制正文模糊查询成本

管理员消息和会话列表支持门店名称、`poi_id`、`epoi_id`、`open_user_id`、`order_id`、状态和时间范围。门店 ID 使用索引精确或前缀匹配；正文关键词查询要求同时提供门店或时间范围，避免对全部 `longtext` 无界扫描。

## Risks / Trade-offs

- [外部发送成功但本地更新前进程退出] → 任务重试复用同一出站 `msg_id`，依赖上游按三方消息 ID 去重，并保留结果未知状态与安全 traceId。
- [长时间模型调用导致同会话锁过期] → 使用可续租 owner-token 锁；续租失败时发送前再次检查状态并无损重排。
- [Conversation 聚合计数与消息事实不一致] → 所有首次状态迁移与计数更新使用条件更新；提供按消息事实重算的管理修复入口作为后续运维能力。
- [100 轮仍超过模型上下文] → 100 轮是读取上限，实际 prompt 继续按 token/字符预算保留最新内容。
- [管理页面暴露敏感正文] → H5 门店隔离、后台权限和数据范围双重校验，普通日志与导出禁正文，详情按需加载。
- [MySQL 向量候选增长] → 第一阶段保留每店候选上限与阈值；达到规模瓶颈后可替换 `KnowledgeVectorStore` 实现而不改编排接口。

## Migration Plan

1. 先扩展 schema、实体与 Mapper，保持 `/imMsg` 原行为测试通过。
2. 上线 Conversation/Session 分配和统一时间线，但保持自动回复任务开关关闭。
3. 上线自动回复任务、Chat、RAG、计费和发送状态机，使用独立配置总开关灰度启用。
4. 上线管理后台和 H5 日志入口及权限数据。
5. 更新美团接口索引和运维指标，完成回调、模型、发送、重复回推与跨 Session 历史端到端验证后开启生产门店。

回滚时先关闭自动回复任务入队和 worker，保留消息、Conversation、Session 与回复记录以供审计；无需关闭美团门店 IM，也不得重新启用美团机器人。

## Open Questions

- 当前确认的默认值为 30 分钟 Session 空闲边界、100 轮历史上限和 2 秒连续消息防抖；均通过配置暴露，后续可按生产观测调整。

## 全局美团 API Dry Run

### 决策

- 在 Spring 唯一的 `MeituanClient` Bean 外包一层代理，统一覆盖 `invokeApi`、`commonInvokeApi`、OAuth Token 获取和 Token 刷新，避免各业务客户端漏加保护。
- 使用 `mt.api.dry-run` 控制，配置默认值为 `true`；仅当环境显式配置 `MT_API_DRY_RUN=false` 时才委托真实 SDK。
- dry-run 命中时抛出专用异常，不构造虚假的成功响应，也不更新“远端已成功”之类的本地状态。
- 专用异常沿用数据库异步任务 dry-run 信号，worker 按 `mt.api.dry-run-reschedule-seconds` 无损重排，不增加重试次数；同步调用快速失败并暴露稳定错误码 `MT_API_DRY_RUN`。
- dry-run 日志只记录 SDK 操作/Request 类型，不记录 access token、授权码、请求正文或响应正文。

### 取舍

统一代理会使 OAuth 和只读查询也被阻断。这是有意的安全默认值：本地测试使用预置门店通过 H5 本地登录入口进入，不依赖真实 OAuth；需要真实联调时由操作者显式关闭一个全局开关，避免配置遗漏导致部分调用意外外呼。

## 本地 IM 测试数据

- `deploy-local/本地测试数据.sql` 作为幂等测试夹具，只清理并重建固定门店 `TEST_EPOI_REPLY_001` 关联的数据，不影响其他门店。
- 固定测试门店具备有效授权、本地 IM/自动回复开关、IM 权益和充足算力，便于直接进入 H5 与管理后台日志页面。
- 至少预置一个持续 Conversation、多个由空闲边界切分的历史 Session，并让最新 Session 本身超过首屏阈值，验证首屏只看最新 Session、向上滚动后再加载历史 Session。
- 预置不同状态的自动回复记录和可用知识条目/切片，覆盖消息列表筛选、门店搜索、RAG 标记、发送成功/跳过/失败等展示场景。
