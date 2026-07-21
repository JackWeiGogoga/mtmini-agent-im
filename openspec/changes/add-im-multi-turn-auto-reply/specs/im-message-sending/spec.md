## MODIFIED Requirements

### Requirement: 接收回调不得直接触发发送
发送客户端的存在 MUST NOT 使 `/imMsg` 回调线程直接调用美团发送；已提交的 `IM_AUTO_REPLY` 异步任务可以在复核门店和消息条件后调用发送客户端。

#### Scenario: 收到用户未回复消息
- **WHEN** `/imMsg` 保存 `biz_type=1`、`msg_source=2` 的合法文本候选
- **THEN** 回调只完成本地事务和响应
- **AND** 美团发送只能由后续异步 Worker 执行

#### Scenario: 自动回复任务发送消息
- **WHEN** Worker 已持久化生成正文、稳定出站消息 ID并通过开关、授权、权益、算力与敏感词校验
- **THEN** Worker 调用两方会话发送客户端一次
- **AND** 任务重试复用同一出站消息 ID和正文

## ADDED Requirements

### Requirement: 自动回复发送结果必须持久化
系统 SHALL 在外部调用前保存 `SENDING` 状态，并根据单次客户端结果更新 `SENT`、可重试失败或终态失败及安全响应摘要。

#### Scenario: 美团明确发送成功
- **WHEN** 发送客户端返回成功
- **THEN** 系统把 AI 统一消息标记为 `SENT` 并幂等更新 Conversation 计数与最后回复时间

#### Scenario: 美团业务失败或结果未知
- **WHEN** 客户端返回安全失败结果
- **THEN** 编排服务按错误类别重试或终止
- **AND** 不在日志中输出正文、密文、token 或完整上游响应

