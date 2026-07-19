## MODIFIED Requirements

### Requirement: 消息接收不得执行自动回复外部业务
回调线程和入库服务 MUST NOT 调用模型、算力扣减或消息发送接口；对于满足条件的候选消息，系统 SHALL 在消息可靠入库的同一数据库事务中创建本地会话事实、自动回复记录和异步任务。

#### Scenario: 收到自动回复候选消息
- **WHEN** 收到门店匹配、解密成功且会话字段完整的用户文本消息
- **THEN** 系统完成验签、解析、统一消息与 Conversation/Session 入库，并幂等创建 `IM_AUTO_REPLY` 任务
- **AND** 回调线程不调用 Embedding、Chat、算力或两方会话发送接口

#### Scenario: 本地任务创建失败
- **WHEN** 新消息已经准备入库但同事务内无法创建必要的回复事实或任务且无法确认幂等存在
- **THEN** 整个事务回滚并返回非 0，使美团可以重试

#### Scenario: 重复回调
- **WHEN** 消息、Conversation 关联和自动回复任务已经存在
- **THEN** 系统按幂等成功返回且不重复创建任务

