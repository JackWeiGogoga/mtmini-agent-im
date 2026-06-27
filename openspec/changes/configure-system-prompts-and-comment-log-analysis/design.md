## Context

当前系统已经存在多处大模型调用：评价回复 RAG、评价图片摘要识别，以及即将接入的评论日志分析。评价回复的 system/user prompt 和图片识别 prompt 主要写在 Java 代码中，`comment_log.prompt` 作为资源文件草稿存在但尚未接入。H5 日志接口当前返回固定 `analysisText = "AI 分析结果"`，不能体现真实评价数据。

本次变更跨后端、管理后台和数据库。后端仍是业务真源；管理后台负责内部运营配置；H5 只展示门店隔离后的日志结果。自动回复链路必须继续保证按门店隔离、幂等、可观测，并且不放宽门店授权和权益约束。

## Goals / Non-Goals

**Goals:**

- 建立通用系统提示词配置模型，支持以后新增 prompt key，而不是为每个业务 prompt 新建配置表。
- 管理后台提供系统提示词配置页面，支持动态修改当前系统使用的 prompt。
- 大模型调用统一通过 prompt 解析服务获取模板，优先数据库启用配置，兜底资源文件或代码默认值。
- 使用优化后的 `comment_log.prompt` 为评价生成 500 字以内、可展示给商家的 AI 分析日志文案。
- 分析日志结果持久化，H5 日志接口返回真实 `analysisText`；缺失或生成失败时保留兜底文案。
- 评论日志分析按现有大模型算力体系扣费，优先使用 token usage，usage 缺失时使用兜底算力。
- 评价星级与好中差判定使用 `product_score` 和 `packing_score` 的最低有效分，不再使用美团已废弃且固定返回 0 的 `food_comment_score`。

**Non-Goals:**

- 不新增或修改美团外部接口调用。
- 不改变美团添加商家回复接口的调用时机、幂等判断和回复状态语义。
- 不让 H5 或管理后台页面实时调用大模型生成日志分析。
- 不在本次做多租户门店自定义 prompt；本次 prompt 配置为系统级后台配置。
- 不提供历史评价补偿生成入口；当前能力尚未上线，本次只覆盖变更上线后的新增自动回复记录。
- 不新增新的前端状态管理框架或后端外部依赖。

## Decisions

1. **使用通用 `system_prompt_config` 表管理 prompt key，而不是把 prompt 写进业务配置表。**

   建议字段包含：`id`、`prompt_key`、`prompt_name`、`prompt_type`、`content`、`default_resource_path`、`variables_json`、`model_hint`、`enabled`、`version`、`remark`、`create_by`、`update_by`、`create_time`、`update_time`。`prompt_key` 唯一，例如 `COMMENT_REPLY_SYSTEM_GOOD`、`COMMENT_REPLY_SYSTEM_NEUTRAL`、`COMMENT_REPLY_SYSTEM_BAD`、`COMMENT_REPLY_USER`、`COMMENT_IMAGE_VISION`、`COMMENT_LOG_ANALYSIS`。

   这样后续新增算力提醒、客服话术、活动分析等 prompt 时只需新增初始化数据和页面配置，不需要改表结构。与把 prompt 存进 `mt_comment_reply_setting` 相比，通用表不会把系统级模型提示词误绑定到单门店设置。

2. **运行时使用 `SystemPromptService` 统一解析 prompt，并提供资源文件兜底。**

   业务服务不直接读数据库或拼接完整提示词，而是通过 `getPrompt(promptKey)` 或 `render(promptKey, variables)` 获取内容。解析顺序为：启用的数据库配置 -> `default_resource_path` 对应资源文件 -> 代码内最小兜底。

   第一版不强制引入复杂模板引擎，继续使用 `{{variableName}}` 变量替换即可；替换前后要保留未识别变量的可观测日志，避免静默生成错误 prompt。后台页面展示 `variables_json`，帮助运营知道可用变量。

3. **评论日志分析结果持久化在评价链路内，并与自动回复主状态分离。**

   优先方案是在 `mt_comment` 增加分析字段：`analysis_status`、`analysis_text`、`analysis_model`、`analysis_prompt_version`、`analysis_prompt_tokens`、`analysis_completion_tokens`、`analysis_power_amount`、`analysis_error`、`analysis_at`。这些字段与 H5 日志读取同表最直接，适合“每条评价一份当前分析文案”的场景。

   如果实现时更希望保留多版本历史，可新增 `mt_comment_analysis_generation` 表并在 `mt_comment` 保存最新生成 ID；但第一版建议避免过度复杂。无论选择哪种，H5 只读取当前评价的最新可展示分析文案。

4. **分析生成挂在自动回复成功后的异步链路，并按模型用量扣算力。**

   自动回复任务成功生成/保存商家回复后，尝试生成分析日志文案。分析生成失败不得影响已完成的美团回复，也不得把 `auto_reply_status=SUCCESS` 改为失败；只更新分析状态和错误信息。

   分析生成成功后复用现有算力扣减体系：优先按模型返回的 `prompt_tokens` 和 `completion_tokens` 计算消耗；模型未返回可用 usage 时使用配置的兜底算力值。扣费请求号按 `COMMENT_LOG_ANALYSIS:{commentId}` 生成，配合分析成功状态避免重复扣费。

5. **评论日志分析 prompt 使用结构化变量，输出面向商家运营视角。**

   在 `comment_log.prompt` 草稿基础上优化为：明确角色为“外卖商家评价运营分析助手”，输入包含口味/配送/商品/包装评分、标签、配送时效、首次评价、追评、图片摘要、评价类型；输出限制 500 字以内，要求先给结论再给改进建议，避免承诺退款、补发、联系顾客等动作，避免输出 JSON 或 Markdown 表格。

   表情符号可以保留但应节制，建议最多 2 个，避免 H5 日志显得不专业。

6. **管理后台页面放在系统管理或运营配置下，权限独立控制。**

   后端接口建议为 `/api/system/prompts`，权限使用 `system:prompt:list`、`system:prompt:query`、`system:prompt:edit`。前端新增 `/system/prompts` 或 `/system/prompt-config` 页面，使用 Ant Design 表格 + 编辑抽屉/Modal，复用现有 request、PageContainer 和 AppMessageContext。

   页面第一版提供列表、详情编辑、启停、恢复默认、变量说明和版本显示，不做多版本 diff。数据库中 `version` 每次保存递增，业务记录保存使用到的版本，满足基础审计和问题追踪。

## Risks / Trade-offs

- [Risk] prompt 配置错误导致模型输出不符合预期 -> 后台保留恢复默认能力；运行时有资源文件兜底；分析生成失败只影响分析状态，不影响自动回复主链路。
- [Risk] 同一评价重复生成分析并重复扣算力 -> 使用 `analysis_status=SUCCESS` 和幂等 `requestNo`，自动回复链路跳过已有成功分析记录。
- [Risk] prompt 变量替换遗漏或字段为空 -> 渲染服务统一处理空值为“无”，并记录未知变量日志；后台展示变量说明。
- [Risk] 分析生成增加自动回复任务耗时 -> 可放在美团回复成功后的独立异步任务或 worker 子任务；失败不阻塞回复状态落库。
- [Risk] 通用 prompt 表过早承载所有配置 -> 第一版只管理大模型提示词，不混入模型 API key、温度、计费策略等运行配置。
- [Risk] `h5-comment-reply-log` 仍在未归档变更中 -> 实现时需要与 `implement-h5-log-page` 的接口字段保持兼容，只替换 `analysisText` 数据来源。

## Migration Plan

1. 新增数据库迁移：创建 `system_prompt_config`，初始化已知 prompt key；为评论分析结果新增字段或生成表；新增管理后台菜单权限。
2. 后端发布后，默认 prompt 先从初始化数据/资源文件提供，不依赖人工配置即可运行。
3. 运营在管理后台调整 prompt 时，保存新版本；如输出异常，使用“恢复默认”或停用配置回退资源文件。
4. 上线后观察新增自动回复记录的分析生成成功率、耗时和算力消耗。
5. 回滚时保留新增字段和配置表，代码回退后 H5 可继续显示旧兜底文案或已生成的 `analysis_text`。

## Open Questions

- 后台 prompt 配置是否需要“测试生成”功能；本次先不做，以降低误调用真实模型和扣算力的风险。
