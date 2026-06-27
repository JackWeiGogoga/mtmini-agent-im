## Why

当前门店侧已经有“差评处理/申诉开关”，但差评自动回复链路尚未调用美团申诉接口，商家仍需要人工判断申诉类型并撰写申诉内容。接入差评自动投诉后，系统可以在识别到差评时自动判断申诉类型、生成合规申诉文本并提交美团，减少人工处理成本。

## What Changes

- 新增差评自动投诉能力：评价被判定为差评后，在门店开启差评处理/申诉开关且具备有效授权、权益约束时，异步提交美团评价申诉。
- 新增美团申诉接口调用，文档地址：`https://developer.meituan.com/docs/api/wmoper-ng-comment-complain-report`，实现时以官方文档或 SDK typed request 字段为准。
- 使用大模型先判断申诉类型 `reportTypeCode`，仅允许输出系统支持的申诉类型编码；模型无法可靠判断时默认使用 `47`（内容无意义评价）。
- 使用大模型生成 300 字以内的申诉文本内容，并复用敏感词/合规边界，避免承诺、攻击、违规联系方式等风险表达。
- 将现有 `comment_report_no_pic.prompt` 与 `comment_report_with_pic.prompt` 合并为一个可配置 prompt 模板，通过变量动态传入“是否有图片”和图片摘要/图片上下文。
- 持久化申诉状态、类型、文本、模型、prompt 版本、token usage、算力消耗、美团响应、错误原因和提交时间，确保同一评价不会重复成功申诉或重复扣算力。
- 同步更新 `docs/美团接口调用索引.md`，记录新增美团申诉接口、本地入口、dry run、幂等和落库字段。

## Capabilities

### New Capabilities

- `negative-comment-auto-complaint`: 差评自动投诉，包括差评触发条件、LLM 申诉类型判断、申诉文本生成、美团申诉提交、幂等持久化和可观测性。

### Modified Capabilities

- `comment-reply-knowledge`: 评论类型判定需要作为差评申诉触发依据，并优先使用现行有效评分字段，避免继续依赖已废弃或恒定返回 0 的评分字段导致误申诉。

## Impact

- 后端：新增差评申诉 service、DTO/实体/Mapper/XML、异步任务类型或自动回复后置任务处理；改造 `MtCommentAutoReplyService` 在差评回复成功后触发申诉；复用 `ShopOperationSettingsService` 的差评处理/申诉开关。
- 数据库：为 `mt_comment` 增加当前申诉状态字段，或新增 `mt_comment_complaint` 表保存申诉生成与提交记录；同步更新迁移 SQL 和 `full_schema.sql`。
- Prompt：新增 `COMMENT_REPORT_TYPE`、`COMMENT_REPORT_CONTENT` 或合并后的 `COMMENT_REPORT` prompt key；合并 `comment_report_no_pic.prompt` 与 `comment_report_with_pic.prompt`。
- 大模型与算力：新增申诉类型判断和申诉文本生成调用，按现有 token usage 规则扣减门店算力；模型失败时使用 `reportTypeCode=47` 兜底，文本生成失败则不提交申诉并记录失败。
- 美团接口：新增评价申诉接口调用，需支持 dry run、日志脱敏、按门店 token 调用和错误重试。
- 文档与验证：更新 `docs/美团接口调用索引.md`；补充差评判定、prompt 渲染、幂等提交、dry run 和失败重试相关测试。
