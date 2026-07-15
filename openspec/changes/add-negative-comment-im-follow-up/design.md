## Context

当前 `COMMENT_AUTO_REPLY` 在美团商家回复成功并落库后，会按商品分和包装分的最低有效分判断评价类型；当评价为 `BAD` 且门店开启“差评处理/申诉”时，系统独立入队 `COMMENT_AUTO_COMPLAINT`。新需求要求在同一节点并行启动 IM 跟进，并在管理后台统一查看所有评价的回复、申诉和建群处理结果。

仓库已有数据库异步任务、按 `bizKey` 入队、dry run 无损重排、门店设置、门店 token、系统提示词后台、OpenAI-compatible 模型和算力幂等扣减。当前美团 SDK 已包含：

- `CreateGroupRequest/Response`，接口 `/wmoper/ng/im/createGroup`
- `SendImMsgWmoperRequest/PushContent`，接口 `/wmoper/ng/im/msg/send`

现有总控台的 `/api/system/master-console/comment-reply-logs` 只能按一个门店读取自动回复摘要，不能作为全量处理审计页。新页面需要以评价为中心聚合 `mt_comment`、`mt_comment_complaint` 和新增 IM 跟进记录。

## Goals / Non-Goals

**Goals:**

- 新差评自动回复成功后，并行执行自动申诉与 IM 跟进，任一失败不影响另一条链路和已成功回复。
- 复用现有自动回复主开关与“差评处理/申诉”开关，不增加门店侧开关。
- 生成允许“核实后补偿”等条件式售后表述、但不直接或无条件承诺具体利益的首条群聊消息；模型不可用或内容不合规时使用带评价变量的固定兜底模板。
- 创建群和发消息分别默认 dry run，并允许按阶段逐步开启。
- 每个美团 IM 外部接口只允许一次真实调用，失败或结果未知即记录终态，不自动重试。
- 按官方规则使用 16 字节 `imSecret` 完成 AES/CBC/PKCS5Padding 加密。
- 对 IM 消息生成幂等扣减算力，并保留生成与兜底审计信息。
- 新增独立的全量评价处理日志页面，供运营复盘和研发排障。

**Non-Goals:**

- 不补处理上线前已经完成自动回复的历史差评。
- 不接收或同步群聊后续消息，不新增 IM 回调。
- 不新增人工审核、编辑、重发、重试、补建群或导出能力。
- 不修改现有自动申诉接口、状态语义和重试策略。
- 不在 H5 展示 IM 状态，也不移除总控台现有单店回复日志。
- 不在数据库、接口响应、提示词或日志中保存/返回 `imSecret`。

## Decisions

### 1. IM 任务与申诉并列入队，只处理新触发差评

新增 `COMMENT_AUTO_IM_FOLLOW_UP`，payload 仅含 `shopId`、`commentId`，`bizKey` 使用 `comment-im-follow-up:{commentId}`。`COMMENT_AUTO_REPLY` 成功落库后，分别尝试入队申诉与 IM；两个入队异常单独记录，均不能改变 `auto_reply_status=SUCCESS`。

worker 再次读取评价、门店、设置、权益和 token，校验 payload 门店与评价归属一致、自动回复已成功且评价仍为 `BAD`。开关开启不会扫描历史差评；只有本变更上线后走到回复成功后置节点的新评价才会产生任务。

### 2. 复用现有差评处理开关

IM 与申诉共同受自动回复主开关和现有“差评处理/申诉”开关控制，不增加独立 IM 开关。任务执行前再次检查；若入队后开关关闭，则标记业务记录 `SKIPPED`，不调用模型或美团接口。申诉与 IM 继续同时独立执行。

### 3. 独立表保存阶段、尝试证据和审计字段

新增 `mt_comment_im_follow_up`，按 `shop_id + comment_id` 唯一，主要字段：

- 关联与总体状态：`shop_id`、`mt_comment_id`、`comment_id`、`status`、`failed_stage`、`error_code`、`error_message`。
- 生成：`message_content`、`message_source`（`MODEL`/`FALLBACK_STATIC`）、`fallback_reason`、provider/model、prompt key/version、token usage、`power_amount`、`generated_at`。
- 建群：`create_group_attempted_at`、`group_id`、`new_group`、`group_ctime`、`group_state`、`group_close_type`、脱敏响应摘要、`group_created_at`。
- 发送：唯一 `message_id`、`message_cts`、`send_attempted_at`、正文 hash、脱敏响应摘要、`sent_at`。
- 可观测：各阶段耗时、`create_time`、`update_time`。

状态至少包含 `PENDING`、`GENERATED`、`GROUP_CREATED`、`SUCCESS`、`FAILED`、`SKIPPED`；`failed_stage` 包含 `GENERATE`、`POWER`、`CREATE_GROUP`、`ENCRYPT`、`SEND_MESSAGE`。`mt_comment` 增加 `im_follow_up_status`、`im_group_id`、`im_message_at`、`im_follow_up_error` 快照，便于查询和排障。

### 4. 先准备消息与扣费，再创建群

顺序为：上下文校验 -> 创建/读取记录 -> 模型生成或固定兜底 -> 渠道合规检查 -> 幂等扣费 -> 标记 `GENERATED` -> 建群 -> 加密 -> 发消息 -> 标记 `SUCCESS`。先准备消息可避免生成问题留下空群。

正常模型输出记录 usage 并按现有规则扣费；固定兜底没有模型 usage，按 `fallback-power-amount` 扣费，默认 1000 算力并允许开发者配置调整。两者共用 `COMMENT_IM_FOLLOW_UP:{commentId}` 唯一 requestNo，同一评价最多扣一次。扣费失败属于本地 `POWER` 失败，不创建群。

### 5. 最终提示词与固定兜底均支持审计

新增 `COMMENT_IM_FOLLOW_UP_MESSAGE` 系统提示词，变量为 `commentContent`、`addComment`，复用现有 `/system/prompts` 页面编辑、启停和恢复默认。默认模板：

```text
你是美团外卖商家的资深售后客服，正在评价匿名群聊中首次联系顾客。

顾客评价：{{commentContent}}
顾客追评：{{addComment}}

请只输出一条要发送给顾客的中文消息，并严格满足：
1. 先简要、准确概括顾客反馈，再真诚致歉并表达核实和解决意愿；不得编造评价之外的事实。
2. 礼貌请顾客提供对应订单信息和方便联系的手机号码，不需要补充手机号用途说明。
3. 可以使用“核实后给您补偿处理”等明确以核实为前提的条件式补偿表述；不得直接或无条件承诺补偿，也不得承诺红包、优惠券、退款、赔付、补发或其他具体利益，不得引导删评、改评、好评。
4. 不得引导添加微信、QQ、扫码、点击外链或前往其他平台，不得索取验证码、身份证号、银行卡号、支付密码等高风险信息。
5. 不超过 150 个 Unicode 字符，单段纯文本，使用 1 至 2 个自然、克制的表情符号；不输出标题、Markdown、JSON、解释或变量占位符。
```

固定兜底模板由代码默认值和开发者配置共同提供，支持 `commentContent` 变量。评价文字会先清理空白、表情和可能干扰格式校验的符号，并按整条消息 150 个 Unicode 字符的上限截断；缺少文字评价时使用“未填写文字评价”。方括号内的变量值视为顾客原文引用，不作为商家补偿、导流或评价诱导承诺判定；模板固定部分仍执行完整渠道合规检查。默认值为：

```text
我是售后处理专员：亲~收到了您的一条差评[{{commentContent}}]，抱歉给您带来不好的体验，能提供一下订单号和联系号码吗？方便我核实后给您补偿处理☺️
```

模型调用失败、超时、空输出、格式失败或渠道合规失败时，不重试模型，记录 `message_source=FALLBACK_STATIC` 和原因，改用兜底文案重新执行同一套确定性格式与合规检查。只有兜底文案也不合规或扣费失败时才停止后续处理。

### 6. 使用匿名群聊专用合规规则

不能复用公开评价回复中将“手机号”一律拦截的规则。新增 IM 渠道校验：允许礼貌索取订单信息和手机号；允许“核实后补偿”“确认后再补偿”等明确带核实前提、且不指定具体利益的表述；仍禁止直接或无条件承诺补偿，并禁止红包、优惠券、退款、赔付、补发等具体利益，禁止删评/改评/好评引导，禁止微信、QQ、二维码、外链、站外平台、私人联系方式引导，禁止高风险身份和支付信息，禁止攻击、违法及平台敏感内容。

合规错误只记录类别和安全摘要。生成明文保存在业务表并可在有权限的处理日志详情中展示；普通应用日志不得输出完整明文。

### 7. 创建群和发消息各只做一次真实尝试

建群前在独立事务中原子写入 `create_group_attempted_at`，只有从空值更新成功的 worker 才能调用 `CreateGroupRequest`。真实响应成功且 `group_id` 非空时持久化群信息；业务失败、异常、超时、连接中断或成功响应缺少 `group_id` 均标记 `FAILED/CREATE_GROUP`。任何已有 attempt 且无 `group_id` 的记录都不得再次调用建群接口。

发送前同样原子写入 `send_attempted_at`，只有首次成功占位的 worker 才能调用 `SendImMsgWmoperRequest`。成功标记 `SUCCESS`；所有失败或未知结果标记 `FAILED/SEND_MESSAGE`。已有 send attempt 且未成功的记录不得再次发送。

任务策略配置 `maxRetryCount=0`。attempt 标记仍是业务层最终防线，覆盖进程在外部调用后、记录响应前崩溃，以及任务被重复投递、超时回收或并发领取的情况。这里接受未知结果可能实际成功但本地显示失败的取舍，以满足“不重试、避免重复群/消息”的明确要求。

### 8. typed SDK 参数固定，消息标识在发送前持久化

建群使用 `CreateGroupRequest`，固定 `groupType=1`，`commentId` 为美团评价 ID。发送使用 `SendImMsgWmoperRequest/PushContent`，固定：

- `bizType=4`
- `msgType=0`
- `msgSource=1`
- `sceneTag=-1`
- `commentId` 为当前评价 ID
- `groupId` 为已持久化群 ID
- `msgId` 为数据库唯一的 long
- `cts` 为首次准备发送时持久化的 10 位秒级时间戳

`msg_id` 和 `cts` 在发送 attempt 之前生成并落库。虽然本版本不自动重试，稳定字段仍用于并发防重、问题核对和未来人工恢复设计。

### 9. `imSecret` 采用已确认的 16 字节规则

开发者级配置 `mt.im.secret=${MT_IM_SECRET:}`。其值必须恰好编码为 16 个 UTF-8 字节，这 16 字节同时作为 AES key 和 CBC IV；算法为 `AES/CBC/PKCS5Padding`，字符集 UTF-8，输出标准 Base64。权限已开通，不新增权限探测流程。

缺失、不是 16 字节或加密失败时标记 `FAILED/ENCRYPT`，不占用发送 attempt，也不调用发送接口。数据库和接口不保存/返回密钥及完整密文，日志不输出 access token、密钥、完整明文、完整 `msg_content` 或完整 `push_content`。用固定测试向量锁定字符集、padding 和 Base64 行为。

### 10. 两阶段 dry run 默认开启且不占真实 attempt

配置：

- `mt.comment.im-follow-up.create-group-dry-run=true`
- `mt.comment.im-follow-up.send-message-dry-run=true`

所有 profile 默认均为 `true`，环境变量可逐项关闭。建群 dry run 可完成生成和扣费，但不写 `create_group_attempted_at`、不伪造 `group_id`，通过 `MtAsyncTaskDryRunException` 无损重排。关闭后执行唯一一次真实建群。

发送 dry run 在真实建群成功后保留 `GROUP_CREATED`，不写 `send_attempted_at`、不标记成功，并无损重排。关闭后执行唯一一次真实发送。推荐逐步启用顺序：两者 dry run -> 仅真实建群 -> 真实建群和发送。

### 11. 新增独立“评价处理日志”读模型和管理页面

新增只读接口：

- `GET /api/system/comment-processing-logs`：以 `mt_comment` 为主表分页，保证未触发申诉/IM 的好评、中评和差评也出现。
- `GET /api/system/comment-processing-logs/{commentId}`：按本地评价主键返回完整处理详情，聚合自动回复、`mt_comment_complaint` 和 `mt_comment_im_follow_up`。

列表支持评价时间范围、门店 ID/名称/ePoiId、评价 ID/关键词、情感类型、自动回复状态、申诉状态和 IM 状态筛选，默认按评价时间倒序；`pageSize` 有服务端上限。列表返回评价摘要和三条处理链路的状态/关键时间，详情返回：

- 评价：门店、评价/追评、评分、图片、情感和评价时间。
- 自动回复：状态、内容、来源、模型、prompt 版本、token/算力、时间、耗时和错误。
- 差评申诉：是否适用、状态、申诉类型/内容、算力、时间、耗时和错误。
- 群聊跟进：是否适用、总体状态、消息来源/明文、兜底原因、模型/prompt、token/算力、建群 attempt 与 `group_id`、发送 attempt 与 `msg_id`、各阶段时间、失败阶段和错误摘要。

新页面路由建议 `/comment-processing-logs`，权限为 `comment:processing-log:list` 与 `comment:processing-log:query`，通过菜单迁移授予既有管理员角色。页面使用筛选区、分页表格和详情抽屉，分区展示“评价内容 / 自动回复 / 差评申诉 / 群聊跟进”。不适用的链路明确显示“未触发/不适用”，不把缺行误判为失败。页面只读，不提供重试按钮。

后端查询必须遵守现有管理后台数据范围；不得返回 access token、`imSecret`、完整密文或完整外部请求。为 `mt_comment` 的评价时间/门店/状态组合，以及申诉和 IM 的 `comment_id/status` 增加必要索引，避免逐行 N+1 查询。

### 12. 文档与可观测性

`docs/美团接口调用索引.md` 登记两个接口的官方地址、SDK request/response、固定参数、dry run、AES、落库、单次真实尝试和门店隔离。业务日志记录 `shopId`、`commentId`、记录 ID、阶段、状态、耗时、安全错误摘要和可关联的 `groupId/messageId`，但不记录秘密或完整消息载荷。

## Risks / Trade-offs

- 外部调用超时后可能已在美团成功，但本地会终态失败；不重试可避免重复群和重复消息，后台日志提供人工核对证据。
- dry run 阶段已经生成消息并扣费；这是对“消息生成扣费”的直接实现，关闭 dry run 后复用已生成结果，不重复扣费。
- 固定兜底弱化了个性化，但可保证模型故障时继续售后触达；兜底仍经过格式和渠道合规校验。
- 复用同一个差评开关无法分别控制申诉与 IM，第一版遵循已确认业务口径。
- 全量日志聚合字段较多，列表采用摘要、详情按需查询，并通过索引和批量 join 控制性能。
- 页面展示生成明文便于复盘但属于敏感运营数据，因此使用独立权限且禁止出现在普通日志和外部请求摘要中。

## Migration Plan

1. 新增 IM 跟进表、`mt_comment` 快照字段、查询索引、提示词和菜单/权限迁移；同步 `full_schema.sql`。
2. 发布任务、生成/兜底、计费、AES、客户端和日志查询代码；两个 IM dry run 保持默认 `true`。
3. 验证新差评只生成/扣费/落库而不外呼，并验证后台可查看所有评价及三条处理链路。
4. 运营编辑提示词后，先关闭建群 dry run 验证 `group_id`；确认后再关闭发送 dry run。
5. 回滚代码时保留新增表、字段和提示词；已创建群和已发送消息不可撤回，attempt 标记不得清除。

## Open Questions

无。当前需求已确认创建群和发送均不重试、`imSecret` 为 16 字节同 key/IV、复用现有开关、仅处理新差评、使用带评价变量的固定兜底、允许核实后条件式补偿但禁止直接或具体利益承诺、消息生成扣费、两个接口默认 dry run，并新增全量评价处理日志页面。
