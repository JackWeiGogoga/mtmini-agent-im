## ADDED Requirements

### Requirement: 总控台必须提供门店范围内的回复日志
管理后台总控台 SHALL 为可见门店提供“回复日志”入口，并在抽屉中分页展示触发用户消息、AI 回复、状态、时间、RAG、算力和安全错误摘要。

#### Scenario: 查看可见门店回复日志
- **WHEN** 后台用户点击总控台某门店的回复日志
- **THEN** 系统只返回该门店的自动回复记录
- **AND** 一行同时展示用户消息与对应 AI 回复

#### Scenario: 修改 shopId 越权访问
- **WHEN** 用户请求不在当前数据范围内的门店日志
- **THEN** 系统返回 403 且不泄露消息存在性和正文

### Requirement: H5 必须提供当前门店日志 Tab
H5 SHALL 在底部导航新增“日志”Tab，以移动端卡片和分页方式展示当前登录门店的用户消息、AI 回复、状态和时间。

#### Scenario: 商家打开日志 Tab
- **WHEN** 已登录商家进入 H5 日志页
- **THEN** 系统只返回当前门店回复历史
- **AND** 页面支持下拉刷新和上拉加载更多

#### Scenario: 回复未实际发送
- **WHEN** 自动回复处于失败、跳过或敏感词拦截状态
- **THEN** 页面显示明确状态标签
- **AND** 不把未发送内容表现为顾客已经收到的正常气泡

### Requirement: 管理员必须拥有独立的自动回复记录页面
管理后台 SHALL 提供受 `system:im-auto-reply:list` 权限保护的页面，并包含“消息列表”和“会话列表”两个 Tab。

#### Scenario: 管理员查看消息列表
- **WHEN** 具备权限的管理员进入消息列表 Tab
- **THEN** 系统按自动回复记录平铺展示门店、用户消息、AI 回复、状态、RAG、模型、token、算力和时间

#### Scenario: 无权限用户访问页面
- **WHEN** 用户缺少 `system:im-auto-reply:list`
- **THEN** 前端不展示菜单且后端拒绝查询接口

### Requirement: 自动回复记录必须支持以门店为核心的常用搜索
系统 SHALL 支持按门店名称、`poi_id`、`epoi_id`、`open_user_id`、`order_id`、回复状态、RAG 使用情况和时间范围查询消息与会话。

#### Scenario: 按门店信息搜索
- **WHEN** 管理员输入门店名称或任一门店 ID
- **THEN** 消息列表和会话列表只返回匹配门店的记录

#### Scenario: 无范围正文搜索
- **WHEN** 管理员仅输入正文关键词且没有门店或时间限制
- **THEN** 系统拒绝无界扫描或要求补充范围条件

### Requirement: 会话列表必须显示聚合和最新活动
会话列表 SHALL 展示门店、顾客标识、总消息数、用户消息数、AI 回复数、Session 数、最新消息摘要、最新消息时间和最新 AI 回复时间，并按最新消息倒序。

#### Scenario: 新消息到达已有 Conversation
- **WHEN** Conversation 收到新用户或已发送 AI 消息
- **THEN** 会话列表更新聚合字段并将该会话移动到最新位置

### Requirement: 会话抽屉必须支持跨 Session 无限历史
管理后台 SHALL 以聊天样式显示统一消息时间线，首次打开定位到底部，向上滚动使用游标加载更早消息和历史 Session。

#### Scenario: 加载更早消息
- **WHEN** 用户滚动到当前已加载时间线顶部且 `hasMore=true`
- **THEN** 前端请求下一旧消息游标并把结果插入顶部
- **AND** 保持用户当前可见消息的滚动位置不跳动

#### Scenario: 加载到最早历史
- **WHEN** 接口返回 `hasMore=false`
- **THEN** 前端停止继续请求并显示已到达会话开始位置

### Requirement: 消息正文查询必须满足安全边界
系统 MUST 对所有日志接口执行登录、权限、门店范围校验，禁止在普通日志或默认导出中输出消息正文、完整 payload、密文、token 或秘密。

#### Scenario: 查询日志成功
- **WHEN** 合法用户查看回复或会话详情
- **THEN** 应用日志仅记录操作者、shopId、conversationId、查询范围和结果数量等安全元数据

