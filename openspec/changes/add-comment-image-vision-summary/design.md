## Context

当前评价自动回复链路会把美团接口返回的 `comment_pictures` 保存到 `mt_comment.comment_pictures`，但回复生成只使用评价文本、追评、标签、点赞菜品和差评菜品。知识库 RAG 已经通过 `MtCommentReplyContentService` 统一服务真实 task 和管理后台测试回复，因此图片理解也应进入同一份评论上下文，而不是另起一套回复生成逻辑。

TokenHub 已验证 `qwen3.5-flash` 可以接收 `image_url` 并完成图片理解。本次设计采用“两阶段”方案：先生成图片摘要并持久化，再由现有 RAG 文本回复链路生成最终商家回复。

## Goals / Non-Goals

**Goals:**

- 对包含图片的评价调用多模态模型生成结构化图片摘要。
- 将图片摘要保存到本地，支持自动回复复用、管理后台查看和问题排查。
- 图片识别按模型返回的 token usage 折算算力并扣减，和现有 RAG 回复计费口径一致。
- 将图片摘要加入现有 `buildQueryText()` 上下文，使 embedding 检索和文本模型 prompt 都能使用图片信息。
- 图片识别失败时不阻塞自动回复，保留可观测日志和失败原因。
- 测试回复效果复用真实回复 service，并能覆盖图片摘要上下文。

**Non-Goals:**

- 不让多模态模型直接生成最终商家回复。
- 不处理“文本情绪与 `food_comment_score` 不一致”的分类问题。
- 不新增美团外部接口调用。
- 不在首版实现图片下载、转存或人工审核工作流。
- 不要求识别所有图片，默认只识别前 3 张。

## Decisions

### 使用 `qwen3.5-flash` 作为默认图片理解模型

默认配置新增到 `mt.comment.knowledge` 下，例如：

- `vision-enabled`
- `vision-model-base-url`
- `vision-model-api-key`
- `vision-model`
- `vision-max-image-count`
- `vision-timeout-seconds`
- `vision-fallback-power-amount`

默认开启图片识别，默认模型为 `qwen3.5-flash`，base URL 使用 `https://tokenhub.tencentmaas.com/v1`。如果 API key 为空，系统按识图失败降级，不调用外部模型。这样可以沿用 OpenAI 兼容协议和现有 TokenHub API key 管理方式。

备选方案是 `hy-vision-2.0-instruct` 或 `youtu-vita`。`hy-vision-2.0-instruct` 更像专门的图片理解模型，但多图限制更明显；`youtu-vita` 适合一次传多张图和视频扩展，但本需求当前只处理评价图片。基于用户已验证 `qwen3.5-flash` 调用成功，首版选它作为默认。

### 图片识别只输出结构化摘要，不直接回复

多模态 prompt 要求输出可解析 JSON，建议字段包括：

- `hasFoodIssue`
- `hasPackageIssue`
- `hasForeignObject`
- `hasLeakage`
- `hasReceiptOrText`
- `summary`
- `confidence`

服务端使用 `JsonUtil` 做 JSON 提取和解析，禁止在业务服务中各自手写 markdown fenced JSON 清洗逻辑。解析失败时保存原始返回摘要和失败原因，但不阻塞自动回复。

### 图片摘要持久化到独立记录

新增评论图片识别结果表，建议命名为 `mt_comment_image_summary`。核心字段：

- `id`
- `shop_id`
- `mt_comment_id`
- `comment_id`
- `image_count`
- `image_urls_json`
- `status`
- `summary_json`
- `summary_text`
- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `power_amount`
- `model_provider`
- `model`
- `error_message`
- `create_time`
- `update_time`

按 `comment_id` 做唯一约束或幂等查询，避免同一评价重复识别。使用独立表比直接加字段到 `mt_comment` 更利于后续记录状态、错误、模型和多图详情。

图片描述保存位置：图片摘要文本保存到 `mt_comment_image_summary.summary_text`，完整结构化识别结果保存到 `mt_comment_image_summary.summary_json`。`mt_comment.comment_pictures` 继续只保存美团原始图片 URL，不混入模型识别结果。

### 图片识别按 token usage 扣算力

图片识别成功后读取 TokenHub 返回的 `usage.prompt_tokens`、`usage.completion_tokens` 和 `usage.total_tokens`，按和 RAG 回复相同的算力折算规则扣减门店余额，并保存 `power_amount`。如果模型没有返回 usage 或 usage 异常，则使用 `vision-fallback-power-amount` 作为兜底扣减值。

图片识别计费需要使用独立 `sourceType`，例如 `COMMENT_IMAGE_VISION`，`requestNo` 建议使用 `COMMENT_IMAGE_VISION:{commentId}`，保证同一评价图片识别重复执行时不会重复扣费。失败或降级不扣算力。

### 自动回复前同步识别，失败降级

在 `MtCommentAutoReplyService.executeCommentAutoReplyTask` 或进入 `MtCommentReplyContentService.generateReply` 前确保图片摘要可用。首版建议在回复内容生成 service 内部按需获取摘要，这样真实 task 和测试回复更容易复用。

处理顺序：

1. 解析 `comment_pictures`。
2. 如果没有图片，评论上下文写入“评价图片：无”或不写图片摘要。
3. 如果有图片且已有成功摘要，直接复用。
4. 如果有图片且没有摘要，调用 `qwen3.5-flash` 识别前 N 张。
5. 成功时把 `summary_text` 加入 `buildQueryText()`。
6. 失败时把“顾客上传了 N 张图片，但系统未能识别图片内容”加入上下文，并记录失败。

这样即使视觉模型异常，仍可继续走原有文本 RAG 或官方回退。

### 测试回复支持图片上下文

管理后台测试回复接口当前构造临时 `MtComment`。为了和真实 task 保持一致，测试参数应支持：

- `commentPictures`：图片 URL 数组，触发真实图片识别。
- 上传多张图片：管理后台先上传到服务端临时对象存储或复用现有 COS 能力，拿到可访问 URL 后触发真实图片识别。
- `imageSummaryText`：直接模拟已识别摘要，便于本地和低成本测试。

如果两者同时存在，优先使用 `imageSummaryText`，避免测试时反复调用视觉模型。

## Risks / Trade-offs

- [Risk] 多模态调用增加自动回复耗时 → 只识别前 3 张图，配置超时，失败降级继续回复。
- [Risk] 图片 URL 失效或美团图片防盗链导致识别失败 → 保存失败状态和错误信息，回复上下文只说明有图但未能识别。
- [Risk] 模型输出非 JSON → 使用 `JsonUtil` 统一解析，解析失败保存原文并降级。
- [Risk] 图片识别错误导致回复不准确 → prompt 要求只描述可见事实，并保存 `confidence`；低置信度摘要可在回复 prompt 中提示“仅作为参考”。
- [Risk] 重复任务导致重复识图和重复成本 → 对 `comment_id` 做幂等记录，成功摘要复用。
- [Risk] 图片识别扣费和 RAG 扣费重复叠加导致用户感知成本变高 → 管理后台测试和日志展示模型 usage 与扣费结果，后续可按运营策略调整折算比例。
- [Risk] 测试回复真实调用视觉模型带来成本 → 提供 `imageSummaryText` 模拟输入，并在 UI 上提示真实图片测试会调用模型。

## Migration Plan

1. 新增数据库迁移脚本创建 `mt_comment_image_summary`。
2. 新增配置项，默认开启；生产配置 API key 后调用模型，未配置 API key 时自动降级。
3. 发布后对新拉取评论按需识别；历史评论不做批量回填。
4. 如需回滚，关闭 `vision-enabled`，自动回复回到现有文本逻辑；已保存摘要保留不影响主链路。
