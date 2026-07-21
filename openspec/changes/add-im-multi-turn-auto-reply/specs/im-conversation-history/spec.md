## ADDED Requirements

### Requirement: 系统必须以门店和顾客建立唯一 Conversation
系统 SHALL 使用 `(shop_id, open_user_id)` 唯一标识门店与顾客的长期 Conversation，并聚合全部历史 Session 的消息数量和首末活动时间。

#### Scenario: 同一顾客再次联系门店
- **WHEN** 相同 `shop_id` 与 `open_user_id` 收到新消息
- **THEN** 系统复用同一 Conversation
- **AND** 会话列表中仍只出现一条顾客会话记录

#### Scenario: 不同门店收到相同顾客标识
- **WHEN** 两个门店收到相同 `open_user_id`
- **THEN** 系统创建彼此隔离的 Conversation

### Requirement: 系统必须使用空闲时间切分短期 Session
系统 SHALL 在 Conversation 当前 Session 超过配置的 30 分钟未活动时关闭旧 Session 并创建递增编号的新 Session，且不得以 `order_id` 变化强制切分。

#### Scenario: 空闲后再次发送消息
- **WHEN** 新消息距离当前 Session 最后活动超过空闲阈值
- **THEN** 系统将新消息关联到新的 Session
- **AND** 旧 Session 历史继续保留

#### Scenario: 同一聊天涉及不同订单
- **WHEN** 活跃 Session 内消息的非零 `order_id` 发生变化
- **THEN** 系统保留消息各自的订单字段
- **AND** 不仅因订单变化创建新 Session

### Requirement: 用户、人工商家和 AI 消息必须进入统一时间线
系统 SHALL 在 `mt_im_message` 中保存 Conversation、Session、发送角色、记录来源、回复关联、投递状态和事件时间，并允许内部 AI 消息没有回调 ID。

#### Scenario: AI 回复生成并发送
- **WHEN** 自动回复生成正文并调用发送接口
- **THEN** 系统创建一条 `sender_role=AI` 的统一消息记录并更新投递状态
- **AND** 正文不在自动回复表重复保存

#### Scenario: 美团回推已发送 AI 消息
- **WHEN** 回调的 `(app_id, platform_msg_id)` 与内部 AI 消息相同
- **THEN** 系统补全同一消息的回调字段
- **AND** 不创建第二条时间线消息或重复增加计数

### Requirement: Conversation 聚合必须可用于稳定会话列表
系统 SHALL 保存消息总数、用户消息数、AI 回复数、人工商家消息数、Session 数、最后消息和最后 AI 回复时间，并只在消息首次成为有效事实时递增。

#### Scenario: 会话列表排序
- **WHEN** 管理员查询会话列表
- **THEN** 系统按 `last_message_at DESC, id DESC` 稳定分页
- **AND** 返回全部历史 Session 的消息数量与最新 AI 回复时间

#### Scenario: 任务重试发送同一 AI 消息
- **WHEN** 已存在的 AI 消息重复进入发送成功处理
- **THEN** Conversation 消息数和 AI 回复数不重复增加

### Requirement: 管理界面必须跨 Session 游标加载完整历史
系统 SHALL 按 Conversation 使用 `(event_time,id)` 复合游标返回消息，首批返回最新消息，后续游标可持续加载更早 Session 直到最早历史。

#### Scenario: 打开会话抽屉
- **WHEN** 管理员点击一条 Conversation
- **THEN** 系统返回最新一批消息、总消息数、总 Session 数、`hasMore` 和下一旧消息游标
- **AND** 前端定位到最新消息所在底部

#### Scenario: 向上滚动加载历史 Session
- **WHEN** 用户滚动到抽屉顶部并提交 `beforeEventTime` 与 `beforeId`
- **THEN** 系统返回严格早于游标的下一批消息
- **AND** 查询不限制为当前 Session

#### Scenario: 分页跨越 Session 边界
- **WHEN** 相邻返回消息属于不同 `session_id`
- **THEN** 返回数据包含 Session 编号、开始时间和边界原因
- **AND** 前端在时间线中插入新的对话阶段分隔线并保持滚动锚点

### Requirement: 历史查看不得改变 AI 短期记忆边界
系统 MUST 将管理历史与模型记忆分开；管理界面可以读取全部 Session，Chat 只能读取当前 Session 最近 100 轮。

#### Scenario: 管理员加载昨日 Session
- **WHEN** 当前 Conversation 存在昨日和今日两个 Session
- **THEN** 管理员可以滚动查看两个 Session
- **AND** 今日自动回复不得把昨日 Session 加入模型上下文

