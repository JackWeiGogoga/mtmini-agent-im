# 差评 IM 跟进上线检查清单

## 上线前

- 执行 `20260714_add_negative_comment_im_follow_up.sql` 和 `20260715_allow_conditional_comment_im_compensation.sql`，确认 IM 表、评价快照字段、条件式补偿提示词、菜单与权限初始化成功。
- 配置 `MT_IM_SECRET`，确认其 UTF-8 编码长度恰好为 16 字节；不得把值写入仓库、工单、日志或截图。
- 确认 `MT_COMMENT_IM_FALLBACK_MESSAGE` 包含 `{{commentContent}}`，并抽查模型失败时评价变量已渲染、超长内容已截断且整条消息不超过 150 个 Unicode 字符。
- 保持 `MT_COMMENT_IM_CREATE_GROUP_DRY_RUN=true` 和 `MT_COMMENT_IM_SEND_MESSAGE_DRY_RUN=true`。
- 确认 `COMMENT_AUTO_IM_FOLLOW_UP` 的 `max_retry_count=0`，并确认美团建群、发消息客户端没有重试器。

## 阶段 0：两个 dry run 均开启

- 选择测试门店产生一条新差评，确认自动回复成功后申诉与 IM 两个任务独立入队。
- 确认 IM 记录停留在 `GENERATED`，消息来源、兜底原因、token usage、算力和生成耗时可在“评价处理日志”查看。
- 确认算力 requestNo 为 `COMMENT_IM_FOLLOW_UP:{commentId}`，相同评价重复调度没有重复扣费。
- 确认 `create_group_attempted_at`、`group_id`、`send_attempted_at` 均为空，美团接口没有被调用。

## 阶段 1：仅关闭建群 dry run

- 小范围设置 `MT_COMMENT_IM_CREATE_GROUP_DRY_RUN=false`，继续保持 `MT_COMMENT_IM_SEND_MESSAGE_DRY_RUN=true`。
- 确认每条评价最多写入一次 `create_group_attempted_at`，成功后状态为 `GROUP_CREATED` 且 `group_id` 已落库。
- 确认任务重排期间复用已有群，不再次建群；`send_attempted_at` 仍为空。
- 对业务失败、超时、连接中断和未知结果，确认记录为 `FAILED/CREATE_GROUP`，不人工清除 attempt，不自动重试。

## 阶段 2：关闭发送 dry run

- 使用开发测试密钥验证已落库明文经 `AES/CBC/PKCS5Padding`、同 key/IV 加密后可正确解密中文和表情。
- 小范围设置 `MT_COMMENT_IM_SEND_MESSAGE_DRY_RUN=false`。
- 确认每条评价只有一个稳定的 `message_id` 和 10 位 `message_cts`，最多写入一次 `send_attempted_at`。
- 确认群内收到的内容与处理日志中的生成明文一致，成功后状态为 `SUCCESS`。
- 对业务失败、超时、连接中断和未知结果，确认记录为 `FAILED/SEND_MESSAGE`，不得重发。

## 安全与可观测性

- 抽查应用日志、错误摘要、后台列表与详情响应，不得出现 access token、`MT_IM_SECRET`、完整密文或完整 `push_content`。
- 后台仅允许有 `comment:processing-log:list/query` 权限且满足数据范围的用户查看；跨门店访问应返回无权限或不存在。
- 只统计 attempt、状态、群 ID、消息 ID、耗时和安全错误摘要；排查时避免复制消息明文到普通日志或群聊。

## 回滚

- 第一时间把两个 dry run 恢复为 `true`，先阻止新的真实建群和发送。
- 不删除 `mt_comment_im_follow_up` 记录，不清空 `create_group_attempted_at`、`send_attempted_at`、`group_id` 或 `message_id`。
- 保留 `max_retry_count=0`；不得通过重置任务或业务状态触发第二次外呼。
- 通过“评价处理日志”按失败阶段、门店和时间范围复盘，修复后仍按阶段 0 -> 阶段 1 -> 阶段 2 逐步恢复。
