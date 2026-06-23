## Context

当前后端通过 `MtCommentAutoReplyService` 查询美团未回复评价、保存到 `mt_comment`，再通过 `COMMENT_AUTO_REPLY` 异步任务逐条调用美团“根据评价 id 添加商家回复”接口。回复内容目前来自 `mt.comment.default-reply`，默认固定为“很好”。

本次变更需要跨越后端数据库、异步任务、文件上传、OpenAI 兼容调用、H5 商户端和管理后台。评价自动回复仍然必须保留现有门店态约束，包括登录态、门店授权、门店权益、自动回复开关、任务幂等和按 `shop_id` 隔离。

当前线上 Redis 未加载 RediSearch/Search 模块，因此首期不使用 Redis 向量检索。为了降低部署复杂度，也不引入 SQLite 向量扩展或独立向量数据库。

## Goals / Non-Goals

**Goals:**

- 将固定回复替换为可配置、可兜底、可观测的回复内容生成策略。
- 支持官方回复文案按好评、中评、差评分类维护，并用于无商户知识或智能生成失败时随机回复。
- 支持商户通过手工 QA 和上传文档维护自有知识库。
- 支持 `txt`、`docx`、`xlsx` 文档解析，不要求商户使用固定模板。
- 基于 `mt_comment.food_comment_score` 将评论划分为差评、中评、好评。
- 对 `food_comment_score` 为空或异常的评价暂不自动回复，只记录异常日志并避免进入智能生成。
- 使用 MySQL 保存知识 chunk 与 embedding，并由 Java 计算 cosine similarity 完成首期检索。
- RAG 回复纳入算力计费，执行前要求门店算力余额大于 100000，授权成功后幂等赠送 800000 算力。
- 回复生成失败时不阻塞自动回复主链路，必须回退到官方文案。

**Non-Goals:**

- 首期不支持 PDF、图片、扫描件 OCR。
- 首期不引入 Redis Stack、Qdrant、Milvus、SQLite 向量扩展或其他独立向量服务。
- 首期不做多轮客服对话、智能申诉、退款赔付决策或按菜品训练模型。
- 首期不要求文档解析结果完全结构化；无法识别分类或 QA 的内容允许作为 `UNKNOWN` 普通知识片段使用。

## Decisions

### Decision: 使用 `food_comment_score` 判定评论类型

评论类型统一由 `mt_comment.food_comment_score` 得出：0-2 为 `BAD`，3 为 `NEUTRAL`，4-5 为 `GOOD`。如果为空或不在范围内，首期暂不生成回复，记录异常日志并避免进入 RAG 或官方随机回复链路。实现时应避免该评价因不可处理评分反复重试，可使用不可重试状态或等价机制记录跳过原因。

替代方案是使用 `order_comment_score` 或多评分聚合，但现有统计 SQL 已使用 `food_comment_score` 作为评价好中差统计依据，沿用该字段可以减少口径分裂。

### Decision: MySQL 作为知识真源，Java 完成首期向量相似度排序

新增知识库表保存官方文案、商户 QA、文档元信息、文档解析状态、知识 chunk 和 embedding。embedding 建议用 `BLOB` 保存 float32 二进制，同时记录 `embedding_model` 和 `embedding_dim`。

检索流程为：

1. 根据当前评论生成 query embedding。
2. 从 MySQL 查询当前 `shop_id` 下 `enabled=1`、`sentiment IN (当前类型, UNKNOWN)` 的候选 chunk。
3. Java 计算 cosine similarity。
4. 按相似度、同类型匹配、来源权重排序，取 topK。

替代方案包括 Redis 向量检索、SQLite 向量扩展和专业向量库。Redis 当前实例不支持 Search 模块；SQLite 会引入本地文件、多实例同步和 native extension 风险；专业向量库运维成本超过首期规模需要。

### Decision: Chat 使用 TokenHub，Embedding 使用阿里百炼兼容接口

回复生成 chat 模型使用腾讯云 TokenHub 的 `deepseek-v4-flash`。模型调用配置统一抽象为 OpenAI-compatible provider，默认 base URL 使用 TokenHub 广州地域 `https://tokenhub.tencentmaas.com/v1`。

embedding 模型使用阿里百炼 OpenAI 兼容接口，默认 base URL 为业务空间专属域名 `https://llm-kz8j7o34hwdm6nb4.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`，默认模型 `text-embedding-v4`，默认维度 `1024`。系统必须记录 `embedding_provider`、`embedding_model` 和 `embedding_dim`，并禁止不同维度向量混用。

### Decision: 官方知识与商户知识分开建模

官方回复文案用于平台兜底和默认回复，商户知识用于 RAG。建议拆分为：

- `mt_reply_knowledge_item`：官方模板和商户手工 QA。
- `mt_reply_knowledge_document`：商户上传文档元信息和解析状态。
- `mt_reply_knowledge_chunk`：QA 或文档解析后的检索片段。
- 可选 `mt_comment_reply_generation` 或在 `mt_comment` 增加字段：记录回复来源、命中知识和模型错误。

官方数据可用 `shop_id IS NULL` 或独立 scope 字段表示，商户数据必须带 `shop_id`。

### Decision: 文档上传采用异步解析

上传接口只做格式、大小、数量和门店权限校验，通过 COS 保存文件并创建 `PENDING` 文档记录，再入队异步解析任务。解析任务读取文件、抽取文本、清洗、切片、尝试识别 QA 与评论类型、生成 embedding、写入 chunk，最后更新 `parse_status`。

这样避免大文件解析和 embedding 调用阻塞 H5 请求，也便于失败重试和排查。

### Decision: 无模板文档以“尽力解析 + UNKNOWN 兜底”处理

文档解析不强制模板。系统优先识别常见结构，例如 `Q/A`、`问题/回答`、`顾客/商家`、`好评回复/中评回复/差评回复`，Excel 优先识别标题、评论类型、问题、回答等近似表头。无法识别结构时按普通文本切片，无法识别评论类型时标记为 `UNKNOWN`。

RAG 检索优先使用当前评论类型的知识，不足时补充 `UNKNOWN` 知识。

解析后的 chunk 首期允许商户查看、启停、删除和修改评论类型，用于纠正文档无模板导致的分类误判；chunk 原文内容首期可以只读，避免商户误改造成与原文来源不一致。

### Decision: RAG 回复计入算力

商户进入 RAG 回复链路前必须校验当前门店算力余额大于 100000。授权成功后，系统幂等赠送 800000 算力，可复用老系统类似的授权赠送逻辑和现有算力账户/赠送记录能力。

RAG 计费范围包括 query embedding、chat 生成以及后续可选的重排调用。官方随机回复和默认固定文案兜底不消耗模型，不计入 RAG 算力消费。

### Decision: 回复内容生成服务作为自动回复链路的独立策略

新增 `MtCommentReplyContentService` 或等价服务，向 `MtCommentAutoReplyService.executeCommentAutoReplyTask()` 提供最终回复文本和生成元数据。

策略为：

- 商户无可用自有知识：按评论类型从官方文案随机选择。
- 商户有可用自有知识且算力余额大于 100000：执行 RAG 检索并调用大模型生成。
- 商户有可用自有知识但算力余额不足或不满足门槛：记录原因，并回退官方随机回复。
- RAG、embedding 或大模型失败：记录错误并回退官方文案随机。
- 官方对应类型无文案时：使用配置中的 `mt.comment.default-reply` 作为最后兜底。

### Decision: 回复生成必须限制模型承诺

大模型 prompt 必须要求输出纯文本、简短自然，不编造知识库不存在的信息，不承诺退款、赔偿、补发、私下联系，除非命中的商户知识明确提供。差评回复必须先表达歉意再回应问题和改进。

## Risks / Trade-offs

- [Risk] 文档内容无模板导致分类或 QA 识别不准 → 使用 `UNKNOWN` 片段兜底，提供解析状态和片段查看/调整能力，优先使用手工 QA。
- [Risk] MySQL 候选过多导致单次检索耗时升高 → 限制每个文档 chunk 数、每商户 chunk 总数、单次候选召回数量，并保留 `KnowledgeVectorStore` 抽象便于后续替换。
- [Risk] 大模型生成不稳定或外部 API 失败 → 捕获失败、记录错误、回退官方随机文案，不让异步回复任务因生成失败长期阻塞。
- [Risk] 跨店知识泄露 → 所有商户知识查询、文档、chunk、检索都必须带 `shop_id`，自动回复只检索当前评价所属门店。
- [Risk] 文档解析依赖兼容性问题 → 首期优先支持 `txt`、`docx`、`xlsx`，对老格式 `doc/xls` 和 PDF 暂不承诺。
- [Risk] embedding 模型切换导致向量维度混用 → chunk 记录 `embedding_model` 和 `embedding_dim`，检索只使用与当前配置一致的向量。

## Migration Plan

1. 新增数据库 migration 和 `full_schema.sql` 更新，部署后不影响现有固定回复链路。
2. 上线官方文案管理并初始化 GOOD、NEUTRAL、BAD 三类官方回复文案。
3. 上线商户知识库管理和文档解析能力，并按全局配置启用 RAG，不做门店灰度。
4. 接入授权成功后幂等赠送 800000 算力，并补齐 RAG 回复前余额门槛校验和消费记录。
5. 将 `MtCommentAutoReplyService` 接入回复内容生成服务，保留 `mt.comment.default-reply` 作为最后兜底。
6. 本次不做门店灰度；上线后观察回复来源、失败率、任务积压、算力消费和美团回复成功率。
7. 如需回滚，关闭 RAG 配置或回退到官方随机/默认固定文案；知识库数据保留但不参与回复。

## Open Questions

- RAG 算力消费的具体单价、token 折算规则和消费明细字段需要与现有算力体系对齐。
- 无。
