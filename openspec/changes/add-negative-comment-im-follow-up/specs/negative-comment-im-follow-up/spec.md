## ADDED Requirements

### Requirement: 差评 IM 跟进触发与任务隔离

系统 SHALL 在新评价自动回复成功、评价类型为 `BAD`、当前门店自动回复主开关和“差评处理/申诉”开关均开启、授权与权益有效时，独立入队 `COMMENT_AUTO_IM_FOLLOW_UP`，且该任务 MUST 与 `COMMENT_AUTO_COMPLAINT` 互不依赖。

#### Scenario: 差评回复成功后同时触发两个后置任务

- **WHEN** `COMMENT_AUTO_REPLY` 成功处理一条新差评且门店满足执行条件
- **THEN** 系统 SHALL 分别入队 `COMMENT_AUTO_COMPLAINT` 和 `COMMENT_AUTO_IM_FOLLOW_UP`

#### Scenario: 非差评不触发 IM 跟进

- **WHEN** 自动回复成功的是好评、中评或无法判定类型的评价
- **THEN** 系统 MUST NOT 入队 `COMMENT_AUTO_IM_FOLLOW_UP`

#### Scenario: 复用现有差评处理开关

- **WHEN** 门店“差评处理/申诉”开关关闭
- **THEN** 系统 MUST NOT 调用模型、创建群或发送群消息，且 MUST NOT 要求独立 IM 开关

#### Scenario: 两个后置任务互不阻塞

- **WHEN** IM 入队或执行失败，或者自动申诉入队或执行失败
- **THEN** 系统 SHALL 继续独立处理另一条链路，并 MUST 保持已成功的 `auto_reply_status=SUCCESS`

#### Scenario: 不补处理历史差评

- **WHEN** 变更上线或门店开启差评处理开关
- **THEN** 系统 MUST NOT 扫描并自动补建上线前已经完成回复的差评 IM 任务

### Requirement: IM 任务上下文校验与门店隔离

系统 MUST 在执行任务时重新校验评价归属、自动回复状态、差评类型、操作开关、门店权益和当前门店 access token，并 MUST 保证所有模型、群和消息数据按 `shop_id` 隔离。

#### Scenario: 任务门店与评价一致

- **WHEN** payload `shopId` 与评价 `shop_id` 一致且其他条件有效
- **THEN** 系统 SHALL 使用该门店的设置、权益和 token 继续处理

#### Scenario: 任务门店与评价不一致

- **WHEN** payload `shopId` 与评价 `shop_id` 不一致
- **THEN** 系统 MUST 拒绝执行，且 MUST NOT 调用模型或美团 IM 接口

#### Scenario: 回复状态或执行条件失效

- **WHEN** 自动回复未成功、评价不再是 `BAD`、权益失效或当前门店 token 不可用
- **THEN** 系统 SHALL 记录跳过或失败原因，且 MUST NOT 使用其他门店 token 或调用美团 IM 接口

#### Scenario: 入队后关闭开关

- **WHEN** 任务入队后自动回复主开关或“差评处理/申诉”开关被关闭
- **THEN** 系统 SHALL 将 IM 业务记录标记为 `SKIPPED`，且 MUST NOT 调用模型或美团接口

### Requirement: 可配置的群聊消息生成与固定兜底

系统 SHALL 通过 `COMMENT_IM_FOLLOW_UP_MESSAGE` 基于 `commentContent` 和 `addComment` 生成群聊首条消息，并 SHALL 在模型或输出不可用时使用固定兜底文案继续处理。

#### Scenario: 使用后台启用的提示词

- **WHEN** `system_prompt_config` 存在启用且非空的 `COMMENT_IM_FOLLOW_UP_MESSAGE`
- **THEN** 系统 SHALL 使用该版本渲染评价和追评并调用模型

#### Scenario: 提示词配置缺失

- **WHEN** 数据库提示词停用、缺失或内容为空
- **THEN** 系统 SHALL 回退到资源文件或代码默认模板

#### Scenario: 运营配置提示词

- **WHEN** 有权限的后台用户在现有“系统提示词”页面管理 `COMMENT_IM_FOLLOW_UP_MESSAGE`
- **THEN** 系统 SHALL 支持查看变量说明、编辑、启停和恢复默认，并 SHALL 让后续新生成消息使用最新有效版本

#### Scenario: 正常生成消息

- **WHEN** 模型返回有效内容
- **THEN** 消息 SHALL 准确简述顾客反馈、表达歉意和解决意愿，并 SHALL 礼貌请顾客提供对应订单信息和方便联系的手机号码

#### Scenario: 允许核实后条件式补偿

- **WHEN** 模型消息或固定兜底消息使用“核实后补偿”“确认后再补偿”或等价表述，且未指定红包、优惠券、退款、赔付、补发等具体利益
- **THEN** 系统 SHALL 允许该消息通过补偿类合规检查

#### Scenario: 禁止直接补偿和具体利益承诺

- **WHEN** 消息直接或无条件承诺补偿，或承诺红包、优惠券、退款、赔付、补发或其他具体利益
- **THEN** 系统 MUST 拒绝该消息，且 MUST NOT 引导删评、改评或好评

#### Scenario: 消息格式

- **WHEN** 系统完成消息清理
- **THEN** 消息 MUST 为不超过 150 个 Unicode 字符的单段纯文本，SHALL 使用 1 至 2 个自然表情符号，且 MUST NOT 包含 Markdown、JSON、解释或未替换变量

#### Scenario: 使用固定兜底

- **WHEN** 模型调用失败、超时、返回空内容，或模型输出未通过格式/合规检查
- **THEN** 系统 MUST NOT 重试模型，SHALL 使用固定模板“我是售后处理专员：亲~收到了您的一条差评[{{commentContent}}]，抱歉给您带来不好的体验，能提供一下订单号和联系号码吗？方便我核实后给您补偿处理☺️”渲染评价文字，并 SHALL 记录来源 `FALLBACK_STATIC` 和兜底原因

#### Scenario: 兜底评价变量超长或缺失

- **WHEN** `commentContent` 使渲染后的兜底消息超过 150 个 Unicode 字符，或评价没有文字内容
- **THEN** 系统 SHALL 在保留固定前后文的前提下截断评价变量并标示省略，或使用“未填写文字评价”，最终消息 MUST 继续满足格式上限且 MUST NOT 留下变量占位符

#### Scenario: 评价原文包含渠道敏感词

- **WHEN** `commentContent` 原文包含退款、补偿、微信或其他渠道敏感词，且这些内容仅出现在固定模板的方括号引用区
- **THEN** 系统 SHALL 将其视为顾客原文而不是商家承诺，并 SHALL 继续对模板固定部分执行完整渠道合规检查

#### Scenario: 兜底也不合规

- **WHEN** 当前配置的固定兜底文案未通过确定性格式或合规检查
- **THEN** 系统 SHALL 标记 `GENERATE` 终态失败，且 MUST NOT 创建群或发送消息

### Requirement: IM 消息生成幂等计费

系统 SHALL 对 IM 消息生成扣减算力，并 MUST 以评价级唯一 requestNo 保证同一评价最多扣费一次。

#### Scenario: 模型生成成功计费

- **WHEN** 模型生成的消息通过校验
- **THEN** 系统 SHALL 按模型实际 usage 计算并扣减算力，记录 token usage 和 `power_amount`

#### Scenario: 固定兜底计费

- **WHEN** 系统因模型或输出失败使用固定兜底
- **THEN** 系统 SHALL 按默认 1000、可由开发者配置调整的兜底算力扣费，并 SHALL 记录消息来源和 `power_amount`

#### Scenario: 防止重复扣费

- **WHEN** 同一评价的任务被重复投递、并发领取或 dry run 重排
- **THEN** 系统 MUST 复用 `COMMENT_IM_FOLLOW_UP:{commentId}` 或等价唯一 requestNo，且 MUST NOT 重复扣费

#### Scenario: 扣费失败

- **WHEN** 算力不足或扣费持久化失败
- **THEN** 系统 SHALL 标记 `POWER` 阶段失败，且 MUST NOT 创建群或发送消息

### Requirement: 评价匿名群聊专用合规检查

系统 SHALL 使用评价匿名群聊专用规则校验模型消息和固定兜底；规则 SHALL 允许礼貌索取订单信息和手机号以及明确以核实为前提的条件式补偿，同时 MUST 阻止直接补偿或具体利益承诺、评价诱导、站外导流和高风险个人信息索取。

#### Scenario: 允许索取手机号

- **WHEN** 消息仅礼貌请顾客提供订单信息和方便联系的手机号
- **THEN** 合规检查 MUST NOT 仅因“手机号”或“手机号码”而拦截

#### Scenario: 允许条件式补偿

- **WHEN** 消息仅表达“核实后补偿”“确认后再补偿”或等价的条件式补偿，且未承诺具体利益
- **THEN** 合规检查 MUST NOT 仅因“补偿”二字拦截

#### Scenario: 阻止直接补偿、具体利益和评价诱导

- **WHEN** 消息直接或无条件承诺补偿，或提及红包、优惠券、退款、赔付、补发、删评、改评、好评或类似表达
- **THEN** 系统 MUST 拒绝该消息并记录合规类别

#### Scenario: 阻止站外导流

- **WHEN** 消息要求添加微信、QQ、扫码、点击外链、前往其他平台或联系私人号码
- **THEN** 系统 MUST 拒绝该消息并记录合规类别

#### Scenario: 阻止高风险信息与攻击内容

- **WHEN** 消息索取验证码、身份证号、银行卡号、支付密码，或包含辱骂、威胁、歧视、违法与平台攻击内容
- **THEN** 系统 MUST 拒绝该消息并记录合规类别

### Requirement: IM 跟进状态与单次尝试证据持久化

系统 SHALL 按 `shop_id + comment_id` 唯一保存 IM 跟进记录、生成审计、算力、群信息、稳定消息标识、阶段状态、attempt 时间、错误和耗时，并 SHALL 通过 attempt 标记防止任何真实外部调用被重复执行。

#### Scenario: 首次创建记录

- **WHEN** 系统首次执行某条新差评的 IM 任务
- **THEN** 系统 SHALL 创建唯一记录并设置 `PENDING`

#### Scenario: 并发创建记录

- **WHEN** 多个 worker 并发处理同一 `shop_id + comment_id`
- **THEN** 系统 MUST 通过唯一索引和重新查询收敛到同一记录

#### Scenario: 生成完成

- **WHEN** 模型消息或兜底消息通过校验且扣费成功
- **THEN** 系统 SHALL 保存消息来源、生成审计和算力并标记 `GENERATED`

#### Scenario: 分配消息标识

- **WHEN** 系统首次准备发送消息
- **THEN** 系统 SHALL 在外部调用前持久化数据库唯一 long `msg_id` 和 10 位秒级 `cts`

#### Scenario: 已成功或已终态失败

- **WHEN** 记录已为 `SUCCESS`、`FAILED` 或 `SKIPPED`
- **THEN** 系统 MUST 跳过所有已禁止的后续动作，且 MUST NOT 自动重置 attempt 标记

#### Scenario: 同步评价快照

- **WHEN** IM 跟进进入生成、建群、成功、失败或跳过状态
- **THEN** 系统 SHALL 更新 `mt_comment` 的 IM 快照，且 MUST NOT 修改自动回复或申诉状态

### Requirement: 创建美团评价联系群且不重试

系统 SHALL 使用当前门店 token 和 `CreateGroupRequest` 调用 `https://api-open-cater.meituan.com/wmoper/ng/im/createGroup`，固定 `group_type=1` 并传入当前 `comment_id`；该接口每条评价最多真实调用一次。

#### Scenario: 首次占用建群尝试

- **WHEN** 合规消息已生成、记录无 `group_id` 且 `create_group_attempted_at` 为空
- **THEN** 系统 SHALL 原子写入 `create_group_attempted_at`，并 SHALL 仅允许写入成功的 worker 调用创建群接口

#### Scenario: 创建群成功

- **WHEN** 美团返回成功且 `data.group_id` 非空
- **THEN** 系统 SHALL 保存 `group_id`、`new_group`、`ctime`、`state`、`close_type` 和脱敏响应摘要并标记 `GROUP_CREATED`

#### Scenario: 创建群失败或结果未知

- **WHEN** 美团返回失败、成功响应缺少 `group_id`、SDK 异常、超时、连接中断或结果未知
- **THEN** 系统 SHALL 保存安全错误摘要并标记 `FAILED/CREATE_GROUP`，且 MUST NOT 自动重试创建群

#### Scenario: 重复任务遇到已有建群尝试

- **WHEN** 记录已有 `create_group_attempted_at` 但没有 `group_id`
- **THEN** 系统 MUST NOT 再次调用创建群接口

### Requirement: AES 加密与群消息发送且不重试

系统 SHALL 使用开发者配置中的 `imSecret` 加密消息，并使用 `SendImMsgWmoperRequest` 调用 `https://api-open-cater.meituan.com/wmoper/ng/im/msg/send`；该接口每条评价最多真实调用一次。

#### Scenario: 按已确认规则加密

- **WHEN** `imSecret` 的 UTF-8 编码恰好为 16 字节
- **THEN** 系统 SHALL 将相同 16 字节同时作为 AES key 和 IV，使用 `AES/CBC/PKCS5Padding` 加密 UTF-8 正文，并将标准 Base64 结果作为 `msg_content`

#### Scenario: 密钥缺失或非法

- **WHEN** `imSecret` 缺失、UTF-8 编码不是 16 字节或无法初始化加密器
- **THEN** 系统 SHALL 标记 `FAILED/ENCRYPT`，MUST NOT 占用发送 attempt 或调用发送接口，且 MUST NOT 输出密钥

#### Scenario: 构造匿名群聊消息

- **WHEN** 已有有效 `group_id`、`msg_id`、`cts` 和密文
- **THEN** 系统 SHALL 设置 `biz_type=4`，并在 `push_content` 设置 `msg_type=0`、`msg_source=1`、`scene_tag=-1`、`msg_id`、10 位 `cts`、`comment_id`、`group_id` 和 `msg_content`

#### Scenario: 首次占用发送尝试

- **WHEN** 记录已 `GROUP_CREATED` 且 `send_attempted_at` 为空
- **THEN** 系统 SHALL 原子写入 `send_attempted_at`，并 SHALL 仅允许写入成功的 worker 调用发送接口

#### Scenario: 发送成功

- **WHEN** 美团发送接口返回成功
- **THEN** 系统 SHALL 保存发送时间和脱敏响应摘要并标记 `SUCCESS`

#### Scenario: 发送失败或结果未知

- **WHEN** 美团返回失败、SDK 异常、超时、连接中断或结果未知
- **THEN** 系统 SHALL 保存安全错误摘要并标记 `FAILED/SEND_MESSAGE`，且 MUST NOT 自动重试发送

#### Scenario: 重复任务遇到已有发送尝试

- **WHEN** 记录已有 `send_attempted_at` 但状态不是 `SUCCESS`
- **THEN** 系统 MUST NOT 再次调用发送接口

### Requirement: 建群和发送分别默认 dry run

系统 SHALL 为创建群和发送消息提供独立 dry run 配置，且两者默认值 MUST 为 `true`；dry run MUST 禁止对应外呼、不得伪造成功、不得写入真实 attempt 标记，并 SHALL 无损重排任务。

#### Scenario: 默认配置保护

- **WHEN** 任一运行 profile 未显式覆盖 IM dry run 配置
- **THEN** `create-group-dry-run` 和 `send-message-dry-run` SHALL 均为 `true`

#### Scenario: 创建群 dry run

- **WHEN** `create-group-dry-run=true` 且消息已生成
- **THEN** 系统 MUST NOT 调用创建群、MUST NOT 写 `create_group_attempted_at` 或伪造 `group_id`，并 SHALL 保持 `GENERATED` 后无损重排

#### Scenario: 发送 dry run

- **WHEN** 已真实建群且 `send-message-dry-run=true`
- **THEN** 系统 MUST NOT 调用发送、MUST NOT 写 `send_attempted_at` 或标记成功，并 SHALL 保持 `GROUP_CREATED` 后无损重排

#### Scenario: 逐步关闭 dry run

- **WHEN** 运营先关闭建群 dry run、随后关闭发送 dry run
- **THEN** 系统 SHALL 分别执行各自唯一一次真实尝试，并 MUST 复用已生成消息、已扣算力、`group_id`、`msg_id` 和 `cts`

### Requirement: IM 可观测性与接口索引

系统 SHALL 记录 IM 跟进关键阶段、attempt、状态、耗时和安全错误摘要，并 MUST 更新美团接口调用索引。

#### Scenario: 记录阶段结果

- **WHEN** IM 跟进执行生成、兜底、扣费、建群、加密、发送、dry run、跳过、失败或成功
- **THEN** 系统 SHALL 记录 `shopId`、`commentId`、业务记录 ID、阶段、状态、耗时和安全错误摘要

#### Scenario: 禁止泄露敏感信息

- **WHEN** 系统记录日志、错误、数据库请求摘要或返回后台接口
- **THEN** 系统 MUST NOT 输出 access token、`imSecret`、完整 `msg_content`、完整 `push_content` 或其他完整外部请求载荷

#### Scenario: 更新美团接口调用索引

- **WHEN** 实现创建群和发送消息调用
- **THEN** `docs/美团接口调用索引.md` MUST 记录官方地址、SDK 类、固定参数、默认 dry run、AES 规则、落库字段、单次真实尝试和门店隔离规则
