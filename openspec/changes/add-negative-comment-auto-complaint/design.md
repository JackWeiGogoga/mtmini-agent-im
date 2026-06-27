## Context

当前评价自动回复链路已经具备门店授权过滤、门店权益约束、差评处理/申诉开关、评价全量字段落库、图片摘要识别、系统 prompt 配置、异步任务 worker 和美团添加商家回复接口调用。`backend/src/main/resources/prompts/` 中已有 `comment_report_no_pic.prompt` 与 `comment_report_with_pic.prompt` 草稿，两者只在“顾客是否上传图片”描述上不同，适合合并为一个可变量渲染的申诉 prompt。

新增差评自动投诉会引入新的美团外部接口调用，必须继续按门店隔离，使用当前门店 access token 和 `epoiId`，并同步维护 `docs/美团接口调用索引.md`。申诉失败不应影响已完成的自动回复主链路；重复执行同一评价时不得重复成功申诉、重复调用模型扣费或重复提交美团。

## Goals / Non-Goals

**Goals:**

- 在差评自动回复链路中接入差评自动投诉，受登录态以外的门店授权、门店权益、自动回复主开关和差评处理/申诉开关约束。
- 先通过大模型判断 `reportTypeCode`，默认兜底为 `47` 内容无意义评价，再通过大模型生成申诉文本。
- 合并两个申诉 prompt 草稿为可配置模板，并通过 `SystemPromptService` 动态渲染有图/无图差异。
- 调用美团评价申诉接口，并记录申诉生成、提交、响应、失败、耗时和算力消耗。
- 保证同一评价申诉链路幂等，成功后不重复提交；模型调用和扣费也必须有可观测记录。

**Non-Goals:**

- 不在本次新增 H5 或管理后台的申诉记录页面。
- 不做人工审核/人工改写申诉内容流程。
- 不提供历史评价一键补偿申诉入口；第一版只覆盖上线后的自动回复链路处理评价。
- 不改变美团添加商家回复接口的成功/失败语义。
- 不绕过现有门店授权、门店权益、自动回复开关或差评处理/申诉开关。

## Decisions

1. **申诉作为自动回复成功后的独立后置能力。**

   自动回复任务仍先完成回复内容生成和美团添加商家回复。若评价被判定为差评，且门店差评处理/申诉开关开启，则触发申诉处理。申诉处理失败只更新申诉状态，不把 `auto_reply_status=SUCCESS` 改回失败。

   相比在回复前先申诉，这样可以保持当前主链路行为稳定；相比把申诉失败抛给同一个 `COMMENT_AUTO_REPLY` 任务重试，单独记录申诉状态更容易避免重复回复。

2. **优先新增独立 `COMMENT_AUTO_COMPLAINT` 异步任务。**

   自动回复成功后入队 `COMMENT_AUTO_COMPLAINT`，payload 包含 `shopId`、`commentId`。worker 领取后重新读取门店、token、评价和设置，再执行申诉。`bizKey` 建议使用 `comment-complaint:{commentId}`，配合本地申诉成功状态防止重复执行。

   如果实现阶段为了缩小改动选择同步后置调用，也必须在 service 内捕获申诉异常，不能影响自动回复成功落库；但第一版建议独立任务，便于重试、dry run 和耗时隔离。

3. **申诉数据使用独立表保存完整过程，并在 `mt_comment` 保存当前状态快照。**

   建议新增 `mt_comment_complaint` 表，字段包含 `shop_id`、`mt_comment_id`、`comment_id`、`status`、`report_type_code`、`report_type_name`、`report_content`、`has_picture`、`model`、`prompt_key`、`prompt_version`、`prompt_tokens`、`completion_tokens`、`power_amount`、`request_payload_json`、`response_payload_json`、`error`、`submitted_at`、`duration_ms`、`create_time`、`update_time`。唯一索引建议为 `uk_mt_comment_complaint_comment(comment_id)` 或 `uk_mt_comment_complaint_shop_comment(shop_id, comment_id)`。

   `mt_comment` 可增加 `complaint_status`、`complaint_report_type_code`、`complaint_report_content`、`complaint_at`、`complaint_error` 快照字段，方便日志页或排障直接查询。若实现时为避免双写复杂度，也可以只用独立表，但 H5/后台未来读取需明确 join 方式。

4. **合并申诉 prompt，并把类型判断和内容生成拆成两个可审计步骤。**

   使用系统 prompt key 管理申诉提示词，建议：
   - `COMMENT_REPORT_TYPE`：输入评分、标签、首评、追评、配送超时说明、图片摘要、是否有图，输出 JSON：`{"reportTypeCode":47,"reason":"..."}`。
   - `COMMENT_REPORT_CONTENT`：输入确定后的 `reportTypeCode`、评分、评价文本、追评、配送超时说明、图片上下文，输出 300 字以内纯文本申诉内容。

   虽然可以只用一个 prompt 同时生成类型和文本，但拆分后更容易校验类型编码、记录兜底原因，也便于后续单独调优。现有两个 `comment_report_xxx.prompt` 草稿合并为 `comment_report_content.prompt`，有图/无图由 `pictureContext` 变量表达。

5. **申诉类型白名单在代码中固定，模型输出必须校验。**

   第一版支持用户提供的类型表：31 骑手配送超时、32 骑手服务问题、34 用户选错评分、35 用户选择环保单、36 评价与订单不符、37 用户未收餐、39 用户未备注产生差评、40 用户提出不合理要求、41 用户以差评威胁商家、43 同行或特殊身份给差评、44 多次差评、46 广告或无意义评价、47 内容无意义评价。

   模型输出不在白名单、JSON 解析失败或置信理由不足时，统一兜底为 `47`，并记录 `type_defaulted=true` 或等价字段。

6. **算力扣减与幂等按“成功生成可用申诉内容”计算。**

   申诉类型判断和申诉文本生成都属于大模型调用。建议在生成申诉内容成功后汇总 token usage 并扣减算力；如果类型判断失败但使用 47 兜底，仍只对实际成功返回 usage 的模型调用计费。扣费 requestNo 使用 `COMMENT_AUTO_COMPLAINT:{commentId}`，并以本地申诉成功/生成成功记录防止重复扣费。

7. **美团接口参数以官方文档或 SDK typed request 为准，业务层只依赖稳定语义。**

   本设计固定业务语义：按当前门店 token 提交评价申诉，核心输入包含评价 ID、门店 `ePoiId`、`reportTypeCode` 和申诉文本；如果官方接口要求图片、订单字段或其他字段，映射应从本地 `mt_comment` 全量字段或原始响应 JSON 中取值，并记录在接口索引文档。

## Risks / Trade-offs

- [Risk] 评分字段误判导致非差评被申诉 -> 使用现行有效评分字段判定差评，并在评分缺失或异常时跳过申诉。
- [Risk] 模型选择错误申诉类型 -> 使用白名单校验和 47 兜底，并记录模型理由与兜底标记。
- [Risk] 申诉文本含平台敏感表达 -> prompt 明确禁止风险表达，并复用现有敏感词检测/改写能力；无法生成合规文本时不提交美团。
- [Risk] 美团申诉接口字段与预期不一致 -> 实现时以官方文档/SDK typed request 为准，并在 `docs/美团接口调用索引.md` 写明最终字段。
- [Risk] 申诉增加 worker 耗时和失败重试压力 -> 使用独立异步任务、独立重试策略和状态字段，避免阻塞自动回复。
- [Risk] 重复任务导致重复申诉或重复扣费 -> 本地成功状态、唯一索引、任务 `bizKey` 和扣费 requestNo 多层幂等。

## Migration Plan

1. 新增数据库迁移：创建 `mt_comment_complaint`，必要时给 `mt_comment` 增加申诉快照字段；更新 `full_schema.sql`。
2. 新增或更新 prompt 资源文件，初始化 `COMMENT_REPORT_TYPE`、`COMMENT_REPORT_CONTENT` 的 `system_prompt_config` 数据。
3. 发布后保持差评处理/申诉开关默认沿用现有门店配置；未开启的门店不会提交申诉。
4. 先在 dry run 环境验证类型判断、文本生成、敏感词处理、入队和状态落库，再关闭 dry run 调用美团接口。
5. 回滚代码时保留新增表字段；已提交成功的申诉状态作为历史记录保留，不再由旧代码更新。

## Open Questions

- 美团申诉接口最终 SDK request 类名、必填字段、返回体字段需要在实现时从官方文档或 SDK 版本确认。
- 申诉是否需要单独的后台运营查询页，本次不做，但数据模型应保留后续展示所需字段。
