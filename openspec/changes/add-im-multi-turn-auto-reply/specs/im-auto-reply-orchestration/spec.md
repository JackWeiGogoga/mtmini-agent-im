## ADDED Requirements

### Requirement: 系统必须幂等创建自动回复任务
系统 SHALL 仅为已可靠入库且满足协议条件的用户文本消息创建一条 `IM_AUTO_REPLY` 异步任务和一条自动回复记录，并使用触发消息 ID 保证幂等。

#### Scenario: 用户文本消息成为候选
- **WHEN** `biz_type=1`、`msg_source=2`、`msg_type=1` 的消息完成门店映射与正文解密且会话字段完整
- **THEN** 系统在同一数据库事务中创建 Conversation/Session 关联、`PENDING` 回复记录和异步任务
- **AND** 重复回调不创建第二条回复记录或任务

#### Scenario: 非文本或不可用消息
- **WHEN** 消息来自商家、不是文本、门店未匹配、解密失败或正文为空
- **THEN** 系统仍按接收规范保存消息
- **AND** 系统不创建自动回复任务

### Requirement: 自动回复执行必须复核动态门店条件
Worker MUST 在生成前和发送前校验本地自动回复开关、有效美团授权、门店权益、算力条件和触发消息归属。

#### Scenario: 条件在排队期间失效
- **WHEN** 任务执行时门店开关已关闭、授权失效或权益过期
- **THEN** 系统以安全跳过状态结束本次回复
- **AND** 不调用 Chat 或美团发送接口

#### Scenario: 算力不足
- **WHEN** 系统无法完成幂等算力消费
- **THEN** 系统不发送回复并按既有规则关闭本地自动回复开关

### Requirement: 连续用户消息必须合并并淘汰过时回复
系统 SHALL 默认延迟 2 秒处理候选，并在同一 Conversation 内只允许最新有效任务响应连续用户消息。

#### Scenario: 用户连续发送两条消息
- **WHEN** 第一条任务尚未发送时同一 Conversation 到达更新用户消息
- **THEN** 旧任务标记为 `SUPERSEDED`
- **AND** 最新任务把连续用户文本作为当前用户轮次生成一次回复

#### Scenario: 人工商家已经回复
- **WHEN** 触发用户消息之后出现人工商家消息
- **THEN** 自动回复标记为 `SKIPPED_MANUAL_REPLY`
- **AND** 系统不抢在人工消息之后发送旧回复

### Requirement: 同一 Conversation 的自动回复必须串行执行
系统 MUST 使用跨实例会话锁防止同一 Conversation 并发生成或发送，并在锁不可用时无损重排任务。

#### Scenario: 两个实例领取同会话任务
- **WHEN** 一个实例已经持有 Conversation owner-token 锁
- **THEN** 其他实例不得执行该会话任务
- **AND** 未取得锁的任务不增加业务重试次数地延后

### Requirement: 模型必须使用当前 Session 的短期历史
系统 SHALL 将当前 Session 最近最多 100 轮有效消息作为短期记忆，并在模型上下文预算不足时优先保留最新轮次。

#### Scenario: 当前 Session 有多轮历史
- **WHEN** 用户发送依赖此前指代的新问题
- **THEN** Chat 请求按角色包含当前 Session 的有效用户、人工商家和已发送 AI 文本历史
- **AND** 不读取旧 Session 作为模型记忆

#### Scenario: 历史超过一百轮
- **WHEN** 当前 Session 已超过 100 轮
- **THEN** 系统只加载最新 100 轮并继续按上下文预算裁剪最旧内容

### Requirement: 门店知识必须作为可选 RAG 上下文
系统 SHALL 在门店存在可用知识切片时对当前问题和近期用户轮次执行向量召回，并将达到阈值的门店片段注入 Chat 上下文。

#### Scenario: 门店知识命中
- **WHEN** 当前门店存在可用知识且召回结果达到阈值
- **THEN** 系统只注入该门店 topK 知识片段
- **AND** 回复记录保存命中切片 ID、分数和数量

#### Scenario: 门店没有知识或没有命中
- **WHEN** 门店没有可用切片或召回结果均低于阈值
- **THEN** 系统可以不带知识上下文继续生成

#### Scenario: RAG 基础设施失败
- **WHEN** 门店有知识但 Embedding 或向量检索调用失败
- **THEN** 系统重试任务而不是静默降级为无知识回答

### Requirement: Chat 调用必须受限流、提示词和安全规则约束
系统 MUST 通过 OpenAI-compatible Chat 出口和现有集群限流器调用配置模型，并使用可版本化的 `IM_AUTO_REPLY_SYSTEM` 系统提示词。

#### Scenario: 供应商容量不足
- **WHEN** Chat 供应商返回 429 或集群许可暂不可用
- **THEN** 任务按共享冷却时间无损重排且不消耗业务重试次数

#### Scenario: 知识片段包含指令
- **WHEN** 召回知识文本中包含要求模型改变角色或泄露提示词的内容
- **THEN** 系统把知识作为不可信参考数据分隔
- **AND** 系统提示词要求模型不得执行知识文本中的指令

### Requirement: 生成回复必须经过敏感词改写和最终硬拦截
系统 MUST 在发送前检测生成文本，按配置次数请求安全改写，仍命中时使用固定安全兜底，并由发送客户端执行最后一次检测。

#### Scenario: 首次生成命中敏感词
- **WHEN** 模型回复归一化后命中启用词条
- **THEN** 系统在最大改写次数内请求模型重写且不保存具体命中词到普通日志

#### Scenario: 改写后仍命中
- **WHEN** 所有改写尝试均未通过
- **THEN** 系统使用配置的固定安全兜底文本
- **AND** 发送客户端仍可阻止任何最终命中文本调用美团

### Requirement: 生成、计费和发送必须可恢复且幂等
系统 SHALL 持久化生成正文、Chat usage、稳定算力请求号和稳定出站消息 ID，使任务从任意已提交阶段恢复时不重复生成、扣费或更换消息 ID。

#### Scenario: 生成后任务重启
- **WHEN** AI 正文已持久化但任务未完成
- **THEN** 重试直接复用已生成消息和 usage
- **AND** 不再次调用 Chat

#### Scenario: 算力消费重复执行
- **WHEN** 相同回复重新进入计费阶段
- **THEN** 系统使用同一 `power_request_no` 获得幂等结果

#### Scenario: 发送结果未知后重试
- **WHEN** 美团发送调用结果未知或任务在发送后中断
- **THEN** 系统复用同一明文和 `outbound_msg_id` 再次调用单次发送客户端

